package com.mrfenek.syncshield.render;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Book renderer ported from the provided Kotlin reference.
 */
public final class BookRenderer {
    private static final int MARGIN_LEFT = 40;
    private static final int MARGIN_TOP = 60;
    private static final int PAGE_WIDTH = 350;
    private static final int PAGE_HEIGHT = 432;

    public BookRenderResult renderBook(ItemStack book) {
        if (!(book.getItemMeta() instanceof BookMeta)) return new BookRenderResult(new ArrayList<>(), null);
        BookMeta meta = (BookMeta) book.getItemMeta();
        List<String> pages = meta.getPages();
        String caption = meta.getTitle() != null ? meta.getTitle() + " by " + meta.getAuthor() : null;

        List<byte[]> renderedPages = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            String page = pages.get(i);
            BufferedImage image = new BufferedImage(PAGE_WIDTH, PAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            renderBackground(g, image);
            drawBookText(g, page, i + 1, pages.size());
            g.dispose();
            try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                ImageIO.write(image, "png", out);
                renderedPages.add(out.toByteArray());
            } catch (Exception ignored) {}
        }
        return new BookRenderResult(renderedPages, caption);
    }

    private void drawBookText(Graphics2D g, String page, int pageIndex, int totalPages) {
        g.setColor(Color.BLACK);
        g.setFont(MinecraftFontLoader.getFont(18f));
        FontMetrics fm = g.getFontMetrics();
        int maxLineWidth = PAGE_WIDTH - MARGIN_LEFT * 2;
        int lineHeight = fm.getHeight();

        String pageNumber = "Page " + pageIndex + " of " + totalPages;
        int width = fm.stringWidth(pageNumber);
        g.drawString(pageNumber, PAGE_WIDTH - width - 30, MARGIN_TOP);

        int y = MARGIN_TOP + lineHeight + 10;
        for (String rawLine : page.split("\n")) {
            String current = "";
            for (String word : rawLine.split(" ")) {
                if (fm.stringWidth(word) > maxLineWidth) {
                    for (String chunk : word.split("(?<=\\G.{19})")) {
                        y = writeChunk(g, fm, chunk, maxLineWidth, lineHeight, y, current);
                        current = chunk;
                    }
                } else {
                    String test = current.isEmpty() ? word : current + " " + word;
                    if (fm.stringWidth(test) > maxLineWidth) {
                        g.drawString(current.trim(), MARGIN_LEFT, y);
                        y += lineHeight - 4;
                        current = word;
                    } else {
                        current = test;
                    }
                }
            }
            if (!current.isEmpty()) {
                g.drawString(current.trim(), MARGIN_LEFT, y);
                y += lineHeight;
            }
        }
    }

    private int writeChunk(Graphics2D g, FontMetrics fm, String chunk, int maxWidth, int lineHeight, int y, String current) {
        String test = current.isEmpty() ? chunk : current + " " + chunk;
        if (fm.stringWidth(test) > maxWidth) {
            g.drawString(current.trim(), MARGIN_LEFT, y);
            return y + lineHeight - 4;
        }
        return y;
    }

    private void renderBackground(Graphics2D g, BufferedImage image) {
        BufferedImage bg = TextureUtils.loadImage("/book.png");
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        if (bg != null) g.drawImage(bg, 0, 0, null);
    }

    public static final class BookRenderResult {
        private final List<byte[]> pages;
        private final String caption;
        public BookRenderResult(List<byte[]> pages, String caption) {
            this.pages = pages;
            this.caption = caption;
        }
        public List<byte[]> getPages() { return pages; }
        public String getCaption() { return caption; }
    }
}
