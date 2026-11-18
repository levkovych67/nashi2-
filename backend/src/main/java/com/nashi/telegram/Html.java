package com.nashi.telegram;

/** Escaping for Telegram's {@code parse_mode=HTML}. */
public final class Html {

    private Html() {
    }

    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
