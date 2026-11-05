package net.ofatech.controlcore.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Backups of the universe (worlds, players and universe resources), as in the control panel's Server Configs ->
 * Backups view and the {@code /cc-backup*} commands. Get it with {@link ControlCoreApi#backups()}.
 *
 * <p>Backups are Hytale's own zip files, made in config.json's {@code Backup.Directory}; older ones are moved to its
 * {@code archive} folder by Hytale. Only one backup runs at a time. A restore never happens while the server runs:
 * {@link #restoreOnNextStart(String)} plans it (keeping its own copy of the zip), and ControlCore applies it at the
 * next start, before the worlds load, after moving the current universe aside to
 * {@code universe-before-restore-<date>}.
 *
 * <p>The API acts with full rights, like the console. {@link #actingFor(PlayerId)} gives the same API with a
 * player's rights: admins and players with {@code ofatech.controlcore.backups} may use it.
 *
 * <pre>{@code
 * api.backups().create().thenAccept(result -> result.onSuccess(backup -> log(backup.name())));
 * }</pre>
 *
 * @since API 2.5.0
 */
public interface BackupsApi {

    /**
     * Returns this API acting for a player. Players without the backups right get every change refused with
     * {@code Backup.Forbidden} and see no backups.
     *
     * @param player the player whose rights apply
     * @return the backups API restricted to {@code player}'s rights
     * @throws NullPointerException if {@code player} is {@code null}
     */
    BackupsApi actingFor(PlayerId player);

    /**
     * Tells whether a backup directory is set, which backups need.
     *
     * @return {@code true} if config.json has a {@code Backup.Directory}
     */
    boolean configured();

    /**
     * Sets config.json's {@code Backup.Directory}. Recorded in History like any config.json change. Acting for a
     * player, only admins may change it ({@code Backup.DirectoryForbidden} otherwise).
     *
     * @param directory a folder inside the server folder, relative to it (no drive letter, no {@code ..}, no leading
     *     slash); {@code null} or empty for {@code backups}
     * @return a message, or an error such as {@code Backup.Forbidden}, {@code Backup.DirectoryForbidden} or
     *     {@code Backup.BadDirectory}
     */
    Result<String> setDirectory(String directory);

    /**
     * Starts a backup. Refused while another one runs ({@code Backup.Running}), when no directory is set
     * ({@code Backup.NotConfigured}) or when the backup directory's disk has less free space than the universe
     * takes ({@code Backup.NoSpace}). Recorded in History.
     *
     * @return a future completed with the new backup or an error once it has finished
     */
    CompletableFuture<Result<Backup>> create();

    /**
     * Lists the backups, newest first.
     *
     * @return the latest and archived backups; empty when none, not configured or not allowed
     */
    List<Backup> list();

    /**
     * Deletes a backup. Refused for the backup planned for restore. Recorded in History.
     *
     * @param name the name as {@link Backup#name()} gives it, for example {@code archive/2026-01-02_03-04-05.zip}
     * @return a message, or an error such as {@code Backup.Unknown} or {@code Backup.InUse}
     */
    Result<String> delete(String name);

    /**
     * Plans a restore of a backup at the next server start. Recorded in History, where rolling it back cancels it.
     *
     * @param name the name as {@link Backup#name()} gives it
     * @return a message, or an error such as {@code Backup.Unknown} or {@code Backup.RestorePending}
     */
    Result<String> restoreOnNextStart(String name);

    /**
     * Cancels the planned restore. Recorded in History.
     *
     * @return a message, or {@code Backup.NoRestore} when none is planned
     */
    Result<String> cancelRestore();

    /**
     * Reads the planned restore.
     *
     * @return the restore planned for the next start, if any
     */
    Optional<PendingRestore> pendingRestore();

    /**
     * One backup file.
     *
     * @param name its name inside the backup directory, with {@code archive/} in front for archived ones
     * @param at when it was written
     * @param size its size in bytes
     * @param archived whether Hytale moved it to the archive folder
     */
    record Backup(String name, Instant at, long size, boolean archived) {}

    /**
     * A restore planned for the next start.
     *
     * @param name the backup to restore
     * @param by who planned it; empty for a mod
     * @param at when it was planned
     */
    record PendingRestore(String name, String by, Instant at) {}
}
