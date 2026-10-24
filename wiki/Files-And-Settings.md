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
| `history/<year>-<month>.jsonl` | the change history, one change per line; months older than `controlcore:history-days` are deleted |

The Server Configs tab writes to Hytale's own `config.json` in the server folder.

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
