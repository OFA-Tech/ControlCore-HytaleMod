package net.ofatech.controlcore.application.backups;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import net.ofatech.controlcore.application.history.ChangeContext;
import net.ofatech.controlcore.application.history.ChangeHistory;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.text.CommonErrors;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Actor;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Kind;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Source;
import net.ofatech.controlcore.domain.model.server.BackupFile;
import net.ofatech.controlcore.domain.model.server.BackupsView;
import net.ofatech.controlcore.domain.model.server.PendingRestore;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.config.PendingRestoreStore;
import net.ofatech.controlcore.domain.ports.game.Messenger;
import net.ofatech.controlcore.domain.ports.game.Permissions;
import net.ofatech.controlcore.domain.ports.game.ServerBackups;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class BackupService {

    public static final String NODE = "backups";
    public static final String DEFAULT_DIRECTORY = "backups";
    static final String CREATED = "created";
    static final String DELETED = "deleted";
    static final String RESTORE = "restore";
    static final String RESTORED = "restored";
    static final String FAILED = "failed";

    private static final SettingId DIRECTORY = ServerConfigEditor.idOf("Backup.Directory");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    record Outcome(String name, boolean ok, String detail) {}

    private final ServerBackups backups;
    private final PendingRestoreStore store;
    private final Permissions permissions;
    private final SettingPermissionPolicy policy;
    private final ServerConfigEditor serverConfig;
    private final ChangeHistory history;
    private final Messenger messenger;
    private final Clock clock;
    private final Texts texts;
    private final Log log;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Optional<Outcome> startup = Optional.empty();
    private volatile boolean startupRecorded;
    private volatile boolean startupShown;
    private volatile Optional<Text> last = Optional.empty();

    public BackupService(
            ServerBackups backups,
            PendingRestoreStore store,
            Permissions permissions,
            SettingPermissionPolicy policy,
            ServerConfigEditor serverConfig,
            ChangeHistory history,
            Messenger messenger,
            Clock clock,
            Texts texts,
            Log log) {
        this.backups = backups;
        this.store = store;
        this.permissions = permissions;
        this.policy = policy;
        this.serverConfig = serverConfig;
        this.history = history;
        this.messenger = messenger;
        this.clock = clock;
        this.texts = texts;
        this.log = log;
    }

    public boolean mayManage(Optional<PlayerId> who) {
        return who.isEmpty() || policy.isAdmin(who.get()) || permissions.hasOwn(who.get(), NODE);
    }

    public boolean configured() {
        return backups.directory().isPresent();
    }

    public boolean running() {
        return running.get();
    }

    public Optional<PendingRestore> pending() {
        return store.load();
    }

    public List<BackupFile> list() {
        Optional<Path> directory = backups.directory();
        if (directory.isEmpty()) {
            return List.of();
        }
        List<BackupFile> found = new ArrayList<>();
        collect(directory.get(), "", false, found);
        collect(directory.get().resolve(UniverseFiles.ARCHIVE), UniverseFiles.ARCHIVE + "/", true, found);
        found.sort(Comparator.comparing(BackupFile::at).reversed());
        return found;
    }

    private void collect(Path folder, String prefix, boolean archived, List<BackupFile> found) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (Files.isRegularFile(file) && name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    found.add(new BackupFile(
                            prefix + name, Files.getLastModifiedTime(file).toInstant(), Files.size(file), archived));
                }
            }
        } catch (IOException e) {
            log.warn("Could not list the backups in " + folder + ": " + e.getMessage());
        }
    }

    public Optional<BackupFile> find(String name) {
        String wanted = name == null ? "" : name.strip().replace('\\', '/');
        return list().stream().filter(file -> file.name().equals(wanted)).findFirst();
    }

    private Result<Path> file(String name) {
        String wanted = name == null ? "" : name.strip().replace('\\', '/');
        Optional<Path> directory = backups.directory();
        if (directory.isEmpty()) {
            return Result.failure(notConfigured());
        }
        return UniverseFiles.resolve(directory.get(), wanted)
                .map(Result::success)
                .orElseGet(() -> Result.failure(Error.notFound(
                        "Backup.Unknown",
                        Text.key(
                                        "controlcore.errors.backup.unknown",
                                        "There is no backup {name}. /cc-backups lists them.")
                                .with("name", wanted))));
    }

    public CompletableFuture<Result<BackupFile>> create(Optional<PlayerId> who, Translator tr) {
        if (!mayManage(who)) {
            return CompletableFuture.completedFuture(Result.failure(forbidden()));
        }
        Optional<Path> directory = backups.directory();
        if (directory.isEmpty()) {
            return CompletableFuture.completedFuture(Result.failure(notConfigured()));
        }
        if (!running.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(Result.failure(Error.conflict(
                    "Backup.Running",
                    Text.key(
                            "controlcore.errors.backup.running",
                            "A backup is already running. Wait for it to finish."))));
        }
        ChangeContext.Attribution by = ChangeContext.current();
        Instant started = clock.now();
        CompletableFuture<Result<BackupFile>> work;
        try {
            work = CompletableFuture.supplyAsync(() -> room(directory.get()))
                    .thenCompose(room -> room.isFailure()
                            ? CompletableFuture.completedFuture(Result.<BackupFile>failure(room.error()))
                            : backups.run().thenApply(done -> newest(directory.get(), started)));
        } catch (RuntimeException e) {
            work = CompletableFuture.failedFuture(e);
        }
        return work.handle((result, failure) -> {
            Result<BackupFile> done = failure == null ? result : Result.failure(failed(failure));
            if (done.isSuccess()) {
                ChangeContext.as(by, () -> {
                    history.record(Kind.BACKUP, done.value().name(), "", CREATED, false);
                    return null;
                });
                last = Optional.of(Text.key("controlcore.backups.done", "Done: {name} ({size}).")
                        .with(
                                "name",
                                done.value().name(),
                                "size",
                                size(done.value().size())));
                log.info("Backup made: " + done.value().name() + " ("
                        + size(done.value().size()) + ")");
            } else {
                last = Optional.of(Text.key("controlcore.backups.failed", "The backup failed: {reason}")
                        .with("reason", texts.forServer().error(done.error())));
                log.warn("Backup failed: " + texts.forServer().error(done.error()));
            }
            running.set(false);
            return done;
        });
    }

    Result<Boolean> room(Path directory) {
        try {
            long needed = UniverseFiles.size(backups.universe());
            long free = backups.usableSpace(UniverseFiles.nearestExisting(directory));
            if (free >= 0 && free < needed) {
                return Result.failure(Error.conflict(
                        "Backup.NoSpace",
                        Text.key(
                                        "controlcore.errors.backup.no_space",
                                        "Not enough free disk space for a backup: the universe is {needed}, the backup folder's disk has {free} free.")
                                .with("needed", size(needed), "free", size(free))));
            }
            return Result.success(true);
        } catch (IOException | RuntimeException e) {
            return Result.failure(failed(e));
        }
    }

    private Result<BackupFile> newest(Path directory, Instant started) {
        Instant since = started.minusSeconds(5);
        return list().stream()
                .filter(file -> !file.archived() && !file.at().isBefore(since))
                .findFirst()
                .or(() -> list().stream().filter(file -> !file.archived()).findFirst())
                .map(Result::success)
                .orElseGet(() -> Result.failure(Error.failure(
                        "Backup.Missing",
                        Text.key(
                                        "controlcore.errors.backup.missing",
                                        "The backup finished but no zip appeared in {directory}.")
                                .with("directory", directory.toString()))));
    }

    public Result<String> delete(String name, Optional<PlayerId> who, Translator tr) {
        if (!mayManage(who)) {
            return Result.failure(forbidden());
        }
        Result<Path> file = file(name);
        if (file.isFailure()) {
            return Result.failure(file.error());
        }
        String shown = name.strip().replace('\\', '/');
        if (pending().map(PendingRestore::name).filter(shown::equals).isPresent()) {
            return Result.failure(Error.conflict(
                    "Backup.InUse",
                    Text.key(
                                    "controlcore.errors.backup.in_use",
                                    "{name} is going to be restored at the next start. Cancel the restore first.")
                            .with("name", shown)));
        }
        try {
            Files.delete(file.value());
        } catch (IOException e) {
            return Result.failure(failed(e));
        }
        history.record(Kind.BACKUP, shown, "", DELETED, false);
        log.info("Backup deleted: " + shown);
        return Result.success(tr.t("controlcore.backups.deleted", "name", shown));
    }

    public synchronized Result<String> restoreOnNextStart(String name, Optional<PlayerId> who, Translator tr) {
        if (!mayManage(who)) {
            return Result.failure(forbidden());
        }
        Result<Path> file = file(name);
        if (file.isFailure()) {
            return Result.failure(file.error());
        }
        String shown = name.strip().replace('\\', '/');
        Optional<PendingRestore> now = pending();
        if (now.isPresent()) {
            return Result.failure(Error.conflict(
                    "Backup.RestorePending",
                    Text.key(
                                    "controlcore.errors.backup.restore_pending",
                                    "The restore of {name} is already planned. Cancel it first.")
                            .with("name", now.get().name())));
        }
        String by = ChangeContext.current().actor().name();
        if (!store.save(new PendingRestore(shown, by, clock.now()))) {
            return Result.failure(CommonErrors.notSaved());
        }
        history.record(Kind.BACKUP, shown, "", RESTORE, false);
        log.info("Backup " + shown + " will be restored at the next start");
        return Result.success(tr.t("controlcore.backups.restore_planned", "name", shown));
    }

    public synchronized Result<String> cancelRestore(Optional<PlayerId> who, Translator tr) {
        if (!mayManage(who)) {
            return Result.failure(forbidden());
        }
        Optional<PendingRestore> now = pending();
        if (now.isEmpty()) {
            return Result.failure(Error.conflict(
                    "Backup.NoRestore", Text.key("controlcore.errors.backup.no_restore", "No restore is planned.")));
        }
        if (!store.clear()) {
            return Result.failure(CommonErrors.notSaved());
        }
        history.record(Kind.BACKUP, now.get().name(), RESTORE, "", false);
        log.info("Restore of backup " + now.get().name() + " cancelled");
        return Result.success(
                tr.t("controlcore.backups.restore_cancelled", "name", now.get().name()));
    }

    public Result<?> rollback(String target, String old, String now, Optional<PlayerId> who) {
        Translator tr = texts.forPlayer(who);
        if (RESTORE.equals(now)) {
            if (pending().map(PendingRestore::name).filter(target::equals).isEmpty()) {
                return Result.failure(Error.conflict(
                        "Backup.NoRestore",
                        Text.key("controlcore.errors.backup.no_restore", "No restore is planned.")));
            }
            return cancelRestore(who, tr);
        }
        if (RESTORE.equals(old) && now.isEmpty()) {
            return restoreOnNextStart(target, who, tr);
        }
        return Result.failure(Error.validation(
                "Backup.NoRollback",
                Text.key(
                        "controlcore.errors.backup.no_rollback",
                        "Making, deleting or restoring a backup can't be rolled back.")));
    }

    public void applyPendingRestore() {
        Optional<PendingRestore> pending;
        try {
            pending = store.load();
        } catch (RuntimeException e) {
            log.error("Could not read the planned backup restore", e);
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        String name = pending.get().name();
        if (!store.clear()) {
            log.error(
                    "Could not delete PendingRestore.json, so backup " + name
                            + " was not restored (it would restore again at every start). Delete the file by hand.",
                    null);
            startup = Optional.of(new Outcome(name, false, "PendingRestore.json could not be deleted"));
            return;
        }
        Path universe = backups.universe();
        Path aside = UniverseFiles.asideOf(
                universe, STAMP.format(LocalDateTime.ofInstant(clock.now(), ZoneId.systemDefault())));
        Optional<Path> zip = backups.directory().flatMap(directory -> UniverseFiles.resolve(directory, name));
        if (zip.isEmpty()) {
            log.error(
                    "Backup " + name + " was not restored: the file is gone or the backup directory is not set."
                            + " The universe was left as it was.",
                    null);
            startup = Optional.of(new Outcome(name, false, "the backup file is gone"));
            return;
        }
        try {
            UniverseFiles.restore(zip.get(), universe, aside);
            log.info("Backup " + name + " restored; the previous universe was moved to " + aside);
            startup = Optional.of(new Outcome(name, true, aside.getFileName().toString()));
        } catch (IOException | RuntimeException e) {
            boolean kept = Files.exists(universe) || !Files.exists(aside);
            String where = kept ? " The universe was left as it was." : " The old universe is in " + aside + ".";
            log.error("Backup " + name + " could not be restored." + where, e);
            startup = Optional.of(new Outcome(name, false, String.valueOf(e.getMessage()) + where));
        }
    }

    public void recordStartup() {
        Optional<Outcome> outcome = startup;
        if (outcome.isEmpty() || startupRecorded) {
            return;
        }
        startupRecorded = true;
        Translator tr = texts.forServer();
        ChangeContext.as(
                new ChangeContext.Attribution(
                        Actor.console(tr.t("controlcore.backups.startup_actor")), Source.AUTOMATION, 0),
                () -> {
                    history.record(
                            Kind.BACKUP,
                            outcome.get().name(),
                            RESTORE,
                            outcome.get().ok() ? RESTORED : FAILED,
                            false);
                    return null;
                });
    }

    public Optional<String> startupText(Translator tr) {
        return startup.map(outcome -> outcome.ok()
                ? tr.t("controlcore.backups.restored_at_start", "name", outcome.name(), "aside", outcome.detail())
                : tr.t(
                        "controlcore.backups.restore_failed_at_start",
                        "name",
                        outcome.name(),
                        "reason",
                        outcome.detail()));
    }

    public void showStartupTo(PlayerId player) {
        if (startup.isEmpty() || startupShown || !mayManage(Optional.of(player))) {
            return;
        }
        startupShown = true;
        startupText(texts.forPlayer(Optional.of(player))).ifPresent(text -> messenger.send(player, text));
    }

    public String status(Translator tr) {
        if (!configured()) {
            return tr.t("controlcore.backups.not_configured");
        }
        if (running()) {
            return tr.t("controlcore.backups.running");
        }
        return last.map(tr::translate)
                .or(() -> startupText(tr))
                .orElseGet(() -> tr.t(
                        "controlcore.backups.configured",
                        "directory",
                        backups.directory().map(Path::toString).orElse(""),
                        "count",
                        list().size()));
    }

    public String settings(Translator tr) {
        return tr.t(
                "controlcore.backups.settings",
                "enabled",
                config("Backup.Enabled"),
                "minutes",
                config("Backup.FrequencyMinutes"),
                "max",
                config("Backup.MaxCount"),
                "archive",
                config("Backup.ArchiveMaxCount"));
    }

    private String config(String path) {
        Result<SettingSnapshot> read = serverConfig.read(ServerConfigEditor.idOf(path), Optional.empty());
        return read.isSuccess() && !read.value().value().isBlank()
                ? read.value().value()
                : "-";
    }

    public String directoryText() {
        Result<SettingSnapshot> read = serverConfig.read(DIRECTORY, Optional.empty());
        return read.isSuccess()
                ? read.value().value()
                : backups.directory().map(Path::toString).orElse("");
    }

    public Result<String> setDirectory(String directory, Optional<PlayerId> who, Translator tr) {
        if (!mayManage(who)) {
            return Result.failure(forbidden());
        }
        String value = directory == null || directory.isBlank() ? DEFAULT_DIRECTORY : directory.strip();
        Result<SettingSnapshot> written = serverConfig.write(DIRECTORY, Optional.empty(), value);
        if (written.isFailure()) {
            return Result.failure(written.error());
        }
        return Result.success(tr.t("controlcore.backups.directory_set", "directory", value));
    }

    public String describe(BackupFile file, Translator tr) {
        return tr.t(
                file.archived() ? "controlcore.backups.line_archived" : "controlcore.backups.line_latest",
                "name",
                file.name(),
                "date",
                WHEN.format(LocalDateTime.ofInstant(file.at(), ZoneId.systemDefault())),
                "size",
                size(file.size()));
    }

    public String pendingText(PendingRestore restore, Translator tr) {
        return tr.t("controlcore.backups.pending", "name", restore.name());
    }

    public String what(String target, Translator tr) {
        return tr.t("controlcore.history.what.backup", "name", target);
    }

    public String shown(String value, Translator tr) {
        return switch (value) {
            case CREATED -> tr.t("controlcore.history.value.backup_created");
            case DELETED -> tr.t("controlcore.history.value.backup_deleted");
            case RESTORE -> tr.t("controlcore.history.value.backup_restore");
            case RESTORED -> tr.t("controlcore.history.value.backup_restored");
            case FAILED -> tr.t("controlcore.history.value.backup_failed");
            default -> tr.t("controlcore.history.value.backup_none");
        };
    }

    static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    public BackupsView viewedBy(PlayerId viewer) {
        Optional<PlayerId> who = Optional.of(viewer);
        Translator tr = texts.forPlayer(who);
        return new BackupsView() {
            @Override
            public boolean configured() {
                return BackupService.this.configured();
            }

            @Override
            public String directory() {
                return directoryText();
            }

            @Override
            public String status() {
                return BackupService.this.status(tr);
            }

            @Override
            public String settings() {
                return BackupService.this.settings(tr);
            }

            @Override
            public Optional<String> pending() {
                return BackupService.this.pending().map(restore -> pendingText(restore, tr));
            }

            @Override
            public boolean running() {
                return BackupService.this.running();
            }

            @Override
            public List<Row> backups() {
                Optional<String> planned = BackupService.this.pending().map(PendingRestore::name);
                return list().stream()
                        .map(file -> new Row(
                                file.name(),
                                describe(file, tr),
                                planned.filter(file.name()::equals).isPresent()))
                        .toList();
            }

            @Override
            public Result<String> setDirectory(String directory) {
                return BackupService.this.setDirectory(directory, who, tr);
            }

            @Override
            public CompletableFuture<Result<String>> backUpNow() {
                return create(who, tr)
                        .thenApply(result -> result.map(file ->
                                tr.t("controlcore.backups.done", "name", file.name(), "size", size(file.size()))));
            }

            @Override
            public Result<String> delete(String name) {
                return BackupService.this.delete(name, who, tr);
            }

            @Override
            public Result<String> restoreOnNextStart(String name) {
                return BackupService.this.restoreOnNextStart(name, who, tr);
            }

            @Override
            public Result<String> cancelRestore() {
                return BackupService.this.cancelRestore(who, tr);
            }
        };
    }

    static Error notConfigured() {
        return Error.unavailable(
                "Backup.NotConfigured",
                Text.key(
                        "controlcore.errors.backup.not_configured",
                        "Backups are not set up: set Backup directory in Server Configs (config.json) or with /cc-backup --directory=backups."));
    }

    private static Error failed(Throwable failure) {
        Throwable cause =
                failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        return Error.failure(
                "Backup.Failed",
                Text.key("controlcore.errors.backup.failed", "The backup failed: {reason}")
                        .with("reason", String.valueOf(cause.getMessage())));
    }

    static Error forbidden() {
        return Error.forbidden(
                "Backup.Forbidden", Text.key("controlcore.errors.backup.forbidden", "You may not manage backups."));
    }
}
