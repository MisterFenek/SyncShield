package com.mrfenek.syncshield.render;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class BlockItemBaker implements Runnable {
    private static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    // Tunable render parameters (defaults aligned to desired Telegram renders).
    private static final int DEFAULT_OUTPUT_SIZE = 64;
    private static final int DEFAULT_RENDER_SIZE = 128;
    private static final float DEFAULT_PROJECTION_SCALE = 90f;
    private static final float DEFAULT_MODEL_SCALE_BOOST = 1.0f;
    // Base perspective for most blocks; some shapes (stairs) need a bit of depth to read correctly.
    private static final float DEFAULT_PERSPECTIVE_STRENGTH = 0.15f;
    private static final float DEFAULT_STAIRS_PERSPECTIVE_STRENGTH = 0.35f;
    // Default rotation: pitch -30, yaw 315, roll 20.
    private static final float[] DEFAULT_ROTATION = new float[]{-30f, 315f, 20f};
    private static final String[] EXTRA_ITEM_MODELS = new String[]{"shield"};

    private final JavaPlugin plugin;
    private final Logger logger;
    private final String version;
    private final Path cacheDir;
    private final boolean logProgress;
    private final Gson gson = new Gson();
    private final Map<String, BufferedImage> textureCache = new HashMap<>();
    private final Map<String, Model> modelCache = new HashMap<>();

    private final int outputSize;
    private final int renderSize;
    private final float projectionScale;
    private final float modelScaleBoost;
    private final float perspectiveStrength;
    private final float stairsPerspectiveStrength;
    private final float[] defaultRotation;

    public BlockItemBaker(JavaPlugin plugin, String version, Path cacheDir, boolean logProgress) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.version = version;
        this.cacheDir = cacheDir;
        this.logProgress = logProgress;
        this.outputSize = DEFAULT_OUTPUT_SIZE;
        this.renderSize = DEFAULT_RENDER_SIZE;
        this.projectionScale = DEFAULT_PROJECTION_SCALE;
        this.modelScaleBoost = DEFAULT_MODEL_SCALE_BOOST;
        this.perspectiveStrength = DEFAULT_PERSPECTIVE_STRENGTH;
        this.stairsPerspectiveStrength = DEFAULT_STAIRS_PERSPECTIVE_STRENGTH;
        this.defaultRotation = DEFAULT_ROTATION.clone();
    }

    public BlockItemBaker(Logger logger, String version, Path cacheDir, boolean logProgress,
                          int outputSize, int renderSize, float projectionScale, float modelScaleBoost,
                          float perspectiveStrength, float stairsPerspectiveStrength, float[] defaultRotation) {
        this.plugin = null;
        this.logger = logger;
        this.version = version;
        this.cacheDir = cacheDir;
        this.logProgress = logProgress;
        this.outputSize = outputSize;
        this.renderSize = renderSize;
        this.projectionScale = projectionScale;
        this.modelScaleBoost = modelScaleBoost;
        this.perspectiveStrength = perspectiveStrength;
        this.stairsPerspectiveStrength = stairsPerspectiveStrength;
        this.defaultRotation = defaultRotation.clone();
    }

    @Override
    public void run() {
        try {
            Files.createDirectories(cacheDir);
            Path assetsDir = cacheDir.resolve("assets");
            Path bakedDir = cacheDir.resolve("baked");
            Files.createDirectories(assetsDir);
            Files.createDirectories(bakedDir);

            if (!Files.exists(assetsDir.resolve("extracted.ok"))) {
                logger.info("[BlockItemBaker] Downloading assets to " + assetsDir.toAbsolutePath());
                downloadAndExtractAssets(assetsDir);
                Files.write(assetsDir.resolve("extracted.ok"), new byte[]{'O', 'K'});
                logger.info("[BlockItemBaker] Assets downloaded and extracted.");
            } else {
                logger.info("[BlockItemBaker] Using existing assets at " + assetsDir.toAbsolutePath());
            }

            // Make extracted textures available to TextureUtils for item-only assets (potions, arrows, etc.).
            TextureUtils.setExternalTexturesDir(assetsDir.resolve("textures"));
            bakeAllBlockItems(assetsDir, bakedDir);
        } catch (Exception e) {
            logger.severe("Block item baking failed: " + e.getMessage());
            e.printStackTrace();
            logger.severe("3D block icons may be missing until the bake succeeds. Check network access, write permissions to " + cacheDir.toAbsolutePath() + ", and available disk space.");
        }
    }

    private void downloadAndExtractAssets(Path assetsDir) throws Exception {
        logger.info("Downloading Minecraft client assets for " + version + "... This is required for accurate 3D block item renders.");
        Path manifest = assetsDir.resolve("version_manifest_v2.json");
        downloadFile(MANIFEST_URL, manifest);
        JsonUtils.VersionInfo info = JsonUtils.findVersionInfo(manifest, version);
        if (info == null || info.url == null) {
            throw new IllegalStateException("Version " + version + " not found in manifest.");
        }
        if (!version.equals(info.id)) {
            logger.warning("Requested version " + version + " not found. Using nearest available " + info.id + ". This is normal for patch-level mismatches.");
        }
        Path versionJson = assetsDir.resolve("version.json");
        downloadFile(info.url, versionJson);
        String clientUrl = JsonUtils.findClientUrl(versionJson);
        String clientSha1 = JsonUtils.findClientSha1(versionJson);
        if (clientUrl == null) {
            throw new IllegalStateException("Client URL not found for version " + version + ".");
        }
        Path clientJar = assetsDir.resolve("client.jar");
        downloadFile(clientUrl, clientJar);
        if (clientSha1 != null && !clientSha1.isEmpty()) {
            String actual = sha1(clientJar);
            if (!clientSha1.equalsIgnoreCase(actual)) {
                throw new IllegalStateException("Client jar SHA1 mismatch. Expected " + clientSha1 + " got " + actual);
            }
        }

        logger.info("Extracting textures and models from the client jar...");
        extractAssets(clientJar, assetsDir);
    }

    private void extractAssets(Path clientJar, Path targetDir) throws Exception {
        Path texturesDir = targetDir.resolve("textures");
        Path modelsDir = targetDir.resolve("models");
        Files.createDirectories(texturesDir.resolve("block"));
        Files.createDirectories(texturesDir.resolve("item"));
        Files.createDirectories(modelsDir.resolve("block"));
        Files.createDirectories(modelsDir.resolve("item"));
        try (ZipFile zip = new ZipFile(clientJar.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String name = entry.getName();
                if (name.startsWith("assets/minecraft/textures/") && name.endsWith(".png")) {
                    String sub = name.substring("assets/minecraft/textures/".length());
                    Path outPath = texturesDir.resolve(sub);
                    Path normalized = outPath.normalize();
                    if (!normalized.startsWith(texturesDir)) continue;
                    Files.createDirectories(outPath.getParent());
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, outPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } else if (name.startsWith("assets/minecraft/models/") && name.endsWith(".json")) {
                    String sub = name.substring("assets/minecraft/models/".length());
                    Path outPath = modelsDir.resolve(sub);
                    Path normalized = outPath.normalize();
                    if (!normalized.startsWith(modelsDir)) continue;
                    Files.createDirectories(outPath.getParent());
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, outPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private void bakeAllBlockItems(Path assetsDir, Path bakedDir) {
        Path texturesDir = assetsDir.resolve("textures");
        Path modelsDir = assetsDir.resolve("models");
        List<Material> blocks = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material == Material.AIR) continue;
            if (!material.isBlock()) continue;
            blocks.add(material);
        }

        int baked = 0;
        int processed = 0;
        for (Material material : blocks) {
            String name = material.name().toLowerCase(Locale.ROOT);
            Path outFile = bakedDir.resolve(name + ".png");
            if (Files.exists(outFile) && !needsRebake(outFile)) {
                continue;
            }

            try {
                BufferedImage result = bakeBlockItem(name, modelsDir, texturesDir);
                if (result == null) continue;
                ImageIO.write(result, "png", outFile.toFile());
                baked++;
            } catch (Exception ignored) {}
            processed++;
            if (logProgress && processed % 200 == 0) {
                logger.info("Baking block items: " + processed + "/" + blocks.size() + " (progress update).");
            }
        }

        for (String extra : EXTRA_ITEM_MODELS) {
            Path outFile = bakedDir.resolve(extra + ".png");
            if (Files.exists(outFile) && !needsRebake(outFile)) {
                continue;
            }
            try {
                BufferedImage result = bakeBlockItem(extra, modelsDir, texturesDir);
                if (result == null) continue;
                ImageIO.write(result, "png", outFile.toFile());
                baked++;
            } catch (Exception ignored) {}
        }
        logger.info("Baked " + baked + " block item textures. Cache saved under " + bakedDir.toAbsolutePath() + ".");
    }

    private BufferedImage bakeBlockItem(String name, Path modelsDir, Path texturesDir) {
        Model model = resolveItemModel(name, modelsDir);
        if (model == null || model.elements.isEmpty()) {
            // Some items rely on builtin/generated models without elements.
            return fallbackFromTextures(name, texturesDir);
        }
        BufferedImage rendered = renderModel(name, model, texturesDir);
        return rendered != null ? scaleToOutput(rendered, name) : null;
    }

    private BufferedImage fallbackFromTextures(String name, Path texturesDir) {
        if ("shield".equals(name)) {
            BufferedImage shieldFace = TextureUtils.loadImage("/bg/shield.png");
            if (shieldFace == null) {
                shieldFace = resolveTextureFromPath("entity/shield_base", texturesDir);
            }
            if (shieldFace == null) {
                shieldFace = resolveTextureFromPath("entity/shield_base_nopattern", texturesDir);
            }
            if (shieldFace == null) {
                shieldFace = resolveTextureFromPath("item/" + name, texturesDir);
            }
            if (shieldFace != null) {
                return scaleToOutput(shieldFace, name);
            }
        }
        String fallback = TextureUtils.resolveBlockFallback(name);
        BufferedImage itemTex = resolveTextureFromPath("item/" + name, texturesDir);
        if (itemTex == null && fallback != null) {
            itemTex = resolveTextureFromPath("item/" + fallback, texturesDir);
        }
        if (itemTex != null) {
            return scaleToOutput(itemTex, name);
        }
        // Try entity textures for items like the shield that don't have baked elements.
        BufferedImage entityTex = resolveTextureFromPath("entity/" + name, texturesDir);
        if (entityTex == null && "shield".equals(name)) {
            entityTex = resolveTextureFromPath("entity/shield_base", texturesDir);
        }
        if (entityTex != null) {
            return scaleToOutput(entityTex, name);
        }
        BufferedImage blockTex = resolveTextureFromPath("block/" + name, texturesDir);
        if (blockTex == null && fallback != null) {
            blockTex = resolveTextureFromPath("block/" + fallback, texturesDir);
        }
        if (blockTex != null) {
            return scaleToOutput(blockTex, name);
        }
        return null;
    }

    private BufferedImage scaleToOutput(BufferedImage image, String name) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int a = (image.getRGB(x, y) >> 24) & 0xFF;
                if (a > 0) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX || maxY < minY) return null;

        int bboxW = maxX - minX + 1;
        int bboxH = maxY - minY + 1;
        double sizeFactor = 1.0;
        if (name != null && name.contains("button")) {
            sizeFactor = 0.5; // make buttons appear smaller in GUI
        }
        int target = (int) Math.max(1, Math.round((outputSize - 2) * sizeFactor)); // small margin
        double scale = Math.min((double) target / bboxW, (double) target / bboxH);
        int outW = Math.max(1, (int) Math.round(bboxW * scale));
        int outH = Math.max(1, (int) Math.round(bboxH * scale));
        int offsetX = (outputSize - outW) / 2;
        int offsetY = (outputSize - outH) / 2;

        BufferedImage out = new BufferedImage(outputSize, outputSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(image, offsetX, offsetY, offsetX + outW, offsetY + outH, minX, minY, maxX + 1, maxY + 1, null);
        g.dispose();
        return out;
    }

    private boolean needsRebake(Path outFile) {
        try {
            BufferedImage existing = ImageIO.read(outFile.toFile());
            if (existing == null) return true;
            if (existing.getWidth() != outputSize || existing.getHeight() != outputSize) return true;
            int minX = outputSize, minY = outputSize, maxX = -1, maxY = -1;
            for (int y = 0; y < outputSize; y++) {
                for (int x = 0; x < outputSize; x++) {
                    int a = (existing.getRGB(x, y) >> 24) & 0xFF;
                    if (a > 0) {
                        if (x < minX) minX = x;
                        if (y < minY) minY = y;
                        if (x > maxX) maxX = x;
                        if (y > maxY) maxY = y;
                    }
                }
            }
            if (maxX < minX || maxY < minY) return true;
            int bboxW = maxX - minX + 1;
            int bboxH = maxY - minY + 1;
            return bboxW < outputSize * 0.6 || bboxH < outputSize * 0.6;
        } catch (Exception e) {
            return true;
        }
    }

    private void downloadFile(String url, Path outFile) throws Exception {
        Files.createDirectories(outFile.getParent());
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IllegalStateException("HTTP " + status + " while fetching " + url);
        }
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFile.toFile()))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static final class JsonUtils {
        private static VersionInfo findVersionInfo(Path manifest, String requested) throws Exception {
            String json = new String(Files.readAllBytes(manifest));
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null || !root.has("versions")) return null;

            JsonElement versionsEl = root.get("versions");
            if (versionsEl == null || !versionsEl.isJsonArray()) return null;

            VersionInfo exact = null;
            VersionInfo best = null;
            int[] reqParts = parseVersion(requested);
            for (JsonElement el : versionsEl.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                if (!obj.has("id") || !obj.has("url")) continue;
                String id = obj.get("id").getAsString();
                String url = obj.get("url").getAsString();
                if (id.equals(requested)) {
                    exact = new VersionInfo(id, url);
                    break;
                }
                if (reqParts == null) continue;
                int[] parts = parseVersion(id);
                if (parts == null) continue;
                if (parts[0] != reqParts[0] || parts[1] != reqParts[1]) continue;
                if (best == null || parts[2] > best.parts[2]) {
                    best = new VersionInfo(id, url);
                    best.parts = parts;
                }
            }
            return exact != null ? exact : best;
        }

        private static String findClientUrl(Path versionJson) throws Exception {
            String json = new String(Files.readAllBytes(versionJson));
            int downloadsIdx = json.indexOf("\"downloads\"");
            if (downloadsIdx < 0) return null;
            int clientIdx = json.indexOf("\"client\"", downloadsIdx);
            if (clientIdx < 0) return null;
            int urlIdx = json.indexOf("\"url\"", clientIdx);
            if (urlIdx < 0) return null;
            int colon = json.indexOf(':', urlIdx);
            int quote1 = json.indexOf('"', colon + 1);
            int quote2 = json.indexOf('"', quote1 + 1);
            return json.substring(quote1 + 1, quote2);
        }

        private static String findClientSha1(Path versionJson) throws Exception {
            String json = new String(Files.readAllBytes(versionJson));
            int downloadsIdx = json.indexOf("\"downloads\"");
            if (downloadsIdx < 0) return null;
            int clientIdx = json.indexOf("\"client\"", downloadsIdx);
            if (clientIdx < 0) return null;
            int shaIdx = json.indexOf("\"sha1\"", clientIdx);
            if (shaIdx < 0) return null;
            int colon = json.indexOf(':', shaIdx);
            int quote1 = json.indexOf('"', colon + 1);
            int quote2 = json.indexOf('"', quote1 + 1);
            return json.substring(quote1 + 1, quote2);
        }

        private static int[] parseVersion(String id) {
            if (id == null) return null;
            String[] parts = id.split("\\.");
            if (parts.length < 2) return null;
            try {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);
                int patch = 0;
                if (parts.length >= 3) {
                    patch = Integer.parseInt(parts[2]);
                }
                return new int[]{major, minor, patch};
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private static final class VersionInfo {
            final String id;
            final String url;
            int[] parts;

            private VersionInfo(String id, String url) {
                this.id = id;
                this.url = url;
            }
        }
    }

    private String sha1(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private Model resolveItemModel(String name, Path modelsDir) {
        String key = "item:" + name;
        if (modelCache.containsKey(key)) return modelCache.get(key);
        Path itemModel = modelsDir.resolve("item").resolve(name + ".json");
        Path blockModel = modelsDir.resolve("block").resolve(name + ".json");
        Model model = null;
        if (Files.exists(itemModel)) {
            model = loadModel(itemModel, modelsDir);
        }
        if (model == null && Files.exists(blockModel)) {
            model = loadModel(blockModel, modelsDir);
        }
        if (model == null && name.endsWith("_wall")) {
            String base = name.substring(0, name.length() - "_wall".length());
            Path inventoryWall = modelsDir.resolve("block").resolve(base + "_wall_inventory.json");
            if (Files.exists(inventoryWall)) {
                model = loadModel(inventoryWall, modelsDir);
            }
        }
        if (model == null && name.endsWith("_fence")) {
            String base = name.substring(0, name.length() - "_fence".length());
            Path inventoryFence = modelsDir.resolve("block").resolve(base + "_fence_inventory.json");
            if (Files.exists(inventoryFence)) {
                model = loadModel(inventoryFence, modelsDir);
            }
        }
        if (model == null && name.endsWith("_fence_gate")) {
            String base = name.substring(0, name.length() - "_fence_gate".length());
            Path inventoryFenceGate = modelsDir.resolve("block").resolve(base + "_fence_gate_open.json");
            if (Files.exists(inventoryFenceGate)) {
                model = loadModel(inventoryFenceGate, modelsDir);
            }
        }
        if (model == null && name.endsWith("_trapdoor")) {
            String base = name.substring(0, name.length() - "_trapdoor".length());
            Path top = modelsDir.resolve("block").resolve(base + "_trapdoor_top.json");
            if (Files.exists(top)) model = loadModel(top, modelsDir);
        }
        modelCache.put(key, model);
        return model;
    }

    private Model loadModel(Path modelPath, Path modelsDir) {
        try {
            String json = new String(Files.readAllBytes(modelPath));
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            Model model = parseModel(obj);
            if (model.parent != null && !model.parent.isEmpty()) {
                Path parentPath = resolveParentPath(model.parent, modelsDir);
                if (parentPath != null && Files.exists(parentPath)) {
                    Model parent = loadModel(parentPath, modelsDir);
                    model = mergeModel(parent, model);
                }
            }
            return model;
        } catch (Exception e) {
            return null;
        }
    }

    private Path resolveParentPath(String parent, Path modelsDir) {
        String clean = parent;
        if (clean.startsWith("minecraft:")) clean = clean.substring("minecraft:".length());
        if (clean.startsWith("block/")) {
            return modelsDir.resolve("block").resolve(clean.substring("block/".length()) + ".json");
        }
        if (clean.startsWith("item/")) {
            return modelsDir.resolve("item").resolve(clean.substring("item/".length()) + ".json");
        }
        if (clean.startsWith("builtin/")) {
            return null;
        }
        if (clean.contains("/")) {
            String[] parts = clean.split("/", 2);
            return modelsDir.resolve(parts[0]).resolve(parts[1] + ".json");
        }
        Path block = modelsDir.resolve("block").resolve(clean + ".json");
        if (Files.exists(block)) return block;
        return modelsDir.resolve("item").resolve(clean + ".json");
    }

    private Model mergeModel(Model parent, Model child) {
        Model out = new Model();
        out.textures.putAll(parent.textures);
        out.textures.putAll(child.textures);
        out.parent = child.parent != null ? child.parent : parent.parent;
        out.elements = child.elements.isEmpty() ? parent.elements : child.elements;
        out.display = child.display != null ? child.display : parent.display;
        return out;
    }

    private Model parseModel(JsonObject obj) {
        Model model = new Model();
        if (obj.has("parent")) model.parent = obj.get("parent").getAsString();
        if (obj.has("textures")) {
            JsonObject tex = obj.getAsJsonObject("textures");
            for (Map.Entry<String, JsonElement> entry : tex.entrySet()) {
                model.textures.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        if (obj.has("elements")) {
            for (JsonElement el : obj.getAsJsonArray("elements")) {
                JsonObject e = el.getAsJsonObject();
                Element element = new Element();
                element.from = vec(e.getAsJsonArray("from"));
                element.to = vec(e.getAsJsonArray("to"));
                if (e.has("rotation")) {
                    JsonObject r = e.getAsJsonObject("rotation");
                    element.rotationAxis = r.get("axis").getAsString();
                    element.rotationAngle = r.get("angle").getAsFloat();
                    element.rotationOrigin = vec(r.getAsJsonArray("origin"));
                }
                if (e.has("faces")) {
                    JsonObject faces = e.getAsJsonObject("faces");
                    for (Map.Entry<String, JsonElement> faceEntry : faces.entrySet()) {
                        String faceName = faceEntry.getKey();
                        JsonObject f = faceEntry.getValue().getAsJsonObject();
                        Face face = new Face();
                        if (f.has("uv")) face.uv = vec4(f.getAsJsonArray("uv"));
                        face.texture = f.get("texture").getAsString();
                        if (f.has("tintindex")) face.tintIndex = f.get("tintindex").getAsInt();
                        if (f.has("rotation")) face.rotation = f.get("rotation").getAsInt();
                        element.faces.put(faceName, face);
                    }
                }
                model.elements.add(element);
            }
        }
        if (obj.has("display")) {
            model.display = new Display();
            JsonObject display = obj.getAsJsonObject("display");
            if (display.has("gui")) {
                JsonObject gui = display.getAsJsonObject("gui");
                model.display.gui = parseTransform(gui);
            }
        }
        return model;
    }

    private Transform parseTransform(JsonObject obj) {
        Transform t = new Transform();
        if (obj.has("rotation")) t.rotation = vec(obj.getAsJsonArray("rotation"));
        if (obj.has("translation")) t.translation = vec(obj.getAsJsonArray("translation"));
        if (obj.has("scale")) t.scale = vec(obj.getAsJsonArray("scale"));
        return t;
    }

    private float[] vec(com.google.gson.JsonArray arr) {
        return new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()};
    }

    private float[] vec4(com.google.gson.JsonArray arr) {
        return new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat(), arr.get(3).getAsFloat()};
    }

    private BufferedImage renderModel(String name, Model model, Path texturesDir) {
        Transform transform = resolveGuiTransform(name, model);
        float perspective = (name != null && name.endsWith("_stairs")) ? stairsPerspectiveStrength : perspectiveStrength;
        List<FaceRender> faces = new ArrayList<>();

        for (Element element : model.elements) {
            for (Map.Entry<String, Face> entry : element.faces.entrySet()) {
                String faceName = entry.getKey();
                Face face = entry.getValue();
                float[][] verts = buildFaceVerts(element, faceName);
                if (verts == null) continue;
                applyElementRotation(verts, element);
                applyTransform(verts, transform);
                faces.add(new FaceRender(faceName, face, verts, perspective));
            }
        }

        faces.sort(Comparator.comparingDouble(a -> -a.avgDepth));
        BufferedImage out = new BufferedImage(renderSize, renderSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        for (FaceRender fr : faces) {
            BufferedImage tex = resolveTexture(model, fr.face.texture, texturesDir);
            if (tex == null) continue;
            BufferedImage cropped = cropTexture(tex, fr.face, fr.faceName);
            if (cropped == null) continue;
            if (fr.face.tintIndex >= 0) {
                cropped = applyTint(cropped, 0x7FB238);
            }
            drawTexturedQuad(g, cropped, fr.projected);
        }

        g.dispose();
        return out;
    }

    private BufferedImage resolveTexture(Model model, String textureRef, Path texturesDir) {
        String key = resolveTextureRef(model, textureRef);
        if (key == null) return null;
        return resolveTextureFromPath(key, texturesDir);
    }

    private BufferedImage resolveTextureFromPath(String key, Path texturesDir) {
        if (key == null) return null;
        String path = key;
        if (path.startsWith("minecraft:")) path = path.substring("minecraft:".length());
        if (path.startsWith("block/")) {
            return loadTextureFile(texturesDir.resolve("block").resolve(path.substring("block/".length()) + ".png"));
        }
        if (path.startsWith("item/")) {
            return loadTextureFile(texturesDir.resolve("item").resolve(path.substring("item/".length()) + ".png"));
        }
        if (path.startsWith("entity/")) {
            return loadTextureFile(texturesDir.resolve("entity").resolve(path.substring("entity/".length()) + ".png"));
        }
        Path block = texturesDir.resolve("block").resolve(path + ".png");
        BufferedImage img = loadTextureFile(block);
        if (img != null) return img;
        img = loadTextureFile(texturesDir.resolve("item").resolve(path + ".png"));
        if (img != null) return img;
        return loadTextureFile(texturesDir.resolve("entity").resolve(path + ".png"));
    }

    private String resolveTextureRef(Model model, String textureRef) {
        if (textureRef == null) return null;
        String ref = textureRef;
        int guard = 0;
        while (ref.startsWith("#") && guard++ < 6) {
            String key = ref.substring(1);
            ref = model.textures.get(key);
            if (ref == null) return null;
        }
        return ref;
    }

    private BufferedImage loadTextureFile(Path path) {
        String key = path.toString();
        if (textureCache.containsKey(key)) return textureCache.get(key);
        BufferedImage img = TextureUtils.loadImage(path);
        textureCache.put(key, img);
        return img;
    }

    private BufferedImage cropTexture(BufferedImage texture, Face face, String faceName) {
        float[] uv = face.uv;
        if (uv == null) {
            uv = defaultUvForFace(faceName);
        }
        int texW = texture.getWidth();
        int texH = texture.getHeight();
        int x1 = Math.round(Math.min(uv[0], uv[2]) / 16f * texW);
        int y1 = Math.round(Math.min(uv[1], uv[3]) / 16f * texH);
        int x2 = Math.round(Math.max(uv[0], uv[2]) / 16f * texW);
        int y2 = Math.round(Math.max(uv[1], uv[3]) / 16f * texH);
        int w = Math.max(1, x2 - x1);
        int h = Math.max(1, y2 - y1);
        if (x1 + w > texW || y1 + h > texH) return null;
        BufferedImage sub = texture.getSubimage(x1, y1, w, h);
        if (face.rotation == 0) return sub;
        return rotateTexture(sub, face.rotation);
    }

    private BufferedImage rotateTexture(BufferedImage image, int rotation) {
        int rot = ((rotation % 360) + 360) % 360;
        if (rot == 0) return image;
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage out = rot == 180 ? new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                : new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        if (rot == 90) {
            g.translate(h, 0);
            g.rotate(Math.toRadians(90));
        } else if (rot == 180) {
            g.translate(w, h);
            g.rotate(Math.toRadians(180));
        } else if (rot == 270) {
            g.translate(0, w);
            g.rotate(Math.toRadians(270));
        }
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return out;
    }

    private float[] defaultUvForFace(String faceName) {
        return new float[]{0, 0, 16, 16};
    }

    private float[][] buildFaceVerts(Element element, String face) {
        float x1 = element.from[0], y1 = element.from[1], z1 = element.from[2];
        float x2 = element.to[0], y2 = element.to[1], z2 = element.to[2];
        switch (face) {
            case "north":
                // top-left (west, top) -> top-right (east, top) -> bottom-right -> bottom-left
                return new float[][]{{x1, y2, z1}, {x2, y2, z1}, {x2, y1, z1}, {x1, y1, z1}};
            case "south":
                // Flip X to avoid mirroring when facing south
                return new float[][]{{x2, y2, z2}, {x1, y2, z2}, {x1, y1, z2}, {x2, y1, z2}};
            case "west":
                // Left-to-right runs south -> north
                return new float[][]{{x1, y2, z2}, {x1, y2, z1}, {x1, y1, z1}, {x1, y1, z2}};
            case "east":
                // Left-to-right runs north -> south
                return new float[][]{{x2, y2, z1}, {x2, y2, z2}, {x2, y1, z2}, {x2, y1, z1}};
            case "up":
                // Texture U: west -> east, V: north -> south
                return new float[][]{{x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}};
            case "down":
                // Match top orientation while on the bottom face
                return new float[][]{{x1, y1, z2}, {x2, y1, z2}, {x2, y1, z1}, {x1, y1, z1}};
            default:
                return null;
        }
    }

    private void applyElementRotation(float[][] verts, Element element) {
        if (element.rotationAxis == null) return;
        float angle = (float) Math.toRadians(element.rotationAngle);
        float[] origin = element.rotationOrigin;
        for (float[] v : verts) {
            float x = v[0] - origin[0];
            float y = v[1] - origin[1];
            float z = v[2] - origin[2];
            float nx = x, ny = y, nz = z;
            switch (element.rotationAxis) {
                case "x":
                    ny = (float) (y * Math.cos(angle) - z * Math.sin(angle));
                    nz = (float) (y * Math.sin(angle) + z * Math.cos(angle));
                    break;
                case "y":
                    nx = (float) (x * Math.cos(angle) + z * Math.sin(angle));
                    nz = (float) (-x * Math.sin(angle) + z * Math.cos(angle));
                    break;
                case "z":
                    nx = (float) (x * Math.cos(angle) - y * Math.sin(angle));
                    ny = (float) (x * Math.sin(angle) + y * Math.cos(angle));
                    break;
                default:
                    break;
            }
            v[0] = nx + origin[0];
            v[1] = ny + origin[1];
            v[2] = nz + origin[2];
        }
    }

    private void applyTransform(float[][] verts, Transform transform) {
        float rx = transform.rotation[0];
        float ry = transform.rotation[1];
        float rz = transform.rotation[2];
        float[] t = transform.translation;
        float[] s = transform.scale;

        float rX = (float) Math.toRadians(rx);
        float rY = (float) Math.toRadians(ry);
        float rZ = (float) Math.toRadians(rz);

        for (float[] v : verts) {
            float x = (v[0] - 8f) / 16f;
            float y = (v[1] - 8f) / 16f;
            float z = (v[2] - 8f) / 16f;

            float y1 = (float) (y * Math.cos(rX) - z * Math.sin(rX));
            float z1 = (float) (y * Math.sin(rX) + z * Math.cos(rX));
            y = y1; z = z1;

            float x2 = (float) (x * Math.cos(rY) + z * Math.sin(rY));
            float z2 = (float) (-x * Math.sin(rY) + z * Math.cos(rY));
            x = x2; z = z2;

            float x3 = (float) (x * Math.cos(rZ) - y * Math.sin(rZ));
            float y3 = (float) (x * Math.sin(rZ) + y * Math.cos(rZ));
            x = x3; y = y3;

            x = (x * s[0] + t[0] / 16f) * modelScaleBoost;
            y = (y * s[1] + t[1] / 16f) * modelScaleBoost;
            z = (z * s[2] + t[2] / 16f) * modelScaleBoost;

            v[0] = x;
            v[1] = y;
            v[2] = z;
        }
    }

    private void drawTexturedQuad(Graphics2D g, BufferedImage tex, Point[] quad) {
        if (quad == null || quad.length != 4) return;
        Polygon poly = new Polygon();
        for (Point p : quad) poly.addPoint(p.x, p.y);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setClip(poly);
        int w = tex.getWidth();
        int h = tex.getHeight();
        AffineTransform at = new AffineTransform(
                (quad[1].x - quad[0].x) / (double) w,
                (quad[1].y - quad[0].y) / (double) w,
                (quad[3].x - quad[0].x) / (double) h,
                (quad[3].y - quad[0].y) / (double) h,
                quad[0].x,
                quad[0].y
        );
        g2.drawImage(tex, at, null);
        g2.dispose();
    }

    /**
     * Vanilla GUI transform: 30 pitch, 225 yaw, 0 roll, 0 translation, scale 0.625.
     * This matches ItemTransforms.GUI used by the client so baked icons line up with in-game visuals.
     * Thin blocks (fences, gates, walls, trapdoors, buttons) reuse the same rotation but are scaled
     * slightly up and nudged down to keep them centered in the 32x32 output.
     */
    private Transform resolveGuiTransform(String name, Model model) {
        // Always enforce unified rotation (DEFAULT_ROTATION). We still honor model scale/translation.
        Transform base = (model.display != null && model.display.gui != null) ? copyTransform(model.display.gui) : defaultGuiTransform();
        base.rotation = DEFAULT_ROTATION.clone();
        if (name == null) return base;
        String n = name.toLowerCase(Locale.ROOT);
        if (isThinInventoryBlock(n)) {
            Transform t = copyTransform(base);
            t.scale = new float[]{base.scale[0] * 0.9f, base.scale[1] * 0.9f, base.scale[2] * 0.9f};
            t.translation = new float[]{base.translation[0], base.translation[1] - 1.0f, base.translation[2]};
            return t;
        }
        return base;
    }

    private boolean isThinInventoryBlock(String name) {
        return name.contains("_fence") || name.contains("_wall") || name.contains("_fence_gate")
                || name.contains("trapdoor") || name.contains("button") || name.contains("pressure_plate");
    }

    private Transform copyTransform(Transform src) {
        Transform t = new Transform();
        t.rotation = src.rotation.clone();
        t.translation = src.translation.clone();
        t.scale = src.scale.clone();
        return t;
    }

    private Transform defaultGuiTransform() {
        Transform t = new Transform();
        // Vanilla furnace/item GUI transform (tuned via DEFAULT_ROTATION): shows front face toward camera with slight tilt.
        t.rotation = DEFAULT_ROTATION.clone();
        t.translation = new float[]{0f, 0f, 0f};
        t.scale = new float[]{0.625f, 0.625f, 0.625f};
        return t;
    }

    private static final class Model {
        String parent;
        Map<String, String> textures = new HashMap<>();
        List<Element> elements = new ArrayList<>();
        Display display;
    }

    private static final class Element {
        float[] from;
        float[] to;
        String rotationAxis;
        float rotationAngle;
        float[] rotationOrigin;
        Map<String, Face> faces = new HashMap<>();
    }

    private static final class Face {
        float[] uv;
        String texture;
        int rotation;
        int tintIndex = -1;
    }

    private static final class Display {
        Transform gui;
    }

    private static final class Transform {
        float[] rotation = new float[]{0f, 0f, 0f};
        float[] translation = new float[]{0f, 0f, 0f};
        float[] scale = new float[]{1f, 1f, 1f};
    }

    private final class FaceRender {
        final String faceName;
        final Face face;
        final float[][] verts;
        final Point[] projected;
        final double avgDepth;

        private FaceRender(String faceName, Face face, float[][] verts, float perspectiveStrength) {
            this.faceName = faceName;
            this.face = face;
            this.verts = verts;
            this.projected = projectVerts(verts, perspectiveStrength);
            this.avgDepth = (verts[0][2] + verts[1][2] + verts[2][2] + verts[3][2]) / 4.0;
        }
    }

    private Point[] projectVerts(float[][] verts, float perspectiveStrength) {
        Point[] out = new Point[4];
        for (int i = 0; i < 4; i++) {
            float x = verts[i][0];
            float y = verts[i][1];
            float z = verts[i][2];
            float perspective = 1f / Math.max(0.5f, 1f - (z * perspectiveStrength));
            float scale = projectionScale * perspective;
            float sx = x * scale + renderSize / 2f;
            float sy = -y * scale + renderSize / 2f;
            out[i] = new Point(Math.round(sx), Math.round(sy));
        }
        return out;
    }

    private BufferedImage applyTint(BufferedImage image, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
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
