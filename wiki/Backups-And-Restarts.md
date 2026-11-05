# Backups and restarts

## Backups

ControlCore uses Hytale's own backups: a zip of the whole universe (`universe/`: worlds, players, universe resources)
named like `2026-11-04_18-30-00.zip`, written to config.json's `Backup.Directory`. Hytale moves older backups to an
`archive` folder inside it and deletes the oldest ones by `Backup.MaxCount` and `Backup.ArchiveMaxCount`.

Open **Server Configs -> Backups** (admins and `ofatech.controlcore.backups`):

- **Backup directory.** If none is set, the bar says so; type a folder and press **Set directory**: a folder inside
  the server folder (`backups` by default) or an absolute local folder such as `D:/Backups`. A network share
  (`\\host\share`, `//host/share`), a `..` segment, a `:` other than a drive letter and the universe folder (or a
  folder inside it) are refused. Only admins and the console may change it, whatever the setting rights say: the same
  rule applies when `Backup.Directory` is changed in the config.json view, with `/cc-set`, through the settings API
  or by rolling it back in History. Others see the folder read-only. It can't change while a backup runs. It works at
  once for **Back up now**; Hytale's automatic backups
  (`Backup.Enabled`, `Backup.FrequencyMinutes`) start after a restart. The line under it shows those settings; change
  them in the config.json view (search `Backup`).
- **Back up now** shows "Backing up..." and then "Done: name (size)" or the error. Only one backup runs at a time, and
  it is refused when the backup folder's disk has less free space than the universe takes. If Hytale finishes without
  writing a new zip, it says so instead of "Done".
- **The list** shows every backup, newest first, with its date, size and whether it is the latest or archived.
  **Delete** and **Restore on next start** both need a second press. Names may only use letters, digits, `.`, `_` and
  `-`; rename other files to use them.

The same from chat or the console: `/cc-backup`, `/cc-backups`, `/cc-backup-delete <name> confirm`,
`/cc-backup-restore <name> confirm`, `/cc-backup-restore-cancel` (see [Commands](Commands)). An automation rule can
**Make a backup**. Every backup made or deleted and every restore planned, cancelled or applied is in History; rolling
back a planned restore cancels it.

## Restoring a backup

A universe cannot be swapped while the server runs, so **Restore on next start** only plans it: ControlCore copies the
zip to `pending-restore/` in its own folder in the background (the view shows "Copying the backup for the
restore..."; backups, other plans and cancels wait until it is done, and a restart that is due waits for it too, at
most 10 minutes; it can't start while a backup runs), so Hytale's rotation or a backup before
restarting cannot move or delete it (the disk needs room for the copy; the planned backup keeps its badge after Hytale
archives it). It writes `PendingRestore.json` and deletes any other copy left in `pending-restore/`; and Server Configs -> Backups shows "The restore
of ... will happen at the next start" with **Cancel restore** (which also deletes the copy). Only a zip with a `worlds`
folder can be planned; a zip made by hand with everything inside one top folder (`universe/worlds/...`) is unpacked
without that folder. While a restore is planned, **Back up before restarting** is skipped.

A restore is refused when the `universe` folder is a symbolic link or a separate mount (for example a Docker volume
mounted at `universe`): such a folder can't be renamed safely. Restore those by hand while the server is stopped, or
mount the folder that holds `universe` instead.

At the next start ControlCore applies it during its setup, before Hytale loads any world or player:

1. A plan never expires while the server runs. But if the server was off for more than 7 days after the plan (counted
   from the last time it stopped with the plan in place), the plan is dropped instead of applied: admins are told,
   History shows it, and the copy stays in `pending-restore/` until the next restore is planned. This keeps an old plan
   from surprising you after a long break.
2. It checks the zip (every entry must stay inside the universe folder, and there must be a `worlds` folder), checks
   that the disk has room for the unpacked backup, and deletes `PendingRestore.json`, so a failing restore never
   repeats at every start.
3. It unpacks the backup into `universe-restoring-<date>` next to the universe. The universe is not touched yet. If
   unpacking fails, that folder is deleted (or, if it can't be, moved to `universe-failed-restore-<date>`) and the
   server starts with the current universe.
4. Only when the unpacked folder is complete, it renames `universe` to `universe-before-restore-<date>` and the new
   folder to `universe`, then deletes the copy. If the first rename fails (for example a file is locked), nothing
   changes. If the second fails, the original is renamed back. The copy is kept in `pending-restore/` on any failure
   (until the next restore is planned) and the message says so.

If the server is killed during a restore (power loss, Ctrl+C, a host timeout), the next start finds the
`universe-restoring-*` folder. When `universe` is intact and there is no `universe-before-restore-*` folder with the
same date, the half-unpacked folder is deleted and the server starts normally (a warning is logged). When there is one,
the restore was cut between its two renames: ControlCore deletes nothing, renames the restored folder to
`universe-failed-restore-<date>`, logs the problem (also in the Health tab), stops the server and names the folders;
keep the one you want as `universe`, move the others away and start again. When `universe` is missing, ControlCore
does not let Hytale create a new, empty world: it logs the problem, stops the server and names the folders.
`universe-restoring-*` then holds the complete backup and `universe-before-restore-*` the universe from before; rename
the one you want to `universe` and start again.

The result is in History (by "ControlCore (at startup)"), in the server log, in the Backups view, and the first admin
who joins gets a chat line. Failures also show in the Health tab's recent problems. The old universe is never deleted:
`universe-before-restore-*` (and any `universe-failed-restore-*`) folders stay until you delete them by hand, once
you are happy with the restore. They take as much disk space as the universe.

## Restarts

**Restart** (press twice) is in the restart banner when changes wait for a restart (also on the Mods tab after turning
mods on or off) and in the Health tab, with a countdown: 10 s, 1 min, 5 min, 10 min or the **Restart countdown**
setting (default 60 s). `/cc-restart --in=5m --reason=Update` does the same; it needs `ofatech.controlcore.restart`
(admins have it).

- Everyone gets a warning bar "Server restarting in 4:32" and chat warnings at 10 min, 5 min, 1 min, 30 s and 10 s
  (those that fit in the countdown). While it counts down, the banner and the Health tab show "Restarting in 0:42" with
  **Cancel restart**; cancelling tells everyone.
- At zero, if **Back up before restarting** is on and no restore is planned, ControlCore makes a backup first (if it
  fails or takes more than 10 minutes, the server still restarts and admins are told, each in their own language). A backup that is already running
  is waited for, also at most 10 minutes. If the server cannot be stopped, admins are told and Restart can be pressed
  again; a backup that finishes after that does not stop the server by itself. Then players are kicked with "Server restarting, back soon!" and the server stops the
  normal way, saving every world.
- **Scheduled restarts** (Mod Settings -> Restarts) takes daily times and cron schedules separated by `;`, for example
  `04:00; 0 16 * * SAT`. The countdown starts early so the server stops at that time; if the server comes up (or is
  paused) inside the countdown, it starts with the time left (at least 10 s). `/cc-restart-status` shows the
  next one and any entry it could not read.

**A server cannot start itself again.** Restart stops the server cleanly; it comes back only if your host panel or
start script starts it again automatically (most hosts do; on your own machine use a loop such as
`:start` / `java -jar ...` / `goto start` in a `.bat`). Otherwise it just stops.

Automation triggers: **A restart countdown starts** (with `{seconds}` and `{reason}`) and **The server is stopping**,
which fires on every stop, also Hytale's `/stop`, before players are disconnected.
