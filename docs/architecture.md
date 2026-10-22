# Architecture

Goal: your features never import Hytale or another mod. Everything external is reached through an
interface (a *port*) in `domain`, implemented by a *translation module* in
`infrastructure/platforms/<system>/`. A Hytale update means fixing `platforms/hytale/`; a change in
another mod means fixing `platforms/<thatmod>/`. Features, the API and their tests stay untouched.

## Layers

```
api             what other mods call       interfaces, implemented directly by application classes
application     our features               commands, handlers, modules, tasks; the runtime
domain          our vocabulary             models, Result/Error, events, ports
infrastructure  implementations of ports
  ├── system          JDK
  ├── persistence     storage
  └── platforms/
      ├── hytale/     Hytale (required host)
      ├── custominventory/  SupremoSama:CustomInventory (optional)
      └── <mod>/      another mod (optional)
```

| Package | May depend on |
|---|---|
| `domain` | JDK only |
| `api` | `domain` |
| `application` | `domain`, `api`, AutoService annotations |
| `infrastructure.system`, `.persistence` | `domain`, Gson |
| `infrastructure.platforms.hytale` | everything, `com.hypixel..` |
| `infrastructure.platforms.<mod>` | everything, `com.hypixel..`, that mod's packages |
| `Main` | `JavaPlugin`, `ModRuntime`, `HytalePlatform` |

## Package map

```
net/ofatech/controlcore/
├── Main.java                          plugin entry point (manifest "Main"), delegates to ModRuntime
├── api/                               ControlCoreApi, ControlCoreApiProvider, ControlCoreEvents, ControlPanelApi,
│                                      SettingsApi, Setting, KeymapsApi, HudApi
├── domain/
│   ├── result/                        Result, Error, ErrorType
│   ├── guard/                         Guard, DomainException
│   ├── event/                         DomainEvent, Subscription, PlayerJoined, PlayerLeft, SettingChanged,
│   │                                  ServerConfigChanged
│   ├── model/                         PlayerId, PlayerInfo, ModSettings, ServerSettings, ControlPanelRequest,
│   │   │                              ControlPanelTab, ControlPanelContent
│   │   ├── settings/                  SettingId, SettingType, SettingScope, SettingDefinition, SettingSnapshot,
│   │   │                              SettingPermission, SettingSearch, SettingsEditor, ...
│   │   ├── serverconfig/              ConfigEntry, ConfigValueKind
│   │   ├── keys/                      KeyAction, Key, GameAction, KeyCombo, KeySignal, CustomKeyBind, ...
│   │   ├── hud/                       HudElement, HudPart
│   │   ├── mods/                      ModInfo, ModsView
│   │   └── text/                      Text, Translator
│   └── ports/
│       ├── platform/                  Platform, PortBinder, ModPlatform, CapabilityBinder, Capabilities
│       ├── game/                      Players, Messenger, Permissions, Scheduler, PlayerData, ServerConfiguration,
│       │                              CommandRunner, PlayerInput, InputProbe, EmptyHandInput, HudDisplay
│       ├── command/                   CommandSpec, ArgSpec, ArgType, CommandContext, CommandSender,
│       │                              CommandHandler, CommandRegistrar, Reply
│       ├── event/                     EventPublisher
│       ├── mods/                      ModLookup, ModCatalog
│       ├── capability/                ControlPanelView
│       ├── config/                    SettingsProvider, SettingStore, KeyBindStore, CustomKeyBindStore,
│       │                              SettingPermissionStore
│       └── system/                    Clock, Log, Translations
├── application/
│   ├── component/                     ModCommand, ModEventHandler, FeatureModule, ModTask, TaskSchedule,
│   │                                  ModuleContext
│   ├── runtime/                       ModRuntime, ComponentIndex, Injector, CapabilityRegistry,
│   │                                  ControlCoreApiService
│   ├── event/                         DomainEventBus
│   ├── text/                          Texts, CommandTexts
│   ├── settings/                      SettingsRegistry (SettingsApi), SettingPermissionPolicy, ModNames,
│   │                                  /cc-settings, /cc-get, /cc-set, /cc-perm, ... commands
│   ├── serverconfig/                  ServerConfigEditor, RestartReminderHandler
│   ├── keymaps/                       KeymapRegistry (KeymapsApi), KeyDispatcher, EmptyHandKeys, /cc-bind, ...
│   ├── controlpanel/                  ControlPanelService, ControlPanelSettings, ControlPanelModule,
│   │                                  /open-admin-config, /prefs, /cc-config
│   ├── hud/                           HudService (HudApi), HudModule, join and leave handlers
│   ├── mods/                          ModsEditor, /cc-mods
│   ├── welcome/                       WelcomeSettings, WelcomeMessageHandler
│   └── persistence/                   PlayerDataFlushTask
└── infrastructure/
    ├── system/                        SystemClock
    ├── i18n/                          BundledTranslations
    ├── persistence/                   JsonFile and the Json*Store classes
    └── platforms/
        └── hytale/                    HytalePlatform + command/ config/ event/ i18n/ input/ log/ mapping/
                                       mods/ player/ server/ ui/ world/
```

Tests mirror this under `src/test/java/...`, plus `architecture/` (ArchUnit) and `fakes/` (in-memory
ports and `FakePlatform`).

## Rules

Checked by `src/test/java/.../architecture/ArchitectureTest.java` on every `./gradlew build`. One
source set holds all code, so these tests are the only thing that stops a stray import. Keep them
green and run CI on every pull request.

1. `domain` depends on nothing but the JDK.
2. `api` depends only on `domain`.
3. `application` depends only on `domain` and `api`.
4. `infrastructure.system` and `.persistence` depend only on `domain` (and Gson).
5. `com.hypixel..` is used only in `infrastructure.platforms..` and `Main`.
6. A mod's packages are used only in its own `infrastructure.platforms.<mod>` folder.
7. Nothing outside `platforms` depends on `platforms`, except `Main`.
8. `ModPlatform` descriptors use only the JDK and our classes, so they load even when the mod is
   absent. Foreign code is reached through a bridge class after the presence check; linkage errors
   are caught and logged and only that integration stays off.
9. `Main` only extends `JavaPlugin` and delegates to `ModRuntime`.
10. Every concrete `ModCommand`, `ModEventHandler`, `FeatureModule`, `ModTask` and `ModPlatform` has
    `@AutoService(<interface>)` and exactly one public constructor. Without the annotation a class
    would silently never register.
11. Expected failures are returned as `Result`, never thrown. Errors carry a stable code such as
    `ControlPanel.PlayerNotFound`.
12. Classes are `final` (or records/enums/interfaces); no mutable static state. The single exception
    is the `ControlCoreApiProvider` holder, which uses a `final AtomicReference`.

**Allowed exception.** Code that must run every tick on the world thread (ECS systems, packet
handlers) may live in `platforms/hytale/` and call our services. List each one here:

- *(none yet)*

## Lifecycle

```
Main(init)  → new ModRuntime(new HytalePlatform(this), classLoader)
setup()     → platform binds ports; event bus created
              → every ModCommand registered, every ModEventHandler subscribed
              → every enabled FeatureModule.setup(ctx)
              → Hytale events connected; ControlCoreApi published
start()     → each ModPlatform whose mod is loaded binds its capabilities (isolated)
              → FeatureModule.start(ctx)
              → every ModTask scheduled
shutdown()  → API withdrawn, tasks cancelled (runOnShutdown tasks run once), modules shut down,
              mod platforms released, subscriptions closed
```

If any component cannot be built, `setup()` logs every failure with its dependency path and fails,
so Hytale marks the plugin as failed instead of running half-configured.

## Discovery and injection

- `@AutoService` (Google AutoService) writes `META-INF/services/<interface>` at compile time.
- `ComponentIndex` reads those files and loads the listed classes without instantiating them. It
  ignores entries from other mods sharing the class path, by checking that the class was defined by
  our class loader. It reads the files itself rather than using `ServiceLoader`, because
  `ServiceLoader` insists on a public no-arg constructor.
- `Injector` builds classes through their single public constructor:
  - ports bound by the platform are returned as is;
  - an interface with exactly one `@AutoService` provider resolves to that provider (that is how
    `ControlPanelApi` resolves to `ControlPanelService`);
  - our concrete classes are created once (singletons).
- `ModRuntimeTest` builds every discovered component against fake ports, so a missing dependency
  fails `./gradlew build`, not the server.

## Errors

`Result<T>` is either `Success(value)` or `Failure(Error)`. `Error(code, description, type)` uses
`ErrorType` (`VALIDATION`, `NOT_FOUND`, `CONFLICT`, `FORBIDDEN`, `UNAVAILABLE`, `FAILURE`). The Hytale
command bridge turns a failure into a red chat message with the description. Exceptions are for
programmer errors (`Guard`, `DomainException`).

## Data folder in development

`runServer` links `run/mods/<Group>_<Name>`, which is also the plugin's data folder, to
`src/main/resources`. Runtime files (`Settings.json`, `players.json`) therefore appear there while
developing. They are git-ignored and excluded from the jar (`build.gradle.kts`).
