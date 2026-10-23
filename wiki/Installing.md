# Installing

## Requirements

- Hytale server **0.7.2 or newer 0.7.x** (tested on 0.7.4; not 0.8).
- No other mod is needed.

## Steps

1. Download `ControlCore-<version>.jar` from the
   [GitHub releases](https://github.com/OFA-Tech/ControlCore-HytaleMod/releases) or
   [CurseForge](https://www.curseforge.com/hytale/mods/control-core).
2. Put it in the server's `mods/` folder.
3. Start the server. The log shows:

   ```
   [ControlCore] Control panel is available (built-in Hytale UI): /open-admin-config
   ```
4. Make yourself an operator in the server console: `/op add <your name>`.
5. Join and run `/open-admin-config`.

Operators have every permission. To give the panel to someone who is not an operator, see [Permissions](Permissions).

## Updating

1. Stop the server.
2. Back up `mods/OFATech_ControlCore/` (your settings, key binds and rules; see [Files and settings](Files-And-Settings)).
3. Replace the old jar with the new one and start the server.

Read the [changelog](https://github.com/OFA-Tech/ControlCore-HytaleMod/blob/main/CHANGELOG.md) first: during the
beta, an update may need mods that use ControlCore to update too (the changelog says so at the top of a version).

## Removing

Delete the jar. Hytale's own `config.json` changes you made in the panel stay, since they are Hytale's file. The
`mods/OFATech_ControlCore/` folder can be deleted too.
