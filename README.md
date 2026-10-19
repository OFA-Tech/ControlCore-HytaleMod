# ControlCore

ControlCore is the **server control and configuration foundation** for OFA-Tech Hytale projects.

Instead of treating configuration as scattered per-mod implementation detail, this project aims to
provide a shared layer for server preferences, administrative controls, configurable actions, and
reusable quality-of-life systems.

## Purpose

ControlCore exists to reduce configuration fragmentation across mods.

When each mod uses different settings formats, command patterns, and defaults, server administration
becomes harder than it needs to be. ControlCore is designed to establish a consistent control surface
so specialized gameplay mods can stay focused on gameplay while reusing a common configuration
philosophy.

## Vision

The long-term vision is a common configuration backbone where compatible mods can:

- Register configurable concepts in a predictable way.
- Expose server-facing and player-facing options consistently.
- Reuse shared defaults, actions, and administrative patterns.
- Integrate with other mods without fragile one-off configuration bridges.

## Core concepts

- **Server control as a platform service**: configuration is treated as part of server identity, not
  isolated side files.
- **Configurable interaction**: commands, actions, preferences, and scheduled behavior remain adjustable.
- **Shared quality-of-life infrastructure**: common mod needs are implemented once and reused.
- **Controlled extensibility**: other mods can depend on the control layer without handing over
  gameplay ownership.

## Current state

- `/open-admin-config` opens the admin control panel. It needs the `<base permission>.admin` node;
  operators have it. The built-in panel (native Hytale UI) has three tabs: **Server Configs** shows the
  current server config (name, MOTD, password set or not, max players, view radius, play timeout);
  **Player Prefs** and **Keymaps** are previews. Editing and rebinding are not wired yet. When a compatible
  [HyUI](https://www.curseforge.com/hytale/mods/hyui) is installed, its HTML panel is used instead.
- `Settings.json` in the mod's data folder: `ControlPanelEnabled` (default `true`).
- Public API for other mods: `ControlCoreApi` (see [docs/api.md](docs/api.md)).

Built on Hytale server **0.7.2** (`>=0.7.2 <0.8.0`), Java 25, Gradle 9.8.1 and
[`com.azuredoom.hytale-tools`](https://github.com/AzureDoom/Hytale-Gradle-Plugin).

## Installing

Put `ControlCore-<version>.jar` in the server's `mods/` folder. HyUI (`Ellie:HyUI`) is optional. HyUI
0.9.8 is not compatible with Hytale 0.7 (its pages crash the world on the first update), so ControlCore
detects that and keeps using the built-in panel.

## Development

```bash
./gradlew build                  # compile, test, architecture rules, jar in build/libs
./gradlew runServer              # local dev server with ControlCore
./gradlew runServer -Ddebug=true -Dhotswap=true   # debugger + hot swap (JetBrains Runtime recommended)
./gradlew setupHytaleDev         # decompiled server sources and assets for your IDE
./gradlew updatePluginManifest   # regenerate src/main/resources/manifest.json from gradle.properties
./gradlew spotlessApply          # format the code
```

First run:

1. `runServer` asks you to sign in to Hytale (device code in the console) to download the server
   assets once; they are cached in `~/.gradle/caches/hytale-assets/`. To use the assets of your
   installed game instead, pass `-PhytaleHomeOverride=<path to Assets.zip>` (on Windows:
   `%APPDATA%\Hytale\install\release\package\game\latest\Assets.zip`).
2. In the server console, run `/auth login browser` so game clients can join. Credentials are saved.
3. Join `localhost`, run `/op add <your name>` in the console, then `/open-admin-config` in game.

## Layout

```
src/main/java/net/ofatech/controlcore/
├── Main.java            entry point Hytale loads; only delegates to ModRuntime
├── api/                 what other mods use (interfaces)
├── domain/              models, Result/Error, events and ports (interfaces for everything external)
├── application/         features (control panel), the runtime
└── infrastructure/
    ├── system/          JDK implementations (clock)
    ├── persistence/     storage (player data as JSON)
    └── platforms/
        ├── hytale/      Hytale → our ports, incl. the built-in panel (ui/)
        └── hyui/        HyUI → our ControlPanelView
src/main/resources/Common/UI/Custom/Pages/   ControlCore/ControlPanel.ui (built-in), control-panel-hyui.html
```

Your features never import Hytale or another mod; commands, event handlers, modules and tasks register
themselves with `@AutoService` and get their dependencies through their constructor.

## Documentation

| Document | Contents |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Layers, rules, lifecycle, package map |
| [docs/components.md](docs/components.md) | Writing commands, event handlers, modules, tasks; injection |
| [docs/platforms.md](docs/platforms.md) | Translating Hytale features; HyUI and other mods |
| [docs/api.md](docs/api.md) | Using ControlCore from another mod; publishing |
| [docs/development.md](docs/development.md) | Running, debugging, custom UI gotchas, current state and next steps |

## Releasing

`.github/workflows/release.yml` is commented out until the first release; uncomment it to enable.
Then push a tag `vX.Y.Z`: it builds with `-Pstrict`, publishes to GitHub Packages and attaches the
jars to a GitHub Release.

## License

MIT, see [LICENSE](LICENSE).
