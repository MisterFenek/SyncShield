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
        try (InputStream fontStream = MinecraftFontLoader.class.getResourceAsStream("/bg/Minecraftia-Regular.ttf")) {
            if (fontStream != null) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, fontStream);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                minecraftFont = font;
            }
        } catch (Exception ignored) {
            minecraftFont = null;
        }
    }

    public static Font getFont(float size) {
        if (minecraftFont != null) {
            return minecraftFont.deriveFont(size);
        }
        return new Font("SansSerif", Font.BOLD, Math.round(size));
    }
}
