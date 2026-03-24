package com.mrfenek.syncshield.render;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Simplified texture loader matching the provided Kotlin reference.
 */
public final class TextureUtils {
    private TextureUtils() {}

    private static volatile Path externalTexturesDir;
    private static volatile Path blockItemCacheDir;
    private static volatile boolean legacyMode = false;

    public static void setExternalTexturesDir(Path dir) {
        externalTexturesDir = dir;
    }

    public static void setBlockItemCacheDir(Path dir) {
        blockItemCacheDir = dir;
    }

    public static void setLegacyMode(boolean enabled) {
        legacyMode = enabled;
    }

    private static void ensureExternal() {
        if (externalTexturesDir != null) return;
        if (blockItemCacheDir != null) {
            Path p = blockItemCacheDir.getParent();
            if (p != null) {
                Path cand = p.resolve("assets").resolve("textures");
                if (Files.exists(cand)) {
                    externalTexturesDir = cand;
                    return;
                }
            }
        }
        Path tmp = Paths.get(".tmp_mcassets", "assets", "minecraft", "textures");
        if (Files.exists(tmp)) externalTexturesDir = tmp;
    }

    public static BufferedImage loadItemTexture(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        String name = item.getType().name().toLowerCase(Locale.ROOT);
        // baked block items first
        if (item.getType().isBlock() && blockItemCacheDir != null) {
            BufferedImage baked = loadImage(blockItemCacheDir.resolve(name + ".png"));
            if (baked != null) return baked;
        }
        if ("shield".equals(name)) {
            BufferedImage custom = loadImage("/bg/shield.png");
            if (custom != null) return custom;
        }
        if ("tipped_arrow".equals(name)) {
            // Render tipped arrows using the normal arrow texture (no tint) per requirement.
            BufferedImage arrow = loadItemTexture("arrow");
            if (arrow != null) return arrow;
        }
        if ("spectral_arrow".equals(name)) {
            BufferedImage spec = loadItemTexture("spectral_arrow");
            if (spec != null) return spec;
        }
        if ("potion".equals(name) || "splash_potion".equals(name) || "lingering_potion".equals(name)) {
            BufferedImage potion = buildPotionTexture(item);
            if (potion != null) return potion;
            BufferedImage base = loadImage("/bg/water-bottle.png");
            if (base != null) return base;
            BufferedImage flat = loadItemTexture("potion");
            if (flat != null) return flat;
        }
        if (item.getItemMeta() instanceof LeatherArmorMeta) {
            BufferedImage leather = loadLeatherArmorTexture(item, name);
            if (leather != null) return leather;
        }
        if (item.getType().isBlock()) {
            BufferedImage blockFlat = loadBlockTexture(name);
            if (blockFlat != null) return blockFlat;
        }
        return loadItemTexture(name);
    }

    public static BufferedImage loadItemTexture(String itemName) {
        ensureExternal();
        // try external baked/cache first
        if (externalTexturesDir != null) {
            BufferedImage ext = loadImage(externalTexturesDir.resolve("item").resolve(itemName + ".png"));
            if (ext != null) return ext;
        }
        // fallback to block dir for block items
        if (externalTexturesDir != null) {
            BufferedImage extBlock = loadImage(externalTexturesDir.resolve("block").resolve(itemName + ".png"));
            if (extBlock != null) return extBlock;
        }
        String texturePath = "/textures/minecraft__" + itemName + ".png";
        return loadImage(texturePath);
    }

    public static BufferedImage loadBlockTexture(String blockName) {
        ensureExternal();
        if (externalTexturesDir != null) {
            BufferedImage ext = loadImage(externalTexturesDir.resolve("block").resolve(blockName + ".png"));
            if (ext != null) return ext;
            BufferedImage side = loadImage(externalTexturesDir.resolve("block").resolve(blockName + "_side.png"));
            if (side != null) return side;
            BufferedImage top = loadImage(externalTexturesDir.resolve("block").resolve(blockName + "_top.png"));
            if (top != null) return top;
        }
        String texturePath = "/textures/block__" + blockName + ".png";
        BufferedImage fromJar = loadImage(texturePath);
        if (fromJar != null) return fromJar;
        BufferedImage sideJar = loadImage("/textures/block__" + blockName + "_side.png");
        if (sideJar != null) return sideJar;
        return loadImage("/textures/block__" + blockName + "_top.png");
    }

    public static BufferedImage loadEntityTexture(String entityName) {
        ensureExternal();
        if (externalTexturesDir != null) {
            BufferedImage ext = loadImage(externalTexturesDir.resolve("entity").resolve(entityName + ".png"));
            if (ext != null) return ext;
        }
        String texturePath = "/textures/entity__" + entityName + ".png";
        return loadImage(texturePath);
    }

    public static BufferedImage loadAwkwardPotionTexture() {
        return loadItemTexture("potion__awkward");
    }

    public static BufferedImage loadPotionTexture(ItemStack item) {
        if (!(item.getItemMeta() instanceof PotionMeta)) return null;
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        String type = meta.getBasePotionData() != null && meta.getBasePotionData().getType() != null
                ? meta.getBasePotionData().getType().name().toLowerCase(Locale.ROOT)
                : null;
        if (type == null || type.isEmpty()) return null;
        return loadItemTexture("potion__" + type);
    }

    public static BufferedImage buildPotionTexture(ItemStack item) {
        if (!(item.getItemMeta() instanceof PotionMeta)) return null;
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        BufferedImage bottle;
        String type = item.getType().name().toLowerCase(Locale.ROOT);
        if ("splash_potion".equals(type)) {
            bottle = loadImage("/bg/splash-potion.png");
        } else if ("lingering_potion".equals(type)) {
            bottle = loadImage("/bg/lingering-potion.png");
        } else {
            bottle = loadImage("/bg/water-bottle.png");
        }
        if (bottle == null) {
            bottle = loadItemTexture("potion");
            if (bottle == null) return null;
        }
        BufferedImage overlay = loadItemTexture("potion_overlay");
        int color = meta.getColor() != null ? meta.getColor().asRGB() : 0x385DC6;
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        BufferedImage out = new BufferedImage(bottle.getWidth(), bottle.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                int bp = bottle.getRGB(x, y);
                int aB = (bp >> 24) & 0xFF;
                if (aB > 0) { out.setRGB(x, y, bp); continue; }
                if (overlay != null && x < overlay.getWidth() && y < overlay.getHeight()) {
                    int op = overlay.getRGB(x, y);
                    int aO = (op >> 24) & 0xFF;
                    if (aO > 0) {
                        int rr = ((op >> 16) & 0xFF) * r / 255;
                        int gg = ((op >> 8) & 0xFF) * g / 255;
                        int bb = (op & 0xFF) * b / 255;
                        out.setRGB(x, y, (aO << 24) | (rr << 16) | (gg << 8) | bb);
                        continue;
                    }
                }
                out.setRGB(x, y, 0);
            }
        }
        return out;
    }

    public static BufferedImage loadTippedArrowTexture(ItemStack item) {
        // Requirement: tipped arrows render like normal arrows (no potion tint).
        return loadItemTexture("arrow");
    }

    public static BufferedImage loadMapTexture() {
        return loadItemTexture("map");
    }

    public static BufferedImage loadImage(String path) {
        try (InputStream in = TextureUtils.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    public static BufferedImage loadImage(Path path) {
        try {
            if (path == null || !Files.exists(path)) return null;
            return ImageIO.read(path.toFile());
        } catch (Exception e) {
            return null;
        }
    }

    // Compatibility with block baker; currently no special fallbacks.
    public static String resolveBlockFallback(String name) {
        return null;
    }

    private static boolean isLeatherArmor(ItemStack item) {
        return item.getItemMeta() instanceof LeatherArmorMeta;
    }

    private static BufferedImage loadLeatherArmorTexture(ItemStack item, String baseName) {
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        BufferedImage base = loadItemTexture(baseName);
        if (base == null) return null;
        BufferedImage tinted = applyLeatherTint(base, meta.getColor());
        BufferedImage overlay = loadItemTexture(baseName + "_overlay");
        if (overlay == null) return tinted;
        BufferedImage out = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.drawImage(tinted, 0, 0, null);
        g.drawImage(overlay, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage applyLeatherTint(BufferedImage base, org.bukkit.Color color) {
        int r = color.getRed(), g = color.getGreen(), b = color.getBlue();
        BufferedImage out = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                int argb = base.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int rr = (argb >> 16) & 0xFF;
                int gg = (argb >> 8) & 0xFF;
                int bb = argb & 0xFF;
                rr = rr * r / 255;
                gg = gg * g / 255;
                bb = bb * b / 255;
                out.setRGB(x, y, (a << 24) | (rr << 16) | (gg << 8) | bb);
            }
        }
        return out;
    }
}
