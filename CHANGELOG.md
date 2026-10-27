# Changelog

## Unreleased

## 0.18.0 - 2026-10-27

Beta. Requires Hytale server 0.7.2 or newer 0.7.x (`>=0.7.2 <0.8.0`), tested on 0.7.4. The API for other mods
is now **2.2.0** (new: toasts and warning bars); mods built for 2.0.0 and 2.1.0 keep working. No migration needed.

### Added
- **Toasts and warning bars:** small timed messages (up to three stacked) and warning bars such as "Restart in 10
  minutes". Each player picks the toast corner (top right by default, top left, bottom right, or Hytale's own
  notifications) and the warning place (under the compass by default, or above the hotbar) in `/prefs`, and can turn
  them off like any other HUD part. Warnings for everyone also reach players who join later.
- **HUD by situation:** one rule per line in Mod Settings -> ControlCore: HUD, for example `compass hide world:lobby`,
  `chat hide combat` or `hotbar show mode:creative`. Rules follow the player's world, game mode and combat state live.
- **Automation:** new triggers *A player enters combat* / *leaves combat*, and new actions *Toast for the player /
  everyone*, *Warning bar for everyone* (`text | minutes`) and *Remove a warning bar*. Toasts and warnings accept a
  level prefix: `success:`, `warning:` or `error:`.
- **Mods tab:** a **Details** view per mod (what it requires, can use, is needed by, and its Hytale versions, each with
  a status badge; names open that mod's details) and a **Config files** view, both with **< Back**.
- **API 2.2.0:** `notifications().toast(...)`, `toastAll(...)`, `warning(...)`, `warningAll(...)`, `clearWarning(...)`
  and `clearWarningAll(...)`, with `ToastLevel`. Mods built for 2.0.0 and 2.1.0 keep working.

### Changed
- The Mods list no longer shows requirements as a line of text; they moved to **Details**.

## 0.17.0 - 2026-10-27

Beta. Requires Hytale server 0.7.2 or newer 0.7.x (`>=0.7.2 <0.8.0`), tested on 0.7.4. API unchanged (2.1.0).
No migration needed.

### Added
- **Mod dependencies in the Mods tab:** each mod shows what it requires, what it can optionally use and which Hytale
  versions it is made for. Badges **Missing**, **Wrong version** and **Needed by**, a red line explaining the
  problem, and a **Has problems** filter.
- **Turning mods off and on follows dependencies:** turning off a mod that others need asks first and turns them off
  too; turning one on offers to turn on what it needs. Commands, snapshots, profiles and rollbacks do the same.
- The per-action **game action** choice (server setting / keep / replace) is now recorded in History, can be rolled
  back, and is saved in snapshots (Keys part).

### Changed
- The **Bans** list updates by itself while it's open.
- The world **Status** line is translated.
- History → Changes: a **Delete:** label before the clear dropdown.

## 0.16.0 - 2026-10-25

Beta. Requires Hytale server 0.7.2 or newer 0.7.x (`>=0.7.2 <0.8.0`), tested on 0.7.4. The API for other mods
is now **2.1.0** (new: notifications, scheduler, sessions, actions); mods built for 2.0.0 keep working. No
migration needed; `AutomationRules.json` is created when the first rule is saved.

### Added
- **Automation tab** (9th tab, admins): rules that run on their own. **When** a player joins, joins for the first
  time, leaves or enters a world; every X minutes; every day at a time; a world's TPS drops below a value; players
  online reach a number; the server becomes empty; a setting changes; or a mod's trigger fires. **If** (optional) the
  player has or lacks a permission, is in a world, players online are between two numbers, or the time is between
  two times. **Then** message the player, everyone or admins; show a title; run a command as the server or the
  player; switch to a profile; change a setting; or run a mod's action. Up to 8 actions per rule, run in the order
  you set with **+ Add action**, **Up**, **Down** and **-**. **Cron schedules** (`0 20 * * FRI`) with the next run
  shown in the list. Placeholders
  (`{player}`, `{world}`, `{online}`, `{max}`, `{tps}`, `{time}`, `{rule}`), a cooldown, an on/off switch per rule,
  **Test** to run it once, and **Remove** with a second click. Changes rules make are recorded in History as
  "automation". Commands: `/cc-rules`, `/cc-rule-toggle`, `/cc-rule-test`, `/cc-rule-remove`. The server setting
  `controlcore:automation-enabled` stops every rule at once.
- A rule that messages "the player" can't be saved with a trigger that has no player (a timer, for example).
- **API 2.1.0 for mods:** `api.notifications()` (messages, titles, admins), `api.scheduler()` (later, every, daily at
  a time), `api.sessions()` (first join, last seen, playtime, current world) and `api.actions()` (offer actions to
  rules and fire your own triggers). Mods built for 2.0.0 keep working.

### Changed
- Tabs are narrower (139) with a slightly smaller font so nine fit.

### Fixed
- "Permissões" was missing its accent on the Portuguese tab.

## 0.15.0 - 2026-10-25

Beta. Requires Hytale server 0.7.2 or newer 0.7.x (`>=0.7.2 <0.8.0`), tested on 0.7.4. The API for other mods
is unchanged (**2.0.0**); mods built for 0.10.0 keep working. No migration needed; new files (`ActiveProfile.json`,
`diagnostics/`) are created when first used.

### Added
- **Profiles** (History -> **Profiles**): whole setups you switch to. Four are built in: **Vanilla** (every
  `config.json` key, world setting and mod setting back to its default), **PvP**, **Creative / Build** and
  **Event / Lobby**. Any snapshot can be marked **Use as profile**. Switching shows what will change first and saves
  the current state as `before-<name>`; the panel shows the active profile and how many values changed since it was
  applied. Commands: `/cc-profiles` and `/cc-profile <id>` (`@vanilla`, `@pvp`, `@creative`, `@event` or a
  snapshot's name).
- **Health tab** (8th tab, admins): Hytale, ControlCore and Java versions, uptime, players, memory, CPU, every
  world's players, chunks, TPS target and tick time (with a warning when a world runs behind), mods that failed to
  load and ControlCore's latest warnings and errors. **Write report** and `/cc-diagnostics` save a text file in
  `diagnostics/` with passwords, tokens and IP addresses removed, for bug reports.
- **Restore defaults** on every tab that edits values (Server Configs and Worlds, Mod Settings, Player Prefs, both
  Keymaps views, Permissions): puts the rows shown back to their default, so a search or legend filter narrows it,
  for example to one mod. Press twice; on server-wide tabs a `before-defaults` snapshot is saved first.
- **Clearing old data:** History -> Changes can delete changes older than 7, 30 or 90 days, or all of them
  (`/cc-history-clear <days|all>`); Snapshots has **Delete all snapshots**, which keeps snapshots used as profiles
  (`/cc-snapshots-clear`). Both ask for a second click and are for admins only.

## 0.14.0 - 2026-10-25

Beta. Requires Hytale server 0.7.2 or newer 0.7.x (`>=0.7.2 <0.8.0`), tested on 0.7.4. The API for other mods
is unchanged (**2.0.0**); mods built for 0.10.0 keep working. Nothing on disk needs migrating; new files
(`KeyGameActions.json`) are created on first use.

### Added
- **Worlds in Server Configs:** a **config.json | Worlds** switch and a world dropdown. Each world has its own
  settings, applied at once: PvP, fall damage, time paused, compass, objective markers, NPC spawning, spawn markers,
  freeze all NPCs, ticking, block ticking, unloading chunks, saving players and chunks, and game mode. Seed, gameplay
  config and storage are shown read-only. A **Status** line shows players, loaded chunks and TPS. Ids are
  `world:<world>.<key>`, for example `/cc-set world:default.is-pvp-enabled true`.
- **Performance:** ticks per second per world (kept after a restart), chunk loading limits per player
  (`controlcore:chunk-sections-per-second`, `controlcore:chunk-sections-per-tick`, 0 keeps Hytale's limits) and
  **Pause empty worlds** (`controlcore:pause-empty-worlds`, off by default): worlds nobody is in stop updating and
  resume when a player arrives.
- **Player speed and jump:** `controlcore:player-speed` and `controlcore:player-jump` (10-500 %, default 100), with an
  optional override per world. Re-applied on join, world change and game-mode change.
- World changes are recorded in **History** (also when made with Hytale's own `/world` commands) and snapshots have a
  new **Worlds** part (`--parts=worlds`).
- A key bind without a key now says so: "It has no key yet, so it never runs".
- **Bans** (Server Configs -> Bans, admins or `ofatech.controlcore.bans`): the list from `bans.json` with who banned
  whom (with each player's head, or a coloured initial until it loads), when, for how long and why, an **Unban** button (press twice), and a form to ban a player by name or UUID,
  online or not (it must be a real Hytale account), permanently or for 1 hour, 1 day, 7 days or 30 days, with a reason. Commands: `/cc-bans`,
  `/cc-ban <player> <length> <reason>` (length like `permanent`, `30m`, `12h`, `7d`, `2w`; reason `-` for none) and
  `/cc-unban <player>`. Bans and unbans are recorded in History and can be rolled back; bans made with Hytale's own
  `/ban` and `/unban` are recorded too.
- New setting **Show player heads** (`controlcore:player-heads`, on): the heads come from hyvatar.io, which gets the player names; off shows only initials.
- Banned players are listed with their name and UUID. The name comes from Hytale itself: the player if online, else Hytale's own save of that player (`universe/players`), else Hytale's account service, the same lookup Hytale's `/ban` uses.
- History has two more filters: **Worlds** and **Bans** (config.json no longer lists world changes).
- **Game action per key action:** in Keymaps → Server defaults each action has a dropdown, *Game action: server setting / keep / replace*. *Keep* and *Replace* win over the server-wide "Bound keys keep their game action" setting for that action. Also `/cc-key-game-action <id> <server|keep|replace>`; saved in `KeyGameActions.json`.

### Changed
- The control panel is bigger: 1320 wide instead of 1120, every list 150 px taller, wider tabs.
- Ticks per second explains that the game keeps its normal speed; lower values update in bigger steps and use
  less CPU.

### Fixed
- The world's **Status** line updates right after a world setting such as Ticks per second is saved.
- Snapshot lists showed `controlcore.snapshots.part.worlds` instead of "worlds".
- Headings with one row say "1 setting" instead of "1 settings".
- Bans made from the console are written the way Hytale's own `/ban` writes them (with the console as the banner), so Hytale reads them after a restart.
- Pressing the tab that is already open left the game on "Loading..."; every click now gets an answer.
- The dev server's change history was shipped inside the 0.13.0 jar (`history/2026-10.jsonl`, harmless test entries);
  runtime folders are now kept out of the jar.

## 0.13.0 - 2026-10-24

Beta. Requires Hytale server 0.7.2 or newer 0.7.x (`>=0.7.2 <0.8.0`), tested on 0.7.4. The API for other mods
is unchanged (**2.0.0**); mods built for 0.10.0 keep working. New data folders `history/` and `snapshots/` are
created on first use; nothing needs migrating.

### Added
- **Change history.** Every server-wide change is recorded: `config.json` keys, mod settings, the server's keys,
  custom binds, permission rules and mods turned on or off. Each entry says who made it (player, console or a mod),
  when, from where (panel, command, mod or rollback), the old and new value, and whether it needs a restart.
  Players' own preferences and keys are not recorded.
- **History tab** (7th tab): newest first, with a filter by kind and a search (name, who, value or `#number`).
  **Roll back** puts back the value a change replaced; the rollback is recorded too. Server-wide rows with changes
  get a **History** button that opens the tab filtered to them.
- `/cc-history [--filter=text]` lists the latest 10 changes; `/cc-rollback <number>` rolls one back.
- New nodes `ofatech.controlcore.history` (see the history) and `ofatech.controlcore.history.rollback` (roll back);
  ControlCore admins have both. Rolling back a setting still checks that you may change it.
- **Snapshots.** Save the server's setup under a name and restore it later: `config.json`, mod settings (all mods
  or one), the server's keys and custom binds, permission rules, mods on/off and, if you tick it, every player's
  preferences and keys. Restoring first shows each difference (`old -> new`); **Save current state, then restore**
  keeps a `before-<name>` snapshot. Restores are recorded in History as "snapshot". Each snapshot is one JSON file in
  `mods/OFATech_ControlCore/snapshots/`, so a setup like `pvp-weekend` can be copied to another server.
  In the panel: History → **Snapshots**. Commands: `/cc-snapshot-save <name> [--parts=...]`, `/cc-snapshots`,
  `/cc-snapshot-diff <name>`, `/cc-snapshot-restore <name>`, `/cc-snapshot-restore-safe <name>`, `/cc-snapshot-delete <name>`.
  Admins only, or the node `ofatech.controlcore.snapshots`.
- New server setting `controlcore:history-days` (default 90): how long history is kept. It is stored as one file per
  month in `mods/OFATech_ControlCore/history/`; older months are deleted.

### Fixed
- The "Permissions" start-tab option was missing its accent in Portuguese.
- `config.json` changes made outside ControlCore (Hytale's `/maxplayers`, `/maxviewradius`, ...) are noticed within
  5 seconds: open panels update and History records them as "outside ControlCore".
- HUD parts a player hid (or a server rule hides) stay hidden after a game-mode change.
- Command replies in the server console and log use `-` instead of `·`, which the log file showed as `�`.

## 0.12.0 - 2026-10-24

Beta. Requires Hytale server 0.7.2 or newer 0.7.x (`>=0.7.2 <0.8.0`), tested on 0.7.4. The API for other mods
is unchanged (**2.0.0**); mods built for 0.10.0 keep working. The control panel has a new look; nothing on disk
changes, so updating needs no migration.

### Changed
- **New look for the control panel.** The panel is wider (1120), the selected tab has a gold edge, and every
  setting row follows one layout: state mark, name, control, then Undo, Default and Save.
- **State marks and legend:** each row shows whether it is changed, on its default, not saved yet, has an error,
  needs a restart or is locked. A **Legend** column on the right counts each state and filters the list with one
  click; the counts follow the search.
- **Feedback where you look:** errors appear under the row they belong to ("'abc' is not a whole number from 1 to
  32."), save messages in the footer, and a restart banner at the top of every tab.
- **Undo** after a change puts back the previous value.
- **Keys are picked, not typed:** each key row has two dropdowns, a key to hold (Ctrl, Alt, Shift, Space, standing
  still) and a main key, listing only keys the server can detect. Combinations the dropdowns can't show are kept
  and shown as "Custom". The **New bind** form folds away and uses the same dropdowns.
- **Mods tab badges:** Code and Assets as coloured badges, plus Off, Failed, restart and Hytale badges.
- **Bound keys keep their game action** (new server setting `controlcore:keys-keep-game-action`, on by default):
  F still opens doors and also runs its binds. Turn it off to let binds replace the key's own action.
- Row descriptions say the default in plain words instead of the type and id; Server defaults / My keys is a
  two-part switch.

### Fixed
- "Permissões" was missing its accent in Portuguese.

### Removed
- The UI test screen `/cc-key-probe ui` used while building the new look.

## 0.11.0 - 2026-10-22

Beta. Requires Hytale server 0.7.2 or newer 0.7.x (`>=0.7.2 <0.8.0`), tested on 0.7.4. The API for other mods
is unchanged (**2.0.0**); mods built for 0.10.0 keep working.

### Added
- Works with [CustomInventory](https://github.com/SupremoSama/Hytale-CustomInventory): when it is installed, its
  inventory screen gets a **ControlCore** button that opens the admin panel (admins) or `/prefs` (everyone else).
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
- Built and tested on Hytale server 0.7.4; 0.7.2 and 0.7.3 are still supported (`>=0.7.2 <0.8.0`). The new
  `config.json` key `SentryDsn` (crash reporting, added in 0.7.3) appears read-only in Server Configs.

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
