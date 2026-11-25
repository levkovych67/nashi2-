package com.nashi.bot;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code /hide <slug або Q-id>} or {@code /show <slug або Q-id>} sent in the admin chat.
 *
 * @param argument the slug or Wikidata id, trimmed; empty when the admin sent the bare command
 */
public record AdminCommand(Action action, String argument) {

    public enum Action { HIDE, SHOW }

    private static final Pattern COMMAND = Pattern.compile("/(hide|show)(?:@\\w+)?(?:\\s+(.*))?", Pattern.DOTALL);

    /** The command in {@code text} ("/hide x", "/show@bot_name x", "/hide"), or empty for any other text. */
    public static Optional<AdminCommand> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher matcher = COMMAND.matcher(text.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        Action action = "hide".equals(matcher.group(1)) ? Action.HIDE : Action.SHOW;
        String argument = matcher.group(2) == null ? "" : matcher.group(2).trim();
        return Optional.of(new AdminCommand(action, argument));
    }

    /** How to use the command, for a bare "/hide" or "/show". */
    public String usage() {
        return "Використання: /" + action.name().toLowerCase() + " <slug або Q-id>";
    }
}
