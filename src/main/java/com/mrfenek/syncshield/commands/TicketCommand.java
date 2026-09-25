package com.mrfenek.syncshield.commands;

import com.mrfenek.syncshield.SyncShield;
import com.mrfenek.syncshield.tickets.TicketManager;
import com.mrfenek.syncshield.util.TextSanitizer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Single executor for /ticket and /report (the duplicate branches inside
 * SyncShield#onCommand are dead code and must not drift from this logic).
 */
public class TicketCommand implements CommandExecutor {
    private final SyncShield plugin;

    public TicketCommand(SyncShield plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMsg("mc-player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (!plugin.isTicketSystemEnabled()) {
            player.sendMessage(plugin.getMsg("ticket-disabled"));
            return true;
        }

        if (command.getName().equalsIgnoreCase("report")) {
            if (!player.hasPermission("syncshield.report")) {
                player.sendMessage(plugin.getMsg("ss-no-permission"));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMsg("report-usage")));
                return true;
            }
            String target = TextSanitizer.truncate(args[0], 16);
            String reason = TextSanitizer.truncate(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)), 480);
            plugin.getTicketManager().createTicket(player, "Report", "Target: " + target + " | Reason: " + reason);
            return true;
        } else if (command.getName().equalsIgnoreCase("ticket")) {
            if (!player.hasPermission("syncshield.ticket")) {
                player.sendMessage(plugin.getMsg("ss-no-permission"));
                return true;
            }
            if (args.length == 0) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMsg("ticket-usage")));
                return true;
            }

            if (args[0].equalsIgnoreCase("chat")) {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMsg("ticket-chat-usage")));
                    return true;
                }
                String chatMsg = TextSanitizer.truncate(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)), 480);
                plugin.getTicketManager().handlePlayerChat(player, chatMsg);
                return true;
            }

            if (args[0].equalsIgnoreCase("close")) {
                TicketManager.Ticket active = plugin.getTicketManager().getActiveTicketByPlayer(player.getUniqueId());
                if (active != null) {
                    plugin.getTicketManager().closeTicket(active.id, player.getName());
                } else {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMsg("ticket-no-active")));
                }
                return true;
            }

            String msg = TextSanitizer.truncate(String.join(" ", args), 500);
            plugin.getTicketManager().createTicket(player, "Ticket", msg);
            return true;
        }

        return false;
    }
}
