package com.mrfenek.syncshield.render;

import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Item card renderer using Monocraft as the exclusive font with full RU/EN localization support.
 */
public final class ItemRenderer {
    private static final int WIDTH = 280;
    private static final int IMAGE_SCALE = 48;
    private static final int MARGIN = 12;
    private static final Color BACKGROUND_COLOR = Color.decode("#210939");
    private static final Color BORDER_COLOR = Color.decode("#1A0B1A");
    private static final Color ENCHANTMENT_COLOR = Color.decode("#A7A7A7");
    private static final Color LORE_COLOR = Color.decode("#AAAAAA");

    private static final Font FONT_NAME = MinecraftFontLoader.getFont(16f);
    private static final Font FONT_ENCHANT = MinecraftFontLoader.getFont(13f);
    private static final Font FONT_LORE = MinecraftFontLoader.getFont(12f);
    private static final Font FONT_STAT = MinecraftFontLoader.getFont(13f);
    private static final Font FONT_COUNT = MinecraftFontLoader.getFont(20f);

    private static final Map<String, String> ENCHANTMENT_RU = new HashMap<>();
    static {
        ENCHANTMENT_RU.put("sharpness", "Острота");
        ENCHANTMENT_RU.put("smite", "Небесная кара");
        ENCHANTMENT_RU.put("bane_of_arthropods", "Бич членистоногих");
        ENCHANTMENT_RU.put("knockback", "Отбрасывание");
        ENCHANTMENT_RU.put("fire_aspect", "Заговор огня");
        ENCHANTMENT_RU.put("looting", "Добыча");
        ENCHANTMENT_RU.put("sweeping_edge", "Разящий клинок");
        ENCHANTMENT_RU.put("efficiency", "Эффективность");
        ENCHANTMENT_RU.put("silk_touch", "Шелковое касание");
        ENCHANTMENT_RU.put("unbreaking", "Прочность");
        ENCHANTMENT_RU.put("fortune", "Удача");
        ENCHANTMENT_RU.put("power", "Сила");
        ENCHANTMENT_RU.put("punch", "Откидывание");
        ENCHANTMENT_RU.put("flame", "Горящая стрела");
        ENCHANTMENT_RU.put("infinity", "Бесконечность");
        ENCHANTMENT_RU.put("luck_of_the_sea", "Морская удача");
        ENCHANTMENT_RU.put("lure", "Приманка");
        ENCHANTMENT_RU.put("loyalty", "Верность");
        ENCHANTMENT_RU.put("impaling", "Пронзание");
        ENCHANTMENT_RU.put("riptide", "Тягун");
        ENCHANTMENT_RU.put("channeling", "Громовержец");
        ENCHANTMENT_RU.put("multishot", "Залп");
        ENCHANTMENT_RU.put("quick_charge", "Быстрая перезарядка");
        ENCHANTMENT_RU.put("piercing", "Пронизывающая стрела");
        ENCHANTMENT_RU.put("mending", "Починка");
        ENCHANTMENT_RU.put("vanishing_curse", "Проклятие утраты");
        ENCHANTMENT_RU.put("binding_curse", "Проклятие несъемности");
        ENCHANTMENT_RU.put("protection", "Защита");
        ENCHANTMENT_RU.put("fire_protection", "Огнеупорность");
        ENCHANTMENT_RU.put("feather_falling", "Невесомость");
        ENCHANTMENT_RU.put("blast_protection", "Взрывоустойчивость");
        ENCHANTMENT_RU.put("projectile_protection", "Защита от снарядов");
        ENCHANTMENT_RU.put("respiration", "Подводное дыхание");
        ENCHANTMENT_RU.put("aqua_affinity", "Подводник");
        ENCHANTMENT_RU.put("thorns", "Шипы");
        ENCHANTMENT_RU.put("depth_strider", "Подводная ходьба");
        ENCHANTMENT_RU.put("frost_walker", "Быстрый шаг");
        ENCHANTMENT_RU.put("soul_speed", "Скорость души");
        ENCHANTMENT_RU.put("swift_sneak", "Проворство");
    }

    private static boolean isRussian = false;
    private static String durabilityFormat = "Durability: %current%/%max%";

    public static void setLanguage(String lang, String durabilityFmt) {
        isRussian = "ru".equalsIgnoreCase(lang);
        if (durabilityFmt != null && !durabilityFmt.isEmpty()) {
            durabilityFormat = durabilityFmt;
        }
    }

    public ItemRenderResult renderItem(ItemStack item) {
        if (item == null || item.getType() == null || item.getType().name().equalsIgnoreCase("AIR")) {
            return new ItemRenderResult(new byte[0], isRussian ? "Воздух" : "Air");
        }
        BufferedImage texture = loadTexture(item);
        int height = calculateDynamicHeight(item);
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        drawBackground(g, height);
        drawTexture(g, texture);
        String itemName = drawItemName(g, item);

        int textYOffset = IMAGE_SCALE + MARGIN + 28;
        textYOffset = drawEnchantments(g, item, textYOffset);
        textYOffset = drawLore(g, item, textYOffset);
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
            height += 18 * enchantments.size();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasLore() && meta.getLore() != null) {
            height += 16 * meta.getLore().size();
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
        g.setFont(FONT_NAME);
        g.setColor(determineNameColor(item));
        g.drawString(fullName, MARGIN, IMAGE_SCALE + MARGIN + 22);
        return fullName;
    }

    private String getItemName(ItemStack item, String displayName) {
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }
        String itemTypeName = item.getType().name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return itemTypeName.substring(0, 1).toUpperCase(Locale.ROOT) + itemTypeName.substring(1);
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
        g.setFont(FONT_ENCHANT);
        g.setColor(ENCHANTMENT_COLOR);
        int currentYOffset = textYOffset;
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            String enchText = formatEnchantmentName(entry.getKey()) + " " + toRoman(entry.getValue());
            g.drawString(enchText, MARGIN, currentYOffset);
            currentYOffset += 18;
        }
        return currentYOffset;
    }

    private int drawLore(Graphics2D g, ItemStack item, int textYOffset) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null) return textYOffset;
        g.setFont(FONT_LORE);
        g.setColor(LORE_COLOR);
        int currentYOffset = textYOffset;
        for (String line : meta.getLore()) {
            String cleanLine = ChatColor.stripColor(line);
            if (!cleanLine.trim().isEmpty()) {
                g.drawString(cleanLine, MARGIN, currentYOffset);
                currentYOffset += 16;
            }
        }
        return currentYOffset;
    }

    private void drawDurability(Graphics2D g, ItemStack item, int textYOffset) {
        if (!(item.getItemMeta() instanceof Damageable) || item.getType().getMaxDurability() <= 0) return;
        Damageable dmg = (Damageable) item.getItemMeta();
        int max = item.getType().getMaxDurability();
        int current = max - dmg.getDamage();
        g.setFont(FONT_STAT);
        g.setColor(Color.WHITE);
        String text = durabilityFormat.replace("%current%", String.valueOf(current)).replace("%max%", String.valueOf(max));
        g.drawString(text, MARGIN, textYOffset);
    }

    private void drawStackSize(Graphics2D g, ItemStack item) {
        if (item.getAmount() > 1) {
            g.setFont(FONT_COUNT);
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
        String key = null;
        try {
            Object keyObj = enchantment.getClass().getMethod("getKey").invoke(enchantment);
            if (keyObj != null) {
                Object k = keyObj.getClass().getMethod("getKey").invoke(keyObj);
                if (k != null) key = k.toString().toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {}

        if (key == null) {
            key = enchantment.getName().toLowerCase(Locale.ROOT);
        }

        if (isRussian && ENCHANTMENT_RU.containsKey(key)) {
            return ENCHANTMENT_RU.get(key);
        }

        String fallback = key.replace('_', ' ');
        return fallback.substring(0, 1).toUpperCase(Locale.ROOT) + fallback.substring(1);
    }

    private String toRoman(int level) {
        switch (level) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            case 6: return "VI";
            case 7: return "VII";
            case 8: return "VIII";
            case 9: return "IX";
            case 10: return "X";
            default: return String.valueOf(level);
        }
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
