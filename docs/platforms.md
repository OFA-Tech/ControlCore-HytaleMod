# Platforms

`infrastructure/platforms/` contains one translation module per external system:

```
platforms/
├── hytale/           required host: Hytale → our ports
└── hyui/             optional mod: HyUI → our ControlPanelView
```

A platform contains translations only, never feature logic.

## The Hytale platform

`HytalePlatform` implements the `Platform` port. `bind(...)` provides every port, and
`connect(...)` starts translating Hytale events.

| Port | Translator | Hytale API used |
|---|---|---|
| `CommandRegistrar` | `command/HytaleCommandRegistrar`, `SpecCommand` | `AbstractCommand`, `ArgTypes`, `requirePermission` |
| events | `event/HytaleGameEvents` | `PlayerReadyEvent` → `PlayerJoined`, `PlayerDisconnectEvent` → `PlayerLeft` |
| `Players`, `Messenger`, `Permissions` | `player/` | `Universe`, `PlayerRef`, `PermissionsModule` |
| `Scheduler` | `world/HytaleScheduler` | `HytaleServer.SCHEDULED_EXECUTOR`, `TaskRegistry` |
| `SettingsProvider` | `config/HytaleSettingsProvider` | `Config` + `BuilderCodec` (`Settings.json`) |
| `ModLookup` | `mods/HytaleModLookup` | `PluginManager`, `PluginIdentifier` |
| `Log` | `log/HytaleLog` | `HytaleLogger` |
| `ServerConfiguration` | `server/HytaleServerConfiguration` | `HytaleServer.getConfig()` |
| `ControlPanelView` | `ui/HytaleControlPanelView`, `ControlPanelPage` | `InteractiveCustomUIPage`, `PageManager`, `.ui` layout |
| `PlayerData` | `infrastructure/persistence/JsonPlayerDataStore` | none; `players.json` in the plugin data folder |
| `Clock` | `infrastructure/system/SystemClock` | none |

The last two are not Hytale code, but `HytalePlatform.bind` creates them because it knows the data
folder.

### Updating Hytale

1. Bump `hytale_version`, `server_version` and `manifestServerVersion` in `gradle.properties`.
2. Run `./gradlew updatePluginManifest build`.
3. Compile errors can only appear in `platforms/` and `Main`. Fix them there.
4. Run `./gradlew runServer` and check the log line `Set up on Hytale`.

### Adding a Hytale capability

Example: a feature needs to know when a block breaks.

1. Add a domain event, `domain/event/BlockBroken(PlayerInfo player, String blockType)`.
2. Translate it in `HytaleGameEvents.register()`. Listen to the Hytale event, convert it to plain
   values, and `publisher.publish(new BlockBroken(...))`.
3. Write a `ModEventHandler<BlockBroken>` in `application/`.

For a new kind of action (teleport, give item, ...), add a port interface in `domain/ports/game/`,
implement it in `platforms/hytale/`, and bind it in `HytalePlatform.bind`.

## Integrating another mod

Ports for other mods are named after the capability, never after the mod: `ControlPanelView`.
A feature asks `Capabilities.find(ControlPanelView.class)` and does not care which mod provides it.

### Steps

1. **Port.** Add the capability interface in `domain/ports/capability/` if it does not exist yet.
2. **Folder.** Create `platforms/<mod>/` with:
   - a descriptor `<Mod>Platform implements ModPlatform`, annotated `@AutoService(ModPlatform.class)`;
     `modId()` returns the mod's `"Group:Name"`. The descriptor must not use the mod's classes;
   - a package-private bridge class with a static `bind(CapabilityBinder)`, called by the descriptor;
   - adapters that implement our port using the mod's API (inbound), and optionally adapters that
     expose our API inside the mod (outbound).
3. **Gradle.**

   ```kotlin
   repositories { maven("https://repo.example.com/releases") } // if hytale-tools doesn't add it
   dependencies {
       compileOnly("com.example:their-mod-api:1.2.3")   // never shipped in our jar
       vineMod("com.example:their-mod:1.2.3")           // loaded by runServer for testing
   }
   ```

   hytale-tools already adds the Hytale, hytalemodding, hytale-mods, HelpChat, CurseMaven,
   AzureDoom, Modtale and Modifold repositories.
4. **Manifest.** Add the mod to `manifest_opt_dependencies` (`Group:Name=*`) so it loads before us,
   then run `./gradlew updatePluginManifest`.
5. **Rule.** Add an ArchUnit rule like `hyUiOnlyInItsPlatform` for the mod's package.

### How it stays safe

- The descriptor is always loaded. `bind` only runs if `ModLookup` reports the mod as loaded.
- The bridge and adapters are loaded lazily inside `bind`. A missing or incompatible mod throws
  `NoClassDefFoundError` / `LinkageError`, which the runtime catches. That integration is rolled back
  and logged, and the rest of the mod keeps working.
- Capabilities are only bound in `start()`, after other mods finished their `setup()`.


### The control panel: built-in UI and HyUI

`ControlPanelView` has two implementations, and `ControlPanelService` picks one per call:

1. **HyUI capability**, if `HyUiPlatform` bound it (preferred).
2. **Built-in**, `platforms/hytale/ui/HytaleControlPanelView`, always bound by `HytalePlatform`. It
   opens `ControlPanelPage`, an `InteractiveCustomUIPage` with the layout
   `src/main/resources/Common/UI/Custom/Pages/ControlCore/ControlPanel.ui` (Hytale's `.ui` syntax,
   styles from `Common.ui`). Values come from the `ServerConfiguration` port
   (`HytaleServerConfiguration` reads `HytaleServer.get().getConfig()`); the Close button is an
   event binding handled in `handleDataEvent`.

[HyUI](https://www.curseforge.com/hytale/mods/hyui) (`Ellie:HyUI`) renders HTML pages as Hytale UI. It
is a separate plugin, so it is never bundled into ControlCore's jar.

- `HyUiPlatform`: descriptor for `Ellie:HyUI`.
- `HyUiBridge`: touches a HyUI class, then checks that the server still has
  `PageManager.updateCustomPage(CustomPage)`. HyUI 0.9.x calls it whenever a page changes (tab click,
  live value); Hytale 0.7 renamed it to `updateLegacyCustomPage`, and the resulting
  `NoSuchMethodError` on the world thread crashes the world. If it is missing, binding fails, is rolled
  back and logged, and the built-in panel is used.
- `HyUiControlPanel` (inbound): opens `Common/UI/Custom/Pages/control-panel-hyui.html` with
  `PageBuilder` on the player's world thread.

Gradle uses `vineImplementation`, not `vineMod`: `runServer` loads ControlCore from the classpath, so
HyUI's classes must be on that classpath as well or ControlCore cannot see them. hytale-tools stages
it both in `run/mods` and on the shared runtime classpath, and still does not bundle it. On a real
server both jars sit in `mods/` and see each other.

HyUI 0.9.8 (latest as of 2026-10) declares `ServerVersion >=0.5.0-pre.9 <0.6.0`. On 0.7.2 it loads
with a warning but fails the check above, so the built-in panel is used. When HyUI publishes a
0.7-compatible build, bump the CurseMaven file id in `build.gradle.kts`; the check passes and HyUI
takes over without code changes.