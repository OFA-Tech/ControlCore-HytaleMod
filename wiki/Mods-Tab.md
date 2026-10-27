# Mods tab

The **Mods** tab lists every mod and asset pack on the server. Everyone has it: admins in `/open-admin-config`,
players in `/prefs`.

## Each row

- Logo, name, version, description, authors, website and id.
- Badges: **Code** (blue) or **No code**, **Assets** (green) or **No assets**, and when it applies **Off**,
  **Failed to load**, **On after a restart**, **Off after a restart** and **Hytale**.
- **Details** opens a page for the mod: what it **requires**, what it **can use**, which mods it is **needed by**
  and which **Hytale** versions it is made for, each with the version needed, the version installed and a badge
  (OK, Missing, Off, Wrong version, Installed, Not installed). Press a mod's name to open its details; **< Back**
  returns to the list.
- Problem badges: **Missing N** (a required mod isn't installed or is turned off), **Wrong version** (an installed
  mod, or Hytale itself, doesn't match the version it needs) and **Needed by N** (other mods require it). A red line
  under the mod says exactly what's wrong.
- **Settings** and **Keys** buttons open that mod's settings or key actions, already searched. They only show when the
  mod registered settings or keys with ControlCore.

Mods without a logo show a puzzle-piece icon. Mod authors: see
[Panel, events and logos](Panel-Events-And-Logos#mod-logo).

## Search and filter

Search by name, id, author or description. The filter has:

| Filter | Shows |
|---|---|
| Mods (default) | every mod except Hytale's own |
| Running | mods that are running |
| Turned off | mods that are off |
| With settings or keys | mods that use ControlCore |
| Has problems | mods with a missing or wrong-version requirement |
| With assets | mods that ship assets |
| Everything | also Hytale's own plugins |

## Admins

- **Enabled** box: turns a mod on or off **from the next start**. It is saved in `config.json` (`Mods`), and the
  change joins the restart reminder in Server Configs. ControlCore itself and Hytale's own plugins can't be turned
  off.
- **Dependencies are followed:** turning off a mod that others require shows a box at the top of the list ("Turning
  off X also turns off A, B. Turn them all off?") with **Turn all off** and **Cancel**. Turning a mod on offers to
  turn on the mods it requires. Snapshots, profiles and rollbacks follow the same rule.
- **Config files**: opens a page with the mod's JSON files from its data folder, read-only, with **< Back**.
- **Mods hidden from players** (`controlcore:mods-hidden`, in Mod Settings): mods players don't see in the tab.

```
/cc-set controlcore:mods-hidden SecretMod, AdminTools
```

## Commands

- `/cc-mods` (admins) lists the mods in chat or the console.
- `/cc-config <mod>` opens the panel on one mod's settings.
