# Server management API

Since API **2.4.0** (ControlCore 0.20.0) a mod can do everything the control panel and the `cc-` commands do:
change `config.json` and world keys, custom key binds, permission rules, mods, history, snapshots and profiles,
health, automation rules, players and defaults. Use `>=0.20.0` in your manifest when you call these.

```java
ControlCoreApi api = ControlCoreApi.get();
api.players().kick("Griefer", "Be nice");
api.settings().write(SettingId.of("server:motd"), Optional.empty(), "Welcome!");
api.snapshots().save("before-event", Set.of(SnapshotsApi.Part.CONFIG, SnapshotsApi.Part.SETTINGS), Optional.empty());
```

## Two ways to call: as the mod, or for a player

Every section works in two modes:

- **As the mod** (`api.players()`, `api.history()`, ...): full rights, like the server console. The History tab shows
  the change as made by **a mod**, source **mod**.
- **For a player** (`api.players().actingFor(playerId)`): exactly the rights that player has in the panel: the same
  permission nodes, the admin-on-admin rule, the per-setting see/change rules, admin-only snapshot parts, and so on.
  History records **that player** as the one who made the change (source **mod**).

Use `actingFor` when your mod offers its own UI or command and the player must not get more than the panel would
give them:

```java
PlayersApi asSender = api.players().actingFor(sender);
asSender.ban(target, "7d", "Spam").thenAccept(result -> result
        .onSuccess(text -> tell(sender, text))
        .onFailure(error -> tell(sender, error.description())));
```

Refusals come back as a failed `Result` with an error code, never an exception, for example `Players.Forbidden`,
`Snapshots.AdminParts`, `Permissions.Forbidden`. Lists the player may not see come back empty.

Messages (`Result<String>`) and display texts are in the acting player's language, or English as the mod.

## Sections

| Section | What | Notes |
|---|---|---|
| `settings()` | mod settings (unchanged) **plus** `config.json` keys `server:<key>` and world keys `world:<world>.<key>` through `read`/`write`/`reset`; `serverConfigs()`, `worlds()` | for a player: hidden keys are unknown, see-only keys are read-only, other players' preferences are refused |
| `keymaps()` | key actions (unchanged) **plus** `customBinds()`, `findCustom`, `addCustom`, `removeCustom`, `gameActionMode`, `setGameActionMode` | for a player: only their own bindings; server keys, custom binds and game action modes are admin-only |
| `permissions()` | `rules()`, `set(target, view/edit/both, node)`, `reset(target)`, `check(settingId)` | admins only |
| `mods()` | `list()` with version, authors, state, dependencies, needed-by and problems; `alsoChanged` (preview) and `setEnabled` (applies to all of them); `configFiles`; `pendingRestart()` | for a player: hidden mods left out; turning on/off is admin-only |
| `history()` | `list(kind, search, limit)`, `rollback(number)`, `clearOlderThan(days)`, `clearAll()` | `ofatech.controlcore.history` to see, `.history.rollback` to roll back, clearing is admin-only |
| `snapshots()` | `save(name, parts, mod)`, `list`, `preview`, `restore`, `restoreSafe`, `delete`, `deleteAll` | `ofatech.controlcore.snapshots`; Keys, Permissions and Mods parts are admin-only |
| `profiles()` | `list` (built-in presets and marked snapshots, with `active`), `status`, `preview`, `switchTo`, `mark`, `unmark` | as snapshots |
| `health()` | `sections()` (server, worlds' TPS and chunks, mods, recent problems), `writeReport()` returns the file path | admins only |
| `rules()` | `list`, `get` (as a `Form`), `save(editing, form)` with the panel's checks, `toggle`, `test`, `remove`; `triggers()`, `conditions()`, `actions()`, `modTriggers()`, `modActions()` | admins only |
| `players()` | `list(filter, search)`, `details`, `inventory` (an `Inventory`: `readable` flag and one `InventoryItem(section, slot, itemId, amount)` per filled slot, section being hotbar, storage, armor, utility, tools or backpack), `recentChat` (`ChatLine(time, player, playerName, text)`), `kick`, `ban`/`unban`, `mute`/`unmute`, `freeze`/`unfreeze`, `toggleGameMode`, `teleportTo`/`bring`, `whitelist`, `requireWhitelist`, `operator`, `groups`, `group`, `setOnlyGroup`, `groupPermissions`, `groupPermission`; `rights()` | each action needs its `ofatech.controlcore.players.*` node, groups are admin-only, admins are protected from non-admins |
| `defaults()` | `count(section, world, search)` and `restore(section, world, search)` | server-wide sections save a safety snapshot first |
| `controlPanel()` | `openForMod(player, modId)` opens the panel on a mod's settings | |
| `notifications()` | `toastAdmins`, `warningAdmins`, `clearWarningAdmins`: the `admins` target of `/cc-toast`, `/cc-warn` and `/cc-warn-clear` | online ControlCore admins only; admins who join later do not get the warning |
| `server()` (2.5.0) | `maintenance()` (a `Maintenance(on, message, motd, since, until)`), `setMaintenance(on, MaintenanceOptions(message, motd, offAfter))`: turning it on kicks everyone who may not join; `restart(delay, reason)`, `cancelRestart()`, `restartStatus()` | admins or `ofatech.controlcore.maintenance` (restarts: `ofatech.controlcore.restart`) |
| `backups()` (2.5.0) | `configured`, `setDirectory`, `create()` (a future with the `Backup`), `list`, `delete(name)`, `restoreOnNextStart(name)` (a future: the zip is copied in the background), `pendingRestore`, `cancelRestore` | admins or `ofatech.controlcore.backups` |
| `announcements()` (2.5.0) | `list`, `add`, `remove`, `setEnabled(id, on)`, `move(id, by)`, `settings()`/`setSettings(Settings(enabled, intervalMinutes, order, channel, minPlayers))`, `sendNow()`, `send(id)` | admins or `ofatech.controlcore.announcements` |

`players()` methods that may have to look a player up (offline names) return a `CompletableFuture`. `teleportTo`
and `bring` move the acting player, so they only work with `actingFor`; as the mod they fail with
`Players.PlayersOnly`.

Not in the API on purpose: the input probe and the "Detect key" prompt.

Full Javadoc for every method: the `-javadoc.jar` next to the API jar on GitHub Packages.
