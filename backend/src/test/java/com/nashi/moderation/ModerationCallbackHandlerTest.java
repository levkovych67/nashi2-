package com.nashi.moderation;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistRepository;
import com.nashi.config.NashiProperties;
import com.nashi.submission.ModerationAction;
import com.nashi.submission.ModerationEntry;
import com.nashi.submission.Moderator;
import com.nashi.submission.Submission;
import com.nashi.submission.SubmissionStatus;
import com.nashi.telegram.ApproverPolicy;
import com.nashi.telegram.ChatAdmins;
import com.nashi.telegram.TelegramGateway;
import com.nashi.telegram.TelegramGatewayException;
import com.nashi.telegram.TelegramProperties;
import com.nashi.telegram.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModerationCallbackHandlerTest {

    private static final long CHAT = -1001L;
    private static final int CARD_MESSAGE = 77;
    private static final String SID = "6560a1b2c3d4e5f6a7b8c9d0";
    private static final String SITE = "https://nashi.example";
    /** 12:05 in Kyiv (UTC+2 in November). */
    private static final Instant DECIDED = Instant.parse("2026-11-18T10:05:00Z");
    private static final Moderator ADMIN1 = new Moderator(42L, "admin1", "Оксана");

    private final ModerationService moderation = mock(ModerationService.class);
    private final TelegramGateway gateway = mock(TelegramGateway.class);
    private final ChatAdmins admins = mock(ChatAdmins.class);
    private final SubmissionCardFormatter cards = mock(SubmissionCardFormatter.class);
    private final ArtistRepository artists = mock(ArtistRepository.class);

    @BeforeEach
    void card() {
        when(cards.card(any(Submission.class), anyInt())).thenReturn("CARD");
        when(admins.contains(42L)).thenReturn(true);
    }

    private ModerationCallbackHandler handler(ApproverPolicy policy) {
        TelegramProperties telegram = new TelegramProperties("t", false, true,
                new TelegramProperties.Moderation(CHAT, null, policy));
        NashiProperties nashi = new NashiProperties(null, null, null, null, null, null, SITE);
        return new ModerationCallbackHandler(moderation, gateway, admins, telegram, cards, artists, nashi);
    }

    private static CallbackQuery query(String data, User from) {
        Message message = new Message();
        message.setMessageId(CARD_MESSAGE);
        message.setChat(Chat.builder().id(CHAT).type("supergroup").build());
        CallbackQuery q = new CallbackQuery();
        q.setId("q1");
        q.setData(data);
        q.setFrom(from);
        q.setMessage(message);
        return q;
    }

    private static User user(long id, String username, String firstName, String lastName) {
        User u = new User(id, firstName, false);
        u.setUserName(username);
        u.setLastName(lastName);
        return u;
    }

    private static User admin1() {
        return user(42L, "admin1", "Оксана", null);
    }

    private static Submission decided(SubmissionStatus status, ModerationAction action, Moderator by) {
        return Submission.builder().id(SID).status(status).name("Океан Ельзи").decidedAt(DECIDED).artistId("a1")
                .moderationLog(List.of(new ModerationEntry(ModerationAction.SENT, null, DECIDED.minusSeconds(60), null),
                        new ModerationEntry(action, by, DECIDED, null)))
                .build();
    }

    private static Artist artist() {
        return Artist.builder().id("a1").slug("okean-elzy").name("Океан Ельзи").build();
    }

    @Test
    void unknownButtonIsAnswered() {
        handler(ApproverPolicy.CHAT_ADMINS).handle(query("approve_123", admin1()));

        verify(gateway).answerCallback("q1", "Невідома кнопка");
        verifyNoInteractions(moderation);
    }

    @Test
    void nonAdminCannotModerate() {
        handler(ApproverPolicy.CHAT_ADMINS).handle(query("sub:" + SID + ":y", user(7L, "stranger", "Петро", null)));

        verify(gateway).answerCallback("q1", "Модерувати можуть лише адміністратори групи");
        verifyNoInteractions(moderation);
        verify(gateway, never()).editControl(anyInt(), anyString());
    }

    @Test
    void anyMemberPolicyAllowsEveryone() {
        Moderator stranger = new Moderator(7L, "stranger", "Петро");
        when(moderation.decide(SID, Verdict.NO, stranger)).thenReturn(new DecisionResult(Verdict.NO,
                decided(SubmissionStatus.REJECTED, ModerationAction.REJECTED, stranger), null));

        handler(ApproverPolicy.ANY_MEMBER).handle(query("sub:" + SID + ":n", user(7L, "stranger", "Петро", null)));

        verify(moderation).decide(SID, Verdict.NO, stranger);
        verifyNoInteractions(admins);
        verify(gateway).answerCallback("q1", "Готово");
    }

    @Test
    void approvalEditsTheCardWithWhoWhenAndThePage() {
        when(moderation.decide(SID, Verdict.YES, ADMIN1)).thenReturn(new DecisionResult(Verdict.YES,
                decided(SubmissionStatus.APPROVED, ModerationAction.APPROVED, ADMIN1), artist()));

        handler(ApproverPolicy.CHAT_ADMINS).handle(query("sub:" + SID + ":y", admin1()));

        verify(gateway).editControl(CARD_MESSAGE, "CARD\n\n✅ Схвалено — @admin1, 18.11 12:05 · "
                + "<a href=\"https://nashi.example/m/okean-elzy\">сторінка</a>");
        verify(gateway).answerCallback("q1", "Готово");
    }

    @Test
    void rejectionEditsTheCard() {
        when(moderation.decide(SID, Verdict.NO, ADMIN1)).thenReturn(new DecisionResult(Verdict.NO,
                decided(SubmissionStatus.REJECTED, ModerationAction.REJECTED, ADMIN1), null));

        handler(ApproverPolicy.CHAT_ADMINS).handle(query("sub:" + SID + ":n", admin1()));

        verify(gateway).editControl(CARD_MESSAGE, "CARD\n\n❌ Відхилено — @admin1, 18.11 12:05");
        verify(gateway).answerCallback("q1", "Готово");
    }

    @Test
    void moderatorWithoutUsernameIsShownByEscapedName() {
        Moderator noUsername = new Moderator(42L, null, "Оксана <К>");
        when(moderation.decide(SID, Verdict.NO, noUsername)).thenReturn(new DecisionResult(Verdict.NO,
                decided(SubmissionStatus.REJECTED, ModerationAction.REJECTED, noUsername), null));

        handler(ApproverPolicy.CHAT_ADMINS).handle(query("sub:" + SID + ":n", user(42L, null, "Оксана", "<К>")));

        verify(gateway).editControl(CARD_MESSAGE, "CARD\n\n❌ Відхилено — Оксана &lt;К&gt;, 18.11 12:05");
    }

    @Test
    void alwaysAnswersCallback() {
        when(moderation.decide(any(), any(), any())).thenThrow(new IllegalStateException("boom"));
        handler(ApproverPolicy.CHAT_ADMINS).handle(query("sub:" + SID + ":y", admin1()));
        verify(gateway, times(1)).answerCallback("q1", "Помилка, спробуйте ще раз");

        when(admins.contains(42L)).thenThrow(new IllegalStateException("unexpected"));
        handler(ApproverPolicy.CHAT_ADMINS).handle(query("sub:" + SID + ":y", admin1()));
        verify(gateway, times(2)).answerCallback("q1", "Помилка, спробуйте ще раз");
    }

    @Test
    void failedApprovalKeepsButtonsForARetry() {
        when(moderation.decide(any(), any(), any())).thenThrow(new IllegalStateException("S3 down"));

        handler(ApproverPolicy.CHAT_ADMINS).handle(query("sub:" + SID + ":y", admin1()));

        verify(gateway).sendControl("⚠️ Помилка, спробуйте ще раз", SubmissionCardFormatter.buttons(SID), CARD_MESSAGE);
        verify(gateway, never()).editControl(anyInt(), anyString());
        verify(gateway, times(1)).answerCallback("q1", "Помилка, спробуйте ще раз");
    }

    @Test
    void oldButtonEditsMessageAndExplains() {
        Moderator admin2 = new Moderator(43L, "admin2", "Іван");
        when(moderation.decide(SID, Verdict.YES, ADMIN1)).thenThrow(new AlreadyDecidedException(
                decided(SubmissionStatus.APPROVED, ModerationAction.APPROVED, admin2)));
        when(artists.findById("a1")).thenReturn(Optional.of(artist()));

        handler(ApproverPolicy.CHAT_ADMINS).handle(query("sub:" + SID + ":y", admin1()));

        verify(gateway).editControl(CARD_MESSAGE, "CARD\n\n✅ Схвалено — @admin2, 18.11 12:05 · "
                + "<a href=\"https://nashi.example/m/okean-elzy\">сторінка</a>");
        verify(gateway).answerCallback("q1", "Заявку вже оброблено (статус: Схвалено)");
    }

    @Test
    void oldButtonOnARejectedSubmission() {
        when(moderation.decide(SID, Verdict.YES, ADMIN1)).thenThrow(new AlreadyDecidedException(
                decided(SubmissionStatus.REJECTED, ModerationAction.REJECTED, ADMIN1)));

        handler(ApproverPolicy.CHAT_ADMINS).handle(query("sub:" + SID + ":y", admin1()));

        verify(gateway).editControl(CARD_MESSAGE, "CARD\n\n❌ Відхилено — @admin1, 18.11 12:05");
        verify(gateway).answerCallback("q1", "Заявку вже оброблено (статус: Відхилено)");
    }

    @Test
    void approvalRolledBackMeanwhileAsksToRetry() {
        Submission pending = Submission.builder().id(SID).status(SubmissionStatus.PENDING).build();
        when(moderation.decide(SID, Verdict.YES, ADMIN1)).thenThrow(new AlreadyDecidedException(pending));

        handler(ApproverPolicy.CHAT_ADMINS).handle(query("sub:" + SID + ":y", admin1()));

        verify(gateway, never()).editControl(anyInt(), anyString());
        verify(gateway).answerCallback("q1", "Помилка, спробуйте ще раз");
    }

    @Test
    void failedEditStillAnswersDone() {
        when(moderation.decide(SID, Verdict.YES, ADMIN1)).thenReturn(new DecisionResult(Verdict.YES,
                decided(SubmissionStatus.APPROVED, ModerationAction.APPROVED, ADMIN1), artist()));
        doThrow(new TelegramGatewayException("Telegram editMessageText failed", null))
                .when(gateway).editControl(anyInt(), anyString());

        handler(ApproverPolicy.CHAT_ADMINS).handle(query("sub:" + SID + ":y", admin1()));

        verify(gateway, times(1)).answerCallback(eq("q1"), anyString());
        verify(gateway).answerCallback("q1", "Готово");
    }
}
