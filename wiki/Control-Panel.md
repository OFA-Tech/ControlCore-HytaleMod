# Control panel

`/open-admin-config` opens the panel. Admins see every tab; someone who may only see some settings (see
[Permissions](Permissions)) sees only those. Players open their own part with `/prefs` (see [For players](For-Players)).

Panels update by themselves: when another admin, a command or a mod changes a value, every open panel shows the
new value and the footer says "Changed elsewhere".

## Reading the panel

Every setting row has the same layout: **state mark · name · control · Undo · Default · Save**. The description
under the name ends with the default value.

| Mark | Means |
|---|---|
| filled orange diamond | **Changed**: the value differs from its default |
| hollow diamond | **Default**: still on its default |
| half circle | **Not saved**: you typed something and haven't pressed Save |
| red cross | **Error**: the last save was refused; the reason is written under the row |
| blue arrow | **Restart**: saved, but only applies after the server restarts |
| hatched square | **Locked**: read-only (Hytale internals, or you may only see it) |

- The **Legend** column on the right counts each state. Click a line to show only those rows; click it again or
  **All** to show everything. The counts follow the search box.
- **Undo** appears after you change a row and puts back the value it had before.
- A blue banner at the top of every tab lists changes that wait for a restart.
- Save messages appear in the footer, next to **Close**.

## Tabs

### Server Configs

Every key of the server's `config.json`, read from Hytale's own config definition, so keys added by future Hytale
versions appear on their own. Grouped by section (General, Connection timeouts, Defaults, Rate limit, Update, Backup,
World map, …).

- **Applies at once:** server name, message of the day, max players, max view radius, require join permission and the
  connection timeouts.
- **Applies after a restart:** default world, game mode and hardcore mode, rate limit, backup, update, world map and
  "display tmp tags". A banner at the top lists every changed key that waits for a restart, until you restart.
- **Read-only:** the password, storage settings, modules, mods and log levels. Edit those in `config.json` while the
  server is stopped.
- **Default** puts back the value of a fresh Hytale server. Two keys have no such value (backup directory, update
  patchline), so their Default button reports an error.

Changes are saved to `config.json` immediately, with Hytale's own checks (a value out of range is refused).

#### Worlds

The **config.json | Worlds** switch at the top shows one world's settings instead; pick the world in the dropdown.
They come from the world's own `universe/worlds/<world>/config.json` and apply at once:

- **Gameplay:** PvP, fall damage, time paused, compass updates, objective markers, game mode.
- **NPCs:** NPC spawning, spawn markers, freeze all NPCs.
- **Performance:** a status line (players, loaded chunks, TPS), ticking, block ticking, unload unused chunks and
  **ticks per second** (1-120, default 30; lower saves CPU, kept after a restart).
- **Saving:** save players, save chunks, save new chunks.
- **Movement:** player speed and jump strength for this world, in %; 0 uses the server-wide value from Mod Settings.
- **Advanced (read-only):** seed, gameplay config, delete when removed.

#### Players

The **Players** switch lists everyone who has played on the server, everyone online, banned or on the whitelist, with
their head, name and UUID, badges (**Online** or **Offline**, **Banned**, **Whitelist**, **Operator**), and a status
line: the world, game mode, ping, playtime and Hytale groups for players online, or when they were last seen. A dropdown
shows **Everyone**, **Online**, **Offline**, **Banned**, **Whitelisted** or **Operators**, and the search box finds a
name or UUID.

Buttons on each row:

- **Game mode** switches the player between Adventure and Creative. The player gets a toast, and History keeps the
  change so it can be rolled back.
- **Teleport to** takes you to the player, **Bring** brings them to you (also across worlds).
- **Kick** (press twice) disconnects the player with the text in the reason field as the message. It is in History
  but can't be undone.
- **Ban** bans the player with the length and reason chosen in the form; **Unban** (press twice) lifts a ban.

**+ Player action** opens a form (and **Close form** folds it away): pick **Ban**, **Unban**, **Kick**, **Add to /
Remove from whitelist**, **Make / Remove operator**, **Add to / Remove from group**, or **Give / Take a permission** for
a group (the form then lists that group's own permissions), type a
name or UUID and press **Apply**. Bans work for offline players too, permanently or for 1 hour, 1 day, 7 days or 30
days, with an optional reason; an online player is kicked at once. **Ban** on a row opens the form with that player
filled in. Bans made with Hytale's own `/ban` and `/unban` appear here too, and every ban and unban is in History.

Admins can do everything. Others need `ofatech.controlcore.players` to see the list, plus
`ofatech.controlcore.players.gamemode`, `ofatech.controlcore.players.teleport` or `ofatech.controlcore.players.kick`
for each button, `ofatech.controlcore.players.whitelist` for the whitelist, and `ofatech.controlcore.bans` for bans.
Operators and groups can only be changed by admins, because adding someone to `hytale:Admin` gives them everything.
Whether joining needs the whitelist is the **Require join permission** key in config.json. All of these changes are in
History and can be rolled back.

World ids are `world:<world>.<key>`, for example `world:default.is-pvp-enabled`, so `/cc-get`, `/cc-set`,
permissions, History and snapshots work on them like on `config.json` keys.

### Mod Settings

Every server setting that mods registered with ControlCore, under `<mod name>: <category>` headings. Each row has the
editor its type needs:

| Type | Row |
|---|---|
| On / off | check box, saved when clicked |
| Whole number or decimal with a range | slider and text field; letting go of the slider saves |
| Text | text field and **Save** |
| Choice | dropdown, saved when picked |
| Colour | colour picker and hex field (`#rrggbb`, `f0a` also works) |
| List | one line per item with **Remove**, and a field with **Add** |

Every row has **Default**, which returns the value to the mod's default, and **Undo** after a change. A text can
show its colour in the same row (for example ControlCore's welcome message).

`controlcore:keys-keep-game-action` (**Bound keys keep their game action**, on by default) decides whether a bound
key still does its normal game action too. See [Key binds](Key-Binds#who-decides-the-keys).

### Player Prefs

Your own preferences: the same rows, but only for you. Default returns to the mod's default.

### Keymaps

Each action has two dropdowns: an optional key to **hold** (Ctrl, Alt, Shift, Space, standing still) and the
**main key** (Q, E, R, F, O, mouse buttons, hotbar 1-9, ability 4, dodge, Alt tap). Picking saves at once.

- **Server defaults** (admins): the keys every player gets unless they choose their own. **+ New bind** opens a form
  to create command binds; **Edit** and **Remove** sit under each one. See [Key binds](Key-Binds).
- **My keys:** your own keys. Default returns to the server's keys.
- **Debug tools** (players with `ofatech.controlcore.debug`, under Server defaults): turn the input probe on and off,
  and open the key test screen. The same as `/cc-input-probe` and `/cc-key-probe input`.

### Mods

Every mod and asset pack. See [Mods tab](Mods-Tab).

### From the inventory

With [CustomInventory](https://github.com/SupremoSama/Hytale-CustomInventory) installed, its inventory screen
(Adventure mode, Tab) has a **ControlCore** button. It opens the admin panel for players who may use it and
`/prefs` for everyone else. Hytale's own inventory screen can't have extra buttons.

### Permissions

Admins only: who may see and change each setting, `config.json` key, category or mod. See
[Permissions](Permissions#in-the-panel).

### History

Admins (and players with `ofatech.controlcore.history`) see every server-wide change: `config.json` keys, mod
settings, the server's keys, custom binds, permission rules and mods turned on or off. Each line shows what changed,
`old -> new`, and `#number · when · who · from where` (panel, command, mod or rollback). Players' own preferences
are not recorded.

- The dropdown limits the list to one kind; the search matches names, people, values and `#number`. The newest 200
  changes are listed; `/cc-history --filter=<text>` searches further back.
- **Roll back** puts back the old value and records the rollback; a change can be rolled back once.
- Rows in Server Configs, Mod Settings and the server's Keymaps that have changes show a **History** button that
  opens this tab filtered to them.
- How long changes are kept: `controlcore:history-days` (default 90).

### Snapshots

**History** → **Snapshots** (admins, or `ofatech.controlcore.snapshots`):

- **New snapshot:** a name (letters, digits, `-`, `_`), what to include (config.json, mod settings, keys and binds,
  permissions, mods on/off, players' preferences) and, for mod settings, all mods or one. A snapshot with the same
  name is replaced.
- **Preview restore** lists every difference as `current -> snapshot`, and how many saved values are skipped
  (a mod that is gone, a read-only key). **Restore** applies them; **Save current state, then restore** first keeps
  the current state as `before-<name>`. Each restored value shows up in Changes as "snapshot", so single values can
  still be rolled back.
- Restoring mod settings only touches the mods the snapshot holds; players' preferences only when it holds them.
- **Delete** asks for a second click.
- Snapshots are files in `mods/OFATech_ControlCore/snapshots/`; copy one to another server to restore it there.
- **Use as profile** turns a snapshot into a profile (see below); snapshots used as profiles can't be deleted until
  you remove them from the profiles.
- **Delete all snapshots** (press twice) deletes every snapshot that is not a profile.
- In **Changes**, the dropdown next to **Clear** deletes changes older than 7, 30 or 90 days, or all of them (press
  twice, admins only).

### Profiles

**History** -> **Profiles**: whole setups you switch to.

- Built in: **Vanilla** (every `config.json` key, world setting and mod setting back to its default), **PvP** (every
  world: PvP, fall damage and compass on), **Creative / Build** (Creative game mode, PvP and fall damage off, time
  paused, no NPC spawning) and **Event / Lobby** (PvP and fall damage off, no NPC spawning, time paused, player speed
  120 %). Built-in profiles only touch the values they name.
- Your own: any snapshot marked **Use as profile**.
- **Switch to...** shows every value that would change; **Save current state, then switch** saves the current state
  as `before-<name>` and applies the profile.
- The line at the top shows the active profile and how many values changed since it was applied.

### Health

Admins see how the server is doing: Hytale, ControlCore and Java versions, uptime, players, memory and CPU; every
world's players, loaded chunks, TPS target and tick time, with a warning when a world runs behind; mods that failed
to load; and ControlCore's latest warnings and errors. **Refresh** reads it again. **Write report** saves a text file
in `mods/OFATech_ControlCore/diagnostics/` with passwords, tokens and IP addresses removed, ready to attach to a bug
report. `/cc-diagnostics` does the same from chat or the console.

## Restore defaults

Every tab that edits values has **Restore defaults** at the end of its search line. It puts the rows currently shown
back to their default, so search for a mod (or click a legend line) first to narrow it. Read-only rows are skipped.
Press it twice; on server-wide tabs (Server Configs and Worlds, Mod Settings, Keymaps -> Server defaults, Permissions)
a `before-defaults` snapshot is saved first, so History -> Snapshots can undo it. Every reset is recorded in History.

## Search

Every list has a search line: `[ field ▾ ] [ match ▾ ] [ text ]`.

- **Field:** All, Name, Mod id, Id, Category, Description, Value / keys, Default, Changed only, Default only.
- **Match:** Contains (default), Equals, Starts with, Ends with, and their "Doesn't …" versions.
- Case and spaces don't matter: `ctrl+f` equals `CTRL + F`.
- Typed filters work too, and win over the dropdowns:

| Typed | Shows |
|---|---|
| `mod:controlcore` | one mod's rows |
| `name:timeout` | rows whose name contains "timeout" |
| `id:connection-timeouts` | by id |
| `key:ctrl+f` | key actions on those keys |
| `cat:backup` | a category |
| `default:60` / `desc:restart` | by default value / description |
| `is:changed` / `is:default` | rows changed from / still on their default |
| `-word`, `-mod:server` | hide rows that match |

The end of the search line says how many rows match, for example "5 of 55 shown".

## Panel settings

| Setting | Effect |
|---|---|
| `controlcore:control-panel-enabled` | `false` switches `/open-admin-config` off (`/prefs` keeps working) |
| `controlcore:panel-start-tab` (each player) | which tab opens first (a tab you can't open falls back to the first one) |
