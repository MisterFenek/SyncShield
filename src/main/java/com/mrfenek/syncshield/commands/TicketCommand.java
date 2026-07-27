package com.mrfenek.syncshield.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.mrfenek.syncshield.SyncShield;
import com.mrfenek.syncshield.tickets.TicketManager;

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
        
        if (command.getName().equalsIgnoreCase("report")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMsg("report-usage")));
                return true;
            }
            String target = args[0];
            String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            plugin.getTicketManager().createTicket(player, "Report", "Target: " + target + " | Reason: " + reason);
            return true;
        } else if (command.getName().equalsIgnoreCase("ticket")) {
            if (args.length == 0) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMsg("ticket-usage")));
                return true;
            }

            if (args[0].equalsIgnoreCase("chat")) {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMsg("ticket-chat-usage")));
                    return true;
                }
                String chatMsg = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
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

            String msg = String.join(" ", args);
            plugin.getTicketManager().createTicket(player, "Ticket", msg);
            return true;
        }
        
        return false;
    }
}
