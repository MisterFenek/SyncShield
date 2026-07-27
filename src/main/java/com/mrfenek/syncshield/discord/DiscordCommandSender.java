package com.mrfenek.syncshield.discord;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;

public class DiscordCommandSender implements ConsoleCommandSender {
    private final StringBuilder output = new StringBuilder();

    @Override
    public void sendMessage(String message) {
        output.append(message).append("\n");
    }

    @Override
    public void sendMessage(String[] messages) {
        for (String msg : messages) {
            output.append(msg).append("\n");
        }
    }
    
    public String getOutput() {
        return output.toString().trim();
    }

    @Override public Server getServer() { return Bukkit.getServer(); }
    @Override public String getName() { return "DiscordRCON"; }
    @Override public Spigot spigot() { return new Spigot(); }
    @Override public boolean isPermissionSet(String name) { return true; }
    @Override public boolean isPermissionSet(Permission perm) { return true; }
    @Override public boolean hasPermission(String name) { return true; }
    @Override public boolean hasPermission(Permission perm) { return true; }
    @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) { return null; }
    @Override public PermissionAttachment addAttachment(Plugin plugin) { return null; }
    @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) { return null; }
    @Override public PermissionAttachment addAttachment(Plugin plugin, int ticks) { return null; }
    @Override public void removeAttachment(PermissionAttachment attachment) {}
    @Override public void recalculatePermissions() {}
    @Override public Set<PermissionAttachmentInfo> getEffectivePermissions() { return null; }
    @Override public boolean isOp() { return true; }
    @Override public void setOp(boolean value) {}
    @Override public void sendMessage(UUID sender, String message) { sendMessage(message); }
    @Override public void sendMessage(UUID sender, String[] messages) { sendMessage(messages); }
    @Override public boolean isConversing() { return false; }
    @Override public void acceptConversationInput(String input) {}
    @Override public boolean beginConversation(Conversation conversation) { return false; }
    @Override public void abandonConversation(Conversation conversation) {}
    @Override public void abandonConversation(Conversation conversation, ConversationAbandonedEvent details) {}
    @Override public void sendRawMessage(String message) { sendMessage(message); }
    @Override public void sendRawMessage(UUID sender, String message) { sendMessage(message); }
}
