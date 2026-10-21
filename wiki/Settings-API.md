# Settings

Register a setting once; read it through the handle you get back. ControlCore validates input, stores values, shows
the setting in the panel and in `/cc-*` commands, applies permissions and reports changes.

```java
SettingsApi settings = ControlCoreApi.get().settings();

Setting<Integer> maxHomes = settings
        .register(SettingDefinition.server("homes", "max-homes", SettingType.integer(1, 50), 3)
                .named("Max homes")
                .describedAs("How many homes each player may set")
                .inCategory("Limits"))                     // heading "Homes: Limits"
        .value();

Setting<Boolean> showHints = settings
        .register(SettingDefinition.player("homes", "show-hints", SettingType.bool(), true)
                .named("Show hints"))                      // heading "Homes"
        .value();

settings.setModName("homes", "Home Teleports");            // headings become "Home Teleports: …"

int limit = maxHomes.get();              // the server's value, or the default
boolean hints = showHints.get(playerId); // this player's value, or the default
```

## Ids

`server(modId, key, …)` and `player(modId, key, …)` create the id `modId:key`, here `homes:max-homes`.

- Mod id: lower case `a-z 0-9 _ -`. Key: lower case `a-z 0-9 _ . -`.
- The id is what admins type (`/cc-set homes:max-homes 5`), what is saved, and what search matches (`mod:homes`).
- Keep ids stable: changing one loses the saved values.

## Scopes

| Created with | Who changes it | Value |
|---|---|---|
| `SettingDefinition.server(…)` | admins (Mod Settings, `/cc-set`) | one value for the whole server |
| `SettingDefinition.player(…)` | each player (`/prefs`, `/cc-pref`) | each player's own; the default until they choose |

## Types

| Type | Java value | Panel row |
|---|---|---|
| `SettingType.bool()` | `Boolean` | check box |
| `SettingType.integer(min, max)` | `Integer` | slider + field |
| `SettingType.decimal(min, max)` | `Double` | slider + field |
| `SettingType.text(maxLength)` | `String` | text field + Save |
| `SettingType.choice("A", "B", …)` | `String` | dropdown |
| `SettingType.color()` | `String` `#rrggbb` | colour picker + hex field |
| `SettingType.textList(maxItems, maxItemLength)` | `List<String>` | items with Remove, field with Add |

The default value must be valid for the type, or `SettingDefinition` throws. Ranges wider than 1,000,000 get no
slider.

## Names and headings

| Method | Shown as |
|---|---|
| `.named("Max homes")` | the row's name (default: the key) |
| `.describedAs("…")` | the line under the name, and in `/cc-get` |
| `.inCategory("Limits")` | a group inside your mod: heading `Homes: Limits` |
| `settings.setModName("homes", "Home Teleports")` | your mod's name in headings, Keymaps and the Mods tab |

Without `setModName`, the mod id is capitalised (`my-mod` → `My Mod`). Every text method also takes a `Text` for
[translations](Translations-API).

## Reading and changing

```java
maxHomes.get();                       // server setting
showHints.get(playerId);              // a player's preference
maxHomes.set(10);                     // Result<Void>; fails if out of range
showHints.set(playerId, false);
maxHomes.reset();                     // back to the default
Subscription sub = maxHomes.onChange(event ->
        log("max homes: " + event.oldValue() + " -> " + event.newValue()));
sub.close();                          // stop listening
```

- `get()` is a map lookup: keep the handle and call it whenever you need the value; don't copy the value into a field.
- `onChange` runs for changes from the panel, commands, other mods and your own `set`. `event.player()` is set for
  preferences.
- To react to every setting, use `settings.onAnyChange(listener)` or subscribe to `SettingChanged` on
  [the events bus](Panel-Events-And-Logos#events).

## Permissions

By default, server settings are for ControlCore admins and preferences are each player's own. You can set your own
rules; admins can override them with `/cc-perm` (see [Permissions](Permissions)).

```java
SettingDefinition.server("homes", "max-homes", SettingType.integer(1, 50), 3)
        .viewPermission(SettingPermission.EVERYONE)  // everyone sees it, read-only
        .editPermission("homes.moderator");          // moderators may change it

SettingDefinition.server("homes", "spawn-cost", SettingType.integer(0, 1000), 0)
        .permission("homes.staff");                  // see and change

settings.setCategoryPermission("Limits", SettingPermission.of("homes.staff"));  // a whole category
```

Permission nodes are full nodes (not prefixed with anything).

## A text and its colour on one row

```java
settings.register(SettingDefinition.server("homes", "greeting-color", SettingType.color(), "#ffaa00"));
settings.register(SettingDefinition.server("homes", "greeting", SettingType.text(128), "Welcome home!")
        .coloredBy("homes:greeting-color"));
```

The colour setting is shown in the text's row (one Save and one Default for both) instead of on its own. It keeps its
own id, value and commands. Both must have the same scope.

## Registering again

Registering the same id again with the same value type and scope (for example after a plugin reload) replaces the
definition and keeps the saved values. A different type or scope fails with `Settings.Conflict`.

## Errors

| Code | When |
|---|---|
| `Settings.InvalidId` | malformed id |
| `Settings.UnknownSetting` | no such setting, or the player may not see it |
| `Settings.InvalidValue` | the value doesn't fit the type or range |
| `Settings.WrongScope` | a preference used as a server setting, or the other way round |
| `Settings.Conflict` | registered again with another type or scope |
| `Settings.Forbidden` | the player may see but not change it |

## Storage

Server values go to `SettingValues.json` immediately; preferences to `players.json` (every 5 minutes and at
shutdown). A saved value that no longer fits (say you narrowed a range) falls back to the default with one warning in
the log.
