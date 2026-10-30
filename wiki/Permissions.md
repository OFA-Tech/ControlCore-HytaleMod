# Permissions

## ControlCore's own nodes

All nodes start with **`ofatech.controlcore`**. Operators have every node.

| Node | Gives |
|---|---|
| `ofatech.controlcore.settings` | ControlCore admin: sees and changes every setting, the server's keys and admins' key binds |
| `ofatech.controlcore.admin` | the same as `.settings` |
| `ofatech.controlcore.keys.server` | may use key binds that run as the server (when the bind sets no permission of its own) |
| `ofatech.controlcore.debug` | the debug commands `/cc-input-probe` and `/cc-key-probe` |
| `ofatech.controlcore.history` | see the change history (History tab, `/cc-history`) |
| `ofatech.controlcore.history.rollback` | roll changes back; for settings it still needs the right to change that setting |
| `ofatech.controlcore.snapshots` | save, restore and delete snapshots |
| `ofatech.controlcore.players` | see Server Configs → Players |
| `ofatech.controlcore.players.gamemode` | switch a player between Adventure and Creative |
| `ofatech.controlcore.players.teleport` | teleport to a player or bring them to you |
| `ofatech.controlcore.players.kick` | kick a player |
| `ofatech.controlcore.players.whitelist` | add players to or remove them from the whitelist |
| `ofatech.controlcore.players.mute` | mute and unmute players |
| `ofatech.controlcore.players.freeze` | freeze and unfreeze players |
| `ofatech.controlcore.players.inventory` | see a player's inventory on their Details page |

Players without any node can still use `/prefs`, their own preferences and keys, `/cc-keys`, `/cc-settings` (showing
only what they may see) and the Mods tab.

## Who may see and change each setting

Every setting, including every `config.json` key in Server Configs, has two sides, checked separately:

- **See:** the setting appears in the panel (read-only if you can't change it) and in `/cc-get` / `/cc-settings`.
  Without it, the setting is hidden and reported as unknown.
- **Change:** you may save it. Changing includes seeing.

**Defaults:** server settings are for ControlCore admins; player preferences are each player's own. The console and
ControlCore admins can always see and change everything.

Mods can set their own rules in code. Admins override them in the panel's **Permissions** tab or with `/cc-perm`.

### In the panel

`/open-admin-config` → **Permissions** (admins only) lists every mod, category, setting and `config.json` key:

- Each setting's line says who may see and change it **now**, and which rule decides it (like `/cc-perm-check`).
- **See** and **Change** hold your rule for that line. Leave a field empty to inherit, or write a full node or
  `everyone`. **Save** stores both; **Default** removes your rule.
- **Whole mod** and **Whole category** lines set a rule for everything below them.
- The search line works like everywhere else; **Changed only** lists the rules you set (like `/cc-perms`).

### `/cc-perm <target> <view|edit|both> <node|everyone|default>`

`<target>` is one of:

| Target | Example |
|---|---|
| one setting | `homes:max-homes`, `server:motd` |
| a category | `category:Homes` |
| a whole mod | `mod:homes`, `mod:server` (all of `config.json`) |

`<node>` is a full permission node, `everyone`, or `default` to remove your rule.

### Examples

```
# Let everyone see the server's config read-only
/cc-perm mod:server view everyone

# Let moderators change the message of the day, and nothing else
/cc-perm server:motd edit staff.motd

# A whole category for a staff group
/cc-perm category:Homes both homes.staff

# Undo
/cc-perm server:motd edit default
```

A moderator with `staff.motd` can now run `/open-admin-config`: the panel opens with only the settings they may see,
and only the MOTD can be saved. Server default keys and admins' key binds stay admin-only.

### Which rule wins

For each side, the first rule that exists wins:

1. an admin's rule for the setting
2. an admin's rule for its category
3. an admin's rule for its mod
4. the mod's own rule on the setting
5. the mod's rule for the category
6. the default

`/cc-perm-check <id>` shows who may see and change a setting and which of these decides it. `/cc-perms` lists every
admin rule. Rules are saved in `SettingPermissions.json`.
