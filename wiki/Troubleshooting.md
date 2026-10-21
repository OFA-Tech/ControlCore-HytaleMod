# Troubleshooting

## The panel doesn't open

- **"The control panel is disabled"**: `controlcore:control-panel-enabled` is off. Turn it back on from the console:
  `/cc-set controlcore:control-panel-enabled true`. `/prefs` works either way.
- **"You do not have access to the admin panel"**: you need `ofatech.controlcore.settings` (or `.admin`), or a
  [permission rule](Permissions) that lets you see at least one server setting. Operators have everything:
  `/op add <name>` in the console.

## I was disconnected ("Crash") when opening or using the panel

The server only says `left with reason: Crash`. The real reason is in the **game's** log:
`%APPDATA%\Hytale\UserData\Logs\<date>_client.log`; search for `Disconnecting with error`. Please
[open an issue](https://github.com/OFA-Tech/ControlCore-HytaleMod/issues) with that line, your ControlCore version and
what you clicked.

## A key bind does nothing

- Check `/cc-keys`: a combination marked **not available** uses a key Hytale doesn't report (H, Tab, F-keys, WASD, …).
  See [which keys work](Key-Binds#which-keys-work).
- **Q, E or right click with an empty hand:** `controlcore:empty-hand-keys` must be on.
- **With an item in hand:** the item must have an action on that key; weapons and tools use their own abilities.
- **A bind that runs as the server** only works for players with `ofatech.controlcore.keys.server` (or the bind's own
  permission). Others don't see it.
- A command that works when typed but not from a key: check `{player}` is where you expect it, and that the player
  has permission for the command (`/cc-keybind-add` runs it with **their** permissions).

## A Server Configs change didn't apply

Keys marked "applies after a restart" need a restart; the gold line at the top of the tab lists them. Changes made
with Hytale's own `/maxplayers` or `/maxviewradius` show in an open panel only after reopening it.

## A hidden HUD part came back

Some parts are switched by the game itself, for example when your game mode changes. Rejoin and ControlCore hides it
again.

## Another mod changes the empty hand

Turn off `controlcore:empty-hand-keys` (it applies at once, no restart). Q, E and right click with an empty hand then
can't be bound.

## A setting went back to its default

A saved value that no longer fits (for example a mod narrowed a range) falls back to the default, and the server log
has a warning naming the setting.

## Reporting a bug

[Open an issue](https://github.com/OFA-Tech/ControlCore-HytaleMod/issues) with: ControlCore version, Hytale server
version, other mods installed, what you did, and the relevant lines from the server log (and the game log for
disconnects).
