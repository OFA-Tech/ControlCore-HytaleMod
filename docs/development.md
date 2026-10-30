# Development notes

Practical knowledge for working on ControlCore: how to run it, where things break, and what is next.
Architecture and rules are in [architecture.md](architecture.md); Hytale and mod translations in
[platforms.md](platforms.md).

## Environment

- JDK 25 (`JAVA_HOME` must point to it; Gradle uses `JAVA_HOME`, not the first `java` on `PATH`).
- Hytale server **0.7.5** (the manifest still allows 0.7.2 to 0.7.4), `com.azuredoom.hytale-tools` 1.0.51, Gradle 9.8.1.
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
  are normal on 0.7.x.

## Debugging

- **Server log:** the `runServer` console, and `run/logs/`.
- **Client log:** `%APPDATA%\Hytale\UserData\Logs\<date>_client.log`. When the server only says
  `left with reason: Crash`, the real reason (usually a custom UI error) is in the client log.
- **Hytale API:** no sources are published. Inspect the server jar
  (`~/.gradle/caches/modules-2/files-2.1/com.hypixel.hytale/Server/0.7.5/.../Server-0.7.5.jar`) with
  `javap -p` / `javap -c -p`, or run `./gradlew setupHytaleDev` for decompiled sources. Vanilla UI
  layouts to copy from are in the assets zip under `Common/UI/Custom/Pages/` (shared templates and
  styles in `Common/UI/Custom/Common.ui`).

## Custom UI (the built-in control panel)

- Code: `infrastructure/platforms/hytale/ui/` (`HytaleControlPanelView`, `ControlPanelPage` and its helpers,
  see [platforms.md](platforms.md#the-control-panel)), layout:
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
  settings tabs do this, one row document per setting (`Setting*Row.ui`, headings `SettingCategory.ui`); the
  plain-Java `SettingsSection` decides the rows and actions, `SettingRows` draws them. Vanilla does the same in `PluginListPage` and `PointInspectorPage`.
- **Reading inputs:** bind with `EventData` that mixes constants and `@` keys, e.g.
  `Action=Save, Id=<id>, @Value=#List[2] #Input.Value`. The codec type must match the element:
  `TextField`/`DropdownBox` → `Codec.STRING`, `CheckBox` → `Codec.BOOLEAN`, `NumberField` → `Codec.DOUBLE`
  (see `LaunchPad$LaunchPadSettingsPage`). Check boxes and dropdowns bind `ValueChanged`; buttons
  `Activating`. Don't write a value back into the element that just changed: if the client echoed it
  as another `ValueChanged`, it would loop.
- **Dropdown entries:** `commands.set("#X.Entries", List<DropdownEntryInfo>)` with
  `new DropdownEntryInfo(LocalizableString.fromString(label), value)`, then `#X.Value`. Text fields take
  `#X.PlaceholderText`.

- **Colour pickers** (`ColorPickerDropdownBox`) have no `Value`: read and set their `Color` (`#rrggbb`).
  Setting or gathering `#Picker.Value` disconnects the client.
- **Images set from the server** (`#X.Background`) are paths relative to `Common/UI/Custom/`, unlike paths
  inside `.ui` files, which are relative to that file.
- **Translation keys in `.ui` files** (`%key;`) may only contain letters, digits and dots; an underscore makes
  the client fail to load and the player cannot join.

## Known limits

- Settings registered while a panel is open appear after reopening it.
- **Server Configs:** the password and structured keys are read-only (edit them in `config.json` while the
  server is stopped). Changes made with Hytale's own `/maxplayers` or `/maxviewradius` are not pushed to open
  panels (Hytale has no event for them); they show after reopening. Keys without a default (backup directory,
  update patchline) cannot be reset from the panel.
- **Keys:** servers only receive game actions, not raw key presses, so keys like H, Tab or Caps Lock cannot be
  bound (they are kept and marked not available).
- **HUD:** parts the game switches by itself (for example on a game mode change) may come back until the
  player rejoins.
- **Mods tab:** turning a mod on or off applies after a restart; mod config files are shown read-only.
- **Permissions:** not yet tested in game with a non-admin account.
