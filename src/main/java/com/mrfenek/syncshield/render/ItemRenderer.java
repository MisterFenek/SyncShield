package com.mrfenek.syncshield.render;

import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * Item card renderer, ported from the Kotlin reference.
 */
public final class ItemRenderer {
    private static final int WIDTH = 250;
    private static final int IMAGE_SCALE = 48;
    private static final int MARGIN = 12;
    private static final Color BACKGROUND_COLOR = Color.decode("#210939");
    private static final Color BORDER_COLOR = Color.decode("#1A0B1A");
    private static final Color ENCHANTMENT_COLOR = Color.decode("#A7A7A7");

    public ItemRenderResult renderItem(ItemStack item) {
        if (item == null || item.getType() == null || item.getType().name().equalsIgnoreCase("AIR")) {
            return new ItemRenderResult(new byte[0], "Air");
        }
        BufferedImage texture = loadTexture(item);
        int height = calculateDynamicHeight(item);
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        drawBackground(g, height);
        drawTexture(g, texture);
        String itemName = drawItemName(g, item);

        int textYOffset = IMAGE_SCALE + MARGIN + 50;
        textYOffset = drawEnchantments(g, item, textYOffset);
        drawDurability(g, item, textYOffset);
        drawStackSize(g, item);

        g.dispose();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return new ItemRenderResult(out.toByteArray(), itemName);
        } catch (IOException e) {
            return new ItemRenderResult(new byte[0], itemName);
        }
    }

    private BufferedImage loadTexture(ItemStack item) {
        String itemName = item.getType().name().toLowerCase(Locale.ROOT);
        if ("potion".equals(itemName) || "splash_potion".equals(itemName) || "lingering_potion".equals(itemName)) {
            BufferedImage potion = TextureUtils.loadPotionTexture(item);
            return potion != null ? potion : TextureUtils.loadAwkwardPotionTexture();
        }
        if ("filled_map".equals(itemName) || itemName.contains("map")) {
            return TextureUtils.loadMapTexture();
        }
        return TextureUtils.loadItemTexture(item);
    }

    private int calculateDynamicHeight(ItemStack item) {
        int height = IMAGE_SCALE + MARGIN * 2 + 30;
        Map<Enchantment, Integer> enchantments = getEnchantments(item);
        if (!enchantments.isEmpty()) {
            height += 20 * enchantments.size();
        }
        if (item.getType().getMaxDurability() > 0) {
            height += 20;
        }
        return height + MARGIN;
    }

    private void drawBackground(Graphics2D g, int height) {
        g.setColor(BACKGROUND_COLOR);
        g.fillRect(0, 0, WIDTH, height);
        g.setColor(BORDER_COLOR);
        g.fillRect(4, 4, WIDTH - 8, height - 8);
    }

    private void drawTexture(Graphics2D g, BufferedImage texture) {
        if (texture == null) {
            g.setColor(Color.GRAY);
            g.fillRect(MARGIN, MARGIN, IMAGE_SCALE, IMAGE_SCALE);
        } else {
            g.drawImage(texture, MARGIN, MARGIN, IMAGE_SCALE, IMAGE_SCALE, null);
        }
    }

    private String drawItemName(Graphics2D g, ItemStack item) {
        String displayName = null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            displayName = ChatColor.stripColor(meta.getDisplayName());
        }
        String fullName = getItemName(item, displayName);
        g.setFont(MinecraftFontLoader.getFont(16f));
        g.setColor(determineNameColor(item));
        g.drawString(fullName, MARGIN, IMAGE_SCALE + MARGIN + 30);
        return fullName;
    }

    private String getItemName(ItemStack item, String displayName) {
        String itemTypeName = item.getType().name().replace('_', ' ').toLowerCase(Locale.ROOT);
        itemTypeName = itemTypeName.substring(0, 1).toUpperCase(Locale.ROOT) + itemTypeName.substring(1);
        return displayName != null && !displayName.isEmpty() ? displayName : itemTypeName;
    }

    private Color determineNameColor(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        boolean enchanted = meta != null && meta.hasEnchants();
        boolean storedEnchanted = meta instanceof EnchantmentStorageMeta && !((EnchantmentStorageMeta) meta).getStoredEnchants().isEmpty();
        if (enchanted || storedEnchanted) return Color.CYAN;
        String type = item.getType().name().toLowerCase(Locale.ROOT);
        if (type.contains("totem") || type.contains("book")) return Color.YELLOW;
        return Color.WHITE;
    }

    private int drawEnchantments(Graphics2D g, ItemStack item, int textYOffset) {
        Map<Enchantment, Integer> enchantments = getEnchantments(item);
        if (enchantments.isEmpty()) return textYOffset;
        g.setFont(MinecraftFontLoader.getFont(14f));
        g.setColor(ENCHANTMENT_COLOR);
        int currentYOffset = textYOffset;
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            g.drawString(formatEnchantmentName(entry.getKey()) + " " + entry.getValue(), MARGIN, currentYOffset);
            currentYOffset += 20;
        }
        return currentYOffset;
    }

    private void drawDurability(Graphics2D g, ItemStack item, int textYOffset) {
        if (!(item.getItemMeta() instanceof Damageable) || item.getType().getMaxDurability() <= 0) return;
        Damageable dmg = (Damageable) item.getItemMeta();
        int max = item.getType().getMaxDurability();
        int current = max - dmg.getDamage();
        g.setFont(MinecraftFontLoader.getFont(14f));
        g.setColor(Color.WHITE);
        g.drawString("Durability: " + current + "/" + max, MARGIN, textYOffset);
    }

    private void drawStackSize(Graphics2D g, ItemStack item) {
        if (item.getAmount() > 1) {
            g.setFont(MinecraftFontLoader.getFont(20f));
            g.setColor(Color.WHITE);
            String stackSize = "x " + item.getAmount();
            int x = MARGIN + IMAGE_SCALE + 10;
            int y = MARGIN + IMAGE_SCALE - 5;
            g.drawString(stackSize, x, y);
        }
    }

    private Map<Enchantment, Integer> getEnchantments(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta) {
            return ((EnchantmentStorageMeta) meta).getStoredEnchants();
        }
        return item.getEnchantments();
    }

    private String formatEnchantmentName(Enchantment enchantment) {
        try {
            Object keyObj = enchantment.getClass().getMethod("getKey").invoke(enchantment);
            if (keyObj != null) {
                Object key = keyObj.getClass().getMethod("getKey").invoke(keyObj);
                if (key != null) return key.toString().replace('_', ' ');
            }
        } catch (Exception ignored) {}
        return enchantment.getName().replace('_', ' ');
    }

    public static final class ItemRenderResult {
        private final byte[] imageBytes;
        private final String itemName;
        public ItemRenderResult(byte[] imageBytes, String itemName) {
            this.imageBytes = imageBytes;
            this.itemName = itemName;
        }
        public byte[] getImageBytes() { return imageBytes; }
        public String getItemName() { return itemName; }
    }
}
