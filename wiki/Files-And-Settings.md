# Files and settings

## Files

ControlCore keeps its data in `mods/OFATech_ControlCore/`. Back up this folder before updating.

| File | Holds |
|---|---|
| `Settings.json` | `ControlPanelEnabled` (default `true`), the starting value of `controlcore:control-panel-enabled` |
| `SettingValues.json` | server settings changed from their default (saved at once) |
| `players.json` | each player's preferences and own keys (saved every 5 minutes and at shutdown) |
| `KeyBinds.json` | the server's keys for key actions |
| `CustomKeyBinds.json` | binds admins created with `/cc-keybind-add` |
| `SettingPermissions.json` | admins' `/cc-perm` rules |
| `snapshots/<name>.json` | snapshots; copy one to another server's folder to restore it there |
| `ActiveProfile.json` | which profile was applied last |
| `diagnostics/report-<date>-<time>.txt` | reports written by the Health tab or `/cc-diagnostics` |
| `history/<year>-<month>.jsonl` | the change history, one change per line; months older than `controlcore:history-days` are deleted |

The Server Configs tab writes to Hytale's own `config.json` in the server folder, and its Worlds view to each
world's `universe/worlds/<world>/config.json`. A world's TPS and speed/jump overrides are kept in
`WorldSettings.json`.

Edit these files only while the server is stopped; while it runs, use the panel or the commands. A saved value that
no longer fits (for example after a mod narrowed a range) falls back to the default, with one warning in the log.

## ControlCore's own settings

### Server

| Id | Default | Does |
|---|---|---|
| `controlcore:control-panel-enabled` | `true` | `/open-admin-config` on or off |
| `controlcore:welcome-message` | empty | sent to players when they join; `{player}` is their name; empty sends nothing |
| `controlcore:welcome-message-color` | `#ffaa00` | colour of the welcome message (shown in the same row) |
| `controlcore:empty-hand-keys` | `true` | let Q, E, right click, ability 4 and dodge be bound with an empty hand (see [Key binds](Key-Binds)) |
| `controlcore:mods-hidden` | empty | mods players don't see in the Mods tab |
| `controlcore:history-days` | `90` | how many days the change history keeps (1 to 3650) |
| `controlcore:keys-keep-game-action` | `true` | bound keys also do their normal game action (see [Key binds](Key-Binds)) |
| `controlcore:pause-empty-worlds` | `false` | worlds nobody is in stop updating and resume when a player arrives |
| `controlcore:chunk-sections-per-second` | `0` | most chunk sections sent to each player per second; 0 keeps Hytale's limit |
| `controlcore:chunk-sections-per-tick` | `0` | most chunk sections sent to each player per tick; 0 keeps Hytale's limit |
| `controlcore:player-speed` | `100` | movement speed of every player in % (10 to 500); a world can override it |
| `controlcore:player-jump` | `100` | jump strength of every player in % (10 to 500); a world can override it |
| `controlcore:hud-rule.<part>` | Players choose | rule for each HUD part (see [HUD](HUD)) |

### Each player

| Id | Default | Does |
|---|---|---|
| `controlcore:show-welcome-message` | `true` | receive the welcome message |
| `controlcore:panel-start-tab` | Server Configs | which tab the panel opens on |
| `controlcore:hud.<part>` | `true` | show that HUD part |

## Languages

Everything ControlCore shows follows each player's **game language**: English, Portuguese (Brazil) and Spanish are
included; other languages fall back to English. A player who changes their game language sees the change the next
time a text is shown. The console is in English.

Mods can translate their own setting and key names (see [Translations](Translations-API)). Hytale's own descriptions
of `config.json` keys stay in English.
