# For players

If a server runs ControlCore, you can change some things for yourself, without being an admin.

## Your preferences: `/prefs`

`/prefs` (or **Ctrl + F** by default) opens a panel with:

- **Player Prefs:** settings that are yours, for example whether you get the welcome message, which tab opens first,
  and any preferences the server's mods offer. Changes apply at once and are remembered.
- **HUD:** switches for the parts of the game's HUD (compass, chat, health, kill feed, …). See [HUD](HUD).
- **Keymaps → My keys:** the key combinations for the server's actions. See below.
- **Mods:** the server's mods with their versions.

Each row shows a mark for its state (changed, default, not saved, ...), explained in the Legend on the right.
**Undo** puts back your last change and **Default** the default. Keys are picked from two dropdowns. Every list
has a search box.

The panel and messages follow your game language (English, Portuguese and Spanish are included).

## Changing keys

In `/prefs` → Keymaps, or with commands:

```
/cc-keys                                      list actions and your keys
/cc-bind controlcore:open-preferences alt+3   change your keys
/cc-bind controlcore:toggle-hud ctrl+r        hide or show the whole HUD with Ctrl + R
/cc-bind-reset controlcore:open-preferences   back to the server's keys
```

Hytale only tells the server about some keys: **Q, E, R, F, O, 1-9, mouse buttons, ability 4, dodge** and a short
**Alt** tap, plus **Ctrl, Alt, Shift or Space** held with them. Keys like H, Tab or F1 can't be used. See
[Key binds](Key-Binds#which-keys-work) for details.

Your keys only change things for you, and they stay when the server changes its own.

## Other commands

| Command | Does |
|---|---|
| `/cc-prefs` | list your preferences |
| `/cc-pref <id> <value>` | change one, e.g. `/cc-pref controlcore:show-welcome-message false` |
| `/cc-pref-reset <id>` | put one back to its default |
| `/cc-config <mod>` | open one mod's preferences |
