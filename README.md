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

**Control panel** (built-in, native Hytale UI). `/open-admin-config` opens it for admins
(`<base permission>.admin` or `.settings`; operators have both) and for players an admin or mod lets see some
settings (they see only those). `/prefs` opens it for any player, on their own tabs. Open panels update when
a value changes elsewhere. Tabs:

- **Server Configs**: every key of the server's `config.json`, read from Hytale's own config definition. Keys the
  server uses while running apply at once, others are saved and apply after a restart (a reminder lists them),
  internal ones (password, storage) are read-only. A **Worlds** switch shows each world's settings (PvP, fall
  damage, NPC spawning, ticking, TPS, game mode, speed and jump) with a live status line.
- **Mod Settings** and **Player Prefs**: every registered setting, with the editor its type needs: text field,
  check box, dropdown, slider plus field for numbers with a range, colour picker, item list. A text setting can
  show its colour in the same row.
- **Keymaps**: the server's default keys (admins, with a form for their own command binds) and your own keys,
  picked from dropdowns that list only keys the server can detect.
  Players with the debug permission also get buttons for the input probe and the key test.
- **Mods**: every mod and asset pack with its logo, version, authors and state; buttons jump to its settings and
  keys. Admins turn mods on or off from the next start and read their config files.
- **Permissions** (admins): who may see and who may change each setting, config key, category or mod, with the
  rule that decides it today. The same rules as `/cc-perm`.
- **History** (admins): every server-wide change with who, when and from where, and **Roll back** to undo one.
  The same as `/cc-history` and `/cc-rollback`. Its **Snapshots** view saves the whole setup (or parts of it) under a
  name, shows what restoring would change, and restores it; snapshot files can be copied between servers. Its
  **Profiles** view switches between whole setups (built in: Vanilla, PvP, Creative / Build, Event / Lobby, or any
  snapshot). Old changes and snapshots can be cleared.
- **Health** (admins): versions, uptime, memory, CPU, every world's TPS and tick time, failed mods and recent errors,
  and a shareable report without secrets (`/cc-diagnostics`).
- Every editing tab has **Restore defaults** for the rows shown.
- **Automation** (admins): rules that run on their own (when a player joins, every X minutes, at a time, when TPS
  drops, ...; if a permission, world, player count or time matches; then message, show a title, run a command, switch
  profile or change a setting).

Every row shows its state (changed, default, not saved, error, needs a restart, locked); a Legend counts and
filters them, errors appear under their row and Undo puts back a change. Every list has a search box with a field
filter and equals / starts with / contains / ends with (for example `mod:controlcore`, `key:ctrl+f`,
`is:changed`).

**For server owners and players**

- **Translations**: everything ControlCore shows follows each player's game language (English, Brazilian
  Portuguese, Spanish), with English as fallback. See [docs/api.md](docs/api.md#translations-api-150).
- **Settings commands**: admins use `/cc-settings`, `/cc-get`, `/cc-set`, `/cc-reset` (also on `config.json`
  keys such as `server:motd`); players use `/cc-prefs`,
  `/cc-pref`, `/cc-pref-reset`. `/cc-config <mod>` opens the panel on one mod's settings.
- **Permissions per setting**: who may see and who may change each setting, category or mod is set by mods and
  overridden by admins in the Permissions tab or with `/cc-perm` (`/cc-perms`, `/cc-perm-check <id>`).
- **Key binds**: players rebind key actions in Keymaps or with `/cc-keys`, `/cc-bind`, `/cc-bind-reset`; admins
  set server keys with `/cc-bind-server` and create command binds without a mod (`/cc-keybind-add`,
  `/cc-keybind-add-server`, `/cc-keybind-list`, `/cc-keybind-remove`). Hytale only reports some keys to servers
  (Q, E, R, F, O, 1-9, mouse buttons, Alt tap, with Ctrl/Alt/Shift/Space held); with an empty hand ControlCore
  also makes right click, abilities and dodge reach the server (`controlcore:empty-hand-keys`).
- **HUD**: players hide or show parts of the game's HUD in `/prefs`, admins set rules per part, and a bindable
  key hides the whole HUD.
- **Mods**: `/cc-mods` lists the server's mods and whether they run.
- **CustomInventory**: with [CustomInventory](https://github.com/SupremoSama/Hytale-CustomInventory) installed,
  its inventory screen (Adventure mode, Tab) has a **ControlCore** button that opens the panel.

**For mod authors** (`ControlCoreApi`, see [docs/api.md](docs/api.md)): register typed settings (server-wide or
per player) and their permissions, key actions (`KeyAction.of(modId, action)`), HUD elements and translated
names. ControlCore stores them, shows them in the panel and reports changes.

**ControlCore's own settings**: `controlcore:control-panel-enabled`, `controlcore:welcome-message` with
`controlcore:welcome-message-color`, `controlcore:empty-hand-keys`, `controlcore:mods-hidden`, the HUD rules,
and per player `controlcore:show-welcome-message`, `controlcore:panel-start-tab` and the HUD parts.
`Settings.json` in the mod's data folder holds `ControlPanelEnabled` (default `true`), the default of
`controlcore:control-panel-enabled`; changed values live in `SettingValues.json`.

Built on Hytale server **0.7.5** (runs on `>=0.7.2 <0.8.0`), Java 25, Gradle 9.8.1 and
[`com.azuredoom.hytale-tools`](https://github.com/AzureDoom/Hytale-Gradle-Plugin).

## Installing

Download `ControlCore-<version>.jar` from the
[GitHub releases](https://github.com/OFA-Tech/ControlCore-HytaleMod/releases) (or CurseForge) and put it in the
server's `mods/` folder. 0.x is a beta: back up `mods/OFATech_ControlCore/` before updating. It needs no other
mod.

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
├── application/         features (settings, keys, control panel, HUD, mods), the runtime
└── infrastructure/
    ├── system/          JDK implementations (clock)
    ├── persistence/     JSON files (settings, player data, key binds, permissions)
    └── platforms/
        └── hytale/      Hytale → our ports, incl. the control panel (ui/)
src/main/resources/Common/UI/Custom/Pages/ControlCore/   ControlPanel.ui and its row documents
src/main/resources/Server/Languages/<locale>/controlcore.lang   translations
```

Your features never import Hytale or another mod; commands, event handlers, modules and tasks register
themselves with `@AutoService` and get their dependencies through their constructor.

## Documentation

**Using ControlCore** (server owners, players, mod authors): the [wiki](https://github.com/OFA-Tech/ControlCore-HytaleMod/wiki).
Its pages live in [wiki/](wiki) and are published to the GitHub wiki on every push to `main`; edit them there.

**Working on ControlCore:**

| Document | Contents |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Layers, rules, lifecycle, package map |
| [docs/components.md](docs/components.md) | Writing commands, event handlers, modules, tasks; injection |
| [docs/platforms.md](docs/platforms.md) | Translating Hytale features; integrating other mods |
| [docs/api.md](docs/api.md) | Exact API reference for other mods; publishing |
| [docs/development.md](docs/development.md) | Running, debugging, custom UI gotchas, known limits |

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
