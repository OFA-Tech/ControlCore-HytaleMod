# Control panel, events and logos

## Opening the panel

```java
ControlPanelApi panel = ControlCoreApi.get().controlPanel();

if (panel.isAvailable()) {
    panel.open(playerId, ControlPanelRequest.ALL);                // the admin panel
}
panel.open(playerId, ControlPanelRequest.PLAYER_PREFERENCES);     // the player's own tabs, like /prefs
```

- `open` doesn't check permissions: your mod decides who may open what. `PLAYER_PREFERENCES` is safe for anyone and
  works even when the admin panel is switched off.
- Failures: `ControlPanel.Disabled`, `ControlPanel.PlayerNotFound`, `ControlPanel.NothingToShow`.
- `PlayerId.of(uuid)` makes a `PlayerId` from a player's UUID.

## Events

```java
Subscription sub = ControlCoreApi.get().events().subscribe(SettingChanged.class, event -> {
    if (event.id().namespace().equals("homes")) {
        reload();
    }
});
sub.close();   // stop listening, for example in your shutdown()
```

| Event | When | Fields |
|---|---|---|
| `PlayerJoined` | a player joined | `player()` (`id()`, `name()`) |
| `PlayerLeft` | a player left | `player()` |
| `SettingChanged` | any setting or key bind changed | `id()`, `scope()`, `player()` (preferences), `oldValue()`, `newValue()` |
| `ServerConfigChanged` | a `config.json` key was changed in the panel | `id()` (e.g. `server:motd`), `oldValue()`, `newValue()` |

Values in events are text, as shown in `/cc-get`. Listeners run on the thread that caused the change: hop to the
world thread before touching the world.

## Mod logo

The **Mods** tab shows your logo when your mod ships it as:

```
src/main/resources/Common/UI/Custom/<Group>_<Name>.png
```

for example `Acme_Homes.png` for `Acme:Homes`. 96×96 pixels, shown at 64×64. It's the same path Better Modlist and
MAC use, so one file serves all three. Mods without a logo get a puzzle-piece icon.

The Mods tab links your mod to its settings and key actions when their ids start with your mod's name in lower case
(`homes:` for `Acme:Homes`), or with the id you passed to `setModName`.

## API version

`ControlCoreApi.VERSION` is the version you compiled against; `api.apiVersion()` is the running one, which may be
newer. The API follows semantic versioning: a new major version (like 2.0.0) can break mods built for the previous
one, and the [changelog](https://github.com/OFA-Tech/ControlCore-HytaleMod/blob/main/CHANGELOG.md) says what to
change.
