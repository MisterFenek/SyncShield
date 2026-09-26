# Changelog

## [1.4.2] - Unreleased

### Added
- **Minecraft 26.X support**: compiled against Spigot API 26.3 (26.1/26.2 also supported); 1.16.X-1.21.X compatibility retained.
- `runServer` test task now launches Minecraft 26.3.

### Changed
- Potion texture lookup prefers the modern `PotionMeta#getBasePotionType()` (1.20.5+) with a reflective fallback to the deprecated `getBasePotionData()` on older servers.

### Fixed
- `isLegacyMaterialServer()` now ignores the minor version on non-1.x servers, so 26.3 no longer incorrectly enables the legacy-material path.

## [1.4.1] - Unreleased

### Security
- Discord RCON now enforces the same `rcon-allowed-commands` allowlist as Telegram (empty list denies all; `*` allows all; sensitive auth commands always refused).
- Fixed `isDiscordAdmin()` comparing Discord user IDs against role IDs (privilege escalation).
- Ticket claim/close buttons and ticket reply/close on both Discord and Telegram are now restricted to admins.
- `/syncshield config` refuses to write secret keys (tokens/passwords).
- OP command notifications no longer relay sensitive commands (`/login`, `/register`, `/changepassword`, ...) that may contain passwords.
- Telegram-origin chat is stripped of Minecraft formatting codes before broadcast; Discord ticket content is sanitized against Markdown pings and formatting injection.
- Discord link codes are only accepted via `/mclink` or as a bare 6-character message in DMs (no longer in shared channels).
- `getEffectivePermissions()` in `DiscordCommandSender` returns an empty set instead of `null`.

### Fixed
- NPE in `onPlayerJoin` when `player.getAddress()` is null (proxies/edge cases) - IP now falls back to "unknown".
- Removed duplicate dead `/ticket` + `/report` branches in `SyncShield#onCommand` (single executor in `TicketCommand`).
- `ticket-system-enabled` master switch is now actually enforced.
- Added `syncshield.ticket` / `syncshield.report` permissions (previously documented but never checked).
- Ticket/report rate limit (30s per player) and 500-char message cap to prevent DB write amplification.
- Collision-safe ticket IDs (no more silent overwrite on duplicate 6-char IDs).
- Server-version gate no longer accepts legacy versions like 1.9; config-version above 2 is left untouched instead of silently downgraded.
- Inventory/ender-chest renders snapshot inventory state on the main thread (async render tasks no longer touch live Bukkit state).
- `/syncshield reload` refreshes Discord channel/admin/RCON settings; Discord 2FA notifications work during the JDA startup window.
- `rcon-allowed-commands` is auto-populated by config migration and documented in `config.yml`.

### Added
- `TextSanitizer` utility (formatting strip, HTML escape, Discord ping neutralization) with JUnit coverage.
- `.gitignore` for build/server artifacts.

## [1.4.0] - 2026-07-27

### Added
- **Discord Bot Integration**: Full Discord support with 2FA login approvals, RCON (`/rcon`), chat sync, and image rendering (`[inv]`, `[item]`, `[ender]`, books, advancements).
- **Cross-Platform Tickets & Reports System**: In-game `/ticket` and `/report` commands with Telegram/Discord interactive buttons (`Claim`/`Close`), thread replies, and `/ticket chat`.
- **Monocraft Font & Full RU/EN Localization**: Replaced all fonts with `Monocraft.ttf` across all renderers and added automatic Russian translations for enchantments, lore, durability, and page labels when `language: ru` is enabled.
- **Auto-Migration & Config Versioning**: Added `config-version: 2` with automatic migration from v1 configs (`config_v1_backup.yml`).
- **Unified Account Linking & Auto-Admin Assignment**: Shortened 2FA linking codes to 6 characters, made them case-insensitive, and added auto-assignment for `owner-id` and `admin-ids` for linked OPs.
- **In-Game Chat Colors & Rendering Fixes**: Full `&` color code support in chat/ticket messages, plus fixed book texture rendering and empty line preservation.

### Changed
- Shifted Discord bot initialization to an asynchronous task to avoid startup thread lock-ups.
- Standardized configuration (`config.yml`) and localization (`messages_*.yml`) with modular switches for Telegram and Discord.
