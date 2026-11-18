package com.nashi.admin;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A command sent by an administrator in the moderation group: {@code /hide <slug або Q-id>}, {@code /show ...},
 * {@code /stats}, {@code /wiki_import}, {@code /audio_resolve}, {@code /ping}.
 *
 * @param argument the text after the command, trimmed; empty when there is none
 */
public record AdminCommand(Action action, String argument) {

    public enum Action { HIDE, SHOW, STATS, WIKI_IMPORT, AUDIO_RESOLVE, PING }

    private static final Pattern COMMAND = Pattern.compile(
            "/(hide|show|stats|wiki_import|audio_resolve|ping)(?:@\\w+)?(?:\\s+(.*))?", Pattern.DOTALL);

    /** The command in {@code text} ("/hide x", "/show@bot_name x", "/ping"), or empty for any other text. */
    public static Optional<AdminCommand> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher matcher = COMMAND.matcher(text.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        Action action = Action.valueOf(matcher.group(1).toUpperCase(Locale.ROOT));
        String argument = matcher.group(2) == null ? "" : matcher.group(2).trim();
        return Optional.of(new AdminCommand(action, argument));
    }

    /** How to use the command, for a bare "/hide" or "/show". */
    public String usage() {
        return "Використання: /" + action.name().toLowerCase(Locale.ROOT) + " <slug або Q-id>";
    }
}
