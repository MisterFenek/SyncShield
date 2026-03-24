**Overview**  
SyncShield adds Telegram‑based 2FA to Minecraft logins, blocks unverified joins, and lets admins approve logins via inline buttons. It also provides Telegram ↔ Minecraft chat sync and can render inventories, items, and ender chests as images in Telegram.

**Advantages**  
- Strong 2FA for OPs and optionally for linked non‑OP players.  
- Security modes: `always`, `session`, `whitelist`, `disabled`.  
- Session expiry control in hours.  
- IP approval + blacklist management.  
- Admin notifications for joins/quits, commands, and security events.  
- Optional Telegram RCON with allowlisted commands.  
- Private‑only bot mode for security.  
- English/Russian message packs with safe reload.  
- Inventory/item/ender chest rendering in Telegram.

**Quick Setup**  
1. Create a Telegram bot with `@BotFather` and copy the token.  
2. Get your Telegram user ID (owner) and optional admin IDs.  
3. Put the plugin in the `plugins` folder and start the server once.  
4. Edit `config.yml` with token and IDs, then run `/syncshield reload`.  
5. In‑game, run `/syncshield link` to pair your Minecraft account.  
6. Approve logins from Telegram when prompted.

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

</details>

<details>
<summary>Config Example</summary>
  
```yaml
# SyncShield Configuration File
# ---------------------------------------------------------
# This plugin provides 2FA security and Telegram chat sync for Minecraft.
# It protects OP accounts, supports command feedback, and can notify admins about server events.
# Supported server versions: 1.16.X - 1.21.X (Paper/Spigot API).

# Telegram Bot Token (REQUIRED)
# You can get this by chatting with @BotFather on Telegram.
# Use the /newbot command and follow instructions.
# After editing, run /syncshield reload or restart the server.
bot-token: "YOUR_BOT_TOKEN_HERE"

# Telegram Owner ID (Superuser, overrides all)
# This ID has full access to the bot regardless of permissions.
# You can get your ID from @userinfobot or similar bots.
# Use a single numeric ID, not a username.
owner-id: 0

# Telegram Admin IDs for monitoring and RCON access
# List of IDs that can manage the bot and receive notifications.
# Example: admin-ids: [123456789, 987654321]
# You can leave it empty and use only owner-id.
admin-ids: []

# Default Language for the plugin
# Supported languages: en (English), ru (Russian)
# This will determine which messages_*.yml file to use.
# Changing this will replace messages.yml with the selected language template.
language: en

# 2FA mode for Operators (OPs): always, session, whitelist, disabled
# always: Approval is required for every login attempt (highest security).
# session: Approval is remembered for the duration of session-expiry-hours.
# whitelist: Approval is permanent for the IP address until manual removal.
# disabled: 2FA is disabled for OPs by default (not recommended).
op-2fa-mode: session

# 2FA mode for non-OP players who have linked their account: always, session, whitelist, disabled
# This applies to regular players who use /syncshield link.
# Default: disabled
# Recommendation: keep disabled unless you need 2FA for everyone.
non-op-2fa-mode: disabled

# Session expiry in hours (only for 'session' mode)
# After this period, the player must re-approve their login from Telegram.
# Shorter values are more secure but less convenient.
session-expiry-hours: 12

# Enable RCON access via Telegram for admins
# If true, admins can run console commands directly from the bot.
# This is powerful; restrict admin-ids and keep bot-token private.
rcon-enabled: true

# Restrict bot to private chats only (Highly recommended for security)
# If true, the bot will ignore any commands or messages from group chats.
# Set to false only if you use chat sync in groups.
private-only: true

# Enable debug outputs in the console
# Use this only for troubleshooting; it can generate a lot of log output.
# Debug logs never show your bot-token.
debug: false

# Data storage encryption
# Data is stored in syncshield_data.db (SQLite). When encryption is enabled,
# the plugin generates a random 16-digit key on first launch and stores it
# in syncshield_data.key. If this key is lost, data cannot be recovered.
# You cannot change the key after first launch.
data-encryption: true

# Telegram <-> Minecraft chat sync
# Add chat IDs where you want chat sync to happen (group or private).
# Make sure the bot is added to the chat and has permission to read messages.
chat-sync-enabled: false
# List of Telegram chat IDs that will receive/forward chat.
# Example: ["123456789", "-1009876543210"]
chat-sync-chat-ids: []
# Optional: map chat IDs to topic IDs for supergroups.
# Format: ["-1001234567890:42", "-1009876543210:7"]
# If a chat ID has a topic mapping, messages will be sent to that topic.
chat-sync-topics: []
# Enable/disable MC -> Telegram chat sync for plain chat messages.
# If disabled, only special tags like [inv]/[item] will be processed.
# This does not affect Telegram -> Minecraft sync.
chat-sync-chat-enabled: true
# Enable/disable MC -> Telegram join/leave messages.
# Use this if you want chat sync but no system join/leave spam.
chat-sync-join-leave-enabled: true
# Enable/disable MC -> Telegram death messages.
# Death messages are pulled from Minecraft and sent to Telegram.
chat-sync-death-enabled: true
# Forward Minecraft chat to Telegram.
# Turn this off if you only want Telegram -> Minecraft.
# Requires chat-sync-enabled to be true.
chat-sync-from-mc: true
# Forward Telegram chat to Minecraft.
# Turn this off if you only want Minecraft -> Telegram.
# Requires chat-sync-enabled to be true.
chat-sync-from-tg: true
# Enable [inv] and [ender] rendering.
# If disabled, [inv] and [ender] tags are ignored.
chat-sync-render-inventory: true
# Enable [ender] rendering.
# If disabled, [ender] tags are ignored even if inventory rendering is on.
chat-sync-render-ender: true
# Enable [item] rendering.
# If disabled, [item] tags are ignored.
chat-sync-render-items: true
# Enable book rendering when holding a written book and using [item].
# If disabled, books will be sent as text only.
chat-sync-render-books: true
# Enable advancement rendering.
# If disabled, advancement images are not sent.
chat-sync-render-advancements: true

# Block item baking (for accurate 3D block item renders)
# If enabled, the plugin will download official assets and bake item renders on first startup per version.
# This can cause CPU spikes and short lag during the first bake.
# The baked cache is stored under plugins/SyncShield/cache/
bake-block-items-on-startup: true
# Log progress while baking block items.
# Disable if you want a quieter console during the bake.
bake-block-items-log-progress: true
```

</details>
