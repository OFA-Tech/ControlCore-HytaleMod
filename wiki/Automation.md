# Automation

The **Automation** tab (admins) holds rules that run on their own: **when** something happens, **if** a condition
holds, **then** do something. Everything a rule changes is recorded in [History](Control-Panel#history) as
"automation", so it can be rolled back.

## Sending a message now

**Send message** opens a small form above the rules: pick who gets it (**Everyone**, **Admins** or a player who is
online), what (**Toast**, **Warning bar** or **Clear warning bar**), the style (Info, Success, Warning, Error), for a
warning bar the seconds it stays (`0` or empty: until cleared), and the text (`Title|text` gives a toast a title).
**Send** does the same as `/cc-toast`, `/cc-warn` and `/cc-warn-clear`; only admins can send.

## Making a rule

Press **+ New rule** and fill in:

| Field | What it is |
|---|---|
| Name | shown in the list; the id is made from it (`Welcome new players` -> `welcome-new-players`) |
| When | the trigger, plus its value when it needs one |
| If | an optional condition and its value |
| Then | a list of actions, run from top to bottom: **+ Add action** adds one (up to 8), **Up** and **Down** change the order, **-** removes one |
| Cooldown | seconds before the same rule can run again (for the same player); empty uses the trigger's default |

The value field shows what it expects for the option you picked.

### When

| Trigger | Value |
|---|---|
| A player joins | - |
| A player joins for the first time | - (counts from when ControlCore 0.16 was installed) |
| A player leaves | - |
| A player enters a world | world name, empty for any |
| A player enters combat / leaves combat | - |
| Every X minutes | 1 to 1440 |
| Every day at | `20:00` (the server's clock) |
| A schedule (cron) | five fields: minute hour day month weekday (see below) |
| A world's TPS drops below | 1 to 60; fires once when it drops, again only after it recovered (default cooldown 300 s) |
| Players online reaches | a number; fires when the count reaches it |
| The server becomes empty | - |
| A setting changes | a setting id like `server:motd`, empty for any |
| A restart countdown starts | - (adds `{seconds}` and `{reason}`) |
| The server is stopping | - (any stop, also Hytale's `/stop`, before players are disconnected) |
| A mod's trigger | `mod:trigger`, listed by `/cc-rules` |

### If

| Condition | Value |
|---|---|
| Player has / lacks permission | a permission node |
| In world | world name |
| Players online between | `2-10` |
| Time of day between | `18:00-23:00` (may cross midnight: `22:00-02:00`) |

### Then

| Action | Value |
|---|---|
| Message the player / everyone / admins | text |
| Title for the player / everyone | `Title | subtitle` |
| Toast for the player / everyone | `title | text`, optionally starting with `success:`, `warning:` or `error:` |
| Warning bar for everyone | `text | minutes` (no minutes: until removed), optionally starting with `warning:` or `error:` |
| Remove a warning bar | the id of the rule that showed it, or `all` |
| Run a command as the server / the player | the command without `/` |
| Switch to a profile | `@vanilla`, `@pvp`, `@creative`, `@event` or a profile's name |
| Change a setting | `id=value`, for example `server:motd=Event tonight!` |
| Mod action | `mod:action`, optionally followed by a value; listed by `/cc-rules` |
| Turn maintenance on | the message kicked and refused players see; empty for the default (kicks everyone who may not join, see [Commands](Commands) `/cc-maintenance`) |
| Turn maintenance off | nothing |
| Make a backup | nothing (needs a backup directory, see [Backups and restarts](Backups-And-Restarts)) |
| Restart the server | the countdown in seconds or like `5m`; empty for the Restart countdown setting |

Placeholders work in every value: `{player}`, `{world}`, `{online}`, `{max}`, `{tps}`, `{time}`, `{rule}`, plus
`{setting}`, `{old}` and `{value}` for "A setting changes" and whatever a mod's trigger adds.

Actions that need a player (message the player, title for the player, command as the player) only work with
player triggers (joins, leaves, enters a world) or a mod's trigger; saving such a rule with a timer is refused.

### Cron schedules

`minute hour day-of-month month day-of-week`, on the server's clock:

| Field | Values |
|---|---|
| minute | 0-59 |
| hour | 0-23 |
| day of month | 1-31 |
| month | 1-12 or `JAN`-`DEC` |
| day of week | 0-7 (0 and 7 are Sunday) or `SUN`-`SAT` |

Each field takes `*` (any), a number, a range `1-5`, a list `1,15`, or a step `*/15` or `8-18/2`. When both day
fields are set, either one matching is enough. Examples: `*/15 * * * *` every 15 minutes, `0 20 * * FRI` Fridays at
20:00, `30 8 * * MON-FRI` weekdays at 8:30, `0 0 1 * *` the first of every month. The rule list shows the next run.

## Managing rules

Each rule shows what it does, when it last ran and (for schedules) when it runs next, with **Turn off/on**, **Test** (runs it once now, ignoring its
trigger, condition and cooldown), **Edit** and **Remove** (press twice). The server setting **Automation rules run**
(`controlcore:automation-enabled`, Mod Settings -> ControlCore: Automation) stops every rule at once.

Rules are saved in `mods/OFATech_ControlCore/AutomationRules.json`.

## Announcements

The **Rules | Announcements** switch at the top of the Automation tab opens rotating announcements: messages sent to
everyone every few minutes. Admins, and players with `ofatech.controlcore.announcements` (they see only this view),
manage them.

- **Turn announcements on/off** starts or stops the rotation.
- **Every N minutes** (1 to 1440), the **order** (*in turn*, or *random*: never the same one twice in a row), the
  **channel** (*chat*, *toast* or *warning bar*) and **Min. players online** (0 for always), then **Save settings**.
  When fewer players are online, that turn is skipped.
- Write a text and press **Add announcement**. Each row has **Up**, **Down**, **Turn on/off** (announcements that
  are off are skipped), **Send now** and **Remove** (press twice).

Texts take the same placeholders and colours as a rule's messages (`{online}`, `{max}`, `{time}`, `{tps}`). For a toast
write `title | text`, optionally starting with `success:`, `warning:` or `error:`; the warning bar stays until the
next announcement. Every change is in History (rollback works), and snapshots can hold them (part
`announcements`; restoring needs an admin). Saved in `mods/OFATech_ControlCore/Announcements.json`. Commands:
`/cc-announce-*` (see [Commands](Commands)).
## Examples

- Welcome new players: *A player joins for the first time* -> *Title for the player* `Welcome {player}! | Read /rules`
- Vote reminder: *Every X minutes* `30` -> *Message everyone* `Vote for us!`
- Event night: *Every day at* `20:00` -> *Switch to a profile* `@event`; another rule at `23:00` -> `@vanilla`
- Lag alert: *A world's TPS drops below* `15` -> *Message admins* `{world} is at {tps} TPS`
