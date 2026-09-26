package com.mrfenek.syncshield.tickets;

import com.mrfenek.syncshield.SyncShield;
import com.mrfenek.syncshield.util.TextSanitizer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TicketManager {
    private static final long TICKET_COOLDOWN_MS = 30_000L;

    private final SyncShield plugin;
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<Long, String> telegramMessageToTicket = new ConcurrentHashMap<>();
    private final Map<Long, String> discordMessageToTicket = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTicketAt = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public static class Ticket {
        public String id;
        public UUID creator;
        public String creatorName;
        public String type; // "Ticket" or "Report"
        public String message;
        public String status; // "Open", "Claimed", "Closed"
        public String claimedBy;
        public long createdAt = System.currentTimeMillis();

        public Long telegramMessageId;
        public Long telegramChatId;
        public Long discordMessageId;
        public Long discordChannelId;
    }

    public TicketManager(SyncShield plugin) {
        this.plugin = plugin;
    }

    public Map<String, Ticket> getTickets() {
        return tickets;
    }

    public void setTickets(Map<String, Ticket> loadedTickets) {
        this.tickets.clear();
        this.telegramMessageToTicket.clear();
        this.discordMessageToTicket.clear();
        if (loadedTickets != null) {
            this.tickets.putAll(loadedTickets);
            for (Ticket t : loadedTickets.values()) {
                if (t.telegramMessageId != null) {
                    telegramMessageToTicket.put(t.telegramMessageId, t.id);
                }
                if (t.discordMessageId != null) {
                    discordMessageToTicket.put(t.discordMessageId, t.id);
                }
            }
        }
    }

    public void registerTelegramMessage(String ticketId, long messageId, long chatId) {
        Ticket t = tickets.get(ticketId);
        if (t != null) {
            t.telegramMessageId = messageId;
            t.telegramChatId = chatId;
            telegramMessageToTicket.put(messageId, ticketId);
            plugin.saveData();
        }
    }

    public void registerDiscordMessage(String ticketId, long messageId, long channelId) {
        Ticket t = tickets.get(ticketId);
        if (t != null) {
            t.discordMessageId = messageId;
            t.discordChannelId = channelId;
            discordMessageToTicket.put(messageId, ticketId);
            plugin.saveData();
        }
    }

    public Ticket getTicketByTelegramMessage(long messageId) {
        String ticketId = telegramMessageToTicket.get(messageId);
        return ticketId != null ? tickets.get(ticketId) : null;
    }

    public Ticket getTicketByDiscordMessage(long messageId) {
        String ticketId = discordMessageToTicket.get(messageId);
        return ticketId != null ? tickets.get(ticketId) : null;
    }

    public Ticket getActiveTicketByPlayer(UUID uuid) {
        for (Ticket t : tickets.values()) {
            if (t.creator != null && t.creator.equals(uuid) && !t.status.equalsIgnoreCase("Closed")) {
                return t;
            }
        }
        return null;
    }

    public Ticket getTicketById(String id) {
        return tickets.get(id);
    }

    /**
     * Generates a collision-safe ticket ID: on the (rare) chance of a duplicate it is
     * extended rather than silently overwriting an existing ticket.
     */
    private String generateTicketId() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        String base = randomId(alphabet, 6);
        String id = base;
        int suffix = 1;
        while (tickets.containsKey(id) && suffix < 100) {
            id = base + randomId(alphabet, 2) + suffix;
            suffix++;
        }
        return id;
    }

    private String randomId(String alphabet, int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    public void createTicket(Player player, String type, String message) {
        if (!plugin.isTicketSystemEnabled()) {
            player.sendMessage(plugin.getMsg("ticket-disabled"));
            return;
        }
        if (!player.hasPermission("syncshield.ticket")) {
            player.sendMessage(plugin.getMsg("ss-no-permission"));
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastTicketAt.get(player.getUniqueId());
        if (last != null && now - last < TICKET_COOLDOWN_MS) {
            player.sendMessage(plugin.getMsg("ticket-cooldown"));
            return;
        }
        if (getActiveTicketByPlayer(player.getUniqueId()) != null) {
            player.sendMessage(plugin.getMsg("ticket-no-active"));
            return;
        }
        lastTicketAt.put(player.getUniqueId(), now);

        Ticket t = new Ticket();
        t.id = generateTicketId();
        t.creator = player.getUniqueId();
        t.creatorName = player.getName();
        t.type = type;
        t.message = TextSanitizer.truncate(message, 500);
        t.status = "Open";
        tickets.put(t.id, t);
        plugin.saveData();

        // Notify in-game player with working chat colors
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMsg("ticket-created").replace("%id%", t.id)));

        // Notify Discord
        if (plugin.getDiscordBot() != null) {
            plugin.getDiscordBot().sendTicketToDiscord(t.id, t.creatorName, t.type, t.message);
        }

        // Notify Telegram
        if (plugin.isTelegramActive()) {
            plugin.sendTicketToTelegram(t.id, t.creatorName, t.type, t.message);
        }
    }

    /**
     * Admin-gated claim. Called from Telegram/Discord callbacks after the caller has
     * verified admin status; the check here is the last line of defense.
     */
    public boolean claimTicketAuthorized(String id, CommandSender byWho) {
        return byWho != null && byWho.hasPermission("syncshield.admin");
    }

    public boolean claimTicket(String id, String claimer) {
        Ticket t = tickets.get(id);
        if (t != null && t.status.equals("Open")) {
            t.status = "Claimed";
            t.claimedBy = claimer;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(t.creator);
                if (p != null) {
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMsg("ticket-claimed").replace("%id%", t.id).replace("%admin%", claimer)));
                }
            });
            plugin.notifyAdminsTicket("Ticket [" + id + "] claimed by " + claimer);
            plugin.saveData();
            return true;
        }
        return false;
    }

    public boolean closeTicket(String id, String closer) {
        Ticket t = tickets.get(id);
        if (t != null && !t.status.equals("Closed")) {
            t.status = "Closed";
            Player p = Bukkit.getPlayer(t.creator);
            if (p != null) {
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMsg("ticket-closed").replace("%id%", t.id).replace("%admin%", closer)));
            }
            plugin.notifyAdminsTicket("Ticket [" + id + "] closed by " + closer);
            if (t.telegramMessageId != null) telegramMessageToTicket.remove(t.telegramMessageId);
            if (t.discordMessageId != null) discordMessageToTicket.remove(t.discordMessageId);
            tickets.remove(id);
            plugin.saveData();
            return true;
        }
        return false;
    }

    public void handlePlayerChat(Player player, String message) {
        Ticket t = getActiveTicketByPlayer(player.getUniqueId());
        if (t == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMsg("ticket-no-active")));
            return;
        }

        // Send in-game confirmation to player
        String inGameFmt = plugin.getMsg("ticket-player-chat-format")
                .replace("%id%", t.id)
                .replace("%message%", message);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', inGameFmt));

        // Forward to Telegram if registered
        if (plugin.isTelegramActive() && t.telegramMessageId != null && t.telegramChatId != null) {
            String tgText = "\uD83D\uDCAC <b>[Ticket #" + t.id + " Chat]</b> <b>" + TextSanitizer.escapeHtml(player.getName()) + ":</b> " + TextSanitizer.escapeHtml(message);
            plugin.sendTelegramReply(t.telegramChatId, t.telegramMessageId, tgText);
        }

        // Forward to Discord if registered
        if (plugin.getDiscordBot() != null && t.discordMessageId != null && t.discordChannelId != null) {
            String dcText = "\uD83D\uDCAC **[Ticket #" + t.id + " Chat]** **" + player.getName() + ":** " + TextSanitizer.sanitizeDiscord(message);
            plugin.getDiscordBot().sendDiscordReply(t.discordChannelId, t.discordMessageId, dcText);
        }
    }
}
