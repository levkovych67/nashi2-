package com.nashi.moderation;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistRepository;
import com.nashi.config.ClockConfig;
import com.nashi.config.NashiProperties;
import com.nashi.submission.ModerationAction;
import com.nashi.submission.ModerationEntry;
import com.nashi.submission.Moderator;
import com.nashi.submission.Submission;
import com.nashi.submission.SubmissionStatus;
import com.nashi.telegram.ApproverPolicy;
import com.nashi.telegram.CallbackData;
import com.nashi.telegram.CallbackQueryHandler;
import com.nashi.telegram.ChatAdmins;
import com.nashi.telegram.Html;
import com.nashi.telegram.TelegramGateway;
import com.nashi.telegram.TelegramProperties;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * «✅ Так» / «❌ Ні» presses in the moderation chat. Every press is answered exactly once, whatever happens, so the
 * button never keeps spinning. A decision edits the card: the buttons go and «Схвалено/Відхилено — who, when» is
 * added. When approving fails the card is not touched (it keeps its buttons) and a reply with the same buttons asks
 * to try again: {@link TelegramGateway#editControl} always removes the buttons, so it cannot show the error itself.
 */
@Component
public class ModerationCallbackHandler implements CallbackQueryHandler {

    static final String UNKNOWN_BUTTON = "Невідома кнопка";
    static final String NOT_ADMIN = "Модерувати можуть лише адміністратори групи";
    static final String DONE = "Готово";
    static final String ERROR = "Помилка, спробуйте ще раз";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ClockConfig.KYIV);
    private static final int MODERATOR_NAME_MAX = 100;
    private static final Logger log = LoggerFactory.getLogger(ModerationCallbackHandler.class);

    private final ModerationService moderation;
    private final TelegramGateway gateway;
    private final ChatAdmins admins;
    private final TelegramProperties telegram;
    private final SubmissionCardFormatter cards;
    private final ArtistRepository artists;
    private final String siteBaseUrl;

    public ModerationCallbackHandler(ModerationService moderation, TelegramGateway gateway, ChatAdmins admins,
                                     TelegramProperties telegram, SubmissionCardFormatter cards,
                                     ArtistRepository artists, NashiProperties properties) {
        this.moderation = moderation;
        this.gateway = gateway;
        this.admins = admins;
        this.telegram = telegram;
        this.cards = cards;
        this.artists = artists;
        this.siteBaseUrl = properties.siteBaseUrl();
    }

    @Override
    public void handle(CallbackQuery query) {
        String answer = ERROR;
        try {
            answer = process(query);
        } catch (RuntimeException e) {
            log.error("Moderation callback failed", e);
        } finally {
            try {
                gateway.answerCallback(query.getId(), answer);
            } catch (RuntimeException e) {
                log.warn("Could not answer a callback query: {}", e.getMessage());
            }
        }
    }

    /** Does what the press asks for and returns the text to answer it with. */
    private String process(CallbackQuery query) {
        Optional<CallbackData> data = CallbackData.parse(query.getData());
        if (data.isEmpty()) {
            return UNKNOWN_BUTTON;
        }
        User from = query.getFrom();
        if (telegram.moderation().approverPolicy() == ApproverPolicy.CHAT_ADMINS && !admins.contains(from.getId())) {
            return NOT_ADMIN;
        }
        String submissionId = data.get().submissionId();
        int messageId = query.getMessage().getMessageId();
        DecisionResult result;
        try {
            result = moderation.decide(submissionId, data.get().verdict(), moderator(from));
        } catch (AlreadyDecidedException e) {
            return alreadyDecided(e, messageId);
        } catch (RuntimeException e) {
            log.error("Moderating submission {} failed", submissionId, e);
            offerRetry(submissionId, messageId);
            return ERROR;
        }
        Submission decided = result.submission();
        String slug = result.artist() == null ? null : result.artist().getSlug();
        edit(messageId, () -> decidedCard(decided, slug));
        return DONE;
    }

    private String alreadyDecided(AlreadyDecidedException e, int messageId) {
        Submission current = e.current();
        SubmissionStatus status = e.currentStatus();
        if (status == SubmissionStatus.PENDING) {
            // an approval failed and was rolled back after this press lost the race: pressing again will work
            return ERROR;
        }
        if (current == null || status == null) {
            return "Заявку не знайдено";
        }
        if (status == SubmissionStatus.APPROVED || status == SubmissionStatus.REJECTED) {
            edit(messageId, () -> decidedCard(current, slugOf(current)));
        }
        return "Заявку вже оброблено (статус: " + statusName(status) + ")";
    }

    private @Nullable String slugOf(Submission s) {
        if (s.getStatus() != SubmissionStatus.APPROVED || s.getArtistId() == null) {
            return null;
        }
        return artists.findById(s.getArtistId()).map(Artist::getSlug).orElse(null);
    }

    /** The card with the line that records the decision, together within Telegram's limit. */
    private String decidedCard(Submission s, @Nullable String slug) {
        String outcome = outcome(s, slug);
        return cards.card(s, SubmissionCardFormatter.MAX_LENGTH - outcome.length()) + outcome;
    }

    private String outcome(Submission s, @Nullable String slug) {
        boolean approved = s.getStatus() == SubmissionStatus.APPROVED;
        ModerationEntry decision = lastEntry(s, approved ? ModerationAction.APPROVED : ModerationAction.REJECTED);
        Instant at = decision != null && decision.at() != null ? decision.at() : s.getDecidedAt();
        String line = "\n\n" + (approved ? "✅ Схвалено" : "❌ Відхилено") + " — "
                + (decision == null ? "?" : who(decision.by())) + ", " + (at == null ? "?" : TIME.format(at));
        if (approved && slug != null) {
            line += " · <a href=\"" + Html.escape(siteBaseUrl + "/m/" + slug) + "\">сторінка</a>";
        }
        return line;
    }

    private static @Nullable ModerationEntry lastEntry(Submission s, ModerationAction action) {
        List<ModerationEntry> entries = s.getModerationLog();
        if (entries == null) {
            return null;
        }
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).action() == action) {
                return entries.get(i);
            }
        }
        return null;
    }

    /** {@code @username}, or the escaped display name when the user has no username. */
    private static String who(@Nullable Moderator by) {
        if (by == null) {
            return "?";
        }
        if (by.username() != null && !by.username().isBlank()) {
            return "@" + Html.escape(by.username());
        }
        if (by.displayName() != null && !by.displayName().isBlank()) {
            return SubmissionCardFormatter.clip(by.displayName(), MODERATOR_NAME_MAX);
        }
        return "id " + by.telegramUserId();
    }

    private static Moderator moderator(User from) {
        String displayName = from.getFirstName() == null ? "" : from.getFirstName();
        if (from.getLastName() != null && !from.getLastName().isBlank()) {
            displayName = (displayName + " " + from.getLastName()).trim();
        }
        return new Moderator(from.getId(), from.getUserName(), displayName);
    }

    private static String statusName(SubmissionStatus status) {
        return switch (status) {
            case APPROVED -> "Схвалено";
            case REJECTED -> "Відхилено";
            case DISCARDED -> "Відкинуто";
            case PENDING -> "Очікує";
        };
    }

    /** Edits the message; a failure (Telegram, or building the text) is logged and does not change the answer. */
    private void edit(int messageId, Supplier<String> html) {
        try {
            gateway.editControl(messageId, html.get());
        } catch (RuntimeException e) {
            log.warn("Could not edit moderation message {}: {}", messageId, e.getMessage());
        }
    }

    private void offerRetry(String submissionId, int cardMessageId) {
        try {
            gateway.sendControl("⚠️ " + ERROR, SubmissionCardFormatter.buttons(submissionId), cardMessageId);
        } catch (RuntimeException e) {
            log.warn("Could not offer a retry for submission {}: {}", submissionId, e.getMessage());
        }
    }
}
