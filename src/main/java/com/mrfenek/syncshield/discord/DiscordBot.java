package com.mrfenek.syncshield.discord;

import com.mrfenek.syncshield.SyncShield;
import com.mrfenek.syncshield.tickets.TicketManager;
import com.mrfenek.syncshield.util.TextSanitizer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DiscordBot extends ListenerAdapter {
    private final SyncShield plugin;
    private JDA jda;
    private volatile long syncChannelId;
    private volatile long ticketChannelId;
    private volatile List<Long> adminRoles = new CopyOnWriteArrayList<>();
    private volatile List<Long> adminIds = new CopyOnWriteArrayList<>();
    private volatile boolean rconEnabled = true;
    private final boolean enabled;

    public DiscordBot(SyncShield plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("discord-enabled", false);
        if (!enabled) return;

        String token = plugin.getConfig().getString("discord-bot-token");
        if (token == null || token.isEmpty() || token.equals("YOUR_DISCORD_BOT_TOKEN_HERE")) {
            plugin.getLogger().warning("Discord bot token is not set. Discord integration disabled.");
            return;
        }

        applyConfigValues();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                this.jda = JDABuilder.createDefault(token)
                        .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                        .addEventListeners(this)
                        .build();
                this.jda.awaitReady();
                plugin.getLogger().info("Discord bot connected successfully.");
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize Discord Bot: " + e.getMessage());
            }
        });
    }

    private void applyConfigValues() {
        this.syncChannelId = plugin.getConfig().getLong("discord-chat-sync-channel", 0L);
        this.ticketChannelId = plugin.getConfig().getLong("discord-ticket-channel", 0L);
        this.adminRoles = new CopyOnWriteArrayList<>(plugin.getConfig().getLongList("discord-admin-roles"));
        this.adminIds = new CopyOnWriteArrayList<>(plugin.getConfig().getLongList("discord-admin-ids"));
        this.rconEnabled = plugin.getConfig().getBoolean("discord-rcon-enabled", true);
    }

    /** Re-reads Discord channel/admin/rcon settings after /syncshield reload. */
    public void refreshConfig() {
        applyConfigValues();
        plugin.getLogger().info("Discord bot configuration refreshed (channels, admins, RCON flag).");
    }

    /** True when a Discord token is configured, even if JDA is still connecting. */
    public boolean isConfigured() {
        return enabled;
    }

    public boolean isEnabled() {
        return enabled && jda != null;
    }

    public void broadcastToDiscord(String message) {
        if (jda == null || syncChannelId == 0) return;
        TextChannel channel = jda.getTextChannelById(syncChannelId);
        if (channel != null) {
            channel.sendMessage(message).queue();
        }
    }

    public void sendPhotoToDiscord(byte[] imageBytes, String caption) {
        if (jda == null || syncChannelId == 0 || imageBytes == null || imageBytes.length == 0) return;
        TextChannel channel = jda.getTextChannelById(syncChannelId);
        if (channel != null) {
            String content = caption != null ? htmlToMarkdown(caption) : "";
            if (content.isEmpty()) {
                channel.sendFiles(FileUpload.fromData(imageBytes, "render.png")).queue();
            } else {
                channel.sendMessage(content).addFiles(FileUpload.fromData(imageBytes, "render.png")).queue();
            }
        }
    }

    public void sendTicketToDiscord(String ticketId, String creatorName, String type, String message) {
        if (jda == null || ticketChannelId == 0) return;
        TextChannel channel = jda.getTextChannelById(ticketChannelId);
        if (channel == null) return;

        String content = "**New " + TextSanitizer.sanitizeDiscord(type) + "** [ID: " + ticketId + "]\n"
                + "**From:** " + TextSanitizer.sanitizeDiscord(creatorName) + "\n"
                + "**Message:** " + TextSanitizer.sanitizeDiscord(message);

        channel.sendMessage(content)
                .addActionRow(
                        Button.success("ticket_claim_" + ticketId, "Claim"),
                        Button.danger("ticket_close_" + ticketId, "Close")
                ).queue(sentMsg -> {
                    plugin.getTicketManager().registerDiscordMessage(ticketId, sentMsg.getIdLong(), channel.getIdLong());
                });
    }

    public void sendDiscordReply(long channelId, long messageId, String text) {
        if (jda == null) return;
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel != null) {
            channel.retrieveMessageById(messageId).queue(msg -> {
                msg.reply(text).queue();
            }, failure -> {
                channel.sendMessage(text).queue();
            });
        }
    }

    private boolean isAdminMember(MessageReceivedEvent event) {
        if (plugin.isDiscordAdmin(event.getAuthor().getIdLong())) return true;
        if (event.getMember() != null) {
            for (var role : event.getMember().getRoles()) {
                if (adminRoles.contains(role.getIdLong())) return true;
            }
        }
        return false;
    }

    private boolean isAdminUser(ButtonInteractionEvent event) {
        if (plugin.isDiscordAdmin(event.getUser().getIdLong())) return true;
        if (event.getMember() != null) {
            for (var role : event.getMember().getRoles()) {
                if (adminRoles.contains(role.getIdLong())) return true;
            }
        }
        return false;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        // Check for admin replies to ticket notifications
        if (event.getMessage().getReferencedMessage() != null) {
            long refId = event.getMessage().getReferencedMessage().getIdLong();
            TicketManager.Ticket ticket = plugin.getTicketManager().getTicketByDiscordMessage(refId);
            if (ticket != null) {
                if (!isAdminMember(event)) {
                    event.getMessage().reply("Only server admins may reply to ticket notifications.").queue();
                    return;
                }
                String adminName = event.getAuthor().getName();
                String text = TextSanitizer.sanitizeDiscord(event.getMessage().getContentDisplay()).trim();

                if (text.equalsIgnoreCase("/close") || text.equalsIgnoreCase("close") || text.equalsIgnoreCase("/ticket close")) {
                    plugin.getTicketManager().closeTicket(ticket.id, adminName);
                    event.getMessage().reply("✔ Ticket **" + ticket.id + "** has been closed by **" + adminName + "**.").queue();
                } else {
                    if (ticket.status.equals("Open")) {
                        plugin.getTicketManager().claimTicket(ticket.id, adminName);
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player p = Bukkit.getPlayer(ticket.creator);
                        if (p != null && p.isOnline()) {
                            String replyFmt = plugin.getMsg("ticket-admin-reply-format")
                                    .replace("%id%", ticket.id)
                                    .replace("%admin%", adminName)
                                    .replace("%message%", text);
                            p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', replyFmt));
                        }
                    });
                    event.getMessage().reply("✔ Reply sent to **" + ticket.creatorName + "** in Minecraft!").queue();
                }
                return;
            }
        }
        if (event.getChannel().getIdLong() == syncChannelId && syncChannelId != 0) {
            if (plugin.getConfig().getBoolean("discord-chat-sync-from-dc", true)) {
                String msg = event.getMessage().getContentDisplay();
                String playerName = event.getAuthor().getName();

                // Sync to MC (formatting/pings stripped before broadcast)
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String formatted = plugin.getMsg("discord-sync-mc-format")
                            .replace("%player%", TextSanitizer.stripFormatting(playerName))
                            .replace("%message%", TextSanitizer.stripFormatting(msg));
                    Bukkit.broadcastMessage(SyncShield.formatColors(formatted));
                });
            }
        }

        // Command handling. /mclink and /rcon work anywhere the bot can read;
        // bare 6-character link codes are only accepted in DMs so that random
        // short messages in shared channels are never consumed as link codes.
        String content = event.getMessage().getContentRaw().trim();
        if (content.startsWith("/rcon ")) {
            handleRcon(event, content.substring("/rcon ".length()).trim());
            return;
        }
        if (content.startsWith("/mclink")) {
            handleLinkAttempt(event, content.substring("/mclink".length()).trim(), true);
            return;
        }
        boolean isPrivate = !event.isFromGuild() && event.getChannel().getIdLong() != syncChannelId;
        if (isPrivate && !content.startsWith("/") && content.length() == 6) {
            handleLinkAttempt(event, content, false);
        }
    }

    private void handleLinkAttempt(MessageReceivedEvent event, String code, boolean explicitCommand) {
        if (code.isEmpty()) {
            if (explicitCommand) {
                event.getChannel().sendMessage("Usage: `/mclink <code>`").queue();
            }
            return;
        }
        long authorId = event.getAuthor().getIdLong();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String mcName = plugin.attemptDiscordLink(code, authorId);
            if (mcName != null) {
                event.getChannel().sendMessage("✔ Successfully linked your Discord account to Minecraft player: **"
                        + TextSanitizer.sanitizeDiscord(mcName) + "**").queue();
            } else if (explicitCommand) {
                event.getChannel().sendMessage("✖ Invalid or expired link code.").queue();
            }
        });
    }

    // RCON logic (requires explicit /rcon <cmd>)
    private void handleRcon(MessageReceivedEvent event, String cmd) {
        if (!rconEnabled) {
            event.getChannel().sendMessage("RCON access is disabled for Discord in config.yml.").queue();
            return;
        }
        if (!isAdminMember(event)) {
            event.getChannel().sendMessage("You do not have permission to use RCON.").queue();
            return;
        }

        String baseCmd = TextSanitizer.baseCommand(cmd);
        if (!plugin.isRconCommandAllowed(baseCmd)) {
            event.getChannel().sendMessage("Command `" + baseCmd + "` is not on the RCON allowlist"
                    + " (`rcon-allowed-commands` in config.yml; use * to allow all).").queue();
            return;
        }
        if (cmd.isEmpty()) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            DiscordCommandSender sender = new DiscordCommandSender();
            Bukkit.dispatchCommand(sender, cmd);
            String output = sender.getOutput();
            if (output.isEmpty()) output = "Command executed with no output.";
            if (output.length() > 1900) output = output.substring(0, 1900) + "...";
            event.getChannel().sendMessage("```\n" + output + "\n```").queue();
        });
    }

    public void send2faRequest(long discordUserId, String message, String approvalId) {
        if (jda == null) return;
        jda.retrieveUserById(discordUserId).queue(user -> {
            user.openPrivateChannel().queue(channel -> {
                channel.sendMessage(message)
                       .addActionRow(
                           Button.success("2fa_approve_" + approvalId, "Approve"),
                           Button.danger("2fa_deny_" + approvalId, "Kick"),
                           Button.secondary("2fa_bl_" + approvalId, "Blacklist")
                       ).queue();
            });
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (componentId.startsWith("ticket_claim_") || componentId.startsWith("ticket_close_")) {
            // Admin-only: ticket buttons execute moderation actions.
            if (!isAdminUser(event)) {
                event.reply("Only server admins may manage tickets.").setEphemeral(true).queue();
                return;
            }
            boolean isClaim = componentId.startsWith("ticket_claim_");
            String ticketId = componentId.substring((isClaim ? "ticket_claim_" : "ticket_close_").length());
            String admin = event.getUser().getName();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (isClaim) {
                    plugin.getTicketManager().claimTicket(ticketId, admin);
                } else {
                    plugin.getTicketManager().closeTicket(ticketId, admin);
                }
            });
            event.reply((isClaim ? "Claiming" : "Closing") + " ticket " + ticketId + "...").setEphemeral(true).queue();
            event.getMessage().editMessage(event.getMessage().getContentRaw()
                    + (isClaim ? "\n\n*Claimed by " : "\n\n*Closed by ") + TextSanitizer.sanitizeDiscord(admin) + "*").setComponents().queue();
        } else if (componentId.startsWith("2fa_")) {
            String[] parts = componentId.split("_");
            if (parts.length < 3) return;
            String action = parts[1]; // approve, deny, bl
            String approvalId = parts[2];
            long fromDiscordId = event.getUser().getIdLong();

            SyncShield.PendingApproval pending = plugin.getPendingApprovals().get(approvalId);

            if (pending == null) {
                event.reply("This 2FA request has expired or was already resolved.").setEphemeral(true).queue();
                return;
            }

            boolean isLinkedUser = plugin.getLinkedDiscordChats().getOrDefault(pending.uuid, -1L) == fromDiscordId;

            if (!isAdminUser(event) && !isLinkedUser) {
                event.reply("You don't have permission to resolve this request.").setEphemeral(true).queue();
                return;
            }

            event.deferReply().queue();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String responseMsg = plugin.resolve2FA(action, approvalId);
                if (responseMsg != null) {
                    String mdMsg = htmlToMarkdown(responseMsg);
                    event.getHook().sendMessage(mdMsg).queue();
                    event.getMessage().editMessage(event.getMessage().getContentRaw() + "\n\n*" + mdMsg + "*").setComponents().queue();
                } else {
                    event.getHook().sendMessage("Failed to resolve 2FA (might be expired).").setEphemeral(true).queue();
                }
            });
        }
    }

    public static String htmlToMarkdown(String html) {
        if (html == null) return null;
        return html.replace("<b>", "**").replace("</b>", "**")
                   .replace("<i>", "*").replace("</i>", "*")
                   .replace("<u>", "__").replace("</u>", "__")
                   .replace("<s>", "~~").replace("</s>", "~~")
                   .replace("<code>", "`").replace("</code>", "`")
                   .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                   .replaceAll("<[^>]*>", "");
    }

    public void shutdown() {
        if (this.jda != null) {
            this.jda.shutdown();
        }
    }
}
