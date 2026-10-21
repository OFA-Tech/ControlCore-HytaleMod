# HUD elements

Hytale 0.7 can show any number of custom HUDs at once, so your mod adds its own `CustomUIHud` as usual (MultipleHUD
and similar mods are no longer needed). ControlCore adds what's missing: a switch for players, remembered per
player, and the "hide the whole HUD" key.

```java
HudApi huds = ControlCoreApi.get().huds();

huds.register(HudElement.of("minimap", "map")             // preference minimap:hud.map
        .displayName(Text.key("minimap.hud.name", "Minimap"))
        .describedAs("The map in the top right corner")
        .onShow(player -> showMinimap(player))            // add your CustomUIHud
        .onHide(player -> removeMinimap(player)));        // remove it
```

- Players get a switch in `/prefs`, under `<mod name>: HUD`.
- ControlCore calls **`onShow`** when the player joins (if it's visible for them), when they turn it on, and when
  "hide the whole HUD" ends; **`onHide`** in the opposite cases.
- **Don't show the HUD yourself** on join: let `onShow` do it, otherwise players who turned it off still see it.
- `.hiddenByDefault()` makes it opt-in: hidden until a player turns it on.

## State

```java
boolean shown = huds.isVisible(playerId, SettingId.of("minimap:hud.map"));
boolean compass = huds.isVisible(playerId, HudPart.COMPASS);   // the game's own parts
boolean allHidden = huds.isHidden(playerId);                   // "hide the whole HUD" is on
huds.setHidden(playerId, true);                                // turn it on from your code
```

`isVisible` is what the player should see right now: their switch, the server's rules for game parts, and
"hide the whole HUD" together.

## The game's HUD parts

`HudPart` lists the 23 parts ControlCore manages (`HOTBAR`, `HEALTH`, `COMPASS`, `CHAT`, `RETICLE`, `KILL_FEED`,
`BOSS_BAR`, …). Players switch them in `/prefs`; admins set rules (see [HUD](HUD)). If your mod hides or shows game
parts itself, prefer leaving that to the player's choice.
