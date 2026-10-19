# Public API

Other mods use ControlCore through the interfaces in `net.ofatech.controlcore.api`. The services
implement them directly; there are no copies or adapters. Signatures use plain Java and `domain`
types (`Result`, `Error`, `PlayerId`, `PlayerInfo`, `ControlPanelRequest`, events), never Hytale
types, so the API survives Hytale updates.

| Type | Purpose |
|---|---|
| `ControlCoreApi` | entry point: `get()`, `find()`, `apiVersion()`, `controlPanel()`, `events()`, `settings()` |
| `ControlPanelApi` | `isAvailable()`, `open(player, request)` |
| `ControlCoreEvents` | `subscribe(eventType, listener)` returning a closable `Subscription` |
| `SettingsApi` (1.1.0) | shared settings registry: `register(definition)`, `definitions()`, `snapshot(scope, player)`, `read` / `write` / `reset` by id |
| `Setting<T>` | handle returned by `register`: `get()`, `get(player)`, `set(..)`, `reset(..)`, `onChange(listener)` |

`ControlPanelApi.open` does not check permissions; the calling mod decides who may open the panel. It
returns errors such as `ControlPanel.Disabled` and `ControlPanel.PlayerNotFound`.
Translated game events (`PlayerJoined`, `PlayerLeft`) and `SettingChanged` are delivered on the events
bus.

## Settings registry

A mod registers its settings once and reads them through the returned handle. ControlCore stores the
values, validates input, offers commands (and later the control panel) to change them and reports
changes.

```java
ControlCoreApi.find().ifPresent(api -> {
    Setting<Integer> maxHomes = api.settings()
            .register(SettingDefinition.server("homes:max-homes", SettingType.integer(1, 50), 3)
                    .named("Max homes")
                    .describedAs("How many homes each player may set")
                    .inCategory("Homes"))
            .value();
    Setting<Boolean> showHints = api.settings()
            .register(SettingDefinition.player("homes:show-hints", SettingType.bool(), true))
            .value();

    int limit = maxHomes.get();                 // server value or default
    boolean hints = showHints.get(playerId);    // this player's value or default
    maxHomes.onChange(event -> log("max homes is now " + event.newValue()));
});
```

- **Ids** are `namespace:key`, lower case (`[a-z0-9_-]` / `[a-z0-9_.-]`). Use your mod's name as the
  namespace.
- **Types:** `SettingType.bool()`, `integer(min, max)`, `decimal(min, max)`, `text(maxLength)`,
  `choice(options...)`. The default must be valid, or `SettingDefinition` throws.
- **Scopes:** `SERVER` (one value, changed by admins) and `PLAYER` (each player's own preference; the
  default applies until they choose).
- **Re-registering** the same id with the same value type and scope (for example after `/plugin reload`)
  replaces the definition and keeps the stored values; another type or scope returns `Settings.Conflict`.
- **Errors:** `Settings.InvalidId`, `Settings.UnknownSetting`, `Settings.InvalidValue`,
  `Settings.WrongScope`, `Settings.Conflict`.
- Keep the handle and call `get()` when you need the value; it is a map lookup. A stored value that no
  longer fits the type (say you narrowed a range) falls back to the default with one warning in the log.
- Server values are saved immediately to `SettingValues.json`; player values go to `players.json`
  (key `setting:<id>`), flushed every 5 minutes and at shutdown.

### Commands

| Command | Who | Does |
|---|---|---|
| `/cc-settings` | `<base>.settings` | list every setting with its value (`(default)` if not changed) |
| `/cc-get <id>` | `<base>.settings` | name, description, value, default, type and scope |
| `/cc-set <id> <value>` | `<base>.settings` | change a server setting (the value may contain spaces) |
| `/cc-reset <id>` | `<base>.settings` | return a server setting to its default |
| `/cc-prefs` | any player | list your preferences |
| `/cc-pref <id> <value>` | any player | change one of your preferences |
| `/cc-pref-reset <id>` | any player | return a preference to the default |

`<base>` is ControlCore's base permission; operators have every node.

## Using it from another mod

1. **Dependency.** Until ControlCore is published, run `./gradlew publishToMavenLocal` here and add
   `mavenLocal()` to the other mod's repositories. Once releases are published to GitHub Packages
   (needs a token with `read:packages`):

   ```kotlin
   repositories {
       maven {
           url = uri("https://maven.pkg.github.com/OFA-Tech/ControlCore-HytaleMod")
           credentials {
               username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
               password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
           }
       }
   }
   dependencies {
       compileOnly("net.ofatech:controlcore:0.1.0")
       vineImplementation("net.ofatech:controlcore:0.1.0") // run it in your dev server (not vineMod: see docs/platforms.md)
   }
   ```
2. **Manifest.** Use `manifest_dependencies = OFATech:ControlCore=>=0.1.0`, or
   `manifest_opt_dependencies` if your mod should work without it.
3. **Code.**

   ```java
   ControlCoreApi.find().ifPresent(api -> {
       if (api.controlPanel().isAvailable()) {
           api.controlPanel().open(playerId, ControlPanelRequest.ALL);
       }
   });
   ```

   `get()` throws if ControlCore is not loaded or not set up yet; `find()` returns empty instead. With an
   optional dependency, keep calls to our classes in a separate class that you only touch after
   `PluginManager.get().getPlugin(PluginIdentifier.fromString("OFATech:ControlCore"))` is non-null.

## Rules for maintainers

- Only interfaces and the provider live in `api`. Add a method to the interface and implement it in
  the service; never copy models.
- Everything public in `api` (and the `domain` types it exposes) has Javadoc.
- Semantic versioning on `ControlCoreApi.VERSION` and the mod version. Removing or changing a method
  is a major version bump; adding one is minor.
- The API is published at the end of `setup()` and withdrawn at the start of `shutdown()`.

## Publishing

`maven-publish` publishes `net.ofatech:controlcore` (the plugin jar plus sources and javadoc). The
artifact id is `mod_id` in lower case, the repository is `github_repository` in `gradle.properties`.

`.github/workflows/release.yml` is commented out for now. Once uncommented, a `vX.Y.Z` tag runs
`./gradlew publish` to GitHub Packages and attaches the jars to a GitHub Release.
