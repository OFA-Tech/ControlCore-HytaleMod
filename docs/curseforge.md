# CurseForge page

Text to paste when creating the CurseForge project and uploading a file. Upload the same
`ControlCore-<version>.jar` the GitHub release has.

- **Name:** Control Core
- **Summary (max 255):** An in-game control panel for server owners: edit config.json, mod settings, player
  preferences, permissions and key binds for commands.
- **Categories:** Utility, Quality of Life, Library
- **Game version:** Hytale 0.7 (0.7.2 to 0.7.5)
- **Release type:** Beta for 0.x
- **License:** MIT
- **Source:** https://github.com/OFA-Tech/ControlCore-HytaleMod

## Description

Paste into the project's Description (Markdown). The images are the project's own CurseForge screenshots.

**ControlCore** is an in-game control panel for Hytale servers. Edit `config.json`, mod settings, player preferences, permissions and key binds without leaving the game, and give other mods one shared place for their settings.

> **Beta.** Requires a Hytale server **0.7.2 or newer 0.7.x** (tested on 0.7.5). Back up `mods/OFATech_ControlCore/` before updating.

![The ControlCore panel: Server Configs with the legend](https://media.forgecdn.net/attachments/2100/29/server-configs-png.png)

## Why ControlCore?

- **No more editing JSON by hand.** Every key of the server's `config.json` is in the panel, with its description, limits and default.
- **See what changed at a glance.** Every row shows its state, and a legend counts and filters them.
- **One panel for every mod.** Mods that use ControlCore show their settings and key binds in the same place, with the same search and permissions.
- **Players get their own settings.** `/prefs` lets each player pick their keys, HUD and preferences.
- **Your language.** English, Português (Brasil) and Español, following each player's game language.

## The panel

Open it with **`/open-admin-config`**. Six tabs:

| Tab | What you can do |
|---|---|
| **Server Configs** | Change any `config.json` key. Changes save at once; keys that only apply after a restart say so, and a banner lists them until you restart. |
| **Mod Settings** | Settings registered by ControlCore and other mods: check boxes, sliders, text, colours, lists and dropdowns. |
| **Player Prefs** | Your own preferences, including which parts of the HUD you see. |
| **Keymaps** | The server's default keys and your own, plus key binds that run any command. |
| **Mods** | Every installed mod with its logo, version and badges. Jump to its settings and keys, turn it on or off, read its config files. |
| **Permissions** | Decide who may see and who may change each setting, category or whole mod. |

### Every setting tells you its state

Each row is marked as **changed**, **default**, **not saved**, **error**, **needs a restart** or **locked**. The **Legend** on the right counts each state; click one to show only those rows, for example everything you changed. Mistakes are explained right under the field, and **Undo** puts back your last change.

![Search, an inline error and the legend counts](https://media.forgecdn.net/attachments/2100/30/server-configs-filters-png.png)

### Find anything in seconds

Every list has a search with a field filter (name, mod, value, key, ...) and *contains / equals / starts with / ends with*, plus "changed only". The legend counts follow your search.

### Mod settings with real controls

Sliders for numbers, colour pickers, lists you can add to and remove from, and **Default** and **Undo** on every row.

![Mod Settings](https://media.forgecdn.net/attachments/2100/26/mod-configs-png.png)

### Key binds that run commands

Bind keys to any command, without writing a mod. Run it as the player or as the server, set a server-wide default, and let every player choose their own keys. Keys are **picked from two dropdowns**, a key to hold and a main key, and only keys the server can really detect are offered.

![Keymaps: server defaults and the New bind form](https://media.forgecdn.net/attachments/2100/25/keybinding-png.png)

![Keymaps: a player's own keys](https://media.forgecdn.net/attachments/2100/28/personal-keybinding-png.png)

> Hytale servers only see game actions, not raw keys. Binds use Q, E, R, F, O, the mouse buttons, hotbar 1-9, ability 4, dodge and combinations with Ctrl, Alt, Shift or Space. Bound keys keep their normal action by default: F still opens doors and runs its binds.

### Every mod at a glance

![The Mods tab with badges](https://media.forgecdn.net/attachments/2100/27/modlist-png.png)

### Speaks your players' language

![The panel in Portuguese](https://media.forgecdn.net/attachments/2100/32/traduaao-png.png)

## For players

- **`/prefs`** opens your own preferences: your key binds, your HUD (hide the compass, health, chat and 20 more parts) and more.
- **Hide or show the HUD** with one key you choose, for clean screenshots.

## Works with CustomInventory

With [CustomInventory](https://github.com/SupremoSama/Hytale-CustomInventory) installed, its inventory screen gets a **ControlCore** button: admins open the admin panel, players open their preferences.

![The ControlCore button in CustomInventory](https://media.forgecdn.net/attachments/2092/753/inventory-button-png.png)

## Commands

| Command | What it does |
|---|---|
| `/open-admin-config` | Open the admin panel |
| `/prefs` | Open your own preferences |
| `/cc-config <mod>` | Open the panel on one mod's settings |
| `/cc-get`, `/cc-set`, `/cc-reset` | Read and change any setting or `config.json` key from chat or the console |
| `/cc-keybind-add`, `/cc-keybind-remove` | Create and remove command key binds |
| `/cc-perm`, `/cc-perms`, `/cc-perm-check` | Manage permissions per setting |
| `/cc-mods` | List the server's mods |

The full list, with permissions, is in the [wiki](https://github.com/OFA-Tech/ControlCore-HytaleMod/wiki/Commands).

## For mod authors

Register settings (server-wide or per player), key actions and HUD elements in a few lines. ControlCore stores the values, shows them in the panel, checks permissions, translates the names and tells your mod when something changes.

```java
ControlCoreApi.find().ifPresent(api -> {
    api.settings().register(
        SettingDefinition.server("homes", "max-homes", SettingType.integer(1, 50), 3)
            .named("Max homes")
            .inCategory("Limits"));
    api.keymaps().register(KeyAction.of("homes", "go-home")
        .displayName("Go home")
        .command("home {player}")
        .defaultKeys(Key.CTRL, Key.R));
});
```

Get started with the [mod author guide](https://github.com/OFA-Tech/ControlCore-HytaleMod/wiki/Mod-Author-Quick-Start).

## Links

- [Wiki](https://github.com/OFA-Tech/ControlCore-HytaleMod/wiki): installing, the panel, commands, permissions, key binds
- [Source code](https://github.com/OFA-Tech/ControlCore-HytaleMod) (MIT license)
- [Report a bug or suggest a feature](https://github.com/OFA-Tech/ControlCore-HytaleMod/issues)

## Gallery

Featured image: **Server Configs**. Titles and descriptions:

| File | Title | Description |
|---|---|---|
| server-configs | Server Configs | Every config.json key, with a legend of changes |
| server-configs-filters | Search and inline errors | Mistakes are explained under the field |
| mod-configs | Mod Settings | Check boxes, sliders, colours and lists |
| keybinding | Key binds for commands | Server defaults and the New bind form |
| personal-keybinding | Your own keys | Pick a key to hold and a main key |
| modlist | Mods tab | Logos, versions and Code/Assets badges |
| traducao | In your language | English, Portuguese and Spanish |
| inventory-button | CustomInventory button | Open the panel from the inventory |
