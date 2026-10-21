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

- `/open-admin-config` opens the admin control panel for admins (`<base permission>.admin` or `.settings`;
  operators have both) and for players an admin or mod lets see some settings (they see only those). The built-in panel (native Hytale UI) has four tabs: **Server Configs** lists every
  key of the server's `config.json`, read from Hytale's own config definition: keys the server uses while
  running apply at once, others are saved and apply after a restart, and internal ones (password, storage,
  mods) are shown read-only;
  **Mod Settings** lists and edits every registered server setting; **Player Prefs** edits your own
  preferences; **Keymaps** edits the server's default keys (admins) and your own key bindings. Every list has a search box
  (for example `mod:controlcore`, `key:ctrl+f`, `is:changed`). Any player can open their own preferences and
  key bindings with `/prefs`. Open panels update when a setting changes elsewhere. When a compatible
  [HyUI](https://www.curseforge.com/hytale/mods/hyui) is installed, its HTML panel is used instead.
- **Translations**: everything ControlCore shows follows each player's game language (English, Brazilian
  Portuguese, Spanish), with English as fallback; mods can translate their own setting and key
  names. See [docs/api.md](docs/api.md#translations-api-150).
- **Settings registry**: mods register typed settings (server-wide or per-player preferences) through
  `ControlCoreApi.settings()`; ControlCore validates and stores them and reports changes. Admins use
  `/cc-settings`, `/cc-get`, `/cc-set`, `/cc-reset`; players use `/cc-prefs`, `/cc-pref`, `/cc-pref-reset`.
  **Who may see and who may change** each setting, category or mod is set by mods and overridden by admins
  with `/cc-perm` (`/cc-perms`, `/cc-perm-check <id>`). See [docs/api.md](docs/api.md#settings-registry).
- **Key actions**: mods register a command or code with default keys through `ControlCoreApi.keymaps()`
  (`KeyAction.of(modId, action).displayName(..).command(..).defaultKeys(Key.CTRL, Key.F)`); players
  rebind them in Keymaps or with `/cc-keys`, `/cc-bind`, `/cc-bind-reset`; admins set server keys with
  `/cc-bind-server`. Actions can run as the server (`.runAsServer()`, limited by permission), and admins
  can create their own binds without a mod (`/cc-keybind-add`, `/cc-keybind-add-server`, or the admin
  panel's Keymaps tab). Hytale only reports some keys to servers (Q, E, R, F, O, 1-9, mouse buttons, Alt tap,
  with Ctrl/Alt/Shift/Space held); others are kept and marked not available. See
  [docs/api.md](docs/api.md#key-actions).
- ControlCore's own settings: `controlcore:control-panel-enabled`, `controlcore:welcome-message` (sent to
  joining players, `{player}` is their name), and per-player `controlcore:show-welcome-message` and
  `controlcore:panel-start-tab`.
- `Settings.json` in the mod's data folder: `ControlPanelEnabled` (default `true`), the default of the
  `controlcore:control-panel-enabled` setting. Changed values live in `SettingValues.json`.
- Public API for other mods: `ControlCoreApi` (see [docs/api.md](docs/api.md)).

Built on Hytale server **0.7.2** (`>=0.7.2 <0.8.0`), Java 25, Gradle 9.8.1 and
[`com.azuredoom.hytale-tools`](https://github.com/AzureDoom/Hytale-Gradle-Plugin).

## Installing

Download `ControlCore-<version>.jar` from the
[GitHub releases](https://github.com/OFA-Tech/ControlCore-HytaleMod/releases) (or CurseForge) and put it in the
server's `mods/` folder. 0.9.x is a beta: back up `mods/OFATech_ControlCore/` before updating. HyUI (`Ellie:HyUI`) is optional. HyUI
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

1. Set `version` in `gradle.properties` and rename `## Unreleased` in `CHANGELOG.md` to `## X.Y.Z - <date>`
   (keep an empty `## Unreleased` above it). Merge that to `main`.
2. Tag `main` and push the tag: `git tag vX.Y.Z` then `git push origin vX.Y.Z`.
3. `.github/workflows/release.yml` checks the tag matches `version`, builds with `-Pstrict`, publishes
   `net.ofatech:controlcore` to GitHub Packages and creates a GitHub release with the jar and that version's
   changelog section. Versions below 1.0.0 (and `-rc.N` style suffixes) are marked as pre-releases.
4. Upload the same jar to CurseForge with the text in [docs/curseforge.md](docs/curseforge.md).

## License

MIT, see [LICENSE](LICENSE).
