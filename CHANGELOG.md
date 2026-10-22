# Changelog

## Unreleased

### Added
- **Permissions tab** (admins): see and change who may see and change each setting, `config.json` key, category
  or mod, with the rule that decides it today. Everything `/cc-perm`, `/cc-perms` and `/cc-perm-check` do.
- **Debug buttons** in Keymaps → Server defaults for players with the debug permission: input probe on/off and
  the key test screen.
- [Wiki](https://github.com/OFA-Tech/ControlCore-HytaleMod/wiki) for server owners, players and mod authors, kept in
  `wiki/` and published by `.github/workflows/wiki.yml`.
- `/cc-get`, `/cc-set` and `/cc-reset` also work on `config.json` keys (`server:motd`, ...), with the same checks
  as the panel; they say when a change only applies after a restart.

### Changed
- `/cc-input-probe` replies in the player's language.
- Built and tested on Hytale server 0.7.3; 0.7.2 is still supported (`>=0.7.2 <0.8.0`). The new `config.json` key
  `SentryDsn` (crash reporting) appears read-only in Server Configs.

## 0.10.0 - 2026-10-21

Beta. Requires Hytale server 0.7.2 (`>=0.7.2 <0.8.0`). The API for other mods is now **2.0.0**
(`ControlCoreApi.VERSION`): mods built against 0.9.0 must create settings with
`SettingDefinition.server(modId, key, type, default)` / `player(modId, key, type, default)` instead of
`server("modid:key", ...)`. Saved values, setting ids and commands are unchanged; nothing on a server needs
migrating.

### Added
- **Mods tab** for everyone (`/open-admin-config` and `/prefs`): every mod and asset pack with logo, version,
  authors, description, website and badges (asset pack, off, failed, on/off after restart). Search plus a filter
  (mods, running, turned off, with settings or keys, with assets, everything incl. Hytale's own, hidden by default).
  **Settings** / **Keys** buttons jump to that mod's rows. Admins can turn mods on or off from the next start
  (saved in `config.json` `Mods`; ControlCore and Hytale's own can't be turned off) and read a mod's JSON config
  files (read-only). Pending mod changes join the restart reminder.
- **Mod logos** compatible with Better Modlist and MAC: `Common/UI/Custom/<Group>_<Name>.png` in the mod's assets.
- `/cc-config <mod>` opens the panel on one mod's settings; `/cc-mods` lists mods in chat/console.
- Setting `controlcore:mods-hidden`: mods players don't see in the Mods tab.
- Setting types `SettingType.color()` (colour picker plus hex field, `#rrggbb`) and
  `SettingType.textList(maxItems, maxItemLength)` (items with Add/Remove). New setting `controlcore:welcome-message-color`, shown in the welcome message's row.
  `SettingDefinition.coloredBy(id)` puts a colour setting in a text setting's row (one line, one Save and Default).
- **Sliders:** whole-number and decimal settings with a range show a slider next to the text field (the server's
  config included). Dragging shows the value in the field; letting go saves it. Ranges wider than 1,000,000 keep
  only the field.
- **HUD preferences:** players turn the game's HUD parts on and off in `/prefs` (`controlcore:hud.<part>`); admins
  set a rule per part (`controlcore:hud-rule.<part>`: players choose, hidden until turned on, always hidden, always
  shown).
- **Hide the whole HUD** with the key action `controlcore:toggle-hud` (bind it in Keymaps).
- **HUD API** (`ControlCoreApi.huds()`, `HudElement`): mods register their HUD and get `onShow` / `onHide`;
  ControlCore stores each player's choice and applies it on join.

### Changed
- **Breaking (API 2.0.0):** settings are created like key actions, from a mod id and a key:
  `SettingDefinition.server("homes", "max-homes", type, default)` (id `homes:max-homes`). The single
  `server("homes:max-homes", ...)` form is removed.
- Headings in the panel and `/cc-settings` are `<mod name>: <category>`, or just the mod name when a setting
  has no category. The mod name is the mod id capitalised (`my-mod` -> `My Mod`) unless a mod sets one with
  `SettingsApi.setModName`; key actions use the same names. ControlCore's own settings are under "ControlCore",
  admins' own binds under "Custom binds". `inCategory` is now only the group inside the mod.

### Removed
- HyUI support (optional dependency `Ellie:HyUI`, its HTML panel and the old HTML layouts). HyUI was never
  compatible with Hytale 0.7, and its panel lacked everything added since; the built-in panel is always used.

### Fixed
- Saving a text or number setting in the panel (or a change made elsewhere while the panel was open) no
  longer loops until the server stops the page; this was never in a release.
- Colour pickers read and set their `Color` property (setting `Value` disconnected the client).
- Mod logos set from the server use paths relative to `Common/UI/Custom/`.

## 0.9.0 - 2026-10-20

First public release (beta). Requires Hytale server 0.7.2 (`>=0.7.2 <0.8.0`); the API for other mods is
version 1.5.0 (`ControlCoreApi.VERSION`).

### Changed
- Rebuilt on the OFA-Tech Hytale mod template: `com.azuredoom.hytale-tools` 1.0.51 instead of
  ScaffoldIt, Gradle 9.8.1, Java 25, Hytale server 0.7.2 (`>=0.7.2 <0.8.0`).
- Plugin id is now `OFATech:ControlCore` (was `net.ofatech:ControlCore`); entry point
  `net.ofatech.controlcore.Main`.
- Code reorganised into `api` / `domain` / `application` / `infrastructure`. Hytale is only used in
  `infrastructure/platforms/hytale`, HyUI only in `infrastructure/platforms/hyui`.
- HyUI is no longer bundled: it is an optional dependency installed next to ControlCore.
- `/open-admin-config` now requires the `<base permission>.admin` node.

### Added
- **Empty-hand keys** (server setting `controlcore:empty-hand-keys`, on by default): right click, abilities 1, 2
  and 4 and dodge now reach the server with an empty hand (or a plain block or item), so they can be bound;
  they still do nothing unless bound. New game actions `ability_4` and `dodge`. Turning it off restores the
  game's own empty-hand interactions at once.
- **Restart reminder:** Server Configs lists the keys changed since the server started that only apply after a
  restart (a key changed back drops off), and admins get the same note in chat when they join.
- **Translations** (API 1.5.0): the panel, command replies, errors and command help are shown in each
  player's game language: English, Brazilian Portuguese and Spanish. Missing texts fall back to English. Mods translate their own setting and key texts with
  `Text.key(key, fallback)` and their own `Server/Languages/<language>/<file>.lang` files.
- **Permissions per setting** (API 1.4.0): mods set who may see and who may change a setting
  (`.viewPermission`, `.editPermission`, `.permission`) or a whole category (`setCategoryPermission`);
  admins override them per setting, category or mod with `/cc-perm` (saved in `SettingPermissions.json`)
  and list them with `/cc-perms` and check one setting with `/cc-perm-check <id>`. Players who may only see a setting get it read-only; hidden ones are
  left out of the panel and commands. Applies to the server config too (`mod:server`).
- `/open-admin-config`, `/cc-settings`, `/cc-get`, `/cc-set` and `/cc-reset` no longer need a command
  permission: they show and change only what the player may (admins still see everything).
- **Search** in every control panel list (Server Configs, Mod Settings, Player Prefs, both Keymaps views):
  words match the name, id, mod id, category, value, default or description (all must match; `ctrl+f`
  finds `CTRL + F`). Prefixes `name:`, `id:`, `mod:`, `cat:`, `key:`/`value:`, `default:`, `desc:`,
  `is:changed`/`is:default`, and `-` to exclude. A dropdown left of each search box (default All) limits words to
  one field or shows only changed or default values; a second dropdown picks how text is compared (Contains,
  Equals, Starts with, Ends with, or their "Doesn't …" opposites; Equals/Starts/Ends compare the whole text).
  The search is kept while the panel is open.
- **Key actions** (API 1.3.0, `ControlCoreApi.keymaps()`): mods register `KeyAction.of(modId, action)`
  with a display name, a command and/or `onPress` code and default keys (`Key` and `GameAction`
  combinations). ControlCore detects them while players play (Q, E, R, F, O, 1-9, mouse buttons, Alt tap,
  with Ctrl/Alt/Shift/Space held), cancels the key's game action, and runs the command as the player on a
  virtual thread. Players rebind in the **Keymaps** tab (now also in `/prefs`) or with `/cc-keys`,
  `/cc-bind`, `/cc-bind-reset`; admins use `/cc-bind-server` and `/cc-bind-server-reset`. Keys Hytale
  does not report are kept and marked not available. ControlCore registers `controlcore:open-preferences`
  (`CTRL + F`).
- Key actions can run as the server console (`.runAsServer()`) and be limited to a permission
  (`.permission(node)`); server-run actions without one need `<base>.keys.server`. Players only see and
  trigger actions they may use.
- Admins create their own key binds without a mod: `/cc-keybind-add`, `/cc-keybind-add-server`,
  `/cc-keybind-remove`, `/cc-keybind-list` and a form in the admin panel's Keymaps tab with Edit and Remove
  on each bind (saved in `CustomKeyBinds.json`). Adding a name again replaces it; quotes around the
  command are dropped.
- The admin panel's Keymaps tab switches between **Server defaults** (the keys everyone gets; Default goes back
  to the mod's) and **My keys** (only yours; Default goes back to the server's). Open panels follow changes.
- `/cc-input-probe` and `/cc-key-probe input` (debug tools, `<base>.debug`; operators have it): show what the
  client reports for each key.
- **Server Configs** tab lists every key of the server's `config.json`, read from Hytale's config
  definition: live keys (name, MOTD, max players, max view radius, connection timeouts, require join
  permission) apply at once, restart keys (world defaults, rate limit, backup, update, world map) apply
  after a restart, everything else is read-only. Save and Default (the value of a fresh server);
  changes are saved to `config.json` straight away and published as `ServerConfigChanged`.
- API 1.2.0: `SettingSnapshot.access()` (`SettingAccess`: editable, applies after restart, read-only).
- `/prefs`: any player opens the panel on their own preferences (no admin tabs); works even when the
  admin panel is disabled. API 1.2.0: `ControlPanelRequest.PLAYER_PREFERENCES`,
  `SettingsEditor.onAnyChange`.
- Open control panels update when a setting is changed elsewhere (another admin, `/cc-set`, another mod)
  and say "Changed elsewhere: …".
- Control panel **Mod Settings** tab (every registered server setting) and a real **Player Prefs** tab
  (your own preferences): text fields with Save, check boxes and dropdowns that save immediately, and a
  Default button per row.
- Settings `controlcore:welcome-message` (join message, `{player}` placeholder),
  `controlcore:show-welcome-message` and `controlcore:panel-start-tab` (per player).
- `ControlPanelView.open` now receives `ControlPanelContent` (server config, settings, start tab).
- Shared settings registry (`ControlCoreApi.settings()`, API 1.1.0): mods register typed settings
  (`bool`, `integer`, `decimal`, `text`, `choice`) with server or player scope; values are validated,
  stored in `SettingValues.json` / `players.json` and reported through `SettingChanged`.
- Commands `/cc-settings`, `/cc-get`, `/cc-set`, `/cc-reset` (`<base>.settings`) and `/cc-prefs`,
  `/cc-pref`, `/cc-pref-reset` (players, their own preferences).
- `controlcore:control-panel-enabled` setting: `ControlPanelEnabled` in `Settings.json` is now its
  default, and `/cc-set` changes it without a restart.
- Built-in control panel with native Hytale UI (`Pages/ControlCore/ControlPanel.ui`) showing the current
  server config; used whenever HyUI is missing or incompatible (HyUI 0.9.8 crashes worlds on 0.7).
- `ControlCoreApi` / `ControlPanelApi` for other mods; `Result` / `Error` for expected failures.
- `Settings.json` with `ControlPanelEnabled`.
- ArchUnit architecture tests, runtime and feature tests with fakes, Spotless, build workflow.

### Removed
- The template's example join message (`ExampleEvent`), avaje-inject, the reflective `ModComponentScanner`,
  unused `*Extensions` helpers and the unused `config/TemplatePlugin/config.json`.
