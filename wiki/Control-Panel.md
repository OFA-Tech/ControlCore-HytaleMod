# Control panel

`/open-admin-config` opens the panel. Admins see every tab; someone who may only see some settings (see
[Permissions](Permissions)) sees only those. Players open their own part with `/prefs` (see [For players](For-Players)).

Panels update by themselves: when another admin, a command or a mod changes a value, every open panel shows the
new value and the status line says "Changed elsewhere".

## Tabs

### Server Configs

Every key of the server's `config.json`, read from Hytale's own config definition, so keys added by future Hytale
versions appear on their own. Grouped by section (General, Connection timeouts, Defaults, Rate limit, Update, Backup,
World map, …).

- **Applies at once:** server name, message of the day, max players, max view radius, require join permission and the
  connection timeouts.
- **Applies after a restart:** default world, game mode and hardcore mode, rate limit, backup, update, world map and
  "display tmp tags". A gold line at the top lists every changed key that waits for a restart, until you restart.
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

Every row has **Default**, which returns the value to the mod's default. A text can show its colour in the same row
(for example ControlCore's welcome message).

### Player Prefs

Your own preferences: the same rows, but only for you. Default returns to the mod's default.

### Keymaps

- **Server defaults** (admins): the keys every player gets unless they choose their own, plus the **Add bind** form
  to create command binds. See [Key binds](Key-Binds).
- **My keys:** your own keys. Default returns to the server's keys.
- **Debug tools** (players with `ofatech.controlcore.debug`, under Server defaults): turn the input probe on and off,
  and open the key test screen. The same as `/cc-input-probe` and `/cc-key-probe input`.

### Mods

Every mod and asset pack. See [Mods tab](Mods-Tab).

### Permissions

Admins only: who may see and change each setting, `config.json` key, category or mod. See
[Permissions](Permissions#in-the-panel).

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

The status line says how many rows match, for example "5 of 55 shown".

## Panel settings

| Setting | Effect |
|---|---|
| `controlcore:control-panel-enabled` | `false` switches `/open-admin-config` off (`/prefs` keeps working) |
| `controlcore:panel-start-tab` (each player) | which tab opens first (a tab you can't open falls back to the first one) |
