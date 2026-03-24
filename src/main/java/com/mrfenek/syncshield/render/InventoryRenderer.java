package com.mrfenek.syncshield.render;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Simplified inventory renderer ported from the provided Kotlin reference.
 */
public final class InventoryRenderer {
    private static final int SLOT_SIZE = 32;
    private static final int PADDING = 4;
    private static final int BORDER = 16;
    private static final int BOTTOM_PADDING = 8;

    private final BufferedImage background;
    private final Logger logger = Logger.getLogger("InventoryRenderer");

    public InventoryRenderer() {
        this.background = TextureUtils.loadImage("/bg/inventoryBackground.png");
    }

    public byte[] renderInventory(Inventory inventory) {
        if (background == null) return new byte[0];
        int columns = 9;
        int rows = 5;
        BufferedImage image = new BufferedImage(background.getWidth(), background.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.drawImage(background, 0, 0, null);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                if (row == 0 && col >= 5) continue; // skip extra armor/offhand slots
                int index;
                switch (row) {
                    case 0:
                        index = 36 + col;
                        break;
                    case 1:
                        index = 9 + col;
                        break;
                    case 2:
                        index = 18 + col;
                        break;
                    case 3:
                        index = 27 + col;
                        break;
                    case 4:
                        index = col;
                        break;
                    default:
                        index = col;
                        break;
                }
                int y = row * (SLOT_SIZE + PADDING) + BORDER;
                if (row == 4) y += BOTTOM_PADDING;
                int x = col * (SLOT_SIZE + PADDING) + BORDER + 2;

                ItemStack item = inventory.getItem(index);
                if (item != null && item.getType() != Material.AIR) {
                    drawItem(g, item, x, y);
                    if (!item.getEnchantments().isEmpty()) {
                        g.setColor(new Color(128, 0, 128, 48));
                        g.fillRect(x, y, SLOT_SIZE, SLOT_SIZE);
                    }
                }
            }
        }
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private void drawItem(Graphics2D g, ItemStack item, int x, int y) {
        String itemName = item.getType().name().toLowerCase();
        BufferedImage texture;
        if ("potion".equals(itemName) || "splash_potion".equals(itemName) || "lingering_potion".equals(itemName)) {
            texture = TextureUtils.buildPotionTexture(item);
            if (texture == null) texture = TextureUtils.loadAwkwardPotionTexture();
        } else if ("tipped_arrow".equals(itemName)) {
            texture = TextureUtils.loadItemTexture("arrow");
        } else if (itemName.contains("map")) {
            texture = TextureUtils.loadMapTexture();
        } else {
            texture = TextureUtils.loadItemTexture(item);
        }
        if (texture == null) {
            logger.warning("Missing texture for item: " + item.getType());
        } else {
            g.drawImage(texture, x, y, SLOT_SIZE, SLOT_SIZE, null);
        }

        if (item.getAmount() > 1) {
            g.setColor(Color.WHITE);
            g.setFont(MinecraftFontLoader.getFont(16f));
            String count = Integer.toString(item.getAmount());
            int textWidth = g.getFontMetrics().stringWidth(count);
            g.drawString(count, x + SLOT_SIZE - textWidth + 2, y + SLOT_SIZE + 10);
        }
    }
}
