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
  changes are listed; `/cc-history <filter>` searches further back.
- **Roll back** puts back the old value and records the rollback; a change can be rolled back once.
- Rows in Server Configs, Mod Settings and the server's Keymaps that have changes show a **History** button that
  opens this tab filtered to them.
- How long changes are kept: `controlcore:history-days` (default 90).

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
