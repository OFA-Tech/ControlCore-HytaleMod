# Changelog

## Unreleased

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
  `/cc-keybind-remove` and an "Add bind" form in the admin panel's Keymaps tab (saved in
  `CustomKeyBinds.json`).
- `/cc-input-probe` and `/cc-key-probe input` (admin): show what the client reports for each key.
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
- Welcome message on join (`ExampleEvent`), avaje-inject, the reflective `ModComponentScanner`,
  unused `*Extensions` helpers and the unused `config/TemplatePlugin/config.json`.
