package com.mrfenek.syncshield;

import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.ShulkerBox;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.text.MessageFormat;
import java.awt.Color;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import com.mrfenek.syncshield.render.AdvancementRenderer;
import com.mrfenek.syncshield.render.BookRenderer;
import com.mrfenek.syncshield.render.BlockItemBaker;
import com.mrfenek.syncshield.render.EnderChestRenderer;
import com.mrfenek.syncshield.render.InventoryRenderer;
import com.mrfenek.syncshield.render.ItemRenderer;
import com.mrfenek.syncshield.render.TextureUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class SyncShield extends JavaPlugin implements Listener, CommandExecutor, org.bukkit.command.TabCompleter {

    private String botToken;
    private long expiryMs = 12 * 60 * 60 * 1000L;
    private String op2faMode = "session";
    private String nonOp2faMode = "disabled";
    private int sessionExpiryHours = 12;
    private final Path dataDbPath = Paths.get(getDataFolder().getPath(), "syncshield_data.db");
    private final Path legacyDataFile = Paths.get(getDataFolder().getPath(), "syncshield_data.json");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, Long> linkedChats = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> approvedIps = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> blacklistedIps = new ConcurrentHashMap<>();
    private final Map<String, PendingLink> pendingLinks = new ConcurrentHashMap<>();
    private final Map<String, PendingApproval> pendingApprovals = new ConcurrentHashMap<>();
    private final Map<Long, String> chatStates = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> disabledNotifications = new ConcurrentHashMap<>();
    private final Map<String, String> ipManagerShortIds = new ConcurrentHashMap<>();
    private final Map<String, Long> ipManagerShortIdsTimestamp = new ConcurrentHashMap<>();
    private final Map<UUID, String> player2faModes = new ConcurrentHashMap<>();
    private long ownerId;
    private final Set<Long> adminIds = ConcurrentHashMap.newKeySet();
    private boolean rconEnabled = true;
    private boolean privateOnly = true;
    private boolean chatSyncEnabled = false;
    private boolean chatSyncFromMc = true;
    private boolean chatSyncFromTg = true;
    private boolean chatSyncChatMessages = true;
    private boolean chatSyncJoinLeave = true;
    private boolean chatSyncDeath = true;
    private boolean chatSyncRenderInventory = true;
    private boolean chatSyncRenderEnder = true;
    private boolean chatSyncRenderItems = true;
    private boolean chatSyncRenderBooks = true;
    private boolean chatSyncRenderAdvancements = true;
    private String chatSyncTelegramFormat = "<b>%player%</b>: %message%";
    private String chatSyncMcFormat = "&7[TG] &f%player%: %message%";
    private String chatSyncAdvancementFormat = "<i>%player%</i> has made the advancement <b>%advancement%</b>: %description%";
    private String chatSyncJoinFormat = "<i>%player% joined the game</i>";
    private String chatSyncQuitFormat = "<i>%player% left the game</i>";
    private String chatSyncDeathFormat = "<i>%player% died: %message%</i>";
    private boolean bakeBlockItemsOnStartup = true;
    private boolean bakeBlockItemsLogProgress = true;
    private final Set<Long> chatSyncChatIds = ConcurrentHashMap.newKeySet();
    private final Map<Long, Integer> chatSyncTopicIds = new ConcurrentHashMap<>();
    private final Set<String> rconAllowedCommands = ConcurrentHashMap.newKeySet();
    private FileConfiguration messagesConfig;
    private volatile Thread pollingThread;
    private final AtomicBoolean blockItemBakeStarted = new AtomicBoolean(false);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService httpExecutor = Executors.newFixedThreadPool(2);
    private final Map<Long, RconFeedbackSession> rconFeedbackSessions = new ConcurrentHashMap<>();
    private final RconLogHandler rconLogHandler = new RconLogHandler();
    private Logger rootLogger;
    private Connection dbConnection;
    private String dataPassword;
    private boolean dataEncryptionEnabled;
    private final Path dataKeyFile = Paths.get(getDataFolder().getPath(), "keys").resolve("syncshield_data.key");
    private volatile boolean isRunning = true;
    private boolean debugEnabled = false;
    private static final long RCON_FEEDBACK_WINDOW_MS = 2000L;
    private static final int RCON_FEEDBACK_MAX_LINES = 80;
    private static final int TELEGRAM_MAX_MESSAGE_CHARS = 3800;
    private static final int DATA_SALT_LEN = 16;
    private static final int DATA_IV_LEN = 12;
    private static final int DATA_GCM_TAG_BITS = 128;
    private static final int DATA_PBKDF2_ITERS = 120000;

    // Rendering offload
    private ThreadPoolExecutor renderExecutor;
    private final Map<UUID, Long> renderCooldowns = new ConcurrentHashMap<>();
    private static final int RENDER_QUEUE_CAPACITY = 64;
    private static final long RENDER_COOLDOWN_MS = 2000L;

    @Override
    public void onEnable() {
        if (!isSupportedServer()) {
            getLogger().severe("SyncShield requires Minecraft/Paper 1.16 or newer. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        migrateLegacyConfigsAndJars();
        saveDefaultConfig();
        reloadPlugin();

        if (debugEnabled) {
            getLogger().info("Debug mode enabled. Expect verbose console output; disable after troubleshooting.");
        }

        if (this.botToken == null || this.botToken.isEmpty() || this.botToken.equalsIgnoreCase("YOUR_BOT_TOKEN_HERE")) {
            getLogger().warning("Telegram bot-token is not set in config.yml. The bot, 2FA approvals, and chat sync will not work until you set bot-token and /syncshield reload (or restart).");
        }

        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        startBlockItemBaker();
        initDatabase();
        loadData();
        initRenderExecutor();

        getServer().getPluginManager().registerEvents(this, this);
        rootLogger = Logger.getLogger("");
        rootLogger.addHandler(rconLogHandler);
        org.bukkit.command.PluginCommand cmd = getCommand("syncshield");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        pollingThread = new Thread(this::pollTelegramUpdates, "SyncShield-Polling");
        pollingThread.start();
        scheduler.scheduleAtFixedRate(this::cleanupExpiredIps, 1, 5, TimeUnit.MINUTES);

        if (!Bukkit.getOnlineMode()) {
            getLogger().warning("SERVER IS RUNNING IN OFFLINE MODE! First-time account linking and IP-based approvals can be spoofed.");
            getLogger().warning("Recommendation: enable online-mode=true or use a secure proxy (e.g., BungeeGuard).");
        }

        debug("Telegram Bot initialized and polling updates.");
        notifyAdmins("server_status", getMsg("ss-server-start"));
    }

    private void reloadPlugin() {
        reloadConfig();
        this.botToken = getConfig().getString("bot-token", "YOUR_BOT_TOKEN_HERE");
        this.debugEnabled = getConfig().getBoolean("debug", false);
        this.ownerId = getConfig().getLong("owner-id", 0L);
        this.op2faMode = getConfig().getString("op-2fa-mode", "session");
        this.nonOp2faMode = getConfig().getString("non-op-2fa-mode", "disabled");
        this.sessionExpiryHours = getConfig().getInt("session-expiry-hours", 12);
        this.expiryMs = (long) sessionExpiryHours * 60 * 60 * 1000L;
        this.adminIds.clear();
        getConfig().getLongList("admin-ids").forEach(this.adminIds::add);
        this.rconEnabled = getConfig().getBoolean("rcon-enabled", true);
        this.privateOnly = getConfig().getBoolean("private-only", true);
        this.dataEncryptionEnabled = getConfig().getBoolean("data-encryption", true);
        this.chatSyncEnabled = getConfig().getBoolean("chat-sync-enabled", false);
        this.chatSyncFromMc = getConfig().getBoolean("chat-sync-from-mc", true);
        this.chatSyncFromTg = getConfig().getBoolean("chat-sync-from-tg", true);
        this.chatSyncChatMessages = getConfig().getBoolean("chat-sync-chat-enabled", true);
        this.chatSyncJoinLeave = getConfig().getBoolean("chat-sync-join-leave-enabled", true);
        this.chatSyncDeath = getConfig().getBoolean("chat-sync-death-enabled", true);
        this.chatSyncRenderInventory = getConfig().getBoolean("chat-sync-render-inventory", true);
        this.chatSyncRenderEnder = getConfig().getBoolean("chat-sync-render-ender", true);
        this.chatSyncRenderItems = getConfig().getBoolean("chat-sync-render-items", true);
        this.chatSyncRenderBooks = getConfig().getBoolean("chat-sync-render-books", true);
        this.chatSyncRenderAdvancements = getConfig().getBoolean("chat-sync-render-advancements", true);
        this.rconAllowedCommands.clear();
        getConfig().getStringList("rcon-allowed-commands").forEach(cmd -> rconAllowedCommands.add(cmd.toLowerCase(Locale.ROOT).trim()));
        this.bakeBlockItemsOnStartup = getConfig().getBoolean("bake-block-items-on-startup", true);
        this.bakeBlockItemsLogProgress = getConfig().getBoolean("bake-block-items-log-progress", true);
        this.chatSyncChatIds.clear();
        getConfig().getLongList("chat-sync-chat-ids").forEach(this.chatSyncChatIds::add);
        this.chatSyncTopicIds.clear();
        for (String entry : getConfig().getStringList("chat-sync-topics")) {
            String[] parts = entry.split(":");
            if (parts.length != 2) continue;
            try {
                long chatId = Long.parseLong(parts[0].trim());
                int topicId = Integer.parseInt(parts[1].trim());
                chatSyncTopicIds.put(chatId, topicId);
            } catch (Exception ignored) {}
        }

        loadMessages();
        this.chatSyncTelegramFormat = messagesConfig.getString("chat-sync-telegram-format", "<b>%player%</b>: %message%");
        this.chatSyncMcFormat = messagesConfig.getString("chat-sync-mc-format", "&7[TG] &f%player%: %message%");
        this.chatSyncAdvancementFormat = messagesConfig.getString("chat-sync-advancement-format", "<i>%player%</i> has made the advancement <b>%advancement%</b> (%description%)");
        this.chatSyncJoinFormat = messagesConfig.getString("chat-sync-join-format", "<i>%player% joined the game</i>");
        this.chatSyncQuitFormat = messagesConfig.getString("chat-sync-quit-format", "<i>%player% left the game</i>");
        this.chatSyncDeathFormat = messagesConfig.getString("chat-sync-death-format", "<i>%player% died: %message%</i>");
        TextureUtils.setLegacyMode(isLegacyMaterialServer());
        approvedIps.clear();
    }

    private final SecureRandom secureRandom = new SecureRandom();

    private boolean isLegacyMaterialServer() {
        try {
            String version = Bukkit.getBukkitVersion();
            if (version == null) return false;
            String base = version.split("-")[0];
            String[] parts = base.split("\\.");
            if (parts.length < 2) return false;
            int minor = Integer.parseInt(parts[1]);
            return minor < 16;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isSupportedServer() {
        try {
            String version = Bukkit.getBukkitVersion();
            if (version == null) return false;
            String base = version.split("-")[0];
            String[] parts = base.split("\\.");
            if (parts.length < 2) return false;
            int minor = Integer.parseInt(parts[1]);
            return minor >= 16;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void startBlockItemBaker() {
        try {
            if (!bakeBlockItemsOnStartup) {
                return;
            }
            if (!blockItemBakeStarted.compareAndSet(false, true)) {
                return;
            }
            String version = getServerVersionString();
            if (version == null || version.isEmpty()) return;
            Path cacheDir = getDataFolder().toPath().resolve("cache").resolve("block_items").resolve(version);
            TextureUtils.setBlockItemCacheDir(cacheDir.resolve("baked"));
            TextureUtils.setExternalTexturesDir(cacheDir.resolve("assets").resolve("textures"));
            getLogger().warning("Block item baking is starting. Expect CPU spikes and possible lag during the initial bake. Disable via bake-block-items-on-startup if needed.");
            Runnable task = new BlockItemBaker(this, version, cacheDir, bakeBlockItemsLogProgress);
            Bukkit.getScheduler().runTaskAsynchronously(this, task);
        } catch (Exception e) {
            getLogger().warning("Block item baker could not start. 3D block icons may fall back to flat textures. Reason: " + e.getMessage() + ". Check network access and file permissions.");
        }
    }

    private void rebakeBlockItemTextures(CommandSender sender) {
        try {
            String version = getServerVersionString();
            if (version == null || version.isEmpty()) {
                sender.sendMessage(colorizeOrDefault("&cUnable to detect server version for baking.", ChatColor.RED));
                return;
            }
            Path cacheDir = getDataFolder().toPath().resolve("cache").resolve("block_items").resolve(version);
            if (Files.exists(cacheDir)) {
                Files.walk(cacheDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {}
                        });
            }
            blockItemBakeStarted.set(false);
            startBlockItemBaker();
            sender.sendMessage(colorizeOrDefault("Textures rebaked successfully.", ChatColor.GREEN));
            getLogger().info("Rebake triggered via command by " + sender.getName() + " for version " + version + ".");
        } catch (Exception e) {
            sender.sendMessage(colorizeOrDefault("Failed to rebake textures. See console for details.", ChatColor.RED));
            getLogger().log(Level.WARNING, "Rebake failed", e);
        }
    }

    private String getServerVersionString() {
        try {
            String version = Bukkit.getBukkitVersion();
            if (version == null) return null;
            return version.split("-")[0];
        } catch (Exception e) {
            return null;
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

    private String colorize(@Nullable String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private String colorizeOrDefault(@Nullable String text, @NotNull ChatColor defaultColor) {
        if (text == null) return "";
        String working = text;
        if (!hasColorCodes(text)) {
            working = "&" + defaultColor.getChar() + text;
        }
        return ChatColor.translateAlternateColorCodes('&', working);
    }

    private boolean hasColorCodes(@NotNull String text) {
        if (text.indexOf('§') >= 0) return true;
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '&') {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if ((code >= '0' && code <= '9')
                        || (code >= 'a' && code <= 'f')
                        || (code >= 'k' && code <= 'o')
                        || code == 'r') {
                    return true;
                }
            }
        }
        return false;
    }

    private String stripColorCodes(@Nullable String text) {
        if (text == null) return "";
        return ChatColor.stripColor(colorize(text));
    }

    private void kickPlayer(@NotNull Player target, @NotNull String reason, @NotNull ChatColor defaultColor) {
        target.kickPlayer(colorizeOrDefault(reason, defaultColor));
    }

    private void loadMessages() {
        String lang = getConfig().getString("language", "en").toLowerCase();
        if (!lang.equals("en") && !lang.equals("ru")) {
            lang = "en";
        }
        File messagesFile = new File(getDataFolder(), "messages.yml");

        if (!messagesFile.exists()) {
            saveResource("messages_" + lang + ".yml", false);
            File langFile = new File(getDataFolder(), "messages_" + lang + ".yml");
            if (langFile.exists()) {
                try {
                    Files.move(langFile.toPath(), messagesFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    getLogger().severe("Could not move messages file to backup: " + e.getMessage() + ". The plugin will continue using the current messages file.");
                }
            }
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        String currentInternalLang = messagesConfig.getString("language-internal", "en");

        if (!currentInternalLang.equalsIgnoreCase(lang)) {
            // Language switched! Backup old
            File backupFile = new File(getDataFolder(), "messages_" + currentInternalLang + ".bak");
            if (messagesFile.exists()) {
                try {
                    Files.move(messagesFile.toPath(), backupFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {}
            }

            // Save new
            saveResource("messages_" + lang + ".yml", true);
            File langFile = new File(getDataFolder(), "messages_" + lang + ".yml");
            if (langFile.exists()) {
                try {
                    Files.move(langFile.toPath(), messagesFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    getLogger().severe("Could not move messages file to backup: " + e.getMessage() + ". The plugin will continue using the current messages file.");
                }
            }

            // Reload
            messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
            getLogger().info("Language switched to " + lang + ". Old messages backed up to " + backupFile.getName() + ". No restart required.");
        }

        // Fill missing keys from jar resource
        InputStream defConfigStream = getResource("messages_" + lang + ".yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String key : defConfig.getKeys(true)) {
                if (!messagesConfig.contains(key)) {
                    messagesConfig.set(key, defConfig.get(key));
                    changed = true;
                }
            }
            if (changed) {
                try {
                    messagesConfig.save(messagesFile);
                } catch (IOException ignored) {}
            }
        }
    }

    private void migrateLegacyConfigsAndJars() {
        try {
            File pluginsDir = getDataFolder().getParentFile();
            if (pluginsDir == null) return;
            File dataDir = getDataFolder();
            if (!dataDir.exists()) dataDir.mkdirs();
            File newConfig = new File(dataDir, "config.yml");
            boolean hasConfig = newConfig.exists() && newConfig.length() > 0;

            String[] legacyNames = new String[]{"Telegram2FALogin", "TelegramLogin", "mrfs-2fa-chat-sync"};
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            File backupRoot = new File(pluginsDir, "LegacyBackup");
            backupRoot.mkdirs();

            boolean migratedAny = false;
            for (String legacyName : legacyNames) {
                File legacyDir = new File(pluginsDir, legacyName);
                File legacyConfig = new File(legacyDir, "config.yml");
                if (!legacyConfig.exists()) continue;
                if (hasConfig) {
                    getLogger().warning("Legacy config detected (" + legacyConfig.getAbsolutePath() + ") but SyncShield config already exists; skipping migration.");
                    continue;
                }

                File backupDir = new File(backupRoot, legacyName + "-" + timestamp);
                copyDirectory(legacyDir.toPath(), backupDir.toPath());

                try {
                    YamlConfiguration legacyYaml = YamlConfiguration.loadConfiguration(legacyConfig);
                    YamlConfiguration mapped = MigrationUtils.mapLegacyConfig(legacyYaml);
                    mapped.save(newConfig);
                    getLogger().info("Migrated " + legacyName + " \u2192 SyncShield/config.yml");
                    deleteDirectory(legacyDir.toPath());
                    hasConfig = true;
                    migratedAny = true;
                } catch (Exception e) {
                    getLogger().warning("Migration failed for " + legacyName + ": " + e.getMessage() + ". Legacy files were not deleted.");
                }
            }

            if (migratedAny) {
                for (String legacyName : legacyNames) {
                    File jar = findLegacyJar(pluginsDir, legacyName);
                    if (jar != null) {
                        File backupDir = new File(backupRoot, legacyName + "-" + timestamp);
                        backupDir.mkdirs();
                        Files.copy(jar.toPath(), new File(backupDir, jar.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                        if (!jar.delete()) {
                            getLogger().warning("Could not delete legacy jar: " + jar.getAbsolutePath());
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().warning("Legacy migration failed: " + e.getMessage() + ". No legacy files were deleted.");
        }
    }

    private File findLegacyJar(File pluginsDir, String legacyName) {
        File[] files = pluginsDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).startsWith(legacyName.toLowerCase(Locale.ROOT)) && name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (files == null || files.length == 0) return null;
        return files[0];
    }

    private void copyDirectory(java.nio.file.Path source, java.nio.file.Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                java.nio.file.Path rel = source.relativize(path);
                java.nio.file.Path dest = target.resolve(rel);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ignored) {}
        });
    }

    private void deleteDirectory(java.nio.file.Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                });
    }

    private String getMsg(String key) {
        return messagesConfig.getString(key, "Message missing: " + key);
    }

    private void debug(String message) {
        if (debugEnabled) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    private void checkTokenError(int statusCode) {
        if (statusCode == 404) {
            getLogger().severe("Telegram API error 404 (Not Found). Please check your bot-token in config.yml; the token is likely invalid or the bot was deleted.");
        }
    }

    private void initDatabase() {
        if (dataEncryptionEnabled) {
            dataPassword = loadOrCreateDataPassword();
            if (dataPassword == null || dataPassword.trim().isEmpty()) {
            getLogger().severe("Data encryption is enabled but no data key is available. Check syncshield_data.key permissions or delete it to regenerate. Encrypted data cannot be read without this key.");
            }
        } else {
            dataPassword = null;
        }
        try {
            String url = "jdbc:sqlite:" + dataDbPath.toAbsolutePath();
            dbConnection = DriverManager.getConnection(url);
            try (Statement stmt = dbConnection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS kv (k TEXT PRIMARY KEY, v BLOB NOT NULL)");
            }
        } catch (Exception e) {
            getLogger().severe("Could not initialize database (syncshield_data.db). The plugin will run but cannot persist data. Check file permissions and disk space. Error: " + e.getMessage());
        }
    }

    private void closeDatabase() {
        if (dbConnection != null) {
            try {
                dbConnection.close();
            } catch (Exception ignored) {}
            dbConnection = null;
        }
    }

    private String loadOrCreateDataPassword() {
        try {
            Files.createDirectories(dataKeyFile.getParent());
            if (Files.exists(dataKeyFile)) {
                String key = new String(Files.readAllBytes(dataKeyFile), StandardCharsets.UTF_8).trim();
                if (!key.isEmpty()) return key;
            }

            String legacy = getConfig().getString("data-password", "").trim();
            if (!legacy.isEmpty() && !"CHANGE_ME_STRONG_PASSWORD".equals(legacy)) {
                Files.write(dataKeyFile, legacy.getBytes(StandardCharsets.UTF_8));
                secureKeyFilePermissions();
                getLogger().warning("Migrated legacy data-password from config.yml into syncshield_data.key. Config password will be ignored going forward.");
                return legacy;
            }

            String generated = generateRandomPassword(16);
            Files.write(dataKeyFile, generated.getBytes(StandardCharsets.UTF_8));
            secureKeyFilePermissions();
            getLogger().info("Generated a new encryption key and stored it in syncshield_data.key. Keep this file safe; it is required to decrypt stored data.");
            return generated;
        } catch (Exception e) {
            getLogger().severe("Could not load or create encryption key: " + e.getMessage() + ". Data encryption cannot be used until this is fixed.");
            return null;
        }
    }

    private void secureKeyFilePermissions() {
        try {
            java.io.File f = dataKeyFile.toFile();
            f.setReadable(false, false);
            f.setWritable(false, false);
            f.setReadable(true, true);
            f.setWritable(true, true);
            if (Files.getFileAttributeView(dataKeyFile, java.nio.file.attribute.PosixFileAttributeView.class) != null) {
                Files.setPosixFilePermissions(dataKeyFile, java.util.Set.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
                ));
            }
        } catch (Exception ignored) {}
    }

    private String generateRandomPassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }

    private byte[] encryptIfNeeded(String json) throws Exception {
        if (!dataEncryptionEnabled) {
            return json.getBytes(StandardCharsets.UTF_8);
        }
        if (dataPassword == null || dataPassword.trim().isEmpty()) {
            throw new IllegalStateException("Data key is missing while data-encryption is enabled.");
        }

        byte[] salt = new byte[DATA_SALT_LEN];
        byte[] iv = new byte[DATA_IV_LEN];
        secureRandom.nextBytes(salt);
        secureRandom.nextBytes(iv);

        SecretKey key = deriveKey(dataPassword.toCharArray(), salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(DATA_GCM_TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));

        byte[] payload = new byte[1 + DATA_SALT_LEN + DATA_IV_LEN + ciphertext.length];
        payload[0] = 1;
        System.arraycopy(salt, 0, payload, 1, DATA_SALT_LEN);
        System.arraycopy(iv, 0, payload, 1 + DATA_SALT_LEN, DATA_IV_LEN);
        System.arraycopy(ciphertext, 0, payload, 1 + DATA_SALT_LEN + DATA_IV_LEN, ciphertext.length);
        return payload;
    }

    private String decryptIfNeeded(byte[] payload) throws Exception {
        if (payload == null || payload.length == 0) return null;
        if (!dataEncryptionEnabled) {
            return new String(payload, StandardCharsets.UTF_8);
        }
        if (payload[0] == '{' || payload[0] == '[') {
            return new String(payload, StandardCharsets.UTF_8);
        }
        if (payload[0] != 1) {
            throw new IllegalStateException("Unknown data payload version.");
        }
        if (payload.length < 1 + DATA_SALT_LEN + DATA_IV_LEN + 1) {
            throw new IllegalStateException("Corrupted encrypted payload.");
        }
        if (dataPassword == null || dataPassword.trim().isEmpty()) {
            throw new IllegalStateException("Data key is missing while data-encryption is enabled.");
        }

        byte[] salt = Arrays.copyOfRange(payload, 1, 1 + DATA_SALT_LEN);
        byte[] iv = Arrays.copyOfRange(payload, 1 + DATA_SALT_LEN, 1 + DATA_SALT_LEN + DATA_IV_LEN);
        byte[] ciphertext = Arrays.copyOfRange(payload, 1 + DATA_SALT_LEN + DATA_IV_LEN, payload.length);

        SecretKey key = deriveKey(dataPassword.toCharArray(), salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(DATA_GCM_TAG_BITS, iv));
        byte[] clear = cipher.doFinal(ciphertext);
        return new String(clear, StandardCharsets.UTF_8);
    }

    private SecretKey deriveKey(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, DATA_PBKDF2_ITERS, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] key = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(key, "AES");
    }

    private static final class SimpleHttpResponse {
        private final int statusCode;
        private final String body;

        private SimpleHttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    private SimpleHttpResponse executeGet(String url, int timeoutSeconds) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(timeoutSeconds * 1000);
        conn.setReadTimeout(timeoutSeconds * 1000);
        conn.setUseCaches(false);
        int status = conn.getResponseCode();
        String body = readResponseBody(conn);
        conn.disconnect();
        return new SimpleHttpResponse(status, body);
    }

    private SimpleHttpResponse executePost(String url, String body, int timeoutSeconds) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(timeoutSeconds * 1000);
        conn.setReadTimeout(timeoutSeconds * 1000);
        conn.setUseCaches(false);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        String responseBody = readResponseBody(conn);
        conn.disconnect();
        return new SimpleHttpResponse(status, responseBody);
    }

    private void executeTelegramPhoto(long chatId, byte[] imageBytes, @Nullable String caption, @Nullable Integer threadId) throws IOException {
        String boundary = "----syncshield" + UUID.randomUUID().toString().replace("-", "");
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.telegram.org/bot" + botToken + "/sendPhoto").openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (BufferedOutputStream out = new BufferedOutputStream(conn.getOutputStream())) {
            writeFormField(out, boundary, "chat_id", String.valueOf(chatId));
            if (threadId != null) {
                writeFormField(out, boundary, "message_thread_id", String.valueOf(threadId));
            }
            if (caption != null && !caption.trim().isEmpty()) {
                writeFormField(out, boundary, "caption", caption);
                writeFormField(out, boundary, "parse_mode", "HTML");
            }
            writeFileField(out, boundary, "photo", "image.png", "image/png", imageBytes);
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        int status = conn.getResponseCode();
        String responseBody = readResponseBody(conn);
        if (status != 200 && responseBody != null && !responseBody.isEmpty()) {
            getLogger().severe("Telegram API error (sendPhoto): " + status + " - " + redactToken(responseBody) + ". Check bot permissions and chat ID.");
        }
        conn.disconnect();
    }

    private void writeFormField(OutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeFileField(OutputStream out, String boundary, String name, String filename, String contentType, byte[] data) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String readResponseBody(HttpURLConnection conn) throws IOException {
        InputStream stream = null;
        try {
            stream = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) return "";
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();
        } finally {
            if (stream != null) {
                try { stream.close(); } catch (IOException ignored) {}
            }
        }
    }

    private String redactToken(String text) {
        if (text == null) return null;
        return text.replaceAll("bot[0-9A-Za-z:_-]+", "bot<redacted>");
    }

    @Override
    public void onDisable() {
        isRunning = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
        notifyAdminsSync("server_status", getMsg("ss-server-stop"));
        saveData();
        closeDatabase();
        if (rootLogger != null) {
            rootLogger.removeHandler(rconLogHandler);
        }
        if (renderExecutor != null) {
            renderExecutor.shutdownNow();
        }
        scheduler.shutdown();
        httpExecutor.shutdownNow();
    }

    private void initRenderExecutor() {
        renderExecutor = new ThreadPoolExecutor(
                2,
                2,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(RENDER_QUEUE_CAPACITY),
                r -> new Thread(r, "syncshield-render-" + UUID.randomUUID()),
                (r, executor) -> getLogger().warning("Render task rejected due to queue limit; consider raising limits or reducing spam.")
        );
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String ip = player.getAddress().getAddress().getHostAddress();

        String pMode = player2faModes.get(uuid);
        String effMode = (pMode != null) ? pMode : (player.isOp() ? op2faMode : nonOp2faMode);

        if (effMode.equalsIgnoreCase("always")) {
            Map<String, Long> ips = approvedIps.get(uuid);
            if (ips != null) {
                if (ips.remove(ip) != null) {
                    debug("Removed " + player.getName() + "'s IP from session (mode: always).");
                }
            }
        }

        if (player.isOp()) {
            notifyAdmins("join_leave", getMsg("ss-admin-join").replace("%player%", escapeHtml(player.getName())));
        }

        if (chatSyncEnabled && chatSyncFromMc && chatSyncJoinLeave) {
            String formatted = chatSyncJoinFormat.replace("%player%", escapeHtml(player.getName()));
            sendChatSyncMessageToTelegram(formatted);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer().isOp()) {
            notifyAdmins("join_leave", getMsg("ss-admin-quit").replace("%player%", escapeHtml(event.getPlayer().getName())));
        }
        if (chatSyncEnabled && chatSyncFromMc && chatSyncJoinLeave) {
            String formatted = chatSyncQuitFormat.replace("%player%", escapeHtml(event.getPlayer().getName()));
            sendChatSyncMessageToTelegram(formatted);
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer().isOp()) {
            notifyAdmins("commands", getMsg("ss-admin-cmd").replace("%player%", escapeHtml(event.getPlayer().getName())).replace("%command%", escapeHtml(event.getMessage())));
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!chatSyncEnabled || !chatSyncFromMc || event.isCancelled()) return;
        Player player = event.getPlayer();
        String message = event.getMessage();
        handleChatSyncFromMinecraft(player, message);
    }

    @EventHandler
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (!chatSyncEnabled || !chatSyncRenderAdvancements) return;
        handleAdvancementRender(event);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!chatSyncEnabled || !chatSyncFromMc || !chatSyncDeath) return;
        String message = event.getDeathMessage();
        if (message == null || message.trim().isEmpty()) return;
        String formatted = chatSyncDeathFormat
                .replace("%player%", escapeHtml(event.getEntity().getName()))
                .replace("%message%", escapeHtml(message));
        sendChatSyncMessageToTelegram(formatted);
    }

    private void notifyAdmins(String category, String message) {
        if (ownerId != 0 && !disabledNotifications.getOrDefault(ownerId, Collections.emptySet()).contains(category)) {
            sendTelegramMessage(ownerId, message);
        }
        for (long adminId : adminIds) {
            if (adminId != ownerId && !disabledNotifications.getOrDefault(adminId, Collections.emptySet()).contains(category)) {
                sendTelegramMessage(adminId, message);
            }
        }
    }

    private void notifyAdminsSync(String category, String message) {
        if (ownerId != 0 && !disabledNotifications.getOrDefault(ownerId, Collections.emptySet()).contains(category)) {
            sendTelegramMessageSync(ownerId, message);
        }
        for (long adminId : adminIds) {
            if (adminId != ownerId && !disabledNotifications.getOrDefault(adminId, Collections.emptySet()).contains(category)) {
                sendTelegramMessageSync(adminId, message);
            }
        }
    }

    private boolean isChatAdmin(long chatId) {
        if (chatId == 0) return false;
        return chatId == ownerId || adminIds.contains(chatId);
    }

    @EventHandler
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String ip = event.getAddress().getHostAddress();
        String name = event.getName();

        debug("Player " + name + " (" + ip + ") is attempting to join.");

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        boolean isOp = offlinePlayer.isOp();

        String playerMode = player2faModes.get(uuid);
        String effectiveMode = (playerMode != null) ? playerMode : (isOp ? op2faMode : nonOp2faMode);

        if (effectiveMode.equalsIgnoreCase("disabled")) {
            debug("2FA disabled for " + name + " (OP: " + isOp + "). Allowing join.");
            return;
        }

        debug("Player " + name + " (OP: " + isOp + ") mode: " + effectiveMode + ". Checking protection...");

        Set<String> blacklist = blacklistedIps.get(uuid);
        if (blacklist != null && blacklist.contains(ip)) {
            debug("Refusing connection for " + name + ": IP " + ip + " is blacklisted.");
            notifyAdmins("security", getMsg("ss-admin-blacklist-attempt").replace("%player%", escapeHtml(name)).replace("%ip%", escapeHtml(ip)));
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    colorizeOrDefault(getMsg("kick-blacklisted"), ChatColor.RED));
            return;
        }

        if (!linkedChats.containsKey(uuid)) {
            debug("Refusing connection for " + name + ": Account not linked to Telegram.");
            notifyAdmins("security", getMsg("ss-admin-unlinked-attempt").replace("%player%", escapeHtml(name)).replace("%ip%", escapeHtml(ip)));
            
            String code = generateCode(uuid);
            pendingLinks.put(code, new PendingLink(uuid));
            
            String kickMsg = getMsg("kick-unlinked").replace("%code%", code);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    colorizeOrDefault(kickMsg, ChatColor.RED));
            return;
        }

        Map<String, Long> playerApproved = approvedIps.get(uuid);
        if (playerApproved != null) {
            Long expiry = playerApproved.get(ip);
            if (expiry != null) {
                if (effectiveMode.equalsIgnoreCase("whitelist") || System.currentTimeMillis() < expiry) {
                    if (effectiveMode.equalsIgnoreCase("always")) {
                        // For 'always' mode, we allow it once and remove immediately.
                        playerApproved.remove(ip);
                        debug("Allowing one-time 'always' connection for " + name + " and removing approval.");
                    } else {
                        debug("Allowing connection for " + name + ": IP " + ip + " is already approved.");
                    }
                    return;
                } else {
                    debug("IP " + ip + " for player " + name + " has expired approval.");
                    playerApproved.remove(ip);
                }
            }
        }

        debug("Suspending login for " + name + ": 2FA verification required via Telegram.");
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                colorizeOrDefault(getMsg("kick-2fa"), ChatColor.YELLOW));
        
        String approvalId = generateShortId();
        pendingApprovals.put(approvalId, new PendingApproval(uuid, ip));
        
        String tgMsg = getMsg("ss-2fa-prompt").replace("%ip%", escapeHtml(ip)).replace("%player%", escapeHtml(name));
        sendTelegramMessageWithButtons(linkedChats.get(uuid), tgMsg, approvalId);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("syncshield")) return false;

        if (args.length == 0) {
            sender.sendMessage(colorizeOrDefault(getMsg("ss-usage"), ChatColor.RED));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("link")) {
            if (!sender.hasPermission("syncshield.use")) {
                sender.sendMessage(colorizeOrDefault(getMsg("ss-no-permission"), ChatColor.RED));
                return true;
            }
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage(colorizeOrDefault(getMsg("mc-player-only"), ChatColor.RED));
                return true;
            }
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;

            UUID uuid = player.getUniqueId();
            if (linkedChats.containsKey(uuid)) {
                sender.sendMessage(colorizeOrDefault(getMsg("mc-already-linked"), ChatColor.RED));
                return true;
            }

            String code = generateCode(uuid);
            pendingLinks.put(code, new PendingLink(uuid));
            sender.sendMessage(colorizeOrDefault(getMsg("mc-link-code").replace("%code%", code), ChatColor.YELLOW));
            return true;
        }

        if (subCommand.equals("reload")) {
            if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("syncshield.admin")) {
                sender.sendMessage(colorizeOrDefault(getMsg("ss-no-permission"), ChatColor.RED));
                return true;
            }
            reloadPlugin();
            sender.sendMessage(colorizeOrDefault(getMsg("ss-reloaded"), ChatColor.GREEN));
            return true;
        }

        if (subCommand.equals("settings")) {
            if (!sender.hasPermission("syncshield.use")) {
                sender.sendMessage(colorizeOrDefault(getMsg("ss-no-permission"), ChatColor.RED));
                return true;
            }
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage(colorizeOrDefault(getMsg("mc-player-only"), ChatColor.RED));
                return true;
            }
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;

            if (args.length < 2) {
                sender.sendMessage(colorizeOrDefault(getMsg("mc-settings-usage"), ChatColor.RED));
                return true;
            }

            String mode = args[1].toLowerCase();
            if (mode.equals("always") || mode.equals("session") || mode.equals("whitelist") || mode.equals("disabled")) {
                player2faModes.put(player.getUniqueId(), mode);
                saveData();
                sender.sendMessage(colorizeOrDefault(getMsg("mc-settings-updated").replace("%mode%", mode), ChatColor.GREEN));
            } else {
                sender.sendMessage(colorizeOrDefault(getMsg("mc-settings-invalid-mode"), ChatColor.RED));
            }
            return true;
        }

        if (subCommand.equals("config")) {
            if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("syncshield.admin")) {
                sender.sendMessage(colorizeOrDefault(getMsg("ss-no-permission"), ChatColor.RED));
                return true;
            }

            if (sender instanceof org.bukkit.entity.Player) {
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
                Long chatId = linkedChats.get(player.getUniqueId());
                if (chatId == null || !isChatAdmin(chatId)) {
                    sender.sendMessage(colorizeOrDefault(getMsg("ss-unauthorized-config"), ChatColor.RED));
                    return true;
                }
            }

            if (args.length < 3) {
                sender.sendMessage(colorizeOrDefault(getMsg("ss-config-usage"), ChatColor.RED));
                return true;
            }

            String path = args[1];
            String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

            try {
                if (path.equalsIgnoreCase("debug")) {
                    boolean val = Boolean.parseBoolean(value);
                    getConfig().set(path, val);
                    this.debugEnabled = val;
                } else if (path.equalsIgnoreCase("owner-id")) {
                    long val = Long.parseLong(value);
                    getConfig().set(path, val);
                    this.ownerId = val;
                } else if (path.equalsIgnoreCase("bot-token")) {
                    getConfig().set(path, value);
                    this.botToken = value;
                } else if (path.equalsIgnoreCase("admin-ids")) {
                    String[] ids = value.split(",");
                    List<Long> longList = new ArrayList<>();
                    for (String s : ids) {
                        try { longList.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) {}
                    }
                    getConfig().set(path, longList);
                    this.adminIds.clear();
                    this.adminIds.addAll(longList);
                } else if (path.equalsIgnoreCase("op-2fa-mode")) {
                    getConfig().set(path, value);
                    this.op2faMode = value;
                } else if (path.equalsIgnoreCase("session-expiry-hours")) {
                    int val = Integer.parseInt(value);
                    getConfig().set(path, val);
                    this.sessionExpiryHours = val;
                    this.expiryMs = (long) val * 60 * 60 * 1000L;
                } else if (path.equalsIgnoreCase("rcon-enabled")) {
                    boolean val = Boolean.parseBoolean(value);
                    getConfig().set(path, val);
                    this.rconEnabled = val;
                } else if (path.equalsIgnoreCase("private-only")) {
                    boolean val = Boolean.parseBoolean(value);
                    getConfig().set(path, val);
                    this.privateOnly = val;
                } else {
                    getConfig().set(path, value);
                }
                saveConfig();
                sender.sendMessage(colorizeOrDefault(getMsg("ss-config-updated").replace("%path%", path).replace("%value%", value), ChatColor.GREEN));
            } catch (Exception e) {
                sender.sendMessage(colorizeOrDefault(getMsg("mc-config-error").replace("%error%", e.getMessage()), ChatColor.RED));
            }
            return true;
        }

        if (subCommand.equals("debug")) {
            if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("syncshield.debug")) {
                sender.sendMessage(colorizeOrDefault(getMsg("ss-no-permission"), ChatColor.RED));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("rebake-textures")) {
                rebakeBlockItemTextures(sender);
                return true;
            }
            sender.sendMessage(colorizeOrDefault("&eUsage: /syncshield debug rebake-textures", ChatColor.YELLOW));
            return true;
        }

        sender.sendMessage(colorizeOrDefault(getMsg("ss-usage"), ChatColor.RED));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("syncshield")) return Collections.emptyList();

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("link".startsWith(input)) completions.add("link");
            if ("settings".startsWith(input)) completions.add("settings");
            if (sender.isOp() || sender instanceof ConsoleCommandSender) {
                if ("reload".startsWith(input)) completions.add("reload");
                if ("config".startsWith(input)) completions.add("config");
                if ("debug".startsWith(input)) completions.add("debug");
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            String input = args[1].toLowerCase();
            if (subCommand.equals("settings")) {
                if ("always".startsWith(input)) completions.add("always");
                if ("session".startsWith(input)) completions.add("session");
                if ("whitelist".startsWith(input)) completions.add("whitelist");
                if ("disabled".startsWith(input)) completions.add("disabled");
            } else if (subCommand.equals("config") && (sender.isOp() || sender instanceof ConsoleCommandSender)) {
                List<String> paths = Arrays.asList("bot-token", "debug", "owner-id", "admin-ids", "language", "op-2fa-mode", "session-expiry-hours");
                for (String path : paths) {
                    if (path.startsWith(input)) completions.add(path);
                }
            } else if (subCommand.equals("debug") && (sender.isOp() || sender instanceof ConsoleCommandSender)) {
                if ("rebake-textures".startsWith(input)) completions.add("rebake-textures");
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            String path = args[1].toLowerCase();
            String input = args[2].toLowerCase();
            if (subCommand.equals("config") && (sender.isOp() || sender instanceof ConsoleCommandSender)) {
                if (path.equals("op-2fa-mode")) {
                    if ("always".startsWith(input)) completions.add("always");
                    if ("session".startsWith(input)) completions.add("session");
                    if ("whitelist".startsWith(input)) completions.add("whitelist");
                } else if (path.equals("debug")) {
                    if ("true".startsWith(input)) completions.add("true");
                    if ("false".startsWith(input)) completions.add("false");
                } else if (path.equals("language")) {
                    if ("en".startsWith(input)) completions.add("en");
                    if ("ru".startsWith(input)) completions.add("ru");
                }
            }
        }

        return completions;
    }

    private String generateCode(UUID uuid) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String code;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) sb.append(alphabet.charAt(secureRandom.nextInt(alphabet.length())));
            code = sb.toString();
        } while (pendingLinks.containsKey(code));
        return code;
    }

    private String generateShortId() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        String id;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) sb.append(alphabet.charAt(secureRandom.nextInt(alphabet.length())));
            id = sb.toString();
        } while (pendingApprovals.containsKey(id) || ipManagerShortIds.containsKey(id));
        return id;
    }

    private static class PendingLink {
        final UUID uuid;
        final long timestamp;
        PendingLink(UUID uuid) {
            this.uuid = uuid;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private static class PendingApproval {
        final UUID uuid;
        final String ip;
        final long timestamp;
        PendingApproval(UUID uuid, String ip) {
            this.uuid = uuid;
            this.ip = ip;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private void cleanupExpiredIps() {
        long now = System.currentTimeMillis();
        approvedIps.values().forEach(ips -> ips.values().removeIf(expiry -> now > expiry));
        pendingLinks.entrySet().removeIf(entry -> now - entry.getValue().timestamp > 10 * 60 * 1000L);
        pendingApprovals.entrySet().removeIf(entry -> now - entry.getValue().timestamp > 5 * 60 * 1000L);
        ipManagerShortIdsTimestamp.entrySet().removeIf(entry -> now - entry.getValue() > 60 * 60 * 1000L);
        ipManagerShortIds.keySet().removeIf(shortId -> !ipManagerShortIdsTimestamp.containsKey(shortId));
        debug("Cleaned up expired 2FA sessions and pending states.");
    }

    private void pollTelegramUpdates() {
        getLogger().info("Telegram update polling started. Long polling is active; if you see no updates, recheck bot-token and network access.");
        long offset = 0;
        boolean firstRun = true;
        while (isRunning) {
            try {
                if (firstRun) {
                    // Skip old updates
                    SimpleHttpResponse response = executeGet("https://api.telegram.org/bot" + botToken + "/getUpdates?offset=-1&limit=1", 10);
                    if (response.statusCode == 200) {
                        JsonObject json = new JsonParser().parse(response.body).getAsJsonObject();
                        if (json.has("result")) {
                            JsonArray result = json.getAsJsonArray("result");
                            if (result.size() > 0) {
                                offset = result.get(0).getAsJsonObject().get("update_id").getAsLong() + 1;
                                debug("Skipping old updates, starting from offset " + offset);
                            }
                        }
                    } else {
                        checkTokenError(response.statusCode);
                    }
                    firstRun = false;
                }

                SimpleHttpResponse response = executeGet("https://api.telegram.org/bot" + botToken + "/getUpdates?offset=" + offset + "&timeout=30", 45);
                if (response.statusCode == 200) {
                    JsonObject json = new JsonParser().parse(response.body).getAsJsonObject();
                    if (!json.has("result")) {
                        getLogger().severe("Telegram API error: result field is missing. Response: " + redactToken(response.body) + ". This can happen if the token is invalid or Telegram returned an error payload.");
                        continue;
                    }
                    JsonArray result = json.getAsJsonArray("result");
                    for (JsonElement element : result) {
                        JsonObject update = element.getAsJsonObject();
                        offset = update.get("update_id").getAsLong() + 1;
                        debug("Received Telegram update: " + update);

                        if (update.has("message")) {
                            JsonObject message = update.getAsJsonObject("message");
                            long chatId = message.get("chat").getAsJsonObject().get("id").getAsLong();
                            long fromId = message.has("from") ? message.get("from").getAsJsonObject().get("id").getAsLong() : chatId;
                            String chatType = message.get("chat").getAsJsonObject().get("type").getAsString();
                            boolean isChatSyncChat = chatSyncEnabled && chatSyncChatIds.contains(chatId);
                            Integer requiredTopicId = chatSyncTopicIds.get(chatId);
                            boolean topicMatches = requiredTopicId == null
                                    || (message.has("message_thread_id") && message.get("message_thread_id").getAsInt() == requiredTopicId);
                            
                            if (privateOnly && !chatType.equals("private") && !isChatSyncChat) {
                                debug("Ignoring message from non-private chat: " + chatType);
                                continue;
                            }

                            if (message.has("text")) {
                                String text = message.get("text").getAsString();

                                if (text.startsWith("/start")) {
                                    handleStartCommand(chatId, fromId);
                                } else if (text.startsWith("/mclink")) {
                                    String[] parts = text.split(" ");
                                    if (parts.length < 2) {
                                        sendTelegramMessage(chatId, getMsg("ss-mclink-usage"));
                                    } else {
                                        String code = parts[1];
                                        if (pendingLinks.containsKey(code)) {
                                            UUID uuid = pendingLinks.remove(code).uuid;
                                            linkedChats.put(uuid, chatId);
                                            String name = Bukkit.getOfflinePlayer(uuid).getName();
                                            sendTelegramMessage(chatId, getMsg("ss-linked").replace("%player%", name != null ? escapeHtml(name) : "Unknown"));
                                            saveData();
                                        } else {
                                            sendTelegramMessage(chatId, getMsg("ss-invalid-code"));
                                        }
                                    }
                                } else if (pendingLinks.containsKey(text)) {
                                    UUID uuid = pendingLinks.remove(text).uuid;
                                    linkedChats.put(uuid, chatId);
                                    String name = Bukkit.getOfflinePlayer(uuid).getName();
                                    sendTelegramMessage(chatId, getMsg("ss-linked").replace("%player%", name != null ? escapeHtml(name) : "Unknown"));
                                    saveData();
                                } else if (isChatAdmin(fromId)) {
                                    if (text.startsWith("/rcon ") && rconEnabled) {
                                        chatStates.remove(chatId);
                                        handleRconCommand(chatId, text.substring(6));
                                    } else if (text.equalsIgnoreCase("/players")) {
                                        chatStates.remove(chatId);
                                        handlePlayersCommand(chatId);
                                    } else if (text.equalsIgnoreCase("/settings")) {
                                        chatStates.remove(chatId);
                                        handleSettingsCommand(chatId);
                                    } else if (text.equalsIgnoreCase("/cancel")) {
                                        chatStates.remove(chatId);
                                        sendTelegramMessage(chatId, getMsg("ss-cancelled"));
                                    } else if (chatStates.containsKey(chatId)) {
                                        handleStateMessage(chatId, text);
                                    } else if (text.startsWith("/") && rconEnabled) {
                                        chatStates.remove(chatId);
                                        handleRconCommand(chatId, text.substring(1));
                                    }
                                }

                                if (isChatSyncChat && chatSyncFromTg && topicMatches && !text.startsWith("/")) {
                                    String senderName = getTelegramSenderName(message);
                                    sendChatSyncToMinecraft(text, senderName, message.get("chat").getAsJsonObject());
                                }
                            }
                        } else if (update.has("callback_query")) {
                            handleCallback(update.getAsJsonObject("callback_query"));
                        }
                    }
                } else {
                    getLogger().severe("Error polling Telegram updates: API returned " + response.statusCode + " - " + response.body + ". Check network access and bot-token validity.");
                    checkTokenError(response.statusCode);
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            } catch (Exception e) {
                if (isRunning && !(e instanceof java.io.IOException && e.getCause() instanceof java.lang.InterruptedException)) {
                    getLogger().severe("Error polling Telegram updates: " + e.getMessage() + ". Check network access and Telegram availability.");
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    private List<UUID> getLinkedUuids(long chatId) {
        List<UUID> uuids = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : linkedChats.entrySet()) {
            if (entry.getValue() == chatId) {
                uuids.add(entry.getKey());
            }
        }
        return uuids;
    }

    private void handleStartCommand(long chatId, long fromId) {
        String welcome = getMsg("ss-start").replace("%chatId%", String.valueOf(chatId));
        JsonObject markup = new JsonObject();
        JsonArray keyboard = new JsonArray();

        if (isChatAdmin(fromId)) {
            JsonArray row1 = new JsonArray();
            row1.add(createButton(getMsg("ss-btn-players"), "player:list_all"));
            row1.add(createButton(getMsg("ss-btn-settings"), "settings:menu"));
            keyboard.add(row1);

            JsonArray row2 = new JsonArray();
            row2.add(createButton(getMsg("ss-btn-blacklist-admin"), "bl:list"));
            keyboard.add(row2);
        }

        List<UUID> linked = getLinkedUuids(chatId);
        if (!linked.isEmpty()) {
            JsonArray row = new JsonArray();
            row.add(createButton(getMsg("ss-btn-me"), "me:list"));
            keyboard.add(row);
        }

        if (keyboard.size() > 0) {
            markup.add("inline_keyboard", keyboard);
        }

        sendTelegramMessage(chatId, welcome, markup);
    }

    private void handleSettingsCommand(long chatId) {
        handleSettingsCommand(chatId, null);
    }

    private void handleSettingsCommand(long chatId, Integer messageId) {
        Set<String> disabled = disabledNotifications.getOrDefault(chatId, Collections.emptySet());

        JsonObject markup = new JsonObject();
        JsonArray keyboard = new JsonArray();

        keyboard.add(createToggleRow("server_status", getMsg("ss-btn-server-status"), disabled));
        keyboard.add(createToggleRow("join_leave", getMsg("ss-btn-join-leave"), disabled));
        keyboard.add(createToggleRow("commands", getMsg("ss-btn-commands"), disabled));
        keyboard.add(createToggleRow("security", getMsg("ss-btn-security"), disabled));

        JsonArray modeRow = new JsonArray();
        modeRow.add(createButton("⚙️ Default 2FA: " + op2faMode.toUpperCase(), "settings:df_2fa_m"));
        keyboard.add(modeRow);

        markup.add("inline_keyboard", keyboard);

        if (messageId != null) {
            editTelegramMessage(chatId, messageId, getMsg("ss-settings-menu"), markup);
        } else {
            sendTelegramMessage(chatId, getMsg("ss-settings-menu"), markup);
        }
    }

    private JsonArray createToggleRow(String category, String label, Set<String> disabled) {
        JsonArray row = new JsonArray();
        boolean isEnabled = !disabled.contains(category);
        String statusText = isEnabled ? getMsg("ss-enabled") : getMsg("ss-disabled");
        row.add(createButton(label + ": " + statusText, "settings:toggle:" + category));
        return row;
    }

    private void handleChatSyncFromMinecraft(Player player, String message) {
        if (chatSyncChatIds.isEmpty()) return;
        String lower = message.toLowerCase(Locale.ROOT);
        String playerName = player.getName();

        if (chatSyncRenderInventory && lower.contains("[inv]")) {
            if (enqueueRender(player.getUniqueId(), () -> {
                String[] beforeAfter = userMessageBeforeAfter(message, "[inv]");
                byte[] image = new InventoryRenderer().renderInventory(player.getInventory());
                String caption = formatCaption(playerName, beforeAfter[0], "Inventory", beforeAfter[1], true);
                sendChatSyncPhotoToTelegram(image, caption);
            })) return;
        }

        if (chatSyncRenderEnder && lower.contains("[ender]")) {
            if (enqueueRender(player.getUniqueId(), () -> {
                String[] beforeAfter = userMessageBeforeAfter(message, "[ender]");
                byte[] image = new EnderChestRenderer().renderEnderChest(player.getEnderChest());
                String caption = formatCaption(playerName, beforeAfter[0], "Ender Chest", beforeAfter[1], true);
                sendChatSyncPhotoToTelegram(image, caption);
            })) return;
        }

        if (chatSyncRenderItems && lower.contains("[item]")) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType() == null || item.getType() == Material.AIR) {
                String[] beforeAfter = userMessageBeforeAfter(message, "[item]");
                String caption = playerName + ": " + beforeAfter[0] + "[Empty hand]" + beforeAfter[1];
                sendChatSyncMessageToTelegram(caption);
                return;
            }
            if (chatSyncRenderBooks && item != null && item.getType() != null && item.getType().name().toLowerCase(Locale.ROOT).contains("book")) {
                enqueueRender(player.getUniqueId(), () -> {
                    BookRenderer.BookRenderResult result = new BookRenderer().renderBook(item);
                    sendBookRenderToTelegram(playerName, result);
                });
                return;
            }

            enqueueRender(player.getUniqueId(), () -> {
                if (item != null && isShulkerBox(item)) {
                    Inventory shulkerInventory = getShulkerBoxContents(item);
                    String[] beforeAfter = userMessageBeforeAfter(message, "[item]");
                    byte[] image = new EnderChestRenderer().renderEnderChest(shulkerInventory);
                    String caption = formatCaption(playerName, beforeAfter[0], "Shulker Box", beforeAfter[1], false);
                    sendChatSyncPhotoToTelegram(image, caption);
                    return;
                }

                ItemRenderer.ItemRenderResult result = new ItemRenderer().renderItem(item);
                String displayName = result.getItemName();
                String amountSuffix = item != null && item.getAmount() > 1 ? " x " + item.getAmount() : "";
                String[] beforeAfter = userMessageBeforeAfter(message, "[item]");
                String caption = playerName + ": " + beforeAfter[0] + "[" + escapeHtml(displayName + amountSuffix) + "]" + beforeAfter[1];
                sendChatSyncPhotoToTelegram(result.getImageBytes(), caption);
            });
            return;
        }

        if (chatSyncChatMessages) {
            String formatted = chatSyncTelegramFormat
                    .replace("%player%", escapeHtml(playerName))
                    .replace("%message%", escapeHtml(message));
            sendChatSyncMessageToTelegram(formatted);
        }
    }

    private void sendBookRenderToTelegram(String playerName, BookRenderer.BookRenderResult result) {
        if (result.getPages().isEmpty()) return;
        int total = result.getPages().size();
        int index = 1;
        for (byte[] page : result.getPages()) {
            String caption;
            if (index == 1) {
                caption = result.getCaption() != null ? escapeHtml(result.getCaption()) : "Book";
                caption = playerName + ": " + caption;
            } else {
                caption = playerName + ": Page " + index + " of " + total;
            }
            sendChatSyncPhotoToTelegram(page, caption);
            index++;
        }
    }

    private void sendChatSyncMessageToTelegram(String formattedHtml) {
        for (long chatId : chatSyncChatIds) {
            Integer threadId = chatSyncTopicIds.get(chatId);
            sendTelegramMessage(chatId, formattedHtml, threadId);
        }
    }

    private void sendChatSyncToMinecraft(String text, String username, JsonObject chatObj) {
        if (!chatSyncFromTg) return;
        String chatTitle = chatObj != null && chatObj.has("title") ? chatObj.get("title").getAsString() : null;
        String formatted = chatSyncMcFormat
                .replace("%player%", username)
                .replace("%message%", text)
                .replace("%chat%", chatTitle != null ? chatTitle : "Telegram");
        String colored = colorize(formatted);
        Bukkit.getScheduler().runTask(this, () -> Bukkit.broadcastMessage(colored));
    }

    private void sendChatSyncPhotoToTelegram(byte[] imageBytes, String caption) {
        if (imageBytes == null || imageBytes.length == 0) {
            sendChatSyncMessageToTelegram(caption);
            return;
        }
        for (long chatId : chatSyncChatIds) {
            Integer threadId = chatSyncTopicIds.get(chatId);
            sendTelegramPhoto(chatId, imageBytes, caption, threadId);
        }
    }

    private String formatCaption(String playerName, String before, String label, String after, boolean showPlayerLabel) {
        String prefix = showPlayerLabel ? playerName + "'s " : "";
        return playerName + ": " + before + "[" + escapeHtml(prefix + label) + "]" + after;
    }

    private String[] userMessageBeforeAfter(String message, String tag) {
        int idx = message.indexOf(tag);
        String before = idx >= 0 ? message.substring(0, idx) : message;
        String after = idx >= 0 ? message.substring(idx + tag.length()) : "";
        return new String[]{escapeHtml(before), escapeHtml(after)};
    }

    private boolean enqueueRender(UUID playerId, Runnable task) {
        long now = System.currentTimeMillis();
        Long last = renderCooldowns.get(playerId);
        if (last != null && now - last < RENDER_COOLDOWN_MS) {
            return false;
        }
        renderCooldowns.put(playerId, now);
        try {
            renderExecutor.execute(task);
            return true;
        } catch (Exception e) {
            getLogger().warning("Render enqueue failed: " + e.getMessage());
            return false;
        }
    }

    private void handleAdvancementRender(PlayerAdvancementDoneEvent event) {
        if (chatSyncChatIds.isEmpty()) return;
        try {
            Object advancement = event.getAdvancement();
            Object display = null;
            try {
                display = advancement.getClass().getMethod("getDisplay").invoke(advancement);
            } catch (Exception ignored) {}
            if (display == null) return;

            boolean announce = true;
            try {
                Object val = display.getClass().getMethod("doesAnnounceToChat").invoke(display);
                if (val instanceof Boolean) announce = (Boolean) val;
            } catch (Exception ignored) {}
            if (!announce) return;

            String title = safeToString(callIfExists(display, "getTitle", "title", "getDisplayName"));
            String description = safeToString(callIfExists(display, "getDescription", "description"));
            String frame = safeToString(callIfExists(display, "getFrame", "frame", "getFrameType"));

            ItemStack icon = null;
            Object iconObj = callIfExists(display, "getIcon", "icon");
            if (iconObj instanceof ItemStack) icon = (ItemStack) iconObj;

            Color textColor = Color.WHITE;
            Object frameObj = callIfExists(display, "getFrame", "frame");
            if (frameObj != null) {
                Object colorObj = callIfExists(frameObj, "getColor", "color");
                if (colorObj != null) {
                    String hex = safeToString(callIfExists(colorObj, "asHexString", "asHexString"));
                    if (hex != null && hex.startsWith("#")) {
                        try { textColor = Color.decode(hex); } catch (Exception ignored) {}
                    }
                }
            }

            String playerName = event.getPlayer().getName();
            String message = chatSyncAdvancementFormat
                    .replace("%player%", escapeHtml(playerName))
                    .replace("%advancement%", escapeHtml(title))
                    .replace("%description%", escapeHtml(description));

            byte[] image = new AdvancementRenderer().renderAdvancement(title, frame, icon, textColor);
            sendChatSyncPhotoToTelegram(image, message);
        } catch (Exception ignored) {}
    }

    private Object callIfExists(Object target, String... methodNames) {
        if (target == null) return null;
        for (String name : methodNames) {
            try {
                return target.getClass().getMethod(name).invoke(target);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String safeToString(Object value) {
        if (value == null) return "";
        String plain = toPlainText(value);
        if (plain == null) plain = value.toString();
        plain = ChatColor.stripColor(plain);
        return plain.replace("\n", " ").replace("\r", " ").trim();
    }

    private String toPlainText(Object value) {
        if (value == null) return "";
        if (value instanceof String) return (String) value;
        try {
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            if (componentClass.isInstance(value)) {
                Object component = value;
                try {
                    Class<?> globalTranslator = Class.forName("net.kyori.adventure.translation.GlobalTranslator");
                    Object rendered = globalTranslator.getMethod("render", componentClass, Locale.class)
                            .invoke(null, component, Locale.ENGLISH);
                    if (rendered != null) component = rendered;
                } catch (Exception ignored) {}

                Class<?> serializerClass = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
                Object serializer = serializerClass.getMethod("plainText").invoke(null);
                return (String) serializerClass.getMethod("serialize", componentClass).invoke(serializer, component);
            }
        } catch (Exception ignored) {}
        return value.toString();
    }

    private boolean isShulkerBox(ItemStack item) {
        if (item == null || item.getType() == null) return false;
        return item.getType().name().toUpperCase(Locale.ROOT).contains("SHULKER_BOX");
    }

    private Inventory getShulkerBoxContents(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof BlockStateMeta)) {
            return Bukkit.createInventory(null, 27);
        }
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        if (!(meta.getBlockState() instanceof ShulkerBox)) {
            return Bukkit.createInventory(null, 27);
        }
        ShulkerBox shulkerBox = (ShulkerBox) meta.getBlockState();
        return shulkerBox.getInventory();
    }

    private String getTelegramSenderName(JsonObject message) {
        if (message == null || !message.has("from")) return "Telegram";
        JsonObject from = message.getAsJsonObject("from");
        String first = from.has("first_name") ? from.get("first_name").getAsString() : "";
        String last = from.has("last_name") ? from.get("last_name").getAsString() : "";
        String username = from.has("username") ? from.get("username").getAsString() : "";
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) return full;
        if (!username.isEmpty()) return "@" + username;
        return "Telegram";
    }

    private RconFeedbackSession startRconFeedback(long chatId) {
        RconFeedbackSession session = new RconFeedbackSession(System.currentTimeMillis() + RCON_FEEDBACK_WINDOW_MS);
        rconFeedbackSessions.put(chatId, session);
        return session;
    }

    private void finishRconFeedback(long chatId, RconFeedbackSession session) {
        RconFeedbackSession current = rconFeedbackSessions.remove(chatId);
        if (current != session) return;

        List<String> lines = session.drainLines();
        if (lines.isEmpty()) return;
        sendTelegramCodeBlocks(chatId, lines);
    }

    private void sendTelegramCodeBlocks(long chatId, List<String> lines) {
        StringBuilder chunk = new StringBuilder();
        int lineCount = 0;
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) continue;
            String cleanLine = line.trim();
            int maxLineLength = TELEGRAM_MAX_MESSAGE_CHARS - 15;
            if (cleanLine.length() > maxLineLength) {
                cleanLine = cleanLine.substring(0, maxLineLength) + "... (truncated)";
            }
            if (chunk.length() + cleanLine.length() + 1 > TELEGRAM_MAX_MESSAGE_CHARS) {
                sendTelegramMessage(chatId, "<code>" + escapeHtml(chunk.toString()) + "</code>");
                chunk.setLength(0);
                lineCount = 0;
            }
            if (lineCount >= RCON_FEEDBACK_MAX_LINES) break;
            if (chunk.length() > 0) {
                chunk.append("\n");
            }
            chunk.append(cleanLine);
            lineCount++;
        }
        if (chunk.length() > 0) {
            sendTelegramMessage(chatId, "<code>" + escapeHtml(chunk.toString()) + "</code>");
        }
    }

    private String formatLogRecord(LogRecord record) {
        String message = record.getMessage();
        if (message == null || message.isEmpty()) return "";
        Object[] params = record.getParameters();
        if (params != null && params.length > 0) {
            try {
                message = MessageFormat.format(message, params);
            } catch (Exception ignored) {}
        }
        return stripColorCodes(message).trim();
    }

    private void handleRconCommand(long chatId, String command) {
        if (!isChatAdmin(chatId)) {
            sendTelegramMessage(chatId, getMsg("ss-unauthorized"));
            return;
        }

        if (command.isEmpty()) {
            sendTelegramMessage(chatId, getMsg("ss-rcon-usage"));
            return;
        }

        String baseCmd = command.split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (!rconAllowedCommands.contains(baseCmd)) {
            sendTelegramMessage(chatId, getMsg("ss-unauthorized"));
            return;
        }

        Bukkit.getScheduler().runTask(this, () -> {
            RconFeedbackSession session = startRconFeedback(chatId);
            TelegramCommandSender rconSender = new TelegramCommandSender(chatId);
            Bukkit.dispatchCommand(rconSender, command);
            Bukkit.getScheduler().runTaskLater(this, () -> finishRconFeedback(chatId, session), 40L);
        });
    }

    private void handlePlayersCommand(long chatId) {
        Collection<? extends org.bukkit.entity.Player> onlinePlayers = Bukkit.getOnlinePlayers();
        if (onlinePlayers.isEmpty()) {
            sendTelegramMessage(chatId, getMsg("ss-no-players"));
            return;
        }

        JsonObject markup = new JsonObject();
        JsonArray keyboard = new JsonArray();

        int count = 0;
        for (org.bukkit.entity.Player player : onlinePlayers) {
            if (count++ >= 100) break;
            JsonArray row = new JsonArray();
            row.add(createButton("👤 " + player.getName(), "player:manage:" + player.getUniqueId()));
            keyboard.add(row);
        }

        markup.add("inline_keyboard", keyboard);
        sendTelegramMessage(chatId, getMsg("ss-select-player"), markup);
    }

    private void handleMeManage(long chatId, UUID uuid) {
        handleMeManage(chatId, uuid, null);
    }

    private void handleMeManage(long chatId, UUID uuid, @Nullable Integer messageId) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        JsonObject markup = new JsonObject();
        JsonArray keyboard = new JsonArray();

        JsonArray row1 = new JsonArray();
        row1.add(createButton(getMsg("ss-btn-kick-me"), "me:kick:" + uuid));
        row1.add(createButton(getMsg("ss-btn-blacklist-me"), "me:bl:" + uuid));
        keyboard.add(row1);

        JsonArray row2 = new JsonArray();
        row2.add(createButton(getMsg("ss-btn-manage-blacklist"), "me:bl_l:" + uuid));
        row2.add(createButton(getMsg("ss-btn-manage-whitelist"), "me:wl_l:" + uuid));
        keyboard.add(row2);

        JsonArray row3 = new JsonArray();
        row3.add(createButton(getMsg("ss-btn-2fa-settings"), "me:2fa_menu:" + uuid));
        keyboard.add(row3);

        List<UUID> linked = getLinkedUuids(chatId);
        if (linked.size() > 1) {
            JsonArray rowBack = new JsonArray();
            rowBack.add(createButton(getMsg("ss-btn-back"), "me:list"));
            keyboard.add(rowBack);
        }

        markup.add("inline_keyboard", keyboard);
        String text = getMsg("ss-me-menu").replace("%player%", name != null ? escapeHtml(name) : "Unknown");

        if (messageId != null) {
            editTelegramMessage(chatId, messageId, text, markup);
        } else {
            sendTelegramMessage(chatId, text, markup);
        }
    }

    private void handle2FASettings(long chatId, UUID uuid, @Nullable Integer messageId, String prefix) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        String currentMode = player2faModes.get(uuid);
        if (currentMode == null) {
            currentMode = Bukkit.getOfflinePlayer(uuid).isOp() ? op2faMode : nonOp2faMode;
        }

        JsonObject markup = new JsonObject();
        JsonArray keyboard = new JsonArray();

        keyboard.add(create2FAModeRow(uuid, "always", getMsg("ss-btn-mode-always"), currentMode, prefix));
        keyboard.add(create2FAModeRow(uuid, "session", getMsg("ss-btn-mode-session"), currentMode, prefix));
        keyboard.add(create2FAModeRow(uuid, "whitelist", getMsg("ss-btn-mode-whitelist"), currentMode, prefix));
        keyboard.add(create2FAModeRow(uuid, "disabled", getMsg("ss-btn-mode-disabled"), currentMode, prefix));

        JsonArray backRow = new JsonArray();
        backRow.add(createButton(getMsg("ss-btn-back"), prefix + ":manage:" + uuid));
        keyboard.add(backRow);

        markup.add("inline_keyboard", keyboard);

        String text = getMsg("ss-2fa-settings-menu").replace("%player%", name != null ? escapeHtml(name) : "Unknown")
                + "\n" + getMsg("ss-2fa-mode-current").replace("%mode%", currentMode);

        if (messageId != null) {
            editTelegramMessage(chatId, messageId, text, markup);
        } else {
            sendTelegramMessage(chatId, text, markup);
        }
    }

    private void handleDefault2FASettings(long chatId, @Nullable Integer messageId) {
        JsonObject markup = new JsonObject();
        JsonArray keyboard = new JsonArray();

        String[] modes = {"always", "session", "whitelist", "disabled"};
        
        // OP Defaults section
        JsonArray headerOp = new JsonArray();
        headerOp.add(createButton("--- " + getMsg("ss-btn-default-2fa-op") + " ---", "ignore"));
        keyboard.add(headerOp);
        
        for (String mode : modes) {
            JsonArray row = new JsonArray();
            String indicator = mode.equalsIgnoreCase(op2faMode) ? "✅ " : "";
            row.add(createButton(indicator + getMsg("ss-btn-mode-" + mode), "settings:df_2fa_s:op:" + mode));
            keyboard.add(row);
        }

        // Non-OP Defaults section
        JsonArray headerNonOp = new JsonArray();
        headerNonOp.add(createButton("--- " + getMsg("ss-btn-default-2fa-nonop") + " ---", "ignore"));
        keyboard.add(headerNonOp);
        
        for (String mode : modes) {
            JsonArray row = new JsonArray();
            String indicator = mode.equalsIgnoreCase(nonOp2faMode) ? "✅ " : "";
            row.add(createButton(indicator + getMsg("ss-btn-mode-" + mode), "settings:df_2fa_s:nonop:" + mode));
            keyboard.add(row);
        }

        JsonArray backRow = new JsonArray();
        backRow.add(createButton(getMsg("ss-btn-back"), "settings:menu"));
        keyboard.add(backRow);

        markup.add("inline_keyboard", keyboard);

        String text = getMsg("ss-default-2fa-menu") + "\n" 
                + getMsg("ss-btn-default-2fa-op") + ": " + op2faMode + "\n"
                + getMsg("ss-btn-default-2fa-nonop") + ": " + nonOp2faMode;

        if (messageId != null) {
            editTelegramMessage(chatId, messageId, text, markup);
        } else {
            sendTelegramMessage(chatId, text, markup);
        }
    }

    private JsonArray create2FAModeRow(UUID uuid, String mode, String label, String currentMode, String prefix) {
        JsonArray row = new JsonArray();
        String indicator = mode.equalsIgnoreCase(currentMode) ? "✅ " : "";
        row.add(createButton(indicator + label, prefix + ":2fa_set:" + uuid + ":" + mode));
        return row;
    }

    private void handleBlacklistAdmin(long chatId) {
        if (blacklistedIps.isEmpty()) {
            sendTelegramMessage(chatId, getMsg("ss-no-blacklist-found"));
            return;
        }

        JsonObject markup = new JsonObject();
        JsonArray keyboard = new JsonArray();

        int count = 0;
        for (UUID uuid : blacklistedIps.keySet()) {
            if (blacklistedIps.get(uuid).isEmpty()) continue;
            if (count++ >= 100) break;
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            JsonArray row = new JsonArray();
            row.add(createButton("👤 " + (name != null ? name : uuid.toString()), "bl:pips:" + uuid));
            keyboard.add(row);
        }

        markup.add("inline_keyboard", keyboard);
        sendTelegramMessage(chatId, getMsg("ss-blacklist-players-menu"), markup);
    }

    private void handlePlayerBlacklist(long chatId, UUID uuid) {
        Set<String> ips = blacklistedIps.getOrDefault(uuid, Collections.emptySet());
        String name = Bukkit.getOfflinePlayer(uuid).getName();

        if (ips.isEmpty()) {
            sendTelegramMessage(chatId, getMsg("ss-no-blacklist-player").replace("%player%", name != null ? escapeHtml(name) : uuid.toString()));
            return;
        }

        JsonObject markup = new JsonObject();
        JsonArray keyboard = new JsonArray();

        long now = System.currentTimeMillis();
        int count = 0;
        for (String ip : ips) {
            if (count++ >= 49) break;
            String shortId = generateShortId();
            ipManagerShortIds.put(shortId, ip);
            ipManagerShortIdsTimestamp.put(shortId, now);
            JsonArray row = new JsonArray();
            row.add(createButton(ip, "ignore"));
            row.add(createButton(getMsg("ss-btn-remove"), "bl:rem:" + uuid + ":" + shortId));
            keyboard.add(row);
        }

        JsonArray backRow = new JsonArray();
        backRow.add(createButton(getMsg("ss-btn-back"), "bl:list"));
        keyboard.add(backRow);

        markup.add("inline_keyboard", keyboard);
        sendTelegramMessage(chatId, getMsg("ss-blacklist-ips-menu").replace("%player%", name != null ? escapeHtml(name) : "Unknown"), markup);
    }

    private void handlePlayerIpManagement(long chatId, UUID uuid, boolean isBlacklist, @Nullable Integer messageId) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        JsonObject markup = new JsonObject();
        JsonArray keyboard = new JsonArray();

        if (isBlacklist) {
            Set<String> ips = blacklistedIps.getOrDefault(uuid, Collections.emptySet());
            if (ips.isEmpty()) {
                String msg = getMsg("ss-no-blacklist-player").replace("%player%", name != null ? escapeHtml(name) : uuid.toString());
                if (messageId != null) editTelegramMessage(chatId, messageId, msg, markup);
                else sendTelegramMessage(chatId, msg, markup);
                return;
            }
            long now = System.currentTimeMillis();
            int count = 0;
            for (String ip : ips) {
                if (count++ >= 49) break;
                String shortId = generateShortId();
                ipManagerShortIds.put(shortId, ip);
                ipManagerShortIdsTimestamp.put(shortId, now);
                JsonArray row = new JsonArray();
                row.add(createButton(ip, "ignore"));
                row.add(createButton(getMsg("ss-btn-remove"), "me:bl_r:" + uuid + ":" + shortId));
                keyboard.add(row);
            }
        } else {
            Map<String, Long> ips = approvedIps.getOrDefault(uuid, Collections.emptyMap());
            if (ips.isEmpty()) {
                String msg = getMsg("ss-no-approved-ips").replace("%player%", name != null ? escapeHtml(name) : uuid.toString());
                if (messageId != null) editTelegramMessage(chatId, messageId, msg, markup);
                else sendTelegramMessage(chatId, msg, markup);
                return;
            }
            long now = System.currentTimeMillis();
            int count = 0;
            for (Map.Entry<String, Long> entry : ips.entrySet()) {
                if (count++ >= 49) break;
                String ip = entry.getKey();
                long expiry = entry.getValue();
                String shortId = generateShortId();
                ipManagerShortIds.put(shortId, ip);
                ipManagerShortIdsTimestamp.put(shortId, now);

                String timeStr;
                if (expiry == Long.MAX_VALUE) {
                    timeStr = getMsg("ss-time-permanent");
                } else {
                    long diff = expiry - now;
                    if (diff <= 0) continue;
                    long hours = diff / (1000 * 60 * 60);
                    long mins = (diff / (1000 * 60)) % 60;
                    timeStr = hours + "h " + mins + "m";
                }

                JsonArray row = new JsonArray();
                row.add(createButton(ip + " (" + timeStr + ")", "ignore"));
                row.add(createButton(getMsg("ss-btn-remove"), "me:wl_r:" + uuid + ":" + shortId));
                keyboard.add(row);
            }
        }

        JsonArray backRow = new JsonArray();
        backRow.add(createButton(getMsg("ss-btn-back"), "me:manage:" + uuid));
        keyboard.add(backRow);
        markup.add("inline_keyboard", keyboard);

        String text = isBlacklist ? getMsg("ss-my-blacklist") : getMsg("ss-my-whitelist");
        text = text.replace("%player%", name != null ? escapeHtml(name) : "Unknown");

        if (messageId != null) {
            editTelegramMessage(chatId, messageId, text, markup);
        } else {
            sendTelegramMessage(chatId, text, markup);
        }
    }

    private void handleStateMessage(long chatId, String text) {
        String state = chatStates.get(chatId);
        if (state == null) return;

        chatStates.remove(chatId); // Always clear state to prevent "sticky" modes

        if (state.startsWith("msg_to:")) {
            UUID targetUuid = UUID.fromString(state.substring(7));
            org.bukkit.entity.Player target = Bukkit.getPlayer(targetUuid);
            if (target != null && target.isOnline()) {
                String format = getMsg("mc-msg-format").replace("%message%", text);
                target.sendMessage(colorizeOrDefault(format, ChatColor.GOLD));
                sendTelegramMessage(chatId, getMsg("ss-msg-sent").replace("%player%", escapeHtml(target.getName())));
            } else {
                sendTelegramMessage(chatId, getMsg("ss-not-online"));
            }
        }
    }

    private void handleCallback(JsonObject cb) {
        if (!cb.has("from")) return;
        long fromId = cb.get("from").getAsJsonObject().get("id").getAsLong();
        long chatId = cb.has("message") ? cb.get("message").getAsJsonObject().get("chat").getAsJsonObject().get("id").getAsLong() : fromId;
        String data = cb.get("data").getAsString();
        String id = cb.get("id").getAsString();

        if (data.equals("ignore")) {
            JsonObject answer = new JsonObject();
            answer.addProperty("callback_query_id", id);
            executeTelegramRequest("answerCallbackQuery", answer);
            return;
        }

        if (!isChatAdmin(fromId)) {
            // Check if it's a 2FA callback or "me" action
            if (!data.startsWith("approve:") && !data.startsWith("deny:") && !data.startsWith("bl:") && !data.startsWith("me:")) {
                JsonObject ans = new JsonObject();
                ans.addProperty("callback_query_id", id);
                executeTelegramRequest("answerCallbackQuery", ans);
                return;
            }
        }

        String[] parts = data.split(":");
        if (parts.length < 2) {
            JsonObject ans = new JsonObject();
            ans.addProperty("callback_query_id", id);
            executeTelegramRequest("answerCallbackQuery", ans);
            return;
        }

        String action = parts[0];

        if (action.equals("approve") || action.equals("deny") || (action.equals("bl") && pendingApprovals.containsKey(parts[1]))) {
            String approvalId = parts[1];
            PendingApproval pending = action.equals("deny") ? pendingApprovals.remove(approvalId) : pendingApprovals.get(approvalId);
            if (pending == null) return;

            UUID uuid = pending.uuid;
            String ip = pending.ip;

            // Security check: Only admins or the linked user can approve/deny/blacklist
            if (!isChatAdmin(fromId) && !linkedChats.getOrDefault(uuid, -1L).equals(fromId)) {
                return;
            }

            String name = Bukkit.getOfflinePlayer(uuid).getName();

            if (action.equals("approve")) {
                pendingApprovals.remove(approvalId);
                String pMode = player2faModes.get(uuid);
                String effMode = (pMode != null) ? pMode : (Bukkit.getOfflinePlayer(uuid).isOp() ? op2faMode : nonOp2faMode);
                long expiry;
                if (effMode.equalsIgnoreCase("whitelist")) {
                    expiry = Long.MAX_VALUE;
                } else if (effMode.equalsIgnoreCase("always")) {
                    expiry = System.currentTimeMillis() + 60000; // 1 minute to allow login
                } else {
                    expiry = System.currentTimeMillis() + expiryMs;
                }
                approvedIps.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(ip, expiry);
                sendTelegramMessage(chatId, getMsg("ss-approved").replace("%ip%", escapeHtml(ip)).replace("%player%", name != null ? escapeHtml(name) : "Unknown"));
            } else if (action.equals("deny")) {
                sendTelegramMessage(chatId, getMsg("ss-denied").replace("%ip%", escapeHtml(ip)));
            } else if (action.equals("bl")) {
                pendingApprovals.remove(approvalId);
                blacklistedIps.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(ip);
                sendTelegramMessage(chatId, getMsg("ss-blacklisted").replace("%ip%", escapeHtml(ip)).replace("%player%", name != null ? escapeHtml(name) : "Unknown"));
            }
        } else if (action.equals("player")) {
            if (parts.length < 3) return;
            String subAction = parts[1];
            UUID uuid = UUID.fromString(parts[2]);
            org.bukkit.entity.Player target = Bukkit.getPlayer(uuid);
            String name = (target != null) ? target.getName() : Bukkit.getOfflinePlayer(uuid).getName();

            if (subAction.equals("manage")) {
                JsonObject markup = new JsonObject();
                JsonArray keyboard = new JsonArray();

                JsonArray row1 = new JsonArray();
                row1.add(createButton(getMsg("ss-btn-kick"), "player:kick:" + uuid));
                row1.add(createButton(getMsg("ss-btn-ban"), "player:ban:" + uuid));

                JsonArray row2 = new JsonArray();
                row2.add(createButton(getMsg("ss-btn-msg"), "player:msg:" + uuid));
                row2.add(createButton(getMsg("ss-btn-2fa-settings"), "player:2fa_menu:" + uuid));

                JsonArray row3 = new JsonArray();
                row3.add(createButton(getMsg("ss-btn-back"), "player:list_all"));

                keyboard.add(row1);
                keyboard.add(row2);
                keyboard.add(row3);
                markup.add("inline_keyboard", keyboard);

                sendTelegramMessage(chatId, getMsg("ss-manage-player").replace("%player%", name != null ? escapeHtml(name) : uuid.toString()), markup);
            } else if (subAction.equals("kick")) {
                Bukkit.getScheduler().runTask(this, () -> {
                    if (target != null && target.isOnline()) {
                        kickPlayer(target, getMsg("mc-kick-reason"), ChatColor.RED);
                        sendTelegramMessage(chatId, getMsg("ss-kicked").replace("%player%", escapeHtml(target.getName())));
                    } else {
                        sendTelegramMessage(chatId, getMsg("ss-not-online"));
                    }
                });
            } else if (subAction.equals("ban")) {
                Bukkit.getScheduler().runTask(this, () -> {
                    String finalName = (name != null) ? name : uuid.toString();
                    Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(finalName, getMsg("mc-ban-reason"), null, null);
                    if (target != null && target.isOnline()) {
                        kickPlayer(target, getMsg("mc-ban-reason"), ChatColor.RED);
                    }
                    sendTelegramMessage(chatId, getMsg("ss-banned").replace("%player%", escapeHtml(finalName)));
                });
            } else if (subAction.equals("msg")) {
                chatStates.put(chatId, "msg_to:" + uuid);
                sendTelegramMessage(chatId, getMsg("ss-msg-prompt").replace("%player%", name != null ? escapeHtml(name) : uuid.toString()));
            } else if (subAction.equals("list_all")) {
                handlePlayersCommand(chatId);
            } else if (subAction.equals("2fa_menu")) {
                if (cb.has("message")) {
                    int msgId = cb.get("message").getAsJsonObject().get("message_id").getAsInt();
                    handle2FASettings(chatId, uuid, msgId, "player");
                } else {
                    handle2FASettings(chatId, uuid, null, "player");
                }
            } else if (subAction.equals("2fa_set")) {
                if (parts.length < 4) return;
                String mode = parts[3];
                player2faModes.put(uuid, mode);
                approvedIps.remove(uuid); // Apply immediately
                if (cb.has("message")) {
                    int msgId = cb.get("message").getAsJsonObject().get("message_id").getAsInt();
                    handle2FASettings(chatId, uuid, msgId, "player");
                }
                String pName = Bukkit.getOfflinePlayer(uuid).getName();
                JsonObject answer = new JsonObject();
                answer.addProperty("callback_query_id", id);
                answer.addProperty("text", getMsg("ss-2fa-mode-updated").replace("%player%", pName != null ? pName : "Unknown").replace("%mode%", mode));
                executeTelegramRequest("answerCallbackQuery", answer);
            }
        } else if (action.equals("settings")) {
            if (parts.length < 2) return;
            String subAction = parts[1];
            if (subAction.equals("menu")) {
                handleSettingsCommand(chatId, cb.get("message").getAsJsonObject().get("message_id").getAsInt());
            } else if (subAction.equals("toggle")) {
                if (parts.length < 3) return;
                String category = parts[2];
                Set<String> disabled = disabledNotifications.computeIfAbsent(chatId, k -> ConcurrentHashMap.newKeySet());
                if (disabled.contains(category)) {
                    disabled.remove(category);
                } else {
                    disabled.add(category);
                }
                if (cb.has("message")) {
                    int msgId = cb.get("message").getAsJsonObject().get("message_id").getAsInt();
                    handleSettingsCommand(chatId, msgId);
                }
            } else if (subAction.equals("df_2fa_m")) {
                Integer msgId = cb.has("message") ? cb.get("message").getAsJsonObject().get("message_id").getAsInt() : null;
                handleDefault2FASettings(chatId, msgId);
            } else if (subAction.equals("df_2fa_s")) {
                if (parts.length < 4) return;
                String type = parts[2];
                String mode = parts[3];
                if (type.equals("op")) {
                    op2faMode = mode;
                    getConfig().set("op-2fa-mode", op2faMode);
                } else {
                    nonOp2faMode = mode;
                    getConfig().set("non-op-2fa-mode", nonOp2faMode);
                }
                saveConfig();
                approvedIps.clear(); // Clear cache so new mode is enforced immediately
                Integer msgId = cb.has("message") ? cb.get("message").getAsJsonObject().get("message_id").getAsInt() : null;
                handleDefault2FASettings(chatId, msgId);
            }
        } else if (action.equals("me")) {
            if (parts.length < 2) return;
            String subAction = parts[1];
            List<UUID> linked = getLinkedUuids(chatId);
            if (linked.isEmpty()) {
                sendTelegramMessage(chatId, getMsg("ss-no-linked-accounts"));
                return;
            }

            if (subAction.equals("list")) {
                if (linked.size() == 1) {
                    handleMeManage(chatId, linked.get(0));
                } else {
                    JsonObject markup = new JsonObject();
                    JsonArray keyboard = new JsonArray();
                    for (UUID uuid : linked) {
                        String name = Bukkit.getOfflinePlayer(uuid).getName();
                        JsonArray row = new JsonArray();
                        row.add(createButton("👤 " + (name != null ? name : uuid.toString()), "me:manage:" + uuid));
                        keyboard.add(row);
                    }
                    markup.add("inline_keyboard", keyboard);
                    String text = getMsg("ss-select-account");
                    if (cb.has("message")) {
                        int msgId = cb.get("message").getAsJsonObject().get("message_id").getAsInt();
                        editTelegramMessage(chatId, msgId, text, markup);
                    } else {
                        sendTelegramMessage(chatId, text, markup);
                    }
                }
            } else if (subAction.equals("manage")) {
                if (parts.length < 3) return;
                UUID uuid = UUID.fromString(parts[2]);
                if (!linked.contains(uuid)) return;
                if (cb.has("message")) {
                    int msgId = cb.get("message").getAsJsonObject().get("message_id").getAsInt();
                    handleMeManage(chatId, uuid, msgId);
                } else {
                    handleMeManage(chatId, uuid);
                }
            } else if (subAction.equals("2fa_menu")) {
                if (parts.length < 3) return;
                UUID uuid = UUID.fromString(parts[2]);
                if (!linked.contains(uuid)) return;
                if (cb.has("message")) {
                    int msgId = cb.get("message").getAsJsonObject().get("message_id").getAsInt();
                    handle2FASettings(chatId, uuid, msgId, "me");
                } else {
                    handle2FASettings(chatId, uuid, null, "me");
                }
            } else if (subAction.equals("2fa_set")) {
                if (parts.length < 4) return;
                UUID uuid = UUID.fromString(parts[2]);
                String mode = parts[3];
                if (!linked.contains(uuid)) return;
                player2faModes.put(uuid, mode);
                approvedIps.remove(uuid); // Clear old approvals to apply new mode immediately
                if (cb.has("message")) {
                    int msgId = cb.get("message").getAsJsonObject().get("message_id").getAsInt();
                    handle2FASettings(chatId, uuid, msgId, "me");
                }
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                JsonObject answer = new JsonObject();
                answer.addProperty("callback_query_id", id);
                answer.addProperty("text", getMsg("ss-2fa-mode-updated").replace("%player%", name != null ? name : "Unknown").replace("%mode%", mode));
                executeTelegramRequest("answerCallbackQuery", answer);
            } else if (subAction.equals("kick")) {
                if (parts.length < 3) return;
                UUID uuid = UUID.fromString(parts[2]);
                if (!linked.contains(uuid)) return;
                Bukkit.getScheduler().runTask(this, () -> {
                    org.bukkit.entity.Player target = Bukkit.getPlayer(uuid);
                    if (target != null && target.isOnline()) {
                        kickPlayer(target, getMsg("mc-kick-reason"), ChatColor.RED);
                        sendTelegramMessage(chatId, getMsg("ss-kicked").replace("%player%", escapeHtml(target.getName())));
                    } else {
                        sendTelegramMessage(chatId, getMsg("ss-not-online"));
                    }
                });
            } else if (subAction.equals("bl")) {
                if (parts.length < 3) return;
                UUID uuid = UUID.fromString(parts[2]);
                if (!linked.contains(uuid)) return;
                
                org.bukkit.entity.Player target = Bukkit.getPlayer(uuid);
                if (target != null && target.isOnline()) {
                    String ip = target.getAddress().getAddress().getHostAddress();
                    blacklistedIps.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(ip);
                    Bukkit.getScheduler().runTask(this, () -> {
                        kickPlayer(target, getMsg("kick-blacklisted"), ChatColor.RED);
                    });
                    sendTelegramMessage(chatId, getMsg("ss-blacklisted-self").replace("%ip%", escapeHtml(ip)).replace("%player%", escapeHtml(target.getName())));
                } else {
                    sendTelegramMessage(chatId, getMsg("ss-not-online"));
                }
            } else if (subAction.equals("bl_l")) {
                if (parts.length < 3) return;
                UUID uuid = UUID.fromString(parts[2]);
                if (!linked.contains(uuid)) return;
                Integer msgId = cb.has("message") ? cb.get("message").getAsJsonObject().get("message_id").getAsInt() : null;
                handlePlayerIpManagement(chatId, uuid, true, msgId);
            } else if (subAction.equals("wl_l")) {
                if (parts.length < 3) return;
                UUID uuid = UUID.fromString(parts[2]);
                if (!linked.contains(uuid)) return;
                Integer msgId = cb.has("message") ? cb.get("message").getAsJsonObject().get("message_id").getAsInt() : null;
                handlePlayerIpManagement(chatId, uuid, false, msgId);
            } else if (subAction.equals("bl_r")) {
                if (parts.length < 4) return;
                UUID uuid = UUID.fromString(parts[2]);
                if (!linked.contains(uuid)) return;
                String shortId = parts[3];
                String ip = ipManagerShortIds.remove(shortId);
                if (ip != null && blacklistedIps.containsKey(uuid)) {
                    blacklistedIps.get(uuid).remove(ip);
                    String name = Bukkit.getOfflinePlayer(uuid).getName();
                    sendTelegramMessage(chatId, getMsg("ss-ip-removed").replace("%ip%", escapeHtml(ip)).replace("%player%", name != null ? escapeHtml(name) : "Unknown"));
                    Integer msgId = cb.has("message") ? cb.get("message").getAsJsonObject().get("message_id").getAsInt() : null;
                    handlePlayerIpManagement(chatId, uuid, true, msgId);
                }
            } else if (subAction.equals("wl_r")) {
                if (parts.length < 4) return;
                UUID uuid = UUID.fromString(parts[2]);
                if (!linked.contains(uuid)) return;
                String shortId = parts[3];
                String ip = ipManagerShortIds.remove(shortId);
                if (ip != null && approvedIps.containsKey(uuid)) {
                    approvedIps.get(uuid).remove(ip);
                    String name = Bukkit.getOfflinePlayer(uuid).getName();
                    sendTelegramMessage(chatId, getMsg("ss-ip-removed-whitelist").replace("%ip%", escapeHtml(ip)).replace("%player%", name != null ? escapeHtml(name) : "Unknown"));
                    Integer msgId = cb.has("message") ? cb.get("message").getAsJsonObject().get("message_id").getAsInt() : null;
                    handlePlayerIpManagement(chatId, uuid, false, msgId);
                }
            }
        } else if (action.equals("bl")) {
            if (!isChatAdmin(fromId)) return;
            if (parts.length < 2) return;
            String subAction = parts[1];

            if (subAction.equals("list")) {
                handleBlacklistAdmin(chatId);
            } else if (subAction.equals("pips")) {
                if (parts.length < 3) return;
                UUID uuid = UUID.fromString(parts[2]);
                handlePlayerBlacklist(chatId, uuid);
            } else if (subAction.equals("rem")) {
                if (parts.length < 4) return;
                UUID uuid = UUID.fromString(parts[2]);
                String shortId = parts[3];
                String ip = ipManagerShortIds.remove(shortId);
                if (ip != null && blacklistedIps.containsKey(uuid)) {
                    blacklistedIps.get(uuid).remove(ip);
                    String name = Bukkit.getOfflinePlayer(uuid).getName();
                    sendTelegramMessage(chatId, getMsg("ss-ip-removed").replace("%ip%", escapeHtml(ip)).replace("%player%", name != null ? escapeHtml(name) : "Unknown"));
                    handlePlayerBlacklist(chatId, uuid);
                }
            }
        }

        // Answer callback to remove loading state in TG
        JsonObject answer = new JsonObject();
        answer.addProperty("callback_query_id", id);
        executeTelegramRequest("answerCallbackQuery", answer);
        saveData();
    }

    private void sendTelegramMessage(long chatId, String text) {
        sendTelegramMessage(chatId, text, (JsonObject) null);
    }

    private void sendTelegramMessage(long chatId, String text, @Nullable Integer threadId) {
        JsonObject json = new JsonObject();
        json.addProperty("chat_id", chatId);
        json.addProperty("text", text);
        if (threadId != null) {
            json.addProperty("message_thread_id", threadId);
        }
        executeTelegramRequest("sendMessage", json);
    }

    private void sendTelegramMessage(long chatId, String text, @Nullable JsonObject replyMarkup) {
        JsonObject json = new JsonObject();
        json.addProperty("chat_id", chatId);
        json.addProperty("text", text);
        if (replyMarkup != null) {
            json.add("reply_markup", replyMarkup);
        }
        executeTelegramRequest("sendMessage", json);
    }

    private void editTelegramMessage(long chatId, int messageId, String text, JsonObject replyMarkup) {
        JsonObject json = new JsonObject();
        json.addProperty("chat_id", chatId);
        json.addProperty("message_id", messageId);
        json.addProperty("text", text);
        if (replyMarkup != null) {
            json.add("reply_markup", replyMarkup);
        }
        executeTelegramRequest("editMessageText", json);
    }

    private void sendTelegramPhoto(long chatId, byte[] imageBytes, @Nullable String caption) {
        sendTelegramPhoto(chatId, imageBytes, caption, null);
    }

    private void sendTelegramPhoto(long chatId, byte[] imageBytes, @Nullable String caption, @Nullable Integer threadId) {
        if (!isRunning) return;
        httpExecutor.execute(() -> {
            try {
                executeTelegramPhoto(chatId, imageBytes, caption, threadId);
            } catch (Exception e) {
                getLogger().severe("Telegram API exception (sendPhoto): " + redactToken(e.getMessage()) + ". Check network access and chat permissions.");
            }
        });
    }

    private void sendTelegramMessageSync(long chatId, String text) {
        JsonObject json = new JsonObject();
        json.addProperty("chat_id", chatId);
        json.addProperty("text", text);
        json.addProperty("parse_mode", "HTML");

        try {
            SimpleHttpResponse response = executePost("https://api.telegram.org/bot" + botToken + "/sendMessage", json.toString(), 10);
            checkTokenError(response.statusCode);
        } catch (Exception ignored) {}
    }

    private void sendTelegramMessageWithButtons(long chatId, String text, String approvalId) {
        JsonObject markup = new JsonObject();
        JsonArray keyboard = new JsonArray();
        JsonArray row1 = new JsonArray();
        row1.add(createButton(getMsg("ss-btn-approve"), "approve:" + approvalId));
        row1.add(createButton(getMsg("ss-btn-deny"), "deny:" + approvalId));
        JsonArray row2 = new JsonArray();
        row2.add(createButton(getMsg("ss-btn-blacklist"), "bl:" + approvalId));

        keyboard.add(row1);
        keyboard.add(row2);
        markup.add("inline_keyboard", keyboard);

        sendTelegramMessage(chatId, text, markup);
    }

    private void executeTelegramRequest(String method, JsonObject body) {
        if (!isRunning) return;
        if (method.equals("sendMessage") || method.equals("editMessageText")) {
            body.addProperty("parse_mode", "HTML");
        }
        String url = "https://api.telegram.org/bot" + botToken + "/" + method;
        String payload = body.toString();

        httpExecutor.execute(() -> {
            try {
                SimpleHttpResponse response = executePost(url, payload, 10);
                if (response.statusCode != 200) {
                    String respBody = response.body;
                    if (respBody.contains("query is already answered") || respBody.contains("message is not modified")) {
                        return;
                    }
                    getLogger().severe("Telegram API error (" + method + "): " + response.statusCode + " - " + redactToken(respBody) + ". Check bot-token and chat permissions.");
                    checkTokenError(response.statusCode);
                }
            } catch (Exception e) {
                getLogger().severe("Telegram API exception (" + method + "): " + redactToken(e.getMessage()) + ". Check network access and Telegram availability.");
            }
        });
    }

    private JsonObject createButton(String text, String data) {
        JsonObject button = new JsonObject();
        button.addProperty("text", text);
        button.addProperty("callback_data", data);
        return button;
    }

    private synchronized void saveData() {
        JsonObject data = new JsonObject();
        JsonObject linked = new JsonObject();
        linkedChats.forEach((uuid, chatId) -> linked.addProperty(uuid.toString(), chatId));
        data.add("linkedChats", linked);

        JsonObject approved = new JsonObject();
        approvedIps.forEach((uuid, ips) -> {
            JsonObject ipMap = new JsonObject();
            ips.forEach(ipMap::addProperty);
            approved.add(uuid.toString(), ipMap);
        });
        data.add("approvedIps", approved);

        JsonObject blacklisted = new JsonObject();
        blacklistedIps.forEach((uuid, ips) -> {
            JsonArray arr = new JsonArray();
            ips.forEach(arr::add);
            blacklisted.add(uuid.toString(), arr);
        });
        data.add("blacklistedIps", blacklisted);

        JsonObject disabled = new JsonObject();
        disabledNotifications.forEach((chatId, categories) -> {
            JsonArray arr = new JsonArray();
            categories.forEach(arr::add);
            disabled.add(chatId.toString(), arr);
        });
        data.add("disabledNotifications", disabled);

        JsonObject pModes = new JsonObject();
        player2faModes.forEach((uuid, mode) -> pModes.addProperty(uuid.toString(), mode));
        data.add("player2faModes", pModes);

        try {
            String json = gson.toJson(data);
            if (dbConnection == null) {
                getLogger().severe("Database connection is not initialized. Data not saved; changes will be lost after restart.");
                return;
            }
            byte[] payload = encryptIfNeeded(json);
            try (PreparedStatement stmt = dbConnection.prepareStatement("INSERT OR REPLACE INTO kv (k, v) VALUES (?, ?)")) {
                stmt.setString(1, "syncshield_data");
                stmt.setBytes(2, payload);
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            getLogger().severe("Could not save data: " + e.getMessage() + ". Check disk space and syncshield_data.key permissions.");
        }
    }

    private void loadData() {
        if (dbConnection == null) return;
        try {
            String json = null;
            boolean fromLegacy = false;
            try (PreparedStatement stmt = dbConnection.prepareStatement("SELECT v FROM kv WHERE k = ?")) {
                stmt.setString(1, "syncshield_data");
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        byte[] payload = rs.getBytes(1);
                        json = decryptIfNeeded(payload);
                    }
                }
            }
            if ((json == null || json.trim().isEmpty()) && Files.exists(legacyDataFile)) {
                json = new String(Files.readAllBytes(legacyDataFile), StandardCharsets.UTF_8);
                fromLegacy = true;
            }
            if (json == null || json.trim().isEmpty()) return;
            JsonObject data = new JsonParser().parse(json).getAsJsonObject();
            if (data.has("linkedChats")) {
                data.getAsJsonObject("linkedChats").entrySet().forEach(entry -> {
                    linkedChats.put(UUID.fromString(entry.getKey()), entry.getValue().getAsLong());
                });
            }
            if (data.has("approvedIps")) {
                data.getAsJsonObject("approvedIps").entrySet().forEach(entry -> {
                    UUID uuid = UUID.fromString(entry.getKey());
                    Map<String, Long> ips = new ConcurrentHashMap<>();
                    entry.getValue().getAsJsonObject().entrySet().forEach(ipEntry -> {
                        ips.put(ipEntry.getKey(), ipEntry.getValue().getAsLong());
                    });
                    approvedIps.put(uuid, ips);
                });
            }
            if (data.has("blacklistedIps")) {
                data.getAsJsonObject("blacklistedIps").entrySet().forEach(entry -> {
                    Set<String> ips = ConcurrentHashMap.newKeySet();
                    entry.getValue().getAsJsonArray().forEach(e -> ips.add(e.getAsString()));
                    blacklistedIps.put(UUID.fromString(entry.getKey()), ips);
                });
            }
            if (data.has("disabledNotifications")) {
                data.getAsJsonObject("disabledNotifications").entrySet().forEach(entry -> {
                    Set<String> categories = ConcurrentHashMap.newKeySet();
                    entry.getValue().getAsJsonArray().forEach(e -> categories.add(e.getAsString()));
                    disabledNotifications.put(Long.parseLong(entry.getKey()), categories);
                });
            }
            if (data.has("player2faModes")) {
                data.getAsJsonObject("player2faModes").entrySet().forEach(entry -> {
                    player2faModes.put(UUID.fromString(entry.getKey()), entry.getValue().getAsString());
                });
            }
            if (fromLegacy) {
                saveData();
                try {
                    Files.move(legacyDataFile, legacyDataFile.resolveSibling("syncshield_data.json.bak"), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            getLogger().severe("Could not load data: " + e.getMessage() + ". If encryption is enabled, confirm syncshield_data.key is present.");
        }
    }

    private static final class RconFeedbackSession {
        private final long expiresAt;
        private final List<String> lines = new ArrayList<>();
        private boolean closed;

        private RconFeedbackSession(long expiresAt) {
            this.expiresAt = expiresAt;
        }

        private synchronized void addLine(String line) {
            if (closed) return;
            if (line == null || line.trim().isEmpty()) return;
            if (lines.size() >= RCON_FEEDBACK_MAX_LINES) return;
            lines.add(line);
        }

        private synchronized List<String> drainLines() {
            closed = true;
            return new ArrayList<>(lines);
        }
    }

    private final class RconLogHandler extends Handler {
        @Override
        public void publish(LogRecord record) {
            if (record == null || rconFeedbackSessions.isEmpty()) return;
            long now = System.currentTimeMillis();
            String line = formatLogRecord(record);
            if (line.isEmpty()) return;
            for (RconFeedbackSession session : rconFeedbackSessions.values()) {
                if (now <= session.expiresAt) {
                    session.addLine(line);
                }
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }

    @SuppressWarnings({"removal", "deprecation"})
    private class TelegramCommandSender implements ConsoleCommandSender {
        private final long chatId;

        public TelegramCommandSender(long chatId) {
            this.chatId = chatId;
        }

        @Override
        public void sendMessage(@NotNull String message) {
            if (message == null || message.isEmpty()) return;
            String stripped = stripColorCodes(message);
            int maxLen = TELEGRAM_MAX_MESSAGE_CHARS - 15;
            if (stripped.length() > maxLen) {
                stripped = stripped.substring(0, maxLen) + "... (truncated)";
            }
            sendTelegramMessage(chatId, "<code>" + escapeHtml(stripped) + "</code>");
        }

        @Override
        public void sendMessage(String @NotNull ... messages) {
            for (String msg : messages) sendMessage(msg);
        }

        public void sendMessage(@Nullable UUID source, @NotNull String message) {
            sendMessage(message);
        }

        public void sendMessage(@Nullable UUID source, String @NotNull ... messages) {
            sendMessage(messages);
        }

        @Override
        public @NotNull Server getServer() {
            return Bukkit.getServer();
        }

        @Override
        public @NotNull String getName() {
            return "TelegramRCON";
        }

        @Override
        public boolean isPermissionSet(@NotNull String name) {
            return true;
        }

        @Override
        public boolean isPermissionSet(@NotNull Permission perm) {
            return true;
        }

        @Override
        public boolean hasPermission(@NotNull String name) {
            return true;
        }

        @Override
        public boolean hasPermission(@NotNull Permission perm) {
            return true;
        }

        @Override
        public @NotNull PermissionAttachment addAttachment(@NotNull org.bukkit.plugin.Plugin plugin, @NotNull String name, boolean value) {
            return new PermissionAttachment(plugin, this);
        }

        @Override
        public @NotNull PermissionAttachment addAttachment(@NotNull org.bukkit.plugin.Plugin plugin) {
            return new PermissionAttachment(plugin, this);
        }

        @Override
        public @Nullable PermissionAttachment addAttachment(@NotNull org.bukkit.plugin.Plugin plugin, @NotNull String name, boolean value, int ticks) {
            return new PermissionAttachment(plugin, this);
        }

        @Override
        public @Nullable PermissionAttachment addAttachment(@NotNull org.bukkit.plugin.Plugin plugin, int ticks) {
            return new PermissionAttachment(plugin, this);
        }

        @Override
        public void removeAttachment(@NotNull PermissionAttachment attachment) {}

        @Override
        public void recalculatePermissions() {}

        @Override
        public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions() {
            return Collections.emptySet();
        }

        @Override
        public boolean isOp() {
            return true;
        }

        @Override
        public void setOp(boolean value) {}

        public void sendRawMessage(@NotNull String message) {
            sendMessage(message);
        }

        public void sendRawMessage(@Nullable UUID source, @NotNull String message) {
            sendMessage(message);
        }

        @Override
        public boolean isConversing() {
            return false;
        }

        @Override
        public void acceptConversationInput(@NotNull String input) {}

        @Override
        public boolean beginConversation(@NotNull org.bukkit.conversations.Conversation conversation) {
            return false;
        }

        @Override
        public void abandonConversation(@NotNull org.bukkit.conversations.Conversation conversation) {}

        @Override
        public void abandonConversation(@NotNull org.bukkit.conversations.Conversation conversation, @NotNull org.bukkit.conversations.ConversationAbandonedEvent event) {}

        @Override
        public @NotNull Spigot spigot() {
            return new Spigot();
        }
    }
}
