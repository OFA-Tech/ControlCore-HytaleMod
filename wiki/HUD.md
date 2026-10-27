# HUD

Players choose which parts of the game's HUD they see; admins can set rules for each part.

## Players

`/prefs` → **ControlCore: HUD** has a switch for each part. The choice is saved and applied every time the player
joins. Changes apply at once.

| Group | Parts |
|---|---|
| Status | hotbar, health, stamina, mana, oxygen, status icons, abilities, ammo indicator, sleep |
| Screen | crosshair (reticle), compass, speedometer, key hints (input bindings) |
| Messages | chat, notifications, kill feed, event titles, boss bar |
| Panels | objectives, portal panel |
| Builder tools | utility slot selector, block variant selector, builder tools legend |

Mods can add their own HUD to the same list (it shows under "<mod name>: HUD").

**Hide everything:** bind the key action **Hide or show the HUD** (`controlcore:toggle-hud`) in Keymaps, for example
`/cc-bind controlcore:toggle-hud alt+3`. It hides the game's HUD and every mod HUD until pressed again or until you
leave. It has no default key.

## Toasts and warnings

Rules, admins and mods can show **toasts** (small timed messages, up to three stacked) and **warning bars** (for
example "Restart in 10 minutes", until they expire or are removed). In `/prefs` -> **ControlCore: HUD** each player
chooses:

| Preference | Choices |
|---|---|
| Toasts and warnings | on (default) or off |
| Toast position | Top right (default), Top left, Bottom right, Hytale notifications |
| Warning position | Under the compass (default), Above the hotbar |

The colour bar on the left shows the level: blue information, green success, gold warning, red error.

## Admins

Mod Settings → **ControlCore: HUD** has a rule for each part (`controlcore:hud-rule.<part>`):

| Rule | Effect |
|---|---|
| Players choose (default) | each player's switch decides |
| Hidden until a player turns it on | hidden for players who haven't chosen; they can turn it on |
| Always hidden | hidden for everyone, whatever they chose |
| Always shown | shown for everyone, whatever they chose |

```
/cc-set controlcore:hud-rule.compass Always hidden
```

### HUD by situation

`controlcore:hud-context-rules` (Mod Settings -> **ControlCore: HUD by situation**) takes one rule per line:
`<part> <hide|show> <when>`, where *when* is `world:<name>`, `mode:adventure`, `mode:creative`, `combat` or
`no-combat`. Situation rules win over the rules above and over players' own switches; when several match, the last
one wins.

```
compass hide world:lobby
chat hide combat
hotbar show mode:creative
```

Part names are the ones in the table above, written in lower case with dashes (`status-icons`, `kill-feed`, ...).

## Limits

ControlCore only hides the parts it was asked to hide, and only shows those back. Parts the game turns on or off by
itself (for example when the game mode changes) are left to the game, so a game mode change can bring a hidden part
back until the player rejoins.
