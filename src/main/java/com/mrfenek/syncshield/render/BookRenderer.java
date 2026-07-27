package com.mrfenek.syncshield.render;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Book renderer using Monocraft as the exclusive font for book page text.
 */
public final class BookRenderer {
    private static final int MARGIN_LEFT = 40;
    private static final int MARGIN_TOP = 60;
    private static final int PAGE_WIDTH = 350;
    private static final int PAGE_HEIGHT = 432;

    private static final Font MONOCRAFT_FONT = MinecraftFontLoader.getFont(18f);

    public BookRenderResult renderBook(ItemStack book) {
        if (book == null || !(book.getItemMeta() instanceof BookMeta)) {
            return new BookRenderResult(new ArrayList<>(), null);
        }
        BookMeta meta = (BookMeta) book.getItemMeta();
        List<String> pages = new ArrayList<>();
        if (meta.hasPages()) {
            pages.addAll(meta.getPages());
        }
        if (pages.isEmpty()) {
            pages.add("");
        }

        String title = meta.hasTitle() && meta.getTitle() != null ? meta.getTitle() : null;
        String author = meta.hasAuthor() && meta.getAuthor() != null ? meta.getAuthor() : null;
        String caption;
        if (title != null && author != null) {
            caption = title + " by " + author;
        } else if (title != null) {
            caption = title;
        } else {
            caption = book.getType().name().toLowerCase(java.util.Locale.ROOT).contains("writable") ? "Book and Quill" : "Written Book";
        }

        List<byte[]> renderedPages = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            String page = pages.get(i);
            BufferedImage image = new BufferedImage(PAGE_WIDTH, PAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            renderBackground(g, image);
            drawBookText(g, page != null ? page : "", i + 1, pages.size());
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
        g.setFont(MONOCRAFT_FONT);
        FontMetrics fm = g.getFontMetrics(MONOCRAFT_FONT);

        int maxLineWidth = PAGE_WIDTH - MARGIN_LEFT * 2;
        int lineHeight = fm.getHeight();

        String pageNumber = "Page " + pageIndex + " of " + totalPages;
        int pageNumWidth = fm.stringWidth(pageNumber);
        g.drawString(pageNumber, PAGE_WIDTH - pageNumWidth - 30, MARGIN_TOP);

        int y = MARGIN_TOP + lineHeight + 10;
        String cleanPage = page.replace("\r", "");
        String[] rawLines = cleanPage.split("\n", -1);

        for (String rawLine : rawLines) {
            String line = ChatColor.stripColor(rawLine);
            if (line.trim().isEmpty()) {
                y += lineHeight;
                continue;
            }

            String current = "";
            String[] words = line.split(" ");
            for (String word : words) {
                int wordWidth = fm.stringWidth(word);
                if (wordWidth > maxLineWidth) {
                    StringBuilder currentChunk = new StringBuilder();
                    for (int offset = 0; offset < word.length(); ) {
                        int codePoint = word.codePointAt(offset);
                        int charCount = Character.charCount(codePoint);
                        String charStr = word.substring(offset, offset + charCount);

                        if (fm.stringWidth(currentChunk.toString() + charStr) > maxLineWidth) {
                            if (current.length() > 0) {
                                g.drawString(current, MARGIN_LEFT, y);
                                y += lineHeight;
                                current = "";
                            }
                            g.drawString(currentChunk.toString(), MARGIN_LEFT, y);
                            y += lineHeight;
                            currentChunk.setLength(0);
                        }
                        currentChunk.append(charStr);
                        offset += charCount;
                    }
                    if (currentChunk.length() > 0) {
                        current = currentChunk.toString();
                    }
                } else {
                    String test = current.isEmpty() ? word : current + " " + word;
                    if (fm.stringWidth(test) > maxLineWidth) {
                        g.drawString(current, MARGIN_LEFT, y);
                        y += lineHeight;
                        current = word;
                    } else {
                        current = test;
                    }
                }
            }
            if (!current.isEmpty()) {
                g.drawString(current, MARGIN_LEFT, y);
                y += lineHeight;
            }
        }
    }

    private void renderBackground(Graphics2D g, BufferedImage image) {
        BufferedImage bg = null;
        try (InputStream in = BookRenderer.class.getResourceAsStream("/bg/book.png")) {
            if (in != null) {
                bg = ImageIO.read(in);
            }
        } catch (Exception ignored) {}

        if (bg == null) {
            bg = TextureUtils.loadImage("/bg/book.png");
        }

        if (bg != null) {
            g.drawImage(bg, 0, 0, image.getWidth(), image.getHeight(), null);
        } else {
            g.setColor(new Color(245, 235, 205));
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
        }
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
