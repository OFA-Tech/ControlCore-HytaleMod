# Translations

ControlCore shows text in each player's **game language** and reads it again every time, so a player who changes
language sees the change. Your setting, key and HUD names can be translated the same way.

## 1. Use `Text.key` instead of plain strings

`Text.key(key, englishFallback)` names a translation and gives the English text used when a language lacks it:

```java
api.settings().setModName("homes", Text.key("homes.mod_name", "Home Teleports"));

SettingDefinition.server("homes", "max-homes", SettingType.integer(1, 50), 3)
        .named(Text.key("homes.settings.max_homes", "Max homes"))
        .describedAs(Text.key("homes.settings.max_homes.desc", "How many homes each player may set"))
        .inCategory(Text.key("homes.settings.limits", "Limits"));

SettingDefinition.player("homes", "sort", SettingType.choice("Name", "Distance"), "Name")
        .optionsKey("homes.sort");                // option "Distance" -> key homes.sort.distance

KeyAction.of("homes", "go-home").displayName(Text.key("homes.keys.go_home", "Go home"));
HudElement.of("homes", "compass").displayName(Text.key("homes.hud.compass", "Home compass"));
```

Plain strings keep working and are shown as they are.

## 2. Ship language files

One file per language in your mod's resources. The file name is the first part of every key, and the keys inside
the file leave it out:

```
src/main/resources/Server/Languages/en-US/homes.lang
src/main/resources/Server/Languages/pt-BR/homes.lang
src/main/resources/Server/Languages/es-ES/homes.lang
```

`pt-BR/homes.lang`:

```
mod_name = Teleportes de Casa
settings.max_homes = Máximo de casas
settings.max_homes.desc = Quantas casas cada jogador pode definir
settings.limits = Limites
sort.distance = Distância
keys.go_home = Ir para casa
hud.compass = Bússola de casa
```

Hytale merges every mod's language files. A key missing in a language falls back to a nearby language, then to
English, then to the fallback text in your code.

## Placeholders

Write `{name}` in the text and fill it in with `.with(…)`:

```java
Text.key("homes.errors.unknown", "There is no home {name}.").with("name", homeName)
```

## Errors in the player's language

`Error` takes a `Text` too, so failures your mod returns are shown translated:

```java
return Result.failure(Error.notFound("Homes.Unknown",
        Text.key("homes.errors.unknown", "There is no home {name}.").with("name", homeName)));
```

## Good to know

- Only shown text is translated. Ids, stored values and choice values stay the same in every language
  (`/cc-set homes:sort Distance` works for everyone).
- Search matches both the translated and the English names.
- **Keys used inside `.ui` files** (`%homes.title;`) may only contain letters, digits and dots: an underscore there
  makes the client fail to load, and players can't join. Keys used from Java, like the ones above, may use `_` and
  `-`.
