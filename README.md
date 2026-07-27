**Overview**  
SyncShield adds 2FA verification to Minecraft logins via Telegram and/or Discord, blocks unverified joins, and lets admins approve logins via interactive buttons. Both Telegram and Discord bots are completely modular and optional — you can use **Telegram only**, **Discord only**, or **both simultaneously**. It also provides Telegram & Discord ↔ Minecraft chat sync, cross-platform ticket and report systems, and 3D image rendering of inventories, items, ender chests, written books, and advancements using the **Monocraft** pixel font with full Russian/English localization.

**Advantages**  
- **Modular Architecture**: Independent toggles (`telegram-enabled` & `discord-enabled`) for Telegram and Discord integrations.  
- **Strong 2FA Protection**: Enforces 2FA for OPs and optionally for linked non-OP players.  
- **Compact 6-Character Codes**: Instant account linking with 6-character codes (`A3F9X2`) via `/mclink <code>`, `/start <code>`, or direct chat.  
- **Case-Insensitive Entry**: Linking accepts codes in both uppercase and lowercase (`a3f9x2` / `A3F9X2`).  
- **Unified Auto-Owner & Admin Assignment**: Linking the first OP account automatically assigns `owner-id` and populates `admin-ids` / `discord-admin-ids`.  
- **Automatic Config Migration**: Includes `config-version: 2` with automatic, safe migration from v1 configurations (`config_v1_backup.yml`).  
- **Monocraft Font & RU/EN Localization**: High-definition pixel rendering using `Monocraft.ttf` with automatic Russian translations for enchantments (`Острота`, `Прочность`, `Починка`), lore descriptions, durability, and book pages.  
- **Cross-Platform Ticket & Report System**: In-game `/ticket` and `/report` commands with Telegram & Discord interactive buttons (`Claim` / `Close`), targeted thread replies, `/ticket chat <msg>`, and `/ticket close`.  
- **Console RCON Access**: Telegram and Discord `/rcon <command>` with role-based permissions and allowlisted commands.  
- **3D Image Rendering**: Renders `[inv]`, `[item]`, `[ender]`, written books, and advancements into high-quality PNG images delivered to Telegram and Discord.  

**Quick Setup**  
1. Put `syncshield-1.4.0.jar` into your server's `plugins/` folder and start the server once.  
2. Edit `config.yml` with your Telegram `bot-token` and/or Discord `discord-bot-token`.  
3. Run `/syncshield reload` or restart the server.  
4. In-game, run `/syncshield link` to generate your 6-character link code.  
5. Send your code to the Telegram or Discord bot to link your account and receive admin access.  

<details>
<summary>Commands</summary>

**Minecraft Commands**

- `/syncshield link` (Alias: `/ss link`)  
  Generates a 6-character one-time code to link your Minecraft account to Telegram/Discord.

- `/syncshield settings <always|session|whitelist|disabled>`  
  Sets your personal 2FA protection mode.

- `/syncshield reload`  
  Reloads `config.yml` and message files. Automatically migrates older config versions. Requires `syncshield.admin`.

- `/syncshield config <key> <value>`  
  Sets a configuration value directly from in-game console or chat. Requires `syncshield.admin`.

- `/syncshield debug rebake-textures`  
  Forces a fresh bake of 3D block and item textures. Requires `syncshield.debug`.

- `/ticket <message>`  
  Opens a support ticket with administrators. Syncs to Telegram and Discord.

- `/ticket chat <message>`  
  Sends a chat message into your active ticket thread in Telegram and Discord.

- `/ticket close`  
  Closes your active support ticket.

- `/report <player> <reason>`  
  Reports a player to administrators. Syncs to Telegram and Discord.

**Telegram Commands**

- `/start`  
  Displays the main interactive menu with quick action buttons.

- `/mclink <code>`  
  Links your Telegram account using the code from `/syncshield link`.

- `/players`  
  Lists online players with quick admin management options (Admins only).

- `/settings`  
  Opens 2FA mode configuration menu (Admins only).

- `/rcon <command>`  
  Executes a server console command (Admins only, when `rcon-enabled: true`).

**Discord Commands**

- `/mclink <code>`  
  Links your Discord account using the code from `/syncshield link`.

- `/rcon <command>`  
  Executes a server console command (Requires a role configured in `discord-admin-roles`).

</details>

<details>
<summary>Permissions</summary>

| Permission | Description | Default |
| :--- | :--- | :--- |
| `syncshield.use` | Allows linking account via `/syncshield link` | `true` |
| `syncshield.ticket` | Allows opening tickets via `/ticket` | `true` |
| `syncshield.report` | Allows reporting players via `/report` | `true` |
| `syncshield.admin` | Allows reloading and modifying plugin settings (`/syncshield reload`, `/syncshield config`) | `op` |
| `syncshield.debug` | Allows running debug commands (`/syncshield debug`) | `op` |

</details>

<details>
<summary>Config Example</summary>

```yaml
# =========================================================
# SyncShield Configuration File
# ---------------------------------------------------------
# Supported server versions: 1.16.X - 1.21.X (Paper/Spigot API).
# =========================================================

# --- DO NOT EDIT (Will probably break something) ---
config-version: 2

# =========================================================
# ⚙️ PLUGIN CONFIG (General Settings)
# =========================================================

# Default Language for the plugin (en / ru)
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

# Block item baking (for accurate 3D block item renders)
bake-block-items-on-startup: true
bake-block-items-log-progress: true


# =========================================================
# ✈️ TELEGRAM CONFIG (Bot & Sync Settings)
# =========================================================

telegram-enabled: true
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
discord-admin-ids: []
discord-rcon-enabled: true

# Discord <-> Minecraft Chat Sync
discord-chat-sync-enabled: false
discord-chat-sync-channel: 0
discord-chat-sync-chat-enabled: true
discord-chat-sync-join-leave-enabled: true
discord-chat-sync-death-enabled: true
discord-chat-sync-from-mc: true
discord-chat-sync-from-dc: true
discord-chat-sync-render-inventory: true
discord-chat-sync-render-ender: true
discord-chat-sync-render-items: true
discord-chat-sync-render-books: true
discord-chat-sync-render-advancements: true

# Discord Ticket Notifications Channel ID
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

</details>- Not implemented  
✅ - Done  

</details>
