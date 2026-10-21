# Mods tab

The **Mods** tab lists every mod and asset pack on the server. Everyone has it: admins in `/open-admin-config`,
players in `/prefs`.

## Each row

- Logo, name, version, description, authors, website and id.
- Labels: **Asset pack**, **Off**, **Failed to load**, **On after a restart**, **Off after a restart**.
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
| With assets | mods that ship assets |
| Everything | also Hytale's own plugins |

## Admins

- **Enabled** box: turns a mod on or off **from the next start**. It is saved in `config.json` (`Mods`), and the
  change joins the restart reminder in Server Configs. ControlCore itself and Hytale's own plugins can't be turned
  off.
- **Config files**: shows the mod's JSON files from its data folder, read-only.
- **Mods hidden from players** (`controlcore:mods-hidden`, in Mod Settings): mods players don't see in the tab.

```
/cc-set controlcore:mods-hidden SecretMod, AdminTools
```

## Commands

- `/cc-mods` (admins) lists the mods in chat or the console.
- `/cc-config <mod>` opens the panel on one mod's settings.
