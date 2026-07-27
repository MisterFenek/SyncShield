**Overview**  
SyncShield adds 2FA verification to Minecraft logins via Telegram and/or Discord, blocks unverified joins, and lets admins approve logins via interactive buttons. Both Telegram and Discord bots are completely modular and optional — you can use **Telegram only**, **Discord only**, or **both simultaneously**. It also provides Telegram & Discord ↔ Minecraft chat sync, cross-platform ticket and report systems, and can render inventories, items, and ender chests as images in Telegram.

**Advantages**  
- Symmetrical & Modular configuration for both Telegram and Discord integrations.  
- Strong 2FA for OPs and optionally for linked non‑OP players.  
- Simple & Compact **6-character verification codes** (`A3F9X2`) for instant account linking.  
- Case-insensitive code entry (`a3f9x2` or `A3F9X2`) via `/mclink <code>`, `/start <code>`, or direct message.  
- IP approval + blacklist management.  
- Admin notifications for joins/quits, commands, and security events.  
- Telegram and Discord RCON with allowlisted commands.  
- Cross-platform **Ticket & Report system** with direct Admin reply forwarding, `/ticket chat <msg>` support, and `/close` execution.  
- Minecraft in-game color code translation (`&a`, `&c`, `&e`, etc.).  
- Private‑only bot mode for security.  
- English/Russian message packs with safe reload.  
- Inventory/item/ender chest rendering in Telegram and Discord. (Credits to [paper-telegram-bridge](https://modrinth.com/plugin/paper-telegram-bridge) for background images)

**Quick Setup**  
1. Create a Telegram bot with `@BotFather` and copy the token.
2. (Optional) Create a Discord bot via the Developer Portal and copy the token.
3. Get your Telegram user ID (owner) and optional admin IDs.  
4. Put the plugin in the `plugins` folder and start the server once.  
5. Edit `config.yml` with tokens and IDs, then run `/syncshield reload`.  
6. In‑game, run `/syncshield link` to pair your Minecraft account.  
7. Approve logins from Telegram when prompted.

<details>
<summary>Commands</summary>

**Minecraft Commands**

- `/syncshield link`  
  Starts account linking and gives a one‑time code to send to the Telegram bot.

- `/syncshield reload`  
  Reloads the plugin configuration and messages. Requires `syncshield.admin` or console.

- `/syncshield settings <always|session|whitelist|disabled>`  
  Sets the 2FA mode for the player who runs the command.

- `/syncshield config <variable> <value>`  
  Sets the specified variable in `config.yml`.

- `/syncshield debug rebake-textures`  
  Forces a fresh block/item texture bake. Requires `syncshield.debug`.

- `/ticket <message>`
  Opens a ticket with administrators. Syncs to Telegram and Discord.

- `/report <player> <reason>`
  Reports a player to administrators. Syncs to Telegram and Discord.

**Aliases**

- `/ss` → `/syncshield`

**Telegram Commands**

- `/start`  
  Shows the start menu with buttons (for admins and/or linked users).

- `/mclink <code>`  
  Links your Telegram to your Minecraft account using the code from `/syncshield link`.

- `/players`  
  Lists players and quick actions (admins only).

- `/settings`  
  Opens the 2FA settings menu (admins only).

- `/cancel`  
  Clears the current input state and cancels the action (admins only).

- `/rcon <command>`  
  Runs a console command on the server (admins only, when `rcon-enabled: true` and command is allowlisted).

- `/<command>`  
  Any `/...` command is also sent to console as RCON (admins only, when `rcon-enabled: true` and allowlisted).

**Discord Commands**

- `/rcon <command>`  
  Runs a console command on the server (Requires a role configured in `discord-admin-roles`).

</details>

<details>
<summary>Config Example</summary>
  
```yaml
# =========================================================
# SyncShield Configuration File
# ---------------------------------------------------------
# Supported server versions: 1.16.X - 1.21.X (Paper/Spigot API).
# =========================================================

# =========================================================
# ⚙️ PLUGIN CONFIG (General Settings)
# =========================================================

# Default Language for the plugin
language: en

# 2FA mode for Operators (OPs): always, session, whitelist, disabled
op-2fa-mode: session

# 2FA mode for non-OP players who have linked their account: always, session, whitelist, disabled
non-op-2fa-mode: disabled

# Session expiry in hours (only for 'session' mode)
session-expiry-hours: 12

# Data storage encryption (SQLite)
data-encryption: true

# Enable debug outputs in the console
debug: false

# Master switch for Minecraft Ticket & Report System
ticket-system-enabled: true

# Block item baking (for accurate 3D block item renders in Telegram)
bake-block-items-on-startup: true
bake-block-items-log-progress: true


# =========================================================
# ✈️ TELEGRAM CONFIG (Bot & Sync Settings)
# =========================================================

bot-token: "YOUR_BOT_TOKEN_HERE"
owner-id: 0
admin-ids: []
private-only: true
rcon-enabled: true

# Telegram <-> Minecraft Chat Sync
chat-sync-enabled: false
chat-sync-chat-ids: []
chat-sync-topics: []
chat-sync-chat-enabled: true
chat-sync-join-leave-enabled: true
chat-sync-death-enabled: true
chat-sync-from-mc: true
chat-sync-from-tg: true
chat-sync-render-inventory: true
chat-sync-render-ender: true
chat-sync-render-items: true
chat-sync-render-books: true
chat-sync-render-advancements: true

# Telegram Ticket Notifications (Chat or Topic ID)
ticket-telegram-chat-id: 0
ticket-telegram-topic-id: 0


# =========================================================
# 👾 DISCORD CONFIG (Bot & Sync Settings)
# =========================================================

discord-enabled: false
discord-bot-token: "YOUR_DISCORD_BOT_TOKEN_HERE"
discord-admin-roles: []
discord-chat-sync-channel: 0
discord-ticket-channel: 0
```

</details>


<details>
<summary>Roadmap</summary>

| Status | Feature | Key Task |
| :---: | :--- | :--- |
| ❌ (Planned for 1.7/1.8) | **Multi-Loader Support** | Support Quilt, Fabric, Forge, and NeoForge |
| ✅ | **Ticket/report system** | Implement a ticket/report system in Minecraft, Telegram & Discord |
| ✅ | **Legacy Support** | Port logic to older Minecraft versions |
| ✅ | **Telegram RCON Feedback** | Add command feedback to Telegram RCON |
| ✅ | **Chat Sync (Telegram)** | Integrate Telegram <-> Minecraft chat |
| ✅ | **Discord 2FA** | Add Discord login approval functions |
| ❌ (Planned for 1.6/1.7) | **App Authentication** | Support Google Auth, Authy, etc. |
| ✅ | **Chat Sync (Discord)** | Integrate Discord <-> Minecraft chat |
| ✅ | **Discord RCON** | Integrate Discord RCON functionality |

WIP - Work in progress  
❌ - Not implemented  
✅ - Done  

</details>
