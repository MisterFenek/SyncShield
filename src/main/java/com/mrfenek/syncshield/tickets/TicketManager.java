package com.mrfenek.syncshield.tickets;

import com.mrfenek.syncshield.SyncShield;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TicketManager {
    private final SyncShield plugin;
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<Long, String> telegramMessageToTicket = new ConcurrentHashMap<>();
    private final Map<Long, String> discordMessageToTicket = new ConcurrentHashMap<>();

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

    public void createTicket(Player player, String type, String message) {
        String id = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Ticket t = new Ticket();
        t.id = id;
        t.creator = player.getUniqueId();
        t.creatorName = player.getName();
        t.type = type;
        t.message = message;
        t.status = "Open";
        tickets.put(id, t);
        plugin.saveData();

        // Notify in-game player with working chat colors
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMsg("ticket-created").replace("%id%", id)));

        // Notify Discord
        if (plugin.getDiscordBot() != null) {
            plugin.getDiscordBot().sendTicketToDiscord(id, t.creatorName, type, message);
        }

        // Notify Telegram
        if (plugin.isTelegramActive()) {
            plugin.sendTicketToTelegram(id, t.creatorName, type, message);
        }
    }

    public void claimTicket(String id, String claimer) {
        Ticket t = tickets.get(id);
        if (t != null && t.status.equals("Open")) {
            t.status = "Claimed";
            t.claimedBy = claimer;
            Player p = Bukkit.getPlayer(t.creator);
            if (p != null) {
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMsg("ticket-claimed").replace("%id%", id).replace("%admin%", claimer)));
            }
            plugin.notifyAdminsTicket("Ticket [" + id + "] claimed by " + claimer);
            plugin.saveData();
        }
    }

    public void closeTicket(String id, String closer) {
        Ticket t = tickets.get(id);
        if (t != null && !t.status.equals("Closed")) {
            t.status = "Closed";
            Player p = Bukkit.getPlayer(t.creator);
            if (p != null) {
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMsg("ticket-closed").replace("%id%", id).replace("%admin%", closer)));
            }
            plugin.notifyAdminsTicket("Ticket [" + id + "] closed by " + closer);
            if (t.telegramMessageId != null) telegramMessageToTicket.remove(t.telegramMessageId);
            if (t.discordMessageId != null) discordMessageToTicket.remove(t.discordMessageId);
            tickets.remove(id);
            plugin.saveData();
        }
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
            String tgText = "💬 <b>[Ticket #" + t.id + " Chat]</b> <b>" + plugin.escapeHtml(player.getName()) + ":</b> " + plugin.escapeHtml(message);
            plugin.sendTelegramReply(t.telegramChatId, t.telegramMessageId, tgText);
        }

        // Forward to Discord if registered
        if (plugin.getDiscordBot() != null && t.discordMessageId != null && t.discordChannelId != null) {
            String dcText = "💬 **[Ticket #" + t.id + " Chat]** **" + player.getName() + ":** " + message;
            plugin.getDiscordBot().sendDiscordReply(t.discordChannelId, t.discordMessageId, dcText);
        }
    }
}
