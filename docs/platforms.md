# Platforms

`infrastructure/platforms/` contains one translation module per external system:

```
platforms/
└── hytale/           required host: Hytale → our ports
```

Other mods get their own folder next to it when ControlCore integrates with them (see below); there are none
at the moment.

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
| `ServerConfiguration` | `server/HytaleServerConfiguration` | `HytaleServer.getConfig()`, `HytaleServerConfig.save` |
| `ControlPanelView` | `ui/HytaleControlPanelView`, `ControlPanelPage` | `InteractiveCustomUIPage`, `PageManager`, `.ui` layout |
| `ModCatalog` | `mods/HytaleModCatalog` | `PluginManager`, `AssetModule`, `HytaleServerConfig.getModConfig` |
| `HudDisplay` | `player/HytaleHudDisplay` | `HudManager` |
| `EmptyHandInput` | `input/HytaleEmptyHandInput` | `UnarmedInteractions` asset store |
| `PlayerData`, `SettingStore`, key bind and permission stores | `infrastructure/persistence/Json*Store` | none; JSON files in the plugin data folder |
| `Clock` | `infrastructure/system/SystemClock` | none |

The stores and the clock are not Hytale code, but `HytalePlatform.bind` creates them because it knows the
data folder.

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

Ports for other mods are named after the capability, never after the mod (for example `MapMarkers`, not
`BetterMapApi`). A feature asks `Capabilities.find(MapMarkers.class)` and does not care which mod provides it.
Each mod gets its own folder, and platforms never depend on each other (an ArchUnit rule checks this), so an
update of one mod only ever touches its folder.

Current integrations:

| Folder | Mod | What it does |
|---|---|---|
| `platforms/custominventory/` | `SupremoSama:CustomInventory` | Adds a **ControlCore** button to its inventory screen that opens the panel (`ControlPanelService.openBestFor`). Uses the mod's API by reflection at runtime, so ControlCore builds without its jar. |

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
       compileOnly("com.example:their-mod-api:1.2.3")         // never shipped in our jar
       vineImplementation("com.example:their-mod:1.2.3")      // loaded by runServer for testing
   }
   ```

   Use `vineImplementation`, not `vineMod`, for a mod whose classes our code calls: `runServer`
   loads this mod from the class path, while `vineMod` jars get their own plugin class loader, so our
   code would hit `ClassNotFoundException`. `vineImplementation` stages the jar in `run/mods` and on
   the shared class path, and still does not bundle it. On a real server both jars sit in `mods/`.

   hytale-tools already adds the Hytale, hytalemodding, hytale-mods, HelpChat, CurseMaven,
   AzureDoom, Modtale and Modifold repositories.
4. **Manifest.** Add the mod to `manifest_opt_dependencies` (`Group:Name=*`) so it loads before us,
   then run `./gradlew updatePluginManifest`.
5. **Rule.** Add an ArchUnit rule so only `platforms/<mod>/` may use the mod's packages.

### How it stays safe

- The descriptor is always loaded. `bind` only runs if `ModLookup` reports the mod as loaded.
- The bridge and adapters are loaded lazily inside `bind`. A missing or incompatible mod throws
  `NoClassDefFoundError` / `LinkageError`, which the runtime catches. That integration is rolled back
  and logged, and the rest of the mod keeps working.
- Capabilities are only bound in `start()`, after other mods finished their `setup()`.


### The control panel

`platforms/hytale/ui/HytaleControlPanelView` (bound by `HytalePlatform`) opens `ControlPanelPage`, an
`InteractiveCustomUIPage` with the layout `src/main/resources/Common/UI/Custom/Pages/ControlCore/ControlPanel.ui`
(Hytale's `.ui` syntax, styles from `Common.ui`). The page owns the tabs, the searches and live updates; it is
helped by:

- `SettingsSection` (plain Java): which rows a list shows and what each action does;
- `SettingRows`: the row document each kind of setting appends (`Setting*Row.ui`), its events and how a value
  is shown;
- `ModsTab` (with `ModsSection`): the Mods tab;
- `KeyBindForm`: the admins' Add bind form and the Edit / Remove buttons of their binds;
- `PanelSection` (where each list is drawn) and `PanelAction` (the data the client sends).

Tabs are text buttons: each click is an event binding handled in `handleDataEvent`, which sends an update
toggling the `#...Tab.Visible` groups and disabling the selected tab's button. (Hytale's `TabNavigation`
element only supports icon tabs.)
