# Commands

Permission nodes start with ControlCore's base permission, **`ofatech.controlcore`**. Operators have every node.
"Who may see it" and "who may change it" follow the [permission rules](Permissions).

Admin commands also work from the server console; commands about your own preferences, keys or panel need a player.

Hytale cuts command text at commas, so no text you type (values, messages, reasons, rule fields) can contain a `,`.
Arguments in `[--name=value]` are optional and must be typed with their name, for example `--search=pvp`.
Hytale splits command text at spaces, so a `--name=value` value is one word: `--search=pvp` works, `--search=pvp arena`
does not search for both words.

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
| `/cc-configs [--world=name] [--search=text]` | who may see the keys | list the `config.json` keys, or a world's keys, by heading, with value, default and `LIVE`, `RESTART` or `READ-ONLY`; only keys you may see |
| `/cc-restart-pending` | admins | what was changed (config keys, mods) and only applies after a restart |
| `/cc-restore-defaults <section> <check\|confirm> [--search=text]` | like the panel | the panel's **Restore defaults** button: `check` says how many rows would change, `confirm` does it |

`/cc-restore-defaults` sections: `mod-settings`, `prefs` (yours), `server-configs`, `world:<name>`, `keymaps-server`
(admins), `keymaps-mine` (yours) and `permissions` (admins). Only rows you may change and that differ from their
default are reset; read-only rows are skipped. Server-wide sections first save a `before-defaults` snapshot when you
may use snapshots, and every reset is in History. `--search=text` limits it to the rows the panel's search shows for that word (one word, see above; `name:`, `id:`, `mod:`, `cat:`, `key:`/`value:`, `default:`, `desc:`, `is:changed`/`is:default` and `-word` work as in the panel).

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

## Players

The same actions as the panel's **Players** tab, with the same rights: each needs its node (or admin), only an admin
may act on an admin, and nobody can unmute, unfreeze, de-op or un-whitelist themselves. `<player>` is a name or UUID.
The console counts as an admin; `/cc-tp` and `/cc-bring` need a player.

| Command | Who | Does |
|---|---|---|
| `/cc-kick <player> <reason\|->` | `ofatech.controlcore.players.kick` | kick an online player; `-` uses the default message |
| `/cc-mute <player> <length>` | `ofatech.controlcore.players.mute` | mute for a length: `permanent`, `30m`, `12h`, `7d`, `2w` |
| `/cc-unmute <player>` | `ofatech.controlcore.players.mute` | lift a mute |
| `/cc-freeze <player>` | `ofatech.controlcore.players.freeze` | hold a player in place (offline players are frozen when they join) |
| `/cc-unfreeze <player>` | `ofatech.controlcore.players.freeze` | let them move again |
| `/cc-gamemode <player>` | `ofatech.controlcore.players.gamemode` | switch an online player between Adventure and Creative |
| `/cc-tp <player>` | `ofatech.controlcore.players.teleport` | teleport yourself to an online player |
| `/cc-bring <player>` | `ofatech.controlcore.players.teleport` | teleport an online player to you |
| `/cc-whitelist` | `ofatech.controlcore.players.whitelist` | list the whitelist, 20 at most |
| `/cc-whitelist-add <player>` / `/cc-whitelist-remove <player>` | `ofatech.controlcore.players.whitelist` | add a player to the whitelist or remove one |
| `/cc-whitelist-require <on\|off>` | `ofatech.controlcore.players.whitelist` | whether joining needs the whitelist (config.json `RequireJoinPermission`, live, in History) |
| `/cc-maintenance <on\|off\|status> [--message=] [--motd=] [--for=]` | `ofatech.controlcore.maintenance` (`status`: anyone) | maintenance mode: `on` kicks every online player who isn't an admin and lacks `ofatech.controlcore.maintenance.join` and refuses them until it is off; `--message=` is what they see (one word, see the top; empty for the default), `--motd=` replaces the MOTD while it is on, `--for=` turns it off by itself (`30m`, `2h`, `never`). In History |
| `/cc-op <player>` / `/cc-deop <player>` | admins | make or remove an operator |
| `/cc-group <add\|remove> <player> <group>` | admins | add a player to a permission group or remove them |
| `/cc-group set <player> <group>` | admins | make that group the player's only group (one History line per group changed) |
| `/cc-groups` | admins | list the permission groups and their own permissions |
| `/cc-group-perm <give\|take> <group> <node>` | admins | give a group a permission node or take it away |
| `/cc-players [--filter=filter] [--search=text]` | `ofatech.controlcore.players` or bans | the Players list: name, short UUID, badges and world or last seen, 20 at most; filters `everyone`, `online`, `offline`, `banned`, `whitelisted`, `operators`, `muted`, `frozen` |
| `/cc-player <player>` | `ofatech.controlcore.players` | status, groups, whitelist, first join, ban, mute, freeze and recent messages |
| `/cc-inventory <player>` | `ofatech.controlcore.players.inventory` | what an online player carries |
| `/cc-chat` | `ofatech.controlcore.players` | the last 50 chat messages |

```
/cc-mute Griefer 30m
/cc-kick Griefer stop spamming
/cc-whitelist-add Builder
/cc-group add Builder hytale:Adventurer
/cc-group-perm give hytale:Adventurer homes.use
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
| `/cc-history [--filter=text] [--kind=kind]` | admins or `ofatech.controlcore.history` | the latest 10 changes, optionally only those whose name, who or value contains the filter, and only one kind like the History dropdown: `all`, `settings`, `config`, `worlds`, `keys`, `binds`, `permissions`, `mods`, `bans`, `players` |
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
| `/cc-profile-preview <id>` | what switching to a profile (built-in or marked snapshot) would change, without changing anything |
| `/cc-profile <id>` | switch to a profile: `@vanilla`, `@pvp`, `@creative`, `@event` or a snapshot marked as profile (saves `before-<name>` first) |
| `/cc-profile-mark <snapshot>` | mark a snapshot as a profile |
| `/cc-profile-unmark <profile>` | turn a profile back into a plain snapshot; only admins change a snapshot once it is a profile |

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
| `/cc-rule-add <fields>` | create a rule, with the same checks as the panel |
| `/cc-rule-edit <id> <fields>` | change a rule; fields you leave out stay as they are, any `do=` replaces all actions |

`<fields>` are `key=value` parts joined with `;`:

| Field | Value |
|---|---|
| `name=` | the rule's name |
| `when=` | the trigger and its value after a `:`, e.g. `join`, `every-minutes:30`, `daily-at:08:30`, `cron:0 20 * * FRI`, `tps-below:15`, `setting-changed:homes:max-homes` |
| `if=` | optional condition and value, e.g. `has-permission:vip`, `in-world:default`, `time-between:18:00-23:00`; `if=none` removes it |
| `do=` | an action and its value, in order, up to 8, e.g. `message-player:Hi {player}`, `toast-all:warning:Restart\|in 5 min`, `set-setting:homes:max-homes=5` |
| `cooldown=` | seconds; empty for the trigger's default |
| `on=` | `yes` or `no` |

Ids for triggers, conditions and actions are the panel's choices in lower case with `-`; an unknown or missing
`when=` lists them. A value can't contain `;` (it starts the next field) or `,` (see the top of this page).

```
/cc-rule-add name=Welcome; when=join; do=message-player:Welcome {player}!; do=toast-player:success:Hi|Have fun
/cc-rule-add name=Night; when=daily-at:22:00; do=message-all:Night mode; do=set-setting:homes:max-homes=2
/cc-rule-edit welcome cooldown=60; on=no
```

## Announcements

Rotating announcements, also in the panel's **Automation** tab, **Announcements** view (see [Automation](Automation)).
Admins or `ofatech.controlcore.announcements`.

| Command | Does |
|---|---|
| `/cc-announce-list` | list the announcements with their ids, and the settings |
| `/cc-announce-add <text>` | add an announcement at the end, turned on; placeholders such as `{online}` work |
| `/cc-announce-remove <id>` | delete an announcement |
| `/cc-announce-toggle <on\|off> [--id=]` | turn the rotation on or off, or with `--id=` one announcement |
| `/cc-announce-move <id> <up\|down>` | move an announcement one place up or down |
| `/cc-announce-now [--id=]` | send the next announcement now, or with `--id=` that one |
| `/cc-announce-set [--interval=] [--order=] [--channel=] [--min-players=]` | minutes between announcements (1 to 1440), `in-turn` or `random`, `chat`, `toast` or `warning`, and the players online needed (0 for always) |
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
| `/cc-mod-info <mod>` | anyone (mods hidden from players stay hidden) | the panel's **Details**: requires, can use, needed by and the Hytale version |
| `/cc-mod-on <mod> <check\|all>` | admins | turn a mod on from the next start; `check` changes nothing and says what `all` would do, including mods it needs that are off; `all` turns it and those mods on |
| `/cc-mod-off <mod> <check\|all>` | admins | turn a mod off from the next start; `check` changes nothing and says what `all` would do, including mods that need it; `all` turns it and those mods off |
| `/cc-mod-configs <mod>` | admins | show the mod's config files (read-only) |

`<mod>` is the mod's id (`Group:Name`), name or settings namespace. A mod that is already on (or off) after the next restart says so for both `check` and `all`, and nothing is changed or recorded.

## HUD and notifications

| Command | Who | Does |
|---|---|---|
| `/cc-hud <hide\|show\|toggle>` | any player | hide or show your whole HUD |
| `/cc-toast <player\|everyone\|admins> <style> <text>` | admins | a small timed message; style is `info`, `success`, `warning` or `error`; `Title\|text` adds a title |
| `/cc-warn <player\|everyone\|admins> <seconds> <text>` | admins | a warning bar; `0` seconds keeps it until cleared; start the text with `warning:` (or `info:`, `success:`, `error:`) for a style |
| `/cc-warn-clear <player\|everyone\|admins>` | admins | take away the warning bar from `/cc-warn` |

Texts are 1 to 200 characters; a toast needs text after `Title|` and a warning after its `warning:` style. `everyone` warnings also reach players who join later; `/cc-warn-clear <player>` says so when one for everyone is still shown.

## Debug tools

| Command | Who | Does |
|---|---|---|
| `/cc-input-probe` | `ofatech.controlcore.debug` | show in chat every action your client sends; run again to stop |
| `/cc-key-probe input` | `ofatech.controlcore.debug` | a test screen that shows the code of every key pressed while it is open |

Players without the debug permission don't see these commands at all. With it, the panel's Keymaps tab
(**Server defaults**) has buttons for both.
