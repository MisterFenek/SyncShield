**Overview**  
Telegram2FALogin adds Telegram-based 2FA to Minecraft logins. It blocks unverified joins, asks for approval in Telegram with inline buttons, and lets admins monitor key server events from the bot.

**Advantages**  
- Strong 2FA for OPs and optionally for linked non‑OP players.  
- Multiple security modes: `always`, `session`, `whitelist`, `disabled`.  
- Session expiry control in hours.  
- IP approval + blacklist management to stop suspicious access.  
- Admin notifications for joins/quits, commands, and security events.  
- Optional Telegram RCON so admins can run console commands remotely.  
- Private‑only bot mode to avoid group chat leakage.  
- Built‑in English/Russian message packs, messages customization and safe reload.

**Quick Setup**  
1. Create a Telegram bot with `@BotFather` and copy the token.  
2. Get your Telegram user ID (owner) and optional admin IDs.  
3. Put the plugin in the `plugins` folder and start the server once.  
4. Edit `config.yml` with token and IDs, then run `/tg2fa reload`.  
5. In‑game, run `/tg2fa link` to pair your Minecraft account.  
6. Approve logins from Telegram when prompted.

**Minecraft Commands**

- `/tg2fa link`  
  Starts account linking and gives a one‑time code to send to the Telegram bot.

- `/tg2fa reload`  
  Reloads the plugin configuration and messages. Available to `OP` and console.

- `/tg2fa settings <always|session|whitelist|disabled>`  
  Sets the 2FA mode for the player who runs the command.

- `/tg2fa config <variable> <value>`\
  Sets the specified variable in config.yml\
  Example: `/tg2fa config owner-id 123123123` - Changes `owner-id` to `123123123`

**Telegram Commands**

- `/start`  
  Shows the start menu with buttons (for admins and/or linked users).

- `/mclink <code>`  
  Links your Telegram to your Minecraft account using the code from `/tg2fa link`.

- `/players`  
  Lists players and quick actions (admins only).

- `/settings`  
  Opens the 2FA settings menu (admins only).

- `/cancel`  
  Clears the current input state and cancels the action (admins only).

- `/rcon <command>`  
  Runs a console command on the server (admins only, when `rcon-enabled: true`).

- `/<command>`  
  Any `/...` command is also sent to console as RCON (admins only, when `rcon-enabled: true`).

<details>
<summary>Config Example</summary>
  
```yaml
# Telegram2FALogin Configuration File
# ---------------------------------------------------------
# This plugin provides 2FA security for Minecraft using Telegram.
# It protects OP accounts and can notify admins about server events.

# Telegram Bot Token (REQUIRED)
# You can get this by chatting with @BotFather on Telegram.
# Use the /newbot command and follow instructions.
bot-token: "YOUR_BOT_TOKEN_HERE"

# Telegram Owner ID (Superuser, overrides all)
# This ID has full access to the bot regardless of permissions.
# You can get your ID from @userinfobot or similar bots.
# Example: owner-id: 123123123
owner-id: 123123123

# Telegram Admin IDs for monitoring and RCON access
# List of IDs that can manage the bot and receive notifications.
# Example: admin-ids: [123456789, 987654321]
admin-ids: [123456789, 987654321]

# Default Language for the plugin
# Supported languages: en (English), ru (Russian)
# You can also translate all the messages yourself in messages.yml
language: en

# 2FA mode for Operators (OPs): always, session, whitelist, disabled
# always: Approval is required for every login attempt (highest security).
# session: Approval is remembered for the duration of session-expiry-hours.
# whitelist: Approval is permanent for the IP address until manual removal.
# disabled: 2FA is disabled for OPs by default (not recommended).
op-2fa-mode: session

# 2FA mode for non-OP players who have linked their account: always, session, whitelist, disabled
# This applies to regular players who use /tg2fa link.
# Default: disabled
non-op-2fa-mode: disabled

# Session expiry in hours (only for 'session' mode)
# After this period, the player must re-approve their login from Telegram.
session-expiry-hours: 12

# Enable RCON access via Telegram for admins
# If true, admins can run console commands directly from the bot.
rcon-enabled: true

# Restrict bot to private chats only (Highly recommended for security)
# If true, the bot will ignore any commands or messages from group chats.
private-only: true

# Enable debug outputs in the console
# Use this if you have issues with the plugin to see more detailed logs.
debug: false
```

</details>
