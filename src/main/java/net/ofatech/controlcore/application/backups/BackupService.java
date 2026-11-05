package net.ofatech.controlcore.application.backups;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
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

    record Outcome(String name, boolean ok, Text detail, List<Text> notes, String original) {

        Outcome(String name, boolean ok, Text detail) {
            this(name, ok, detail, List.of(), "");
        }
    }

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

    public boolean mayChangeDirectory(Optional<PlayerId> who) {
        return who.isEmpty() || policy.isAdmin(who.get());
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
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        && name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
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
        if (!UniverseFiles.NAME.matcher(wanted).matches() && find(wanted).isPresent()) {
            return Result.failure(Error.validation(
                    "Backup.BadName",
                    Text.key(
                                    "controlcore.errors.backup.bad_name",
                                    "{name} can't be used: backup names may only have letters, digits, . _ and -. Rename the file.")
                            .with("name", wanted)));
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
            return CompletableFuture.completedFuture(Result.failure(busy()));
        }
        ChangeContext.Attribution by = ChangeContext.current();
        CompletableFuture<Result<BackupFile>> work;
        try {
            work = CompletableFuture.supplyAsync(() -> room(directory.get())).thenCompose(room -> {
                if (room.isFailure()) {
                    return CompletableFuture.completedFuture(Result.<BackupFile>failure(room.error()));
                }
                Set<String> before = names();
                return backups.run().thenApply(done -> newest(directory.get(), before));
            });
        } catch (RuntimeException e) {
            work = CompletableFuture.failedFuture(e);
        }
        return work.handle((result, failure) -> {
            try {
                return finished(failure == null ? result : Result.failure(failed(failure)), by);
            } finally {
                running.set(false);
            }
        });
    }

    private Result<BackupFile> finished(Result<BackupFile> done, ChangeContext.Attribution by) {
        if (done.isSuccess()) {
            ChangeContext.as(by, () -> {
                history.record(Kind.BACKUP, done.value().name(), "", CREATED, false);
                return null;
            });
            last = Optional.of(Text.key("controlcore.backups.done", "Done: {name} ({size}).")
                    .with("name", done.value().name(), "size", size(done.value().size())));
            log.info("Backup made: " + done.value().name() + " ("
                    + size(done.value().size()) + ")");
        } else {
            last = Optional.of(Text.key("controlcore.backups.failed", "The backup failed: {reason}")
                    .with("reason", texts.forServer().error(done.error())));
            log.warn("Backup failed: " + texts.forServer().error(done.error()));
        }
        return done;
    }

    private Set<String> names() {
        return list().stream().map(BackupFile::name).collect(Collectors.toSet());
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

    private Result<BackupFile> newest(Path directory, Set<String> before) {
        return list().stream()
                .filter(file -> !file.archived() && !before.contains(file.name()))
                .findFirst()
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
        Result<Path> copy = copyForRestore(file.value(), shown);
        if (copy.isFailure()) {
            return Result.failure(copy.error());
        }
        String by = ChangeContext.current().actor().name();
        if (!store.save(new PendingRestore(shown, by, clock.now(), copy.value().toString()))) {
            deleteCopy(copy.value());
            return Result.failure(CommonErrors.notSaved());
        }
        history.record(Kind.BACKUP, shown, "", RESTORE, false);
        log.info("Backup " + shown + " will be restored at the next start");
        return Result.success(tr.t("controlcore.backups.restore_planned", "name", shown));
    }

    private Result<Path> copyForRestore(Path source, String shown) {
        try {
            UniverseFiles.layout(source, backups.universe());
        } catch (UniverseFiles.NotUniverse e) {
            return Result.failure(Error.validation("Backup.NotUniverse", notUniverse()));
        } catch (IOException | RuntimeException e) {
            return Result.failure(Error.validation(
                    "Backup.Unreadable",
                    Text.key("controlcore.errors.backup.unreadable", "{name} can't be restored: {reason}")
                            .with("name", shown, "reason", String.valueOf(e.getMessage()))));
        }
        Path folder = store.copies();
        Path copy = folder.resolve(source.getFileName().toString());
        try {
            long needed = Files.size(source);
            long free = backups.usableSpace(UniverseFiles.nearestExisting(folder));
            if (free >= 0 && free < needed) {
                return Result.failure(Error.conflict(
                        "Backup.NoSpace",
                        Text.key(
                                        "controlcore.errors.backup.no_space_copy",
                                        "Not enough free disk space to keep a copy of {name} for the restore: it is {needed}, the disk has {free} free.")
                                .with("name", shown, "needed", size(needed), "free", size(free))));
            }
            Files.createDirectories(folder);
            Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
            return Result.success(copy);
        } catch (IOException | RuntimeException e) {
            deleteCopy(copy);
            return Result.failure(Error.failure(
                    "Backup.CopyFailed",
                    Text.key("controlcore.errors.backup.copy_failed", "Could not copy {name} for the restore: {reason}")
                            .with("name", shown, "reason", String.valueOf(e.getMessage()))));
        }
    }

    private Optional<Path> copyOf(PendingRestore restore) {
        if (restore.copy().isBlank()) {
            return Optional.empty();
        }
        try {
            Path copy = Path.of(restore.copy()).toAbsolutePath().normalize();
            Path folder = store.copies().toAbsolutePath().normalize();
            return copy.startsWith(folder) && Files.isRegularFile(copy, LinkOption.NOFOLLOW_LINKS)
                    ? Optional.of(copy)
                    : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private void deleteCopy(Path copy) {
        try {
            Files.deleteIfExists(copy);
        } catch (IOException e) {
            log.warn("Could not delete the restore copy " + copy + ": " + e.getMessage());
        }
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
        copyOf(now.get()).ifPresent(this::deleteCopy);
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
            startup = Optional.of(new Outcome(name, false, Text.of("PendingRestore.json could not be deleted")));
            return;
        }
        Path universe = backups.universe();
        Path aside = UniverseFiles.asideOf(
                universe, STAMP.format(LocalDateTime.ofInstant(clock.now(), ZoneId.systemDefault())));
        Optional<Path> copy = copyOf(pending.get());
        Optional<Path> zip =
                copy.or(() -> backups.directory().flatMap(directory -> UniverseFiles.resolve(directory, name)));
        if (zip.isEmpty()) {
            log.error(
                    "Backup " + name + " was not restored: the file is gone or the backup directory is not set."
                            + " The universe was left as it was.",
                    null);
            startup = Optional.of(new Outcome(name, false, Text.of("the backup file is gone")));
            return;
        }
        try {
            UniverseFiles.Layout layout = UniverseFiles.layout(zip.get(), universe);
            long free = backups.usableSpace(UniverseFiles.nearestExisting(universe));
            if (free >= 0 && free < layout.unpackedSize()) {
                throw new NoSpace(layout.unpackedSize(), free);
            }
            UniverseFiles.restore(zip.get(), universe, aside, layout);
            log.info("Backup " + name + " restored; the previous universe was moved to " + aside);
            startup = Optional.of(
                    new Outcome(name, true, Text.of(aside.getFileName().toString())));
            copy.ifPresent(this::deleteCopy);
        } catch (IOException | RuntimeException e) {
            startup = Optional.of(failedRestore(name, e, universe, aside, copy));
            Outcome failed = startup.get();
            Translator tr = texts.forServer();
            log.error(
                    "Backup " + name + " could not be restored: " + tr.translate(failed.detail()) + " "
                            + String.join(
                                    " ",
                                    failed.notes().stream().map(tr::translate).toList()),
                    e);
        }
    }

    private static final class NoSpace extends IOException {
        private static final long serialVersionUID = 1L;
        private final long needed;
        private final long free;

        NoSpace(long needed, long free) {
            super("not enough free disk space");
            this.needed = needed;
            this.free = free;
        }
    }

    private Outcome failedRestore(String name, Exception e, Path universe, Path aside, Optional<Path> copy) {
        Text reason = switch (e) {
            case UniverseFiles.NotUniverse ignored -> notUniverse();
            case NoSpace space ->
                Text.key(
                                "controlcore.errors.backup.no_space_restore",
                                "Not enough free disk space to restore: the backup unpacks to {needed}, the disk has {free} free.")
                        .with("needed", size(space.needed), "free", size(space.free));
            default -> Text.of(String.valueOf(e.getMessage()));
        };
        List<Text> notes = new ArrayList<>();
        String original = "";
        if (Files.exists(aside)) {
            original = aside.toString();
            notes.add(Text.key(
                            "controlcore.backups.universe_aside",
                            "The original universe is in {aside}; the universe folder may be missing or incomplete.")
                    .with("aside", aside.toString()));
        } else {
            notes.add(Text.key("controlcore.backups.universe_kept", "The universe was left as it was."));
        }
        Path partial = UniverseFiles.failedOf(aside);
        if (Files.exists(partial)) {
            notes.add(Text.key("controlcore.backups.universe_partial", "The partly restored files are in {folder}.")
                    .with("folder", partial.toString()));
        }
        copy.ifPresent(
                kept -> notes.add(Text.key("controlcore.backups.copy_kept", "The copy of the backup is kept in {copy}.")
                        .with("copy", kept.toString())));
        return new Outcome(name, false, reason, List.copyOf(notes), original);
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
                            outcome.get().ok()
                                    ? RESTORED
                                    : outcome.get().original().isEmpty()
                                            ? FAILED
                                            : FAILED + ":" + outcome.get().original(),
                            false);
                    return null;
                });
    }

    public Optional<String> startupText(Translator tr) {
        return startup.map(outcome -> outcome.ok()
                ? tr.t(
                        "controlcore.backups.restored_at_start",
                        "name",
                        outcome.name(),
                        "aside",
                        tr.translate(outcome.detail()))
                : tr.t(
                                "controlcore.backups.restore_failed_at_start",
                                "name",
                                outcome.name(),
                                "reason",
                                tr.translate(outcome.detail()))
                        + outcome.notes().stream()
                                .map(note -> " " + tr.translate(note))
                                .collect(Collectors.joining()));
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
                Boolean.parseBoolean(config("Backup.Enabled"))
                        ? tr.t("controlcore.history.value.on")
                        : tr.t("controlcore.history.value.off"),
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
        if (!mayChangeDirectory(who)) {
            return Result.failure(Error.forbidden(
                    "Backup.DirectoryForbidden",
                    Text.key(
                            "controlcore.errors.backup.directory_forbidden",
                            "Only admins and the console may change the backup directory.")));
        }
        String value = directory == null || directory.isBlank() ? DEFAULT_DIRECTORY : directory.strip();
        if (!insideServerFolder(value)) {
            return Result.failure(Error.validation(
                    "Backup.BadDirectory",
                    Text.key(
                                    "controlcore.errors.backup.bad_directory",
                                    "'{directory}' can't be the backup directory: use a folder inside the server folder, for example backups (no drive letter, no .. and no leading slash).")
                            .with("directory", value)));
        }
        Result<SettingSnapshot> written = serverConfig.write(DIRECTORY, Optional.empty(), value);
        if (written.isFailure()) {
            return Result.failure(written.error());
        }
        return Result.success(tr.t("controlcore.backups.directory_set", "directory", value));
    }

    static boolean insideServerFolder(String value) {
        if (value.startsWith("/") || value.startsWith("\\") || value.contains(":")) {
            return false;
        }
        for (String part : value.split("[/\\\\]")) {
            if (part.strip().equals("..")) {
                return false;
            }
        }
        try {
            Path server = Path.of("").toAbsolutePath().normalize();
            Path relative = Path.of(value);
            Path resolved = server.resolve(relative).normalize();
            return !relative.isAbsolute() && resolved.startsWith(server) && !resolved.equals(server);
        } catch (InvalidPathException e) {
            return false;
        }
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
        if (value.startsWith(FAILED + ":")) {
            return tr.t("controlcore.history.value.backup_failed_aside", "aside", value.substring(FAILED.length() + 1));
        }
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

    static Error busy() {
        return Error.conflict(
                "Backup.Running",
                Text.key("controlcore.errors.backup.running", "A backup is already running. Wait for it to finish."));
    }

    private static Text notUniverse() {
        return Text.key(
                "controlcore.errors.backup.not_universe",
                "This zip is not a Hytale universe backup (it has no worlds folder).");
    }

    static Error forbidden() {
        return Error.forbidden(
                "Backup.Forbidden", Text.key("controlcore.errors.backup.forbidden", "You may not manage backups."));
    }
}
