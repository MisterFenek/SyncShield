# Changelog

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
