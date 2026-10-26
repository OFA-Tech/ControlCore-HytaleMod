# Commands

Permission nodes start with ControlCore's base permission, **`ofatech.controlcore`**. Operators have every node.
"Who may see it" and "who may change it" follow the [permission rules](Permissions).

Admin commands also work from the server console; commands about your own preferences, keys or panel need a player.

## Panel

| Command | Who | Does |
|---|---|---|
| `/open-admin-config` | admins, and anyone allowed to see at least one server setting | open the control panel |
| `/prefs` | any player | open your preferences, key binds, HUD and the Mods tab (works even when the admin panel is off) |
| `/cc-config <mod>` | any player | open the panel on one mod's settings; `<mod>` is its id (`Group:Name`), name or setting prefix, e.g. `controlcore` |

## Settings

| Command | Who | Does |
|---|---|---|
| `/cc-settings` | anyone | list mods' server settings you may see, with their values (`(default)` if unchanged) |
| `/cc-get <id>` | who may see it | name, description, value, default, type and scope of one setting or `config.json` key |
| `/cc-set <id> <value>` | who may change it | change a server setting or `config.json` key; the value may contain spaces |
| `/cc-reset <id>` | who may change it | put a server setting or `config.json` key back to its default |
| `/cc-prefs` | any player | list your preferences |
| `/cc-pref <id> <value>` | any player | change one of your preferences |
| `/cc-pref-reset <id>` | any player | put a preference back to its default |

Ids look like `mod:key`, for example `controlcore:welcome-message`. The server's `config.json` keys are `server:…`,
for example `server:motd` or `server:connection-timeouts.play-timeout`; `/cc-set` says when a key only applies after a
restart, and read-only keys can't be changed.

Bans (admins or `ofatech.controlcore.bans`):

```
/cc-bans                               list the bans
/cc-ban Griefer 7d griefing at spawn   ban for 7 days; length is permanent or a number with m, h, d or w
/cc-ban 3a030bb2-... permanent -       ban by UUID, no reason
/cc-unban Griefer                      lift a ban
```

World settings are `world:<world>.<key>`, for example `world:default.is-pvp-enabled` or `world:default.tps`.

```
/cc-set server:motd Have fun!
/cc-set world:default.is-pvp-enabled true
/cc-set controlcore:welcome-message Welcome, {player}!
/cc-set controlcore:welcome-message-color #55ff55
/cc-set controlcore:mods-hidden SomeMod, OtherMod
/cc-pref controlcore:panel-start-tab Mod Settings
```

## Permissions

All three are also in the panel's **Permissions** tab.

| Command | Who | Does |
|---|---|---|
| `/cc-perm <target> <view\|edit\|both> <node\|everyone\|default>` | `ofatech.controlcore.settings` | set who may see or change a setting, a category or a mod; `default` removes the rule |
| `/cc-perms` | `ofatech.controlcore.settings` | list the rules admins set |
| `/cc-perm-check <id>` | `ofatech.controlcore.settings` | who may see and change one setting, and which rule decides |

See [Permissions](Permissions) for examples.

## History

Also in the panel's **History** tab.

| Command | Who | Does |
|---|---|---|
| `/cc-history [--filter=text]` | admins or `ofatech.controlcore.history` | the latest 10 changes, optionally only those whose name, who or value contains the filter |
| `/cc-rollback <number>` | admins or `ofatech.controlcore.history.rollback` | put back the value change `#number` replaced |
| `/cc-history-clear <days\|all>` | admins | delete changes older than a number of days (`30` or `30d`), or all of them |

## Snapshots

Also in the panel: **History** → **Snapshots**. All need admins or `ofatech.controlcore.snapshots`.

| Command | Does |
|---|---|
| `/cc-snapshot-save <name> [--parts=...]` | save a snapshot (replaces one with the same name). `--parts=` is `all` (default: everything except players) or parts joined with `+`: `config`, `worlds`, `settings`, `settings:<mod>/<mod>`, `keys`, `permissions`, `mods`, `players` |
| `/cc-snapshots` | list the snapshots |
| `/cc-snapshot-diff <name>` | what restoring would change, without changing anything |
| `/cc-snapshot-restore <name>` | restore it |
| `/cc-snapshot-restore-safe <name>` | save the current state as `before-<name>`, then restore it |
| `/cc-snapshot-delete <name>` | delete it |
| `/cc-snapshots-clear` | delete every snapshot that is not used as a profile |
| `/cc-profiles` | list the profiles and show which one is active |
| `/cc-profile <id>` | switch to a profile: `@vanilla`, `@pvp`, `@creative`, `@event` or a snapshot marked as profile (saves `before-<name>` first) |

```
/cc-snapshot-save pvp-weekend --parts=config+settings+keys
/cc-snapshot-save homes-only --parts=settings:homes
/cc-snapshot-diff pvp-weekend
/cc-snapshot-restore-safe pvp-weekend
```

## Automation

Also in the panel's **Automation** tab (see [Automation](Automation)). Admins only.

| Command | Does |
|---|---|
| `/cc-rules` | list the rules, and the actions and triggers mods offer |
| `/cc-rule-toggle <id>` | turn a rule on or off |
| `/cc-rule-test <id>` | run a rule once now, ignoring its trigger, condition and cooldown |
| `/cc-rule-remove <id>` | delete a rule |

## Diagnostics

| Command | Who | Does |
|---|---|---|
| `/cc-diagnostics` | admins | show the server's health and write a report to `diagnostics/` without passwords, tokens or IP addresses |

## Key binds

| Command | Who | Does |
|---|---|---|
| `/cc-keys` | anyone | list key actions, your keys, and whether the server can detect them |
| `/cc-bind <id> <keys>` | any player | change your keys, e.g. `/cc-bind controlcore:open-preferences alt+3` |
| `/cc-bind-reset <id>` | any player | go back to the server's keys |
| `/cc-bind-server <id> <keys>` | `ofatech.controlcore.settings` | change the server's keys for everyone who has not chosen their own |
| `/cc-bind-server-reset <id>` | `ofatech.controlcore.settings` | go back to the mod's default keys |
| `/cc-key-game-action <id> <server\|keep\|replace>` | `ofatech.controlcore.settings` | whether this action keeps the key's game action (`server` follows the server setting) |
| `/cc-keybind-add <name> <keys> <command>` | `ofatech.controlcore.settings` | create `custom:<name>`, run as the player who presses it |
| `/cc-keybind-add-server <name> <keys> <command>` | `ofatech.controlcore.settings` | create `custom:<name>`, run as the server console |
| `/cc-keybind-remove <name>` | `ofatech.controlcore.settings` | delete a bind admins created |
| `/cc-keybind-list` | `ofatech.controlcore.settings` | list the binds admins created, with their commands |

See [Key binds](Key-Binds) for the key syntax and which keys work.

## Mods

| Command | Who | Does |
|---|---|---|
| `/cc-mods` | `ofatech.controlcore.settings` | list the server's mods and whether they run |

## Debug tools

| Command | Who | Does |
|---|---|---|
| `/cc-input-probe` | `ofatech.controlcore.debug` | show in chat every action your client sends; run again to stop |
| `/cc-key-probe input` | `ofatech.controlcore.debug` | a test screen that shows the code of every key pressed while it is open |

Players without the debug permission don't see these commands at all. With it, the panel's Keymaps tab
(**Server defaults**) has buttons for both.
