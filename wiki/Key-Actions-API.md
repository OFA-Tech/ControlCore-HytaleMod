# Key actions

A key action is something that runs when a player presses a key combination: a command, your own code, or both.
ControlCore detects the keys during play, lets every player rebind them (Keymaps tab, `/cc-bind`) and lets admins
change the server's keys. Read [Key binds](Key-Binds) first for which keys the server can detect.

```java
KeymapsApi keymaps = ControlCoreApi.get().keymaps();

keymaps.register(KeyAction.of("homes", "go-home")          // id homes:go-home
        .displayName("Go home")
        .describedAs("Teleports you to your home.")
        .command("home {player}")                          // run as the player who pressed it
        .defaultKeys(Key.CTRL, Key.NUM_3)                  // held together
        .alternativeKeys(Key.MOUSE_MIDDLE)                 // another combination for the same action
        .onPress(player -> log("home!")));                 // optional: your own code
```

## Building an action

| Method | Does |
|---|---|
| `KeyAction.of(modId, action)` | the id `modId:action`; same rules as [setting ids](Settings-API#ids) |
| `.displayName(…)`, `.describedAs(…)` | names in Keymaps and `/cc-keys` (plain text or `Text`) |
| `.command("…")` | runs the command as the player; `{player}` and `{uuid}` are filled in; no leading `/` needed |
| `.onPress(player -> …)` | runs your code with the player's `PlayerId` |
| `.defaultKeys(…)` | inputs held together; leave out for no default |
| `.alternativeKeys(…)` | a second combination |
| `.defaultKeys(KeyBinds.parse("ctrl+f \| alt+3").value())` | the same from text |
| `.keepGameAction()` | don't cancel the key's own game action (see below) |
| `.runAsServer()` | run the command as the server console |
| `.permission("node")` | only players with this full node can see and use it |

An action needs `command` or `onPress` (or both); otherwise `register` fails with `Keys.NothingToRun`.

## Inputs

`defaultKeys` and `alternativeKeys` take any mix of:

- **`Key`**: `Key.A` … `Key.Z`, `Key.NUM_0` … `Key.NUM_9`, `Key.CTRL`, `Key.ALT`, `Key.SHIFT`, `Key.SPACE`,
  `Key.MOUSE_LEFT`, `Key.MOUSE_RIGHT`, `Key.MOUSE_MIDDLE`, `Key.F1` …, `Key.TAB`, `Key.CAPS`, …
- **`GameAction`**: `GameAction.CROUCH`, `SPRINT`, `WALK`, `JUMP`, `STAND_STILL`, `INTERACT`, `ABILITY_1` …
  `ABILITY_4`, `DODGE`, `HOTBAR_1` … `HOTBAR_9`, `ATTACK`, `SECONDARY_ACTION`, `PICK_BLOCK`, `SWITCH_GAME_MODE`.

Every `Key` and `GameAction` is accepted, but only the ones Hytale reports can fire (see
[which keys work](Key-Binds#which-keys-work)); the others are saved and shown as "not available". Pick defaults that
work: `Key.CTRL, Key.R` or `Key.ALT, Key.NUM_3` are good choices; `Key.H` is not.

## How it runs

- Commands go through Hytale's command manager with the **player's** permissions, on a virtual thread.
- `onPress` also runs on a virtual thread: hop to the world thread (`world.execute(…)`) before touching the world.
- A 250 ms cooldown stops repeats.
- When several actions match a press, the one with the most held keys wins (Ctrl+F over F); equal ones all run.
- The pressed key's own game action (ability, interact, attack, game mode switch) is cancelled unless you call
  `.keepGameAction()`. Hotbar changes are never cancelled. Server owners can keep the game action for every
  bind with the setting **Bound keys keep their game action** (`controlcore:keys-keep-game-action`, on by default).

## Running as the server

```java
keymaps.register(KeyAction.of("admintools", "announce")
        .displayName("Announce restart")
        .command("say Restart in 5 minutes ({player})")
        .runAsServer()                          // console, every permission
        .permission("admintools.announce")      // optional; default ofatech.controlcore.keys.server
        .defaultKeys(Key.CTRL, Key.O));
```

Only players with the permission see the action and can use it; for others its keys keep their normal behaviour.
Every server-run press is logged.

## Keys of an action

```java
SettingId id = SettingId.of("homes:go-home");
KeyBinds mine = keymaps.keysFor(id, playerId);    // what fires for this player
KeyBinds server = keymaps.serverKeys(id);         // the server's keys
keymaps.bind(id, playerId, "alt+3");              // a player's own keys
keymaps.resetBind(id, playerId);
keymaps.bindServer(id, "ctrl+h");                 // the server's keys
keymaps.resetServerBind(id);
```

The mod's defaults apply until an admin changes the server's keys; a player's own keys win over both.

## Errors

| Code | When |
|---|---|
| `Keys.NothingToRun` | no `command` and no `onPress` |
| `Keys.InvalidCombo` | key text that can't be read |
| `Keys.UnknownAction` | no such action, or the player may not use it |
| `Keys.InvalidName` | a bad name for an admin bind |
| `Keys.ReservedId` | the mod id `custom`, which is reserved for admins' binds |

## Storage

The server's keys go to `KeyBinds.json`; players' keys to `players.json`.
