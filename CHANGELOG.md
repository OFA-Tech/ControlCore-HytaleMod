# Changelog

## Unreleased

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
