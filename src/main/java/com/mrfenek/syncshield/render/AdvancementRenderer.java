package com.mrfenek.syncshield.render;

import org.bukkit.inventory.ItemStack;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Locale;

public final class AdvancementRenderer {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 64;
    private static final Color TITLE_COLOR = new Color(234, 234, 2);
    private static final float FONT_SIZE = 16f;

    public byte[] renderAdvancement(String advancementTitle, String frameType, ItemStack icon, Color textColor) {
        try {
            BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();

            drawBackground(g);
            drawTitle(g, advancementTitle);
            drawSubtitle(g, frameType, textColor);
            drawIcon(g, icon);

            g.dispose();
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", out);
                return out.toByteArray();
            }
        } catch (Throwable e) {
            return new byte[0];
        }
    }

    private void drawBackground(Graphics2D g) {
        try {
            BufferedImage backgroundImage = TextureUtils.loadImage("/bg/advancement32.png");
            if (backgroundImage != null) {
                g.drawImage(backgroundImage, 0, 0, Color.BLACK, null);
                return;
            }
        } catch (Throwable ignored) {}
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, WIDTH, HEIGHT);
    }

    private void drawTitle(Graphics2D g, String title) {
        try {
            g.setFont(MinecraftFontLoader.getFont(FONT_SIZE));
            g.setColor(Color.WHITE);
            g.drawString(title != null ? title : "", 72, 60);
        } catch (Throwable ignored) {}
    }

    private void drawSubtitle(Graphics2D g, String frameType, Color textColor) {
        try {
            String subtitle;
            String f = frameType != null ? frameType.toLowerCase(Locale.ROOT) : "";
            switch (f) {
                case "goal":
                    subtitle = "Goal Reached!";
                    break;
                case "challenge":
                    subtitle = "Challenge Complete!";
                    break;
                default:
                    subtitle = "Advancement Made!";
                    break;
            }
            g.setFont(MinecraftFontLoader.getFont(FONT_SIZE));
            Color col = textColor != null ? textColor : Color.WHITE;
            g.setColor(col.equals(new Color(85, 255, 85)) ? TITLE_COLOR : col);
            g.drawString(subtitle, 72, 35);
        } catch (Throwable ignored) {}
    }

    private void drawIcon(Graphics2D g, ItemStack item) {
        BufferedImage texture = null;
        try {
            if (item != null && item.getType() != null) {
                texture = TextureUtils.loadItemTexture(item);
            }
        } catch (Throwable ignored) {}
        if (texture != null) {
            g.drawImage(texture, 20, 15, 32, 32, null);
        } else {
            g.setColor(Color.GRAY);
            g.fillRect(20, 15, 32, 32);
        }
    }
}
