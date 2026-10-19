# Development notes

Practical knowledge for working on ControlCore: how to run it, where things break, and what is next.
Architecture and rules are in [architecture.md](architecture.md); Hytale and mod translations in
[platforms.md](platforms.md).

## Environment

- JDK 25 (`JAVA_HOME` must point to it; Gradle uses `JAVA_HOME`, not the first `java` on `PATH`).
- Hytale server **0.7.2**, `com.azuredoom.hytale-tools` 1.0.51, Gradle 9.8.1.
- Strict build, as CI runs it: `./gradlew build -Pstrict` (tests, ArchUnit rules, warnings as errors).
  `-Werror` is only on with `-Pstrict`, because incremental compiles re-read `@AutoService` classes from
  bytecode and lose `@SuppressWarnings("rawtypes")`.

## Running the dev server

1. `./gradlew runServer`. The first run asks for a Hytale sign-in (device code in the console) to
   download assets, or pass `-PhytaleHomeOverride=%APPDATA%\Hytale\install\release\package\game\latest\Assets.zip`.
2. In the server console: `/auth login browser` (saved, needed once) so clients can join.
3. Join `localhost`, then in the console `/op add <name>`, then `/open-admin-config` in game.

Things to know:

- Only one dev server can run at a time (port 5520). `prepareRunServer` is untracked, so leftover
  `run/logs/*.lck` files from a crashed server no longer break `runServer`.
- The console reads commands from stdin without a leading `/` (`cc-settings`, `stop`). To script it,
  start `gradlew runServer` with redirected stdin and write lines to it (a small PowerShell wrapper with
  `System.Diagnostics.Process` + `RedirectStandardInput` works); `stop` shuts the server down cleanly.

- `runServer` links `run/mods/OFATech_ControlCore` (the plugin's data folder) to `src/main/resources`.
  `Settings.json`, `Settings.json.bak` and `players.json` therefore appear there; they are git-ignored
  and excluded from the jar.
- `runServer` loads ControlCore from the class path, not from a jar. Another plugin whose classes we
  call must be a `vineImplementation` dependency, not `vineMod` (see [platforms.md](platforms.md)).
- Only one dev server can run at a time: a second one fails in `prepareRunServer` on a locked
  `run/logs/*.lck` file. Stop the running server first (stale `.lck` files can be deleted once no
  server runs).
- Lots of `WARN`/`SEVERE` lines from Hytale's own assets (block sets, NPC animations, `Reallocate`)
  are normal on 0.7.2.

## Debugging

- **Server log:** the `runServer` console, and `run/logs/`.
- **Client log:** `%APPDATA%\Hytale\UserData\Logs\<date>_client.log`. When the server only says
  `left with reason: Crash`, the real reason (usually a custom UI error) is in the client log.
- **Hytale API:** no sources are published. Inspect the server jar
  (`~/.gradle/caches/modules-2/files-2.1/com.hypixel.hytale/Server/0.7.2/.../Server-0.7.2.jar`) with
  `javap -p` / `javap -c -p`, or run `./gradlew setupHytaleDev` for decompiled sources. Vanilla UI
  layouts to copy from are in the assets zip under `Common/UI/Custom/Pages/` (shared templates and
  styles in `Common/UI/Custom/Common.ui`).

## Custom UI (the built-in control panel)

- Code: `infrastructure/platforms/hytale/ui/` (`HytaleControlPanelView`, `ControlPanelPage`), layout:
  `src/main/resources/Common/UI/Custom/Pages/ControlCore/ControlPanel.ui`, loaded as
  `Pages/ControlCore/ControlPanel.ui`.
- `build()` appends the layout, fills values with `commands.set("#Id.Text", ...)` and binds events
  with `events.addEventBinding(Activating, "#ButtonId", EventData.of(KEY, value))`.
- **EventData keys:** a key starting with `@` is a UI property reference that the client reads
  (`append("@Name", "#NameField.Value")`). A constant under an `@` key disconnects the client
  ("Could not gather property value for CustomUI event binding"). Constants use plain keys
  (`Action`), and the page's event `BuilderCodec` must use the same key names.
- **Updates:** change an open page with `sendUpdate(new UICommandBuilder()..., false)`; toggle groups
  with `#Group.Visible` and buttons with `#Button.Disabled`.
- **Tabs:** `TabNavigation` only supports icon tabs, so the panel uses text buttons
  (`$C.@SmallSecondaryTextButton`) that show one `#...Tab` group and disable the selected button.
- Open pages on the world thread: `world.execute(() -> player.getPageManager().openCustomPage(ref, store, page))`.
- **Lists built at runtime:** `commands.append("#List", "Pages/ControlCore/SettingTextRow.ui")` appends a
  whole document as a child of `#List` (a `Group` with `LayoutMode: TopScrolling` scrolls). The n-th
  appended child is `#List[n]`, and its descendants are `#List[n] #Input` (space = descendant). The
  settings tabs do this, one row document per setting (`Setting{Text,Toggle,Choice}Row.ui`, headings
  `SettingCategory.ui`); the plain-Java `SettingsSection` decides the rows and actions, `ControlPanelPage`
  draws them. Vanilla does the same in `PluginListPage` and `PointInspectorPage`.
- **Reading inputs:** bind with `EventData` that mixes constants and `@` keys, e.g.
  `Action=Save, Id=<id>, @Value=#List[2] #Input.Value`. The codec type must match the element:
  `TextField`/`DropdownBox` → `Codec.STRING`, `CheckBox` → `Codec.BOOLEAN`, `NumberField` → `Codec.DOUBLE`
  (see `LaunchPad$LaunchPadSettingsPage`). Check boxes and dropdowns bind `ValueChanged`; buttons
  `Activating`. Don't write a value back into the element that just changed: if the client echoed it
  as another `ValueChanged`, it would loop.
- **Dropdown entries:** `commands.set("#X.Entries", List<DropdownEntryInfo>)` with
  `new DropdownEntryInfo(LocalizableString.fromString(label), value)`, then `#X.Value`. Text fields take
  `#X.PlaceholderText`.

## HyUI

HyUI 0.9.8 (latest as of 2026-10) is not compatible with Hytale 0.7: it calls
`PageManager.updateCustomPage`, renamed to `updateLegacyCustomPage` in 0.7, and the
`NoSuchMethodError` crashes the world on the first page update. `HyUiBridge` refuses to bind in that
case and the built-in panel is used. HyUI is only `compileOnly` for now; see
[platforms.md](platforms.md) for how to bring it back once a compatible version exists.

## Current state and next steps

Done (merged in PR #1): template structure, `/open-admin-config` with the
`<base>.admin` permission, built-in panel with three tabs, `ControlCoreApi` for other mods.

Done: settings registry (`ControlCoreApi.settings()`, `/cc-*` commands, `SettingValues.json`), verified
on the dev server from the console (list/get/set/reset, invalid input, persistence across a restart).

Done: **Mod Settings** and **Player Prefs** tabs list and edit the registered settings (text fields with
Save, check boxes and dropdowns save immediately, Default resets); ControlCore's own settings: panel
switch, welcome message, show-welcome and panel start tab.

Done: `/prefs` opens the panel on the Player Prefs tab only, for any player (the page builds only the
sections in the `ControlPanelRequest` and ignores events for the others). Open panels follow setting
changes made elsewhere (`SettingsEditor.onAnyChange`): the page updates on the player's world thread and
skips values it already shows, so its own saves don't echo.

Open:

- Settings registered while a panel is open appear after reopening it.
- **Server Configs tab:** values are read-only. Editing needs a write method on the
  `ServerConfiguration` port (Hytale's `HytaleServerConfig` has setters) plus input fields and a Save
  button whose event reads the fields with `@` keys.
- **Keymaps tab:** preview only; the Rebind and Server Default buttons are disabled. Needs a design for
  how key bindings are captured and applied.
- `control-panel-hyui.html` is kept for when HyUI supports 0.7; it is a separate layout from
  `ControlPanel.ui`.
- Merge `restructure-template` into `main` once happy.
