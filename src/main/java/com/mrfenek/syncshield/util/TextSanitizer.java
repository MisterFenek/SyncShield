package com.mrfenek.syncshield.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Pure (Bukkit-free) text sanitization helpers shared by the Minecraft, Telegram and
 * Discord code paths. Kept free of Bukkit imports so it can be unit tested in isolation.
 */
public final class TextSanitizer {

    private static final char SECTION_CHAR = '\u00A7'; // Minecraft formatting char
    private static final String COLOR_CODE_CHARS = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    /** Commands whose arguments frequently contain credentials; never relayed to chat platforms. */
    private static final Set<String> SENSITIVE_COMMANDS = new HashSet<>(Arrays.asList(
            "login", "log", "l", "register", "reg", "unregister", "changepassword", "changepw",
            "password", "passwd", "authme", "2fa", "otp"));

    private TextSanitizer() {}

    /**
     * Removes Minecraft legacy formatting codes written with '&' or '\u00A7' prefixes.
     * Non-code characters (including a lone '&' or '\u00A7') are preserved.
     */
    public static String stripFormatting(String input) {
        if (input == null) return "";
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == SECTION_CHAR) && i + 1 < input.length()
                    && COLOR_CODE_CHARS.indexOf(input.charAt(i + 1)) >= 0) {
                i++; // skip the code character as well
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /** Escapes the characters that are special inside Telegram HTML parse mode. */
    public static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Makes untrusted user content safe to post to Discord: neutralizes @everyone/@here
     * pings with a zero-width space and strips Minecraft formatting codes.
     */
    public static String sanitizeDiscord(String input) {
        if (input == null) return "";
        String out = stripFormatting(input);
        out = out.replace("@everyone", "@\u200Beveryone");
        out = out.replace("@here", "@\u200Bhere");
        return out;
    }

    /**
     * Extracts the base command from a raw command line:
     * "/kick Notch griefing" &rarr; "kick", "essentials:kick Notch" &rarr; "kick".
     */
    public static String baseCommand(String commandLine) {
        if (commandLine == null) return "";
        String s = commandLine.trim();
        if (s.startsWith("/")) s = s.substring(1);
        int space = s.indexOf(' ');
        if (space >= 0) s = s.substring(0, space);
        int colon = s.indexOf(':');
        if (colon >= 0 && colon + 1 < s.length()) s = s.substring(colon + 1);
        return s.toLowerCase(Locale.ROOT);
    }

    /** True if the base command commonly carries credentials and must not be relayed. */
    public static boolean isSensitiveCommand(String baseCommand) {
        if (baseCommand == null) return false;
        return SENSITIVE_COMMANDS.contains(baseCommand.toLowerCase(Locale.ROOT));
    }

    /** Truncates the input to {@code max} characters, appending an ellipsis when cut. */
    public static String truncate(String input, int max) {
        if (input == null) return "";
        if (max <= 0) return "";
        if (input.length() <= max) return input;
        return input.substring(0, max) + "\u2026";
    }
}
