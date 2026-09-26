package com.mrfenek.syncshield;

import com.mrfenek.syncshield.util.TextSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextSanitizerTest {

    @Test
    void stripFormattingRemovesAmpersandColorCodes() {
        assertEquals("hello world", TextSanitizer.stripFormatting("&ahello &lworld"));
        assertEquals("hello", TextSanitizer.stripFormatting("§ahello"));
    }

    @Test
    void stripFormattingPreservesLoneAmpersand() {
        assertEquals("100% & more", TextSanitizer.stripFormatting("100% & more"));
        assertEquals("a & b", TextSanitizer.stripFormatting("a & b"));
    }

    @Test
    void escapeHtmlEscapesAllSpecials() {
        assertEquals("&amp;lt;b&amp;gt;", TextSanitizer.escapeHtml("&lt;b&gt;"));
        assertEquals("&amp;", TextSanitizer.escapeHtml("&"));
    }

    @Test
    void sanitizeDiscordNeutralizesPingsAndFormatting() {
        String out = TextSanitizer.sanitizeDiscord("&4@everyone look §a@here");
        assertFalse(out.contains("@everyone"));
        assertFalse(out.contains("@here"));
        assertFalse(out.contains("&4"));
        assertTrue(out.contains("everyone"));
    }

    @Test
    void baseCommandParsesNamespacedCommands() {
        assertEquals("kick", TextSanitizer.baseCommand("/kick Notch griefing"));
        assertEquals("kick", TextSanitizer.baseCommand("essentials:kick Notch"));
        assertEquals("kick", TextSanitizer.baseCommand("essentials:kick"));
        assertEquals("", TextSanitizer.baseCommand(null));
        assertEquals("", TextSanitizer.baseCommand("   "));
    }

    @Test
    void sensitiveCommandsAreFlagged() {
        assertTrue(TextSanitizer.isSensitiveCommand("login"));
        assertTrue(TextSanitizer.isSensitiveCommand("l"));
        assertTrue(TextSanitizer.isSensitiveCommand("changepassword"));
        assertFalse(TextSanitizer.isSensitiveCommand("kick"));
        assertFalse(TextSanitizer.isSensitiveCommand(null));
    }

    @Test
    void truncateRespectsLimitAndAppendsEllipsis() {
        assertEquals("abc", TextSanitizer.truncate("abc", 10));
        assertEquals(201, TextSanitizer.truncate("x".repeat(300), 200).length());
        assertEquals("", TextSanitizer.truncate("abc", 0));
        assertEquals("", TextSanitizer.truncate(null, 5));
    }

    @Test
    void nullAndEmptyInputsAreSafe() {
        assertEquals("", TextSanitizer.stripFormatting(null));
        assertEquals("", TextSanitizer.escapeHtml(null));
        assertEquals("", TextSanitizer.sanitizeDiscord(null));
    }
}
