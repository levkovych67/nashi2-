package com.nashi.moderation;

import com.nashi.media.FileStorage;
import com.nashi.submission.ModerationAction;
import com.nashi.submission.ModerationEntry;
import com.nashi.submission.Submission;
import com.nashi.submission.SubmissionCreatedEvent;
import com.nashi.submission.SubmissionRepository;
import com.nashi.submission.SubmissionStatus;
import com.nashi.submission.TelegramRefs;
import com.nashi.telegram.ApproverPolicy;
import com.nashi.telegram.InlineButton;
import com.nashi.telegram.NamedBytes;
import com.nashi.telegram.TelegramGateway;
import com.nashi.telegram.TelegramGatewayException;
import com.nashi.telegram.TelegramProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class SubmissionNotifierTest {

    private static final String SID = "6560a1b2c3d4e5f6a7b8c9d0";
    private static final long CHAT = -1001L;
    private static final int THREAD = 7;
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private static final byte[] PHOTO0 = {1, 2, 3};
    private static final byte[] PHOTO1 = {4, 5, 6};

    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final TelegramGateway gateway = mock(TelegramGateway.class);
    private final SubmissionCardFormatter cards = mock(SubmissionCardFormatter.class);

    @BeforeEach
    void stubs() {
        when(storage.get("submissions/" + SID + "/0.jpg")).thenReturn(PHOTO0);
        when(storage.get("submissions/" + SID + "/1.jpg")).thenReturn(PHOTO1);
        when(cards.card(any(Submission.class))).thenReturn("CARD");
        when(gateway.sendAlbum(anyList())).thenReturn(List.of(501, 502));
        when(gateway.sendControl(anyString(), anyList(), any())).thenReturn(503);
    }

    private SubmissionNotifier notifier(boolean sendingEnabled) {
        TelegramProperties telegram = new TelegramProperties("t", false, sendingEnabled,
                new TelegramProperties.Moderation(CHAT, THREAD, ApproverPolicy.CHAT_ADMINS));
        return new SubmissionNotifier(submissions, storage, gateway, telegram, cards, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Submission.SubmissionBuilder pending() {
        String p = "submissions/" + SID + "/";
        return Submission.builder().id(SID).version(0L).status(SubmissionStatus.PENDING).name("Океан Ельзи")
                .imageKeys(List.of(p + "0.jpg", p + "0-thumb.jpg", p + "1.jpg", p + "1-thumb.jpg"))
                .moderationLog(List.of());
    }

    private void stored(Submission s) {
        when(submissions.findById(SID)).thenReturn(Optional.of(s));
    }

    private Submission saved() {
        ArgumentCaptor<Submission> saved = ArgumentCaptor.forClass(Submission.class);
        verify(submissions).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void sendsAlbumThenControlWithButtons() {
        stored(pending().build());

        notifier(true).onCreated(new SubmissionCreatedEvent(SID));

        ArgumentCaptor<List<NamedBytes>> album = ArgumentCaptor.forClass(List.class);
        InOrder order = inOrder(gateway);
        order.verify(gateway).sendAlbum(album.capture());
        order.verify(gateway).sendControl("CARD", List.of(List.of(
                new InlineButton("✅ Так", "sub:" + SID + ":y"),
                new InlineButton("❌ Ні", "sub:" + SID + ":n"))), 501);
        assertThat(album.getValue()).extracting(NamedBytes::filename).containsExactly("0.jpg", "1.jpg");
        assertThat(album.getValue()).extracting(NamedBytes::bytes).containsExactly(PHOTO0, PHOTO1);

        Submission s = saved();
        assertThat(s.getTelegram()).isEqualTo(new TelegramRefs(CHAT, THREAD, List.of(501, 502), 503));
        assertThat(s.getModerationLog()).containsExactly(new ModerationEntry(ModerationAction.SENT, null, NOW, null));
    }

    @Test
    void possibleDuplicateIsLogged() {
        stored(pending().duplicateOfArtistId("a1").build());

        notifier(true).send(SID);

        assertThat(saved().getModerationLog()).containsExactly(
                new ModerationEntry(ModerationAction.SENT, null, NOW, null),
                new ModerationEntry(ModerationAction.DUPLICATE_WARNING, null, NOW, "a1"));
    }

    @Test
    void failureIsLoggedForRetry() {
        stored(pending().build());
        when(gateway.sendControl(anyString(), anyList(), any()))
                .thenThrow(new TelegramGatewayException("Telegram sendMessage failed: Bad Request", null));

        notifier(true).send(SID);

        Submission s = saved();
        assertThat(s.getTelegram()).as("the album is kept so a retry sends only the control message")
                .isEqualTo(new TelegramRefs(CHAT, THREAD, List.of(501, 502), null));
        ModerationEntry last = s.getModerationLog().getLast();
        assertThat(last.action()).isEqualTo(ModerationAction.FAILED);
        assertThat(last.by()).isNull();
        assertThat(last.at()).isEqualTo(NOW);
        assertThat(last.note()).isEqualTo("TelegramGatewayException: Telegram sendMessage failed: Bad Request");
    }

    @Test
    void failureBeforeAnythingWasSentKeepsNoRefs() {
        stored(pending().build());
        when(storage.get("submissions/" + SID + "/1.jpg")).thenThrow(new IllegalStateException("S3 down"));

        notifier(true).send(SID);

        Submission s = saved();
        assertThat(s.getTelegram()).isNull();
        assertThat(s.getModerationLog()).extracting(ModerationEntry::action).containsExactly(ModerationAction.FAILED);
        verifyNoInteractions(gateway);
    }

    @Test
    void retryWithAnAlbumSendsOnlyTheControlMessage() {
        stored(pending().telegram(new TelegramRefs(CHAT, THREAD, List.of(501, 502), null))
                .moderationLog(List.of(new ModerationEntry(ModerationAction.FAILED, null, NOW, "x"))).build());

        notifier(true).send(SID);

        verify(gateway, never()).sendAlbum(anyList());
        verify(storage, never()).get(anyString());
        verify(gateway).sendControl(eq("CARD"), anyList(), eq(501));
        Submission s = saved();
        assertThat(s.getTelegram()).isEqualTo(new TelegramRefs(CHAT, THREAD, List.of(501, 502), 503));
        assertThat(s.getModerationLog()).extracting(ModerationEntry::action)
                .containsExactly(ModerationAction.FAILED, ModerationAction.SENT);
    }

    @Test
    void disabledSendingLeavesTheSubmissionUnsent() {
        stored(pending().build());

        notifier(false).send(SID);

        verifyNoInteractions(gateway, storage);
        verify(submissions, never()).save(any());
    }

    @Test
    void controlIdMinusOneIsNotStoredAsSent() {
        stored(pending().build());
        when(gateway.sendAlbum(anyList())).thenReturn(List.of());
        when(gateway.sendControl(anyString(), anyList(), any())).thenReturn(-1);

        notifier(true).send(SID);

        verify(gateway).sendControl(eq("CARD"), anyList(), eq(null));
        verify(submissions, never()).save(any());
    }

    @Test
    void decidedOrAlreadySentSubmissionsAreSkipped() {
        stored(pending().status(SubmissionStatus.APPROVED).build());
        notifier(true).send(SID);

        stored(pending().telegram(new TelegramRefs(CHAT, THREAD, List.of(501), 503)).build());
        notifier(true).send(SID);

        when(submissions.findById(SID)).thenReturn(Optional.empty());
        notifier(true).send(SID);

        verify(gateway, never()).sendAlbum(anyList());
        verify(gateway, never()).sendControl(anyString(), anyList(), any());
        verify(gateway, never()).editControl(anyInt(), anyString());
        verify(submissions, never()).save(any());
    }
}
