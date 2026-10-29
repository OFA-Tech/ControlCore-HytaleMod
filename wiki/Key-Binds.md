# Key binds

Mods register **key actions** (something that runs when a key combination is pressed). Admins can also create their
own binds that run any command, without a mod. Every player can change the keys for themselves.

## Which keys work

Hytale clients never send raw key presses to the server, only game actions. So ControlCore can only react to keys
that produce one:

| Works during play | Notes |
|---|---|
| **Q, E, R** (abilities 1-3), **F** (interact), **O** (switch game mode) | |
| **1-9** (hotbar) | the hotbar still changes slot |
| **left, right and middle mouse** | |
| **Ability 4** and **dodge** | on whichever keys your game uses for them |
| a short **Alt** tap while standing still | |
| **Ctrl, Alt, Shift, Space** held together with the keys above | for example `ctrl+r`, `alt+3`, `shift+mouse_middle` |

**Not detectable:** H, Tab, Caps Lock, F1-F12, WASD and other keys the game doesn't turn into an action. Commands
accept and save them; the panel shows such a bind as "Custom" and it does nothing until Hytale reports those keys.

Keys mean the **game action**, not the physical key: `q` means "ability 1". If a player moved ability 1 to another
key in their game settings, that key triggers the bind.

**Empty hand:** normally Q, E and right click only reach the server when the held item has an ability for them.
The setting `controlcore:empty-hand-keys` (on by default) gives the empty hand, plain blocks and plain items a
do-nothing action for right click, abilities 1, 2 and 4 and dodge, so those can be bound too. Weapons and tools keep
their own abilities. Turn it off if another mod changes the empty hand.

## Picking keys in the panel

Keymaps shows two dropdowns per action: a key to **hold** (none, Ctrl, Alt, Shift, Space or standing still) and a
**main key**. Only keys that work are listed, each with its game action, for example "Q · Ability 1". The choice
saves at once. A combination made with commands that the dropdowns can't show (such as Ctrl+Alt+3) appears as
"Custom: ..." and stays until you pick something else; extra alternatives are kept and listed as "Also: ...".

## Detect key

Every key row and the **New bind** form have a **Detect key** button. Press it, then press the key you want (holding
Ctrl, Alt, Shift or Space if you want them). How it works is chosen in **Mod Settings -> ControlCore: Keymaps ->
How Detect key works**:

- **From the game** (default): the panel closes and a toast asks you to press the key within 10 seconds. The server
  takes the next game action it receives from you, that one press doesn't do its normal action, and the panel comes
  back with the key saved (on a row) or filled in (in the form; press **Add bind** to save). Only keys the server can
  hear are detected.
- **Capture screen**: a small "Press a key" window opens and reads any key, even ones the server can't hear during play
  yet (numpad, F-keys, ...). Esc cancels. Use it when Hytale starts sending every key, or to prepare binds now.

**Numpad and other extra keys today:** give the key a game action in Hytale's own **Settings -> Controls** first, for
example set Numpad 5 as **Ability 4**. Then press **Detect key** and press Numpad 5: the server hears "Ability 4", and
the bind works from then on.

**Every key in the lists:** turn on **Show every keyboard key in key lists** (same category) to list the whole
keyboard in the Key dropdown. Keys the server can't hear yet are marked "(server can't hear it)", and a bind on one
says so on its row.

## Writing keys (commands)

- Keys held together are joined with `+`: `ctrl+f`, `alt+shift+3`.
- Alternatives with `|`: `alt+3 | mouse_middle`.
- `none` removes the keys.
- Case and spaces don't matter.

| Name | Keys |
|---|---|
| `a` … `z`, `0` … `9` | letters and numbers |
| `ctrl`, `alt`, `shift`, `space` | modifiers (`control`, `lctrl`, `rshift`, … also work) |
| `mouse_left`, `mouse_right`, `mouse_middle`, `mouse_4`, `mouse_5` | mouse (`lmb`, `rmb`, `mmb` also work) |
| `tab`, `caps`, `esc`, `enter`, `f1` … `f12`, … | accepted, not detectable today |
| `crouch`, `sprint`, `walk`, `jump`, `stand_still`, `interact`, `ability_1` … `ability_4`, `dodge`, `hotbar_1` … `hotbar_9`, `attack`, `secondary_action`, `pick_block`, `switch_game_mode` | game actions by name |

## Who decides the keys

1. The **mod's default** keys apply…
2. …until an admin changes the **server's keys** for everyone (`/cc-bind-server`, or Keymaps → **Server defaults**).
3. A **player's own keys** (`/cc-bind`, Keymaps → **My keys**, or `/prefs`) apply only to them and win over both.

**Default** on a player's row goes back to the server's keys; on a server row, to the mod's. Players who chose their
own keys keep them when the server's keys change.

When several actions match one press, the one with the most held keys wins (Ctrl+F over F); equal ones all run.

The key's normal game action (ability, interact, attack, game mode switch) **still happens** by default: F opens a
door and runs its binds. The server setting **Bound keys keep their game action**
(`controlcore:keys-keep-game-action`) turned off makes binds replace the key's action instead, unless the mod keeps
it. Hotbar changes are never cancelled.

Admins can choose this for one action too: in Keymaps → **Server defaults** each action has a dropdown **Game action:
server setting / keep / replace** (or `/cc-key-game-action <id> <server|keep|replace>`). *Keep* and *Replace* win over
the server setting and over the mod's own choice; *server setting* follows the server-wide switch.

## Admins' command binds

Create a bind that runs any command:

```
/cc-keybind-add warp-spawn ctrl+r warp spawn {player}
/cc-keybind-add-server announce ctrl+o say Restart in 5 minutes
```

- `{player}` and `{uuid}` are replaced with the player who pressed the keys.
- `/cc-keybind-add` runs the command **as the player**, with their permissions.
- `/cc-keybind-add-server` runs it **as the server console**, with every permission. Only players with
  `ofatech.controlcore.keys.server` can use it; others don't see it and their keys keep their normal behaviour. Each
  use is logged.
- The bind becomes `custom:<name>`; adding the same name again replaces it. Quotes around the whole command are
  dropped.
- In the panel: Keymaps → **Server defaults** → **+ New bind** opens a form (name, keys from the two dropdowns,
  command, "run as the server"); each bind has **Edit** and **Remove**.
- `/cc-keybind-list` lists them, `/cc-keybind-remove <name>` deletes one.
- Players can rebind admins' binds for themselves like any other action.

## ControlCore's own actions

| Id | Does | Default |
|---|---|---|
| `controlcore:open-preferences` | runs `/prefs` | `ctrl+f` |
| `controlcore:toggle-hud` | hides or shows the whole HUD | none |
