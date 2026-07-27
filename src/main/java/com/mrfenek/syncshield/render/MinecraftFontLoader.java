package com.mrfenek.syncshield.render;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;

public final class MinecraftFontLoader {
    private static Font minecraftFont;

    static {
        loadMinecraftFont();
    }

    private MinecraftFontLoader() {}

    private static void loadMinecraftFont() {
        try (InputStream fontStream = MinecraftFontLoader.class.getResourceAsStream("/bg/Monocraft.ttf")) {
            if (fontStream != null) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, fontStream);
                try {
                    GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                } catch (Throwable ignored) {}
                minecraftFont = font;
            }
        } catch (Throwable ignored) {
            minecraftFont = null;
        }
    }

    public static Font getFont(float size) {
        try {
            if (minecraftFont != null) {
                return minecraftFont.deriveFont(size);
            }
        } catch (Throwable ignored) {}
        return new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size));
    }
}
