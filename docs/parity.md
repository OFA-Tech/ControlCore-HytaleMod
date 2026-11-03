# Panel, command and API parity

Every feature of ControlCore can be used from three places: the control panel, a command (players or the console)
and the API (other mods, `ControlCoreApi.get()`). The tables below map each operation to all three, with the same
rights everywhere: commands and the panel follow the sender's rights, the API acts with full rights or with a
player's panel rights through `section().actingFor(playerId)`.

`ParityDocTest` keeps this page honest: every command in the **Command** column must exist, every registered command
must be on this page, and every public API method must be in the **API method** column (apart from a short allow-list
of mod-integration methods, see the end).

Documented exceptions:

- **Detect key** (press a key to fill a key row or a bind form) is panel only: it needs the panel's key capture.
- **Input probe** and **key test** are debug tools: panel and command only, not in the API.

## Settings

Mod settings (server-wide) and player preferences. Ids are `mod:key`.

| Operation | Panel (where) | Command | API method |
|---|---|---|---|
| List server settings | Mod Settings tab | `/cc-settings` | `settings().snapshot`, `settings().definitions` |
| Show one setting | Mod Settings row (description ends with the default) | `/cc-get <id>` | `settings().read`, `settings().definition` |
| Change a setting | Mod Settings row: control, then Save (or saved at once) | `/cc-set <id> <value>` | `settings().write` |
| Reset a setting | Mod Settings row: Default | `/cc-reset <id>` | `settings().reset` |
| List my preferences | Player Prefs tab (`/prefs`) | `/cc-prefs` | `settings().snapshot` with a player |
| Change a preference | Player Prefs row | `/cc-pref <id> <value>` | `settings().write` with a player |
| Reset a preference | Player Prefs row: Default | `/cc-pref-reset <id>` | `settings().reset` with a player |

## config.json keys

Ids are `server:<key>`, for example `server:motd`.

| Operation | Panel (where) | Command | API method |
|---|---|---|---|
| List the keys, with value, default and live/restart/read-only | Server Configs tab (config.json) | `/cc-configs` | `settings().serverConfigs` |
| Show one key | Server Configs row | `/cc-get server:<key>` | `settings().read` |
| Change a key | Server Configs row | `/cc-set server:<key> <value>` | `settings().write` |
| Reset a key | Server Configs row: Default | `/cc-reset server:<key>` | `settings().reset` |
| What waits for a restart | Blue banner at the top of every tab | `/cc-restart-pending` | `mods().pendingRestart` |

## World keys

Ids are `world:<world>.<key>`, for example `world:default.is-pvp-enabled`.

| Operation | Panel (where) | Command | API method |
|---|---|---|---|
| List the worlds | Server Configs -> Worlds: world dropdown | `/cc-configs --world=<world>` | `settings().worlds` |
| List one world's keys | Server Configs -> Worlds | `/cc-configs --world=<world>` | `settings().serverConfigs` |
| Show, change, reset a world key | Server Configs -> Worlds row | `/cc-get`, `/cc-set`, `/cc-reset` with `world:<world>.<key>` | `settings().read`, `settings().write`, `settings().reset` |

## Keymaps

| Operation | Panel (where) | Command | API method |
|---|---|---|---|
| List key actions and keys | Keymaps tab | `/cc-keys` | `keymaps().actions`, `keymaps().find`, `keymaps().keysFor` |
| Change my keys | Keymaps -> My keys: hold and main key dropdowns | `/cc-bind <id> <keys>` | `keymaps().bind` |
| Go back to the server's keys | Keymaps -> My keys: Default | `/cc-bind-reset <id>` | `keymaps().resetBind` |
| Show the server's keys | Keymaps -> Server defaults | `/cc-keys` | `keymaps().serverKeys` |
| Change the server's keys | Keymaps -> Server defaults: dropdowns | `/cc-bind-server <id> <keys>` | `keymaps().bindServer` |
| Go back to the mod's default keys | Keymaps -> Server defaults: Default | `/cc-bind-server-reset <id>` | `keymaps().resetServerBind` |
| Keep or replace the key's game action | Keymaps row: game action picker | `/cc-key-game-action <id> <mode>` | `keymaps().gameActionMode`, `keymaps().setGameActionMode` |
| Create or replace a command bind (run as the player) | Keymaps -> Server defaults: + New bind / Edit | `/cc-keybind-add <name> <keys> <command>` | `keymaps().addCustom` (`runAsServer` false) |
| Create or replace a command bind (run as the server) | Keymaps -> Server defaults: + New bind / Edit, "run as server" | `/cc-keybind-add-server <name> <keys> <command>` | `keymaps().addCustom` (`runAsServer` true) |
| Delete a command bind | Keymaps -> Server defaults: Remove | `/cc-keybind-remove <name>` | `keymaps().removeCustom` |
| List command binds | Keymaps -> Server defaults | `/cc-keybind-list` | `keymaps().customBinds`, `keymaps().findCustom` |
| Detect key (exception: panel only) | Keymaps row or bind form: Detect | — | — |

## Permission rules

| Operation | Panel (where) | Command | API method |
|---|---|---|---|
| List the rules admins set | Permissions tab, Changed only | `/cc-perms` | `permissions().rules` |
| Set who may see or change a setting, category or mod | Permissions row: See / Change, Save | `/cc-perm <target> <view, edit or both> <node>` | `permissions().set` |
| Remove a rule | Permissions row: Default | `/cc-perm <target> both default` | `permissions().reset` |
| Who may see and change one setting, and why | Permissions row (status line) | `/cc-perm-check <id>` | `permissions().check` |

## Mods

| Operation | Panel (where) | Command | API method |
|---|---|---|---|
| List the mods and whether they run | Mods tab | `/cc-mods` | `mods().list` |
| Details: requires, can use, needed by, Hytale version | Mods row: Details | `/cc-mod-info <mod>` | `mods().list` (`Mod.dependencies`, `Mod.neededBy`) |
| Which other mods turning one on or off changes | Mods: the confirm box | `/cc-mod-on <mod> check`, `/cc-mod-off <mod> check` | `mods().alsoChanged` |
| Turn a mod on or off from the next start | Mods row: Enabled box (Turn all on/off) | `/cc-mod-on <mod> all`, `/cc-mod-off <mod> all` | `mods().setEnabled` |
| Show a mod's config files | Mods row: Config files | `/cc-mod-configs <mod>` | `mods().configFiles` |
| Open a mod's settings or keys | Mods row: Settings / Keys | `/cc-config <mod>` | `controlPanel().openForMod` |

## History

| Operation | Panel (where) | Command | API method |
|---|---|---|---|
| List the latest changes | History tab -> Changes | `/cc-history` | `history().list` |
| Search the changes, or one row's changes | History search; History button on a row | `/cc-history --filter=<text>` | `history().list` with a search |
| Show one kind of change | History: kind dropdown | `/cc-history --kind=<kind>` | `history().list` with a kind |
| Roll back a change | History row: Roll back | `/cc-rollback <number>` | `history().rollback` |
| Delete changes older than N days | History: Clear dropdown (7, 30, 90 days) | `/cc-history-clear <days>` | `history().clearOlderThan` |
| Delete every change | History: Clear dropdown (all) | `/cc-history-clear all` | `history().clearAll` |

## Snapshots

| Operation | Panel (where) | Command | API method |
|---|---|---|---|
| Save a snapshot | History -> Snapshots: New snapshot | `/cc-snapshot-save <name> --parts=...` | `snapshots().save` |
| List the snapshots | History -> Snapshots | `/cc-snapshots` | `snapshots().list` |
| Preview a restore | Snapshots row: Preview restore | `/cc-snapshot-diff <name>` | `snapshots().preview` |
| Restore | Snapshots preview: Restore | `/cc-snapshot-restore <name>` | `snapshots().restore` |
| Save the current state, then restore | Snapshots preview: Save current state, then restore | `/cc-snapshot-restore-safe <name>` | `snapshots().restoreSafe` |
| Delete a snapshot | Snapshots row: Delete | `/cc-snapshot-delete <name>` | `snapshots().delete` |
| Delete every snapshot that is not a profile | Snapshots: Delete all snapshots | `/cc-snapshots-clear` | `snapshots().deleteAll` |

## Profiles

| Operation | Panel (where) | Command | API method |
|---|---|---|---|
| List the profiles and the active one | History -> Profiles | `/cc-profiles` | `profiles().list`, `profiles().status` |
| Preview a switch | Profiles row: Switch to... | `/cc-profile-preview <profile>` | `profiles().preview` |
| Switch to a profile (saves `before-<name>` first) | Profiles preview: Save current state, then switch | `/cc-profile <id>` | `profiles().switchTo` |
| Mark a snapshot as a profile | Snapshots row: Use as profile | `/cc-profile-mark <snapshot>` | `profiles().mark` |
| Turn a profile back into a snapshot | Profiles row: Remove from profiles | `/cc-profile-unmark <profile>` | `profiles().unmark` |

## Health

| Operation | Panel (where) | Command | API method |
|---|---|---|---|
| Show the server's health | Health tab (Refresh) | `/cc-diagnostics` | `health().sections` |
| Write a shareable report | Health: Write report | `/cc-diagnostics` | `health().writeReport` |

## Automation

| Operation | Panel (where) | Command | API method |
|---|---|---|---|
| List the rules | Automation tab | `/cc-rules` | `rules().list` |
| Read one rule | Automation row: Edit (opens the form) | `/cc-rules` (summary) | `rules().get` |
| Create a rule | Automation: + New rule, Save | `/cc-rule-add <fields>` | `rules().save` (no `editing`) |
| Change a rule | Automation row: Edit, Save | `/cc-rule-edit <id> <fields>` | `rules().save` (with `editing`) |
| Turn a rule on or off | Automation row: toggle | `/cc-rule-toggle <id>` | `rules().toggle` |
| Run a rule once now | Automation row: Test | `/cc-rule-test <id>` | `rules().test` |
| Delete a rule | Automation row: Remove | `/cc-rule-remove <id>` | `rules().remove` |
| Built-in triggers, conditions and actions | Rule form dropdowns | `/cc-rule-add` lists them for an unknown id | `rules().triggers`, `rules().conditions`, `rules().actions` |
| Triggers and actions mods offer | Rule form dropdowns | `/cc-rules` | `rules().modTriggers`, `rules().modActions` |

## Players & moderation

`<player>` is a name or UUID.

| Operation | Panel (where) | Command | API method |
|---|---|---|---|
| List players (filter, search) | Server Configs -> Players: filter dropdown and search | `/cc-players [--filter=<filter>] [--search=<text>]` | `players().list` |
| List the bans | Players: Banned filter | `/cc-bans` | `players().list` (`BANNED`) |
| Details: status, groups, whitelist, ban, mute, freeze, chat | Players row: Details | `/cc-player <player>` | `players().details` |
| Inventory | Players -> Details | `/cc-inventory <player>` | `players().inventory` |
| Recent chat | Players: Recent chat | `/cc-chat` | `players().recentChat` |
| Kick | Players row: Kick; + Player action | `/cc-kick <player> <reason>` | `players().kick` |
| Ban | Players row: Ban; + Player action | `/cc-ban <player> <length> <reason>` | `players().ban` |
| Unban | Players row: Unban; + Player action | `/cc-unban <player>` | `players().unban` |
| Mute | + Player action: Mute | `/cc-mute <player> <length>` | `players().mute` |
| Unmute | + Player action: Unmute | `/cc-unmute <player>` | `players().unmute` |
| Freeze | + Player action: Freeze | `/cc-freeze <player>` | `players().freeze` |
| Unfreeze | + Player action: Unfreeze | `/cc-unfreeze <player>` | `players().unfreeze` |
| Switch Adventure and Creative | Players row: Game mode | `/cc-gamemode <player>` | `players().toggleGameMode` |
| Teleport to a player | Players row: Teleport to | `/cc-tp <player>` | `players().teleportTo` |
| Bring a player | Players row: Bring | `/cc-bring <player>` | `players().bring` |
| Add to or remove from the whitelist | + Player action | `/cc-whitelist-add <player>`, `/cc-whitelist-remove <player>` | `players().whitelist` |
| List the whitelist | Players: Whitelisted filter | `/cc-whitelist` | `players().list` (`WHITELISTED`) |
| Require the whitelist to join | Players, Whitelisted filter: Require whitelist / Stop requiring | `/cc-whitelist-require <on or off>` | `players().whitelistRequired`, `players().requireWhitelist` |
| Make or remove an operator | + Player action: Make / Remove operator | `/cc-op <player>`, `/cc-deop <player>` | `players().operator` |
| List groups and their permissions | + Player action: group dropdown | `/cc-groups` | `players().groups`, `players().groupPermissions` |
| Add to or remove from a group | + Player action | `/cc-group <add or remove> <player> <group>` | `players().group` |
| Set as only group | + Player action: Set as only group | `/cc-group set <player> <group>` | `players().setOnlyGroup` |
| Give or take a group permission | + Player action: Give / Take a permission | `/cc-group-perm <give or take> <group> <node>` | `players().groupPermission` |

## HUD

| Operation | Panel (where) | Command | API method |
|---|---|---|---|
| Hide or show my whole HUD | Player Prefs -> ControlCore: HUD: Hide the whole HUD | `/cc-hud <hide, show or toggle>` | `huds().isHidden`, `huds().setHidden` |
| List the HUD parts and mods' HUDs | Player Prefs -> ControlCore: HUD | `/cc-prefs` | `huds().elements` |
| Turn one HUD part on or off for me | Player Prefs -> HUD row | `/cc-pref <hud id> <true or false>` | `huds().isVisible` (read), `settings().write` with a player |
| Force a HUD part shown or hidden for everyone | Mod Settings -> ControlCore: HUD | `/cc-set controlcore:hud-rule.<part> <rule>` | `settings().write` |

## Notifications

| Operation | Panel (where) | Command | API method |
|---|---|---|---|
| Toast to a player | Automation -> Send message: toast, one player | `/cc-toast <player> <style> <text>` | `notifications().toast` |
| Toast to everyone | Automation -> Send message: toast, everyone | `/cc-toast everyone <style> <text>` | `notifications().toastAll` |
| Warning bar to a player | Automation -> Send message: warning, one player | `/cc-warn <player> <seconds> <text>` | `notifications().warning` |
| Warning bar to everyone | Automation -> Send message: warning, everyone | `/cc-warn everyone <seconds> <text>` | `notifications().warningAll` |
| Clear a warning bar | Automation -> Send message: clear | `/cc-warn-clear <player or everyone>` | `notifications().clearWarning`, `notifications().clearWarningAll` |
| Toast to the online admins | Automation -> Send message: toast, admins | `/cc-toast admins <style> <text>` | `notifications().toastAdmins` |
| Warning bar to the online admins | Automation -> Send message: warning, admins | `/cc-warn admins <seconds> <text>` | `notifications().warningAdmins` |
| Clear the admins' warning bar | Automation -> Send message: clear, admins | `/cc-warn-clear admins` | `notifications().clearWarningAdmins` |

## Restore defaults

| Operation | Panel (where) | Command | API method |
|---|---|---|---|
| How many rows would be reset | Restore defaults (first press) | `/cc-restore-defaults <section> check [--search=<text>]` | `defaults().count` |
| Reset the rows shown (safety snapshot first) | Restore defaults (second press), end of the search line | `/cc-restore-defaults <section> confirm [--search=<text>]` | `defaults().restore` |

## Panel opening

| Operation | Panel (where) | Command | API method |
|---|---|---|---|
| Is the panel available | — | — | `controlPanel().isAvailable` |
| Open the admin panel | CustomInventory: ControlCore button | `/open-admin-config` | `controlPanel().open` |
| Open my preferences | CustomInventory: ControlCore button (players) | `/prefs` | `controlPanel().open` (preference tabs) |
| Open one mod's settings | Mods row: Settings | `/cc-config <mod>` | `controlPanel().openForMod` |

## Debug tools

Exception: panel and command only. Players need `ofatech.controlcore.debug`.

| Operation | Panel (where) | Command | API method |
|---|---|---|---|
| Input probe on or off | Keymaps -> Server defaults -> Debug tools | `/cc-input-probe` | — |
| Key test screen | Keymaps -> Server defaults -> Debug tools | `/cc-key-probe input` | — |

## Not in these tables (API for mods)

These describe or plumb a mod's own integration and have no panel button or command, so `ParityDocTest` allow-lists
them: `actingFor` and `register`/`unregister` on every section, `events()`, `scheduler()`, `sessions()`, `actions()`
(automation actions and triggers a mod adds), `settings().setModName`, `settings().modName`,
`settings().setCategoryPermission`, `settings().categoryPermission`, `settings().onAnyChange`, `players().rights`, and
the chat/title helpers `notifications().message`, `broadcast`, `toAdmins`, `title`, `titleAll` (automation rules
send these as actions).
