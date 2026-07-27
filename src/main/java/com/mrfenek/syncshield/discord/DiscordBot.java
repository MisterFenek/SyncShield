package com.mrfenek.syncshield.discord;

import com.mrfenek.syncshield.SyncShield;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
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

public class DiscordBot extends ListenerAdapter {
    private final SyncShield plugin;
    private JDA jda;
    private long syncChannelId;
    private long ticketChannelId;
    private List<Long> adminRoles;
    private boolean enabled;

    public DiscordBot(SyncShield plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("discord-enabled", false);
        if (!enabled) return;

        String token = plugin.getConfig().getString("discord-bot-token");
        if (token == null || token.isEmpty() || token.equals("YOUR_DISCORD_BOT_TOKEN_HERE")) {
            plugin.getLogger().warning("Discord bot token is not set. Discord integration disabled.");
            return;
        }

        this.syncChannelId = plugin.getConfig().getLong("discord-chat-sync-channel", 0L);
        this.ticketChannelId = plugin.getConfig().getLong("discord-ticket-channel", 0L);
        this.adminRoles = plugin.getConfig().getLongList("discord-admin-roles");

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
        if (channel != null) {
            String content = "**New " + type + "** [ID: " + ticketId + "]\n" +
                             "**From:** " + creatorName + "\n" +
                             "**Message:** " + message;
            
            channel.sendMessage(content)
                   .addActionRow(
                       Button.success("ticket_claim_" + ticketId, "Claim"),
                       Button.danger("ticket_close_" + ticketId, "Close")
                   ).queue(sentMsg -> {
                       plugin.getTicketManager().registerDiscordMessage(ticketId, sentMsg.getIdLong(), channel.getIdLong());
                   });
        }
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

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        // Check for admin replies to ticket notifications
        if (event.getMessage().getReferencedMessage() != null) {
            long refId = event.getMessage().getReferencedMessage().getIdLong();
            com.mrfenek.syncshield.tickets.TicketManager.Ticket ticket = plugin.getTicketManager().getTicketByDiscordMessage(refId);
            if (ticket != null) {
                String adminName = event.getAuthor().getName();
                String text = event.getMessage().getContentDisplay().trim();

                if (text.equalsIgnoreCase("/close") || text.equalsIgnoreCase("close") || text.equalsIgnoreCase("/ticket close")) {
                    plugin.getTicketManager().closeTicket(ticket.id, adminName);
                    event.getMessage().reply("✔ Ticket **" + ticket.id + "** has been closed by **" + adminName + "**.").queue();
                } else {
                    if (ticket.status.equals("Open")) {
                        plugin.getTicketManager().claimTicket(ticket.id, adminName);
                    }
                    Player p = Bukkit.getPlayer(ticket.creator);
                    if (p != null && p.isOnline()) {
                        String replyFmt = plugin.getMsg("ticket-admin-reply-format")
                                .replace("%id%", ticket.id)
                                .replace("%admin%", adminName)
                                .replace("%message%", text);
                        p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', replyFmt));
                    }
                    event.getMessage().reply("✔ Reply sent to **" + ticket.creatorName + "** in Minecraft!").queue();
                }
                return;
            }
        }
        if (event.getChannel().getIdLong() == syncChannelId && syncChannelId != 0) {
            if (plugin.getConfig().getBoolean("discord-chat-sync-from-dc", true)) {
                String msg = event.getMessage().getContentDisplay();
                String playerName = event.getAuthor().getName();
                
                // Sync to MC
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String formatted = plugin.getMsg("discord-sync-mc-format")
                            .replace("%player%", playerName)
                            .replace("%message%", msg);
                    Bukkit.broadcastMessage(SyncShield.formatColors(formatted));
                });
            }
        }

        // Direct link code logic (e.g., A3F9X2 or /mclink A3F9X2)
        String content = event.getMessage().getContentRaw().trim();
        if (content.startsWith("/mclink") || (!content.startsWith("/") && content.length() == 6)) {
            String code = content.startsWith("/mclink") ? content.substring(7).trim() : content;
            if (!code.isEmpty()) {
                long authorId = event.getAuthor().getIdLong();
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    String mcName = plugin.attemptDiscordLink(code, authorId);
                    if (mcName != null) {
                        event.getChannel().sendMessage("✔ Successfully linked your Discord account to Minecraft player: **" + mcName + "**").queue();
                    } else if (content.startsWith("/mclink")) {
                        event.getChannel().sendMessage("✖ Invalid or expired link code.").queue();
                    }
                });
                if (content.startsWith("/mclink")) return;
            }
        }

        // RCON logic (requires explicit /rcon <cmd>)
        if (content.startsWith("/rcon ")) {
            if (!plugin.getConfig().getBoolean("discord-rcon-enabled", true)) {
                event.getChannel().sendMessage("RCON access is disabled for Discord in config.yml.").queue();
                return;
            }
            boolean isAdmin = plugin.isDiscordAdmin(event.getAuthor().getIdLong()) ||
                             (event.getMember() != null && event.getMember().getRoles().stream().anyMatch(r -> adminRoles.contains(r.getIdLong())));
            
            if (!isAdmin) {
                event.getChannel().sendMessage("You do not have permission to use RCON.").queue();
                return;
            }

            String cmd = content.substring(6).trim();
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
        if (componentId.startsWith("ticket_claim_")) {
            String ticketId = componentId.substring("ticket_claim_".length());
            String admin = event.getUser().getName();
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getTicketManager().claimTicket(ticketId, admin);
            });
            event.reply("You claimed ticket " + ticketId).setEphemeral(true).queue();
            event.getMessage().editMessage(event.getMessage().getContentRaw() + "\n\n*Claimed by " + admin + "*").setComponents().queue();
        } else if (componentId.startsWith("ticket_close_")) {
            String ticketId = componentId.substring("ticket_close_".length());
            String admin = event.getUser().getName();
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getTicketManager().closeTicket(ticketId, admin);
            });
            event.reply("You closed ticket " + ticketId).setEphemeral(true).queue();
            event.getMessage().editMessage(event.getMessage().getContentRaw() + "\n\n*Closed by " + admin + "*").setComponents().queue();
        } else if (componentId.startsWith("2fa_")) {
            String[] parts = componentId.split("_");
            if (parts.length < 3) return;
            String action = parts[1]; // approve, deny, bl
            String approvalId = parts[2];
            long fromDiscordId = event.getUser().getIdLong();
            
            boolean isAdmin = plugin.isDiscordAdmin(fromDiscordId);
            SyncShield.PendingApproval pending = plugin.getPendingApprovals().get(approvalId);
            
            if (pending == null) {
                event.reply("This 2FA request has expired or was already resolved.").setEphemeral(true).queue();
                return;
            }
            
            boolean isLinkedUser = plugin.getLinkedDiscordChats().getOrDefault(pending.uuid, -1L) == fromDiscordId;
            
            if (!isAdmin && !isLinkedUser) {
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
