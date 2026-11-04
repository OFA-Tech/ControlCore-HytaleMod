# Backups and restarts

## Backups

ControlCore uses Hytale's own backups: a zip of the whole universe (`universe/`: worlds, players, universe resources)
named like `2026-11-04_18-30-00.zip`, written to config.json's `Backup.Directory`. Hytale moves older backups to an
`archive` folder inside it and deletes the oldest ones by `Backup.MaxCount` and `Backup.ArchiveMaxCount`.

Open **Server Configs -> Backups** (admins and `ofatech.controlcore.backups`):

- **Backup directory.** If none is set, the bar says so; type a folder (relative to the server folder, `backups` by
  default) and press **Set directory**. It works at once for **Back up now**; Hytale's automatic backups
  (`Backup.Enabled`, `Backup.FrequencyMinutes`) start after a restart. The line under it shows those settings; change
  them in the config.json view (search `Backup`).
- **Back up now** shows "Backing up..." and then "Done: name (size)" or the error. Only one backup runs at a time, and
  it is refused when the backup folder's disk has less free space than the universe takes.
- **The list** shows every backup, newest first, with its date, size and whether it is the latest or archived.
  **Delete** and **Restore on next start** both need a second press.

The same from chat or the console: `/cc-backup`, `/cc-backups`, `/cc-backup-delete <name> confirm`,
`/cc-backup-restore <name> confirm`, `/cc-backup-restore-cancel` (see [Commands](Commands)). An automation rule can
**Make a backup**. Every backup made or deleted and every restore planned, cancelled or applied is in History; rolling
back a planned restore cancels it.

## Restoring a backup

A universe cannot be swapped while the server runs, so **Restore on next start** only plans it: ControlCore writes
`PendingRestore.json` in its folder, and Server Configs -> Backups shows "The restore of ... will happen at the next
start" with **Cancel restore**.

At the next start ControlCore applies it during its setup, before Hytale loads any world or player:

1. It checks the zip (every entry must stay inside the universe folder) and deletes `PendingRestore.json`, so a
   failing restore never repeats at every start.
2. It moves the current `universe` folder to `universe-before-restore-<date>` next to it. If that is not possible (for
   example a file is locked), nothing is changed and the server starts with the current universe.
3. It unpacks the backup into a fresh `universe` folder. If that fails, the partial folder is deleted and the old
   universe is moved back.

The result is in History (by "ControlCore (at startup)"), in the server log, in the Backups view, and the first admin
who joins gets a chat line. Failures also show in the Health tab's recent problems. The old universe is never deleted:
remove `universe-before-restore-*` folders yourself once you are happy with the restore.

## Restarts

**Restart** (press twice) is in the restart banner when changes wait for a restart (also on the Mods tab after turning
mods on or off) and in the Health tab, with a countdown: 10 s, 1 min, 5 min, 10 min or the **Restart countdown**
setting (default 60 s). `/cc-restart --in=5m --reason=Update` does the same; it needs `ofatech.controlcore.restart`
(admins have it).

- Everyone gets a warning bar "Server restarting in 4:32" and chat warnings at 10 min, 5 min, 1 min, 30 s and 10 s
  (those that fit in the countdown). While it counts down, the banner and the Health tab show "Restarting in 0:42" with
  **Cancel restart**; cancelling tells everyone.
- At zero, if **Back up before restarting** is on, ControlCore makes a backup first (if it fails, the server still
  restarts and admins are told). Then players are kicked with "Server restarting, back soon!" and the server stops the
  normal way, saving every world.
- **Scheduled restarts** (Mod Settings -> Restarts) takes daily times and cron schedules separated by `;`, for example
  `04:00; 0 16 * * SAT`. The countdown starts early so the server stops at that time. `/cc-restart-status` shows the
  next one and any entry it could not read.

**A server cannot start itself again.** Restart stops the server cleanly; it comes back only if your host panel or
start script starts it again automatically (most hosts do; on your own machine use a loop such as
`:start` / `java -jar ...` / `goto start` in a `.bat`). Otherwise it just stops.

Automation triggers: **A restart countdown starts** (with `{seconds}` and `{reason}`) and **The server is stopping**,
which fires on every stop, also Hytale's `/stop`, before players are disconnected.
