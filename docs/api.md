# Public API

Other mods use ControlCore through the interfaces in `net.ofatech.controlcore.api`. The services
implement them directly; there are no copies or adapters. Signatures use plain Java and `domain`
types (`Result`, `Error`, `PlayerId`, `PlayerInfo`, `ControlPanelRequest`, events), never Hytale
types, so the API survives Hytale updates.

| Type | Purpose |
|---|---|
| `ControlCoreApi` | entry point: `get()`, `find()`, `apiVersion()`, `controlPanel()`, `events()`, `settings()`, `keymaps()` (1.3.0); setting permissions (1.4.0); translations (1.5.0) |
| `ControlPanelApi` | `isAvailable()`, `open(player, request)` |
| `ControlCoreEvents` | `subscribe(eventType, listener)` returning a closable `Subscription` |
| `SettingsApi` (1.1.0) | shared settings registry: `register(definition)`, `definitions()`, `snapshot(scope, player)`, `read` / `write` / `reset` by id, `onAnyChange(listener)` (1.2.0) |
| `Setting<T>` | handle returned by `register`: `get()`, `get(player)`, `set(..)`, `reset(..)`, `onChange(listener)` |
| `KeymapsApi` (1.3.0) | key actions: `register(KeyAction)`, `unregister(id)`, `find(id)`, `actions()`, `keysFor(id, player)`, `serverKeys(id)`, `bind` / `resetBind` (a player's own), `bindServer` / `resetServerBind` |

`ControlPanelApi.open` does not check permissions; the calling mod decides who may open the panel. The
request picks the tabs: `ControlPanelRequest.ALL` is the admin panel, `ControlPanelRequest.PLAYER_PREFERENCES`
(1.2.0) only the player's own preferences (and, since 1.3.0, their key bindings), safe for anyone and available even when the admin panel is
disabled. It returns errors such as `ControlPanel.Disabled`, `ControlPanel.PlayerNotFound` and
`ControlPanel.NothingToShow`.
Translated game events (`PlayerJoined`, `PlayerLeft`), `SettingChanged` and `ServerConfigChanged`
(1.2.0; a server config field such as `server:motd` changed in the control panel) are delivered on the
events bus.

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
  `Settings.WrongScope`, `Settings.Conflict`, `Settings.Forbidden` (may see but not change).
- Keep the handle and call `get()` when you need the value; it is a map lookup. A stored value that no
  longer fits the type (say you narrowed a range) falls back to the default with one warning in the log.
- Server values are saved immediately to `SettingValues.json`; player values go to `players.json`
  (key `setting:<id>`), flushed every 5 minutes and at shutdown.

### Who may see and change a setting (API 1.4.0)

```java
SettingDefinition.server("homes:max-homes", SettingType.integer(1, 50), 3)
        .viewPermission(SettingPermission.EVERYONE)   // everyone sees it read-only
        .editPermission("homes.moderator");           // moderators may change it
api.settings().setCategoryPermission("Homes", SettingPermission.of("homes.staff")); // a whole category
```

- **See** and **change** are separate. Players who may only see a setting get it read-only (panel and
  `/cc-get`); without either it is hidden (panel, `/cc-settings`) and reported as unknown. Changing
  implies seeing. `.permission(node)` sets both.
- **Defaults:** server settings need `<base>.settings`; player preferences are everyone's own.
  ControlCore admins (`<base>.settings` or `<base>.admin`) and the console may always see and change
  everything.
- **Admins override** mods' rules, per setting, category or mod, with `/cc-perm` (saved in
  `SettingPermissions.json`). For each side the first rule that has one wins: admin rule for the setting
  -> for its category -> for its mod -> the mod's rule on the setting -> the mod's category rule ->
  default. `/cc-perm-check <id>` shows which rule decides.
- The server's own config (`server:*` in Server Configs) follows the same rules, for example
  `/cc-perm mod:server view everyone` or `/cc-perm server:motd edit staff.motd`.
- `/open-admin-config` opens for admins and for anyone who may see at least one server setting or config
  value; they only see those. Server default keys and admins' own key binds stay admin-only.

### Commands

| Command | Who | Does |
|---|---|---|
| `/cc-settings` | anyone (shows what you may see) | list settings with their value (`(default)` if not changed) |
| `/cc-get <id>` | who may see it | name, description, value, default, type and scope |
| `/cc-set <id> <value>` | who may change it | change a server setting (the value may contain spaces) |
| `/cc-reset <id>` | who may change it | return a server setting to its default |
| `/cc-perm <target> <view\|edit\|both> <node\|everyone\|default>` | `<base>.settings` | who may see or change a setting (`homes:max-homes`), a category (`category:Homes`) or a mod (`mod:homes`); `default` removes the rule |
| `/cc-perms` | `<base>.settings` | list admins' rules |
| `/cc-perm-check <id>` | `<base>.settings` | who may see and change one setting, and which rule decides it |
| `/prefs` | any player | open the panel on your preferences (works even when the admin panel is disabled) |
| `/cc-prefs` | any player | list your preferences |
| `/cc-pref <id> <value>` | any player (unless a rule limits it) | change one of your preferences |
| `/cc-pref-reset <id>` | any player | return a preference to the default |

`<base>` is ControlCore's base permission; operators have every node.

## Translations (API 1.5.0)

ControlCore shows its panel, command replies, errors and command help in each player's **game language**
(`en-US`, `pt-BR` and `es-ES` are included). A text missing in that language, or a language ControlCore
lacks, falls back to English; the console reads English. Players can change their game language at any
time; ControlCore reads it every time it shows text.

Mods can have their setting and key texts translated too. Pass a `Text.key(key, englishFallback)` instead
of a plain string and ship the keys in your own language files:

```java
SettingDefinition.server("homes:max-homes", SettingType.integer(1, 50), 3)
        .named(Text.key("homes.settings.max_homes", "Max homes"))
        .describedAs(Text.key("homes.settings.max_homes.desc", "How many homes each player may set"))
        .inCategory(Text.key("homes.settings.category", "Homes"));
SettingDefinition.player("homes:sort", SettingType.choice("Name", "Distance"), "Name")
        .optionsKey("homes.sort");                      // option "Distance" -> homes.sort.distance
KeyAction.of("homes", "go-home").displayName(Text.key("homes.keys.go_home", "Go home"));
```

```
# src/main/resources/Server/Languages/pt-BR/homes.lang   (keys are read as homes.<key>)
settings.max_homes = Máximo de casas
settings.max_homes.desc = Quantas casas cada jogador pode definir
sort.distance = Distância
keys.go_home = Ir para casa
```

- Plain strings keep working and are shown as they are.
- Only the shown text is translated: ids, stored values and the search's own words stay the same. Search
  also matches the translated names.
- Placeholders are written `{name}` and filled with `Text.key(...).with("name", value)`.
- Errors take a `Text` too, so players see them in their language:
  `Error.notFound("Homes.Unknown", Text.key("homes.errors.unknown", "There is no home {name}.").with("name", name))`.
- The server config keys' names and section headings are translated; Hytale's own descriptions stay in
  English.

## Key actions

A mod registers what a key combination runs; ControlCore detects the keys while players play, lets every
player rebind them (Keymaps tab, `/cc-bind`) and runs the command as the player, or the mod's code.

```java
ControlCoreApi.find().ifPresent(api -> api.keymaps().register(
        KeyAction.of("homes", "go-home")               // mod id, action
                .displayName("Go home")
                .describedAs("Teleports you to your home.")
                .command("home {player}")              // run as the player; {player} and {uuid} filled in
                .defaultKeys(Key.CTRL, Key.NUM_3)      // held together
                .alternativeKeys(Key.MOUSE_MIDDLE)     // optional: another combination
                .onPress(player -> log("home!"))));    // optional: your own code (on a virtual thread)

// run as the server console; only players with the permission can see or use it
api.keymaps().register(KeyAction.of("admintools", "announce")
        .displayName("Announce restart")
        .command("say Restart in 5 minutes ({player})")
        .runAsServer()
        .permission("admintools.announce")             // optional; default: <base>.keys.server
        .defaultKeys(Key.CTRL, Key.O));
```

- **Inputs** are `Key`s (every keyboard key and mouse button) and `GameAction`s (`CROUCH`, `SPRINT`,
  `WALK_FORWARD`, `ABILITY_1`, `HOTBAR_3`, ...). Players write them as text: `ctrl+f`, `alt+3 | mouse_middle`,
  `crouch+interact`, `none`.
- **What the server can detect.** Hytale clients never send raw keys, only actions. Detected during play:
  Q, E, R, F, O, 1-9, left/right/middle mouse (each as one press), a short tap of Alt while standing still,
  and Ctrl, Alt, Shift or Space held together with them (or on their own, firing when they go down).
  Q, E and right click only arrive when the held item has an ability or secondary action for them. Other
  keys (H, Caps Lock, F-keys, WASD directions, ...) are accepted and saved but shown as "not available"
  until Hytale reports them; the server cannot read a player's client keybinds either, so `Q` means
  "ability 1".
- **Precedence:** the mod's default keys apply until an admin changes the server's keys for everyone
  (`/cc-bind-server`, or the admin panel's Keymaps tab on **Server defaults**); a player's own keys
  (`/cc-bind`, Keymaps on **My keys**, or `/prefs`) only change them for that player and win over both.
  Default on a player's row goes back to the server's keys; on a server row, to the mod's. When
  several actions match, the one with the most held keys wins (Ctrl+F over F); equal ones all run.
- **Game actions:** the pressed key's own game action (ability, use, attack, game mode switch) is cancelled
  unless the action calls `keepGameAction()`. Hotbar changes are never cancelled.
- Commands run on a virtual thread through Hytale's command manager, with the player's permissions;
  `onPress` code also runs on a virtual thread, so hop to the world thread for world changes. A 250 ms
  cooldown stops repeats.
- Server keys are saved to `KeyBinds.json`; players' keys go to `players.json` (key `keybind:<id>`).
- **Who can use it:** `.permission(node)` (a full node) limits an action to players who have it; they are
  the only ones who see it in Keymaps and `/cc-keys`, and its keys do nothing for others. `.runAsServer()`
  runs the command as the console with every permission; without its own `.permission(..)` it needs
  ControlCore's `<base>.keys.server` node (operators have it). Each server-run press is logged.
- **Admins' own binds** (no mod needed): `/cc-keybind-add`, `/cc-keybind-add-server` or the form in the admin
  panel's Keymaps tab create `custom:<name>` actions (adding a name again replaces it; the panel's rows have
  Edit and Remove buttons; quotes around the command are dropped), saved in `CustomKeyBinds.json`; mods cannot register
  in the `custom` namespace (`Keys.ReservedId`).
- **Errors:** `Keys.NothingToRun`, `Keys.UnknownAction`, `Keys.InvalidCombo`, `Keys.InvalidName`,
  `Keys.ReservedId`.

### Key commands

| Command | Who | Does |
|---|---|---|
| `/cc-keys` | anyone | list key actions with your keys and whether they can be detected |
| `/cc-bind <id> <keys>` | any player | change your keys, for example `/cc-bind homes:go-home alt+3` |
| `/cc-bind-reset <id>` | any player | go back to the server's keys |
| `/cc-bind-server <id> <keys>` | `<base>.settings` | change the server's keys for everyone without their own |
| `/cc-bind-server-reset <id>` | `<base>.settings` | go back to the mod's default keys |
| `/cc-keybind-add <name> <keys> <command>` | `<base>.settings` | create `custom:<name>`, run as the player who presses it |
| `/cc-keybind-add-server <name> <keys> <command>` | `<base>.settings` | create `custom:<name>`, run as the server (usable with `<base>.keys.server`) |
| `/cc-keybind-remove <name>` | `<base>.settings` | delete a bind created by an admin |
| `/cc-keybind-list` | `<base>.settings` | list the binds admins created, with their commands |
| `/cc-input-probe` | `<base>.debug` | debug tool: show in chat every key action your client sends (run again to stop) |
| `/cc-key-probe input` | `<base>.debug` | debug tool: a test screen that shows the code of every key pressed while it is open |

With an empty hand the client only reports keys the hand has an interaction for. The server setting
`controlcore:empty-hand-keys` (on by default) gives the empty hand, plain blocks and plain items a do-nothing
interaction for right click, abilities 1, 2 and 4 and dodge, so those keys can be bound too. Weapons and tools keep
their own abilities. Turn it off if another mod changes the empty hand (`Server/Item/Unarmed/Interactions`).

ControlCore registers `controlcore:open-preferences` (runs `/prefs`, default `CTRL + F`).

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

`.github/workflows/release.yml.disabled` is turned off for now. Once renamed to `release.yml` and uncommented, a `vX.Y.Z` tag runs
`./gradlew publish` to GitHub Packages and attaches the jars to a GitHub Release.
