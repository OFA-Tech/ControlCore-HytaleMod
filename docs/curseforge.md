# CurseForge page

Text to paste when creating the CurseForge project and uploading a file. Upload the same
`ControlCore-<version>.jar` the GitHub release has.

- **Name:** ControlCore
- **Summary (max 255):** A server control panel and settings backbone for Hytale: edit config.json in game,
  per-setting permissions, mod settings and player preferences, key binds for commands, in English, Portuguese
  and Spanish.
- **Category:** Server Utility (also: Admin Tools, Library / API)
- **Game version:** Hytale 0.7 (0.7.2 and 0.7.3)
- **Release type:** Beta for 0.x
- **License:** MIT
- **Source:** https://github.com/OFA-Tech/ControlCore-HytaleMod

## Description

**ControlCore** gives server owners one in-game panel for everything they configure, and gives other mods a
shared place to put their settings and key binds.

### For server owners
- **/open-admin-config** opens the control panel:
  - **Server Configs:** every key of `config.json`, read from Hytale's own config definition. Changes save at
    once; keys that need a restart say so, and a reminder lists them until you restart.
  - **Mod Settings:** every setting other mods register with ControlCore.
  - **Player Prefs:** your own preferences.
  - **Keymaps:** the server's default keys and your own, plus your own key binds that run any command.
- **Search** in every list, with a field filter and equals / starts with / contains / ends with (and not).
- **Permissions per setting:** let moderators see or change only some settings (`/cc-perm`).
- **Key binds for commands:** `/cc-keybind-add warp-spawn ctrl+r warp spawn {player}`; run as the player or
  as the server. Q, E, R, F, O, mouse buttons, hotbar 1-9 and combinations with Ctrl, Alt, Shift or Space.
- **Languages:** English, Portuguese (Brazil) and Spanish, following each player's game language.

### For players
- **/prefs** opens your own preferences and key bindings.

### For mod authors
Register typed settings (server-wide or per player), key actions and translated names through
`ControlCoreApi`. ControlCore stores them, shows them in the panel, checks permissions and tells you when they
change. Maven: `net.ofatech:controlcore` on GitHub Packages. See the
[API docs](https://github.com/OFA-Tech/ControlCore-HytaleMod/blob/main/docs/api.md).

### Notes
- Beta: back up `mods/OFATech_ControlCore/` before updating.
- Hytale servers only see actions, not raw keys, so keys like H or Caps Lock cannot be bound.

## Screenshots to add
1. Server Configs tab with the search and a restart reminder.
2. Mod Settings tab.
3. Keymaps tab with the Server defaults / My keys switch and the Add bind form.
4. The panel in Portuguese.
