package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.api.BackupsApi;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.HistoryApi;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.server.PendingRestore;
import net.ofatech.controlcore.domain.result.Result;
import net.ofatech.controlcore.fakes.FakeServerBackups;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupsRuntimeTest extends RuntimeTestBase {

    @TempDir
    Path root;

    private Path universe;
    private Path folder;

    @BeforeEach
    void folders() throws IOException {
        universe = root.resolve("universe");
        folder = root.resolve("backups");
        Files.createDirectories(universe.resolve("worlds/default"));
        Files.writeString(universe.resolve("worlds/default/old.txt"), "0123456789".repeat(10));
        platform.backups.universe = universe;
        platform.backups.directory = Optional.of(folder);
    }

    private void startUp() {
        runtime.setup();
        runtime.start();
    }

    @Test
    void aBackupIsMadeListedAndRecorded() {
        startUp();

        Result<BackupsApi.Backup> made = api().create().join();

        assertThat(made.isSuccess()).isTrue();
        assertThat(api().list())
                .extracting(BackupsApi.Backup::name)
                .containsExactly(made.value().name());
        assertThat(history())
                .extracting(HistoryApi.Change::target)
                .contains(made.value().name());
    }

    @Test
    void onlyOneBackupRunsAtATime() {
        startUp();
        CompletableFuture<Void> running = new CompletableFuture<>();
        platform.backups.next = running;

        CompletableFuture<Result<BackupsApi.Backup>> first = api().create();
        awaitRun(1);
        Result<BackupsApi.Backup> second = api().create().join();
        assertThat(second.error().code()).isEqualTo("Backup.Running");
        assertThat(platform.commands.runAsConsole("cc-backup", Map.of()).error().code())
                .isEqualTo("Backup.Running");

        platform.backups.next = null;
        running.complete(null);
        first.join();
        assertThat(api().create().join().isSuccess()).isTrue();
    }

    @Test
    void aBackupNeedsADirectoryAndEnoughFreeSpace() {
        startUp();
        platform.backups.usable = 10;
        assertThat(api().create().join().error().code()).isEqualTo("Backup.NoSpace");
        assertThat(platform.backups.runs.get()).isZero();

        platform.backups.directory = Optional.empty();
        assertThat(api().create().join().error().code()).isEqualTo("Backup.NotConfigured");
    }

    @Test
    void namesOutsideTheBackupFolderAreUnknown() throws IOException {
        startUp();
        Files.createDirectories(folder);
        Files.writeString(root.resolve("secret.zip"), "x");

        for (String name : List.of(
                "../secret.zip",
                "archive/../../secret.zip",
                root.resolve("secret.zip").toString())) {
            assertThat(api().delete(name).error().code()).isEqualTo("Backup.Unknown");
            assertThat(api().restoreOnNextStart(name).error().code()).isEqualTo("Backup.Unknown");
        }
        assertThat(root.resolve("secret.zip")).exists();
    }

    @Test
    void commandsNeedTheWordConfirm() throws IOException {
        startUp();
        String name = api().create().join().value().name();

        assertThat(platform.commands
                        .runAsConsole("cc-backup-delete", Map.of("name", name, "confirm", "yes"))
                        .error()
                        .code())
                .isEqualTo("Backup.Confirm");
        assertThat(platform.commands
                        .runAsConsole("cc-backup-delete", Map.of("name", name, "confirm", "confirm"))
                        .isSuccess())
                .isTrue();
        assertThat(api().list()).isEmpty();
    }

    @Test
    void aRestoreIsPlannedShownCancelledAndRolledBack() {
        startUp();
        String name = api().create().join().value().name();

        Result<?> planned =
                platform.commands.runAsConsole("cc-backup-restore", Map.of("name", name, "confirm", "confirm"));
        assertThat(planned.isSuccess()).isTrue();
        assertThat(api().pendingRestore()).map(BackupsApi.PendingRestore::name).contains(name);
        assertThat(api().delete(name).error().code()).isEqualTo("Backup.InUse");
        assertThat(platform.commands
                        .runAsConsole("cc-backups", Map.of())
                        .value()
                        .text()
                        .orElseThrow())
                .contains(name);

        HistoryApi.Change change = history().getFirst();
        assertThat(ControlCoreApi.get().history().rollback(change.id()).isSuccess())
                .isTrue();
        assertThat(api().pendingRestore()).isEmpty();
        assertThat(api().cancelRestore().error().code()).isEqualTo("Backup.NoRestore");
    }

    @Test
    void aPlannedRestoreIsAppliedAtStartupAndReported() throws IOException {
        Files.createDirectories(folder);
        FakeServerBackups.zip(folder.resolve("good.zip"), "worlds/default/new.txt", "new");
        platform.pendingRestore.restore = Optional.of(new PendingRestore("good.zip", "Admin", Instant.EPOCH));

        startUp();
        assertThat(universe.resolve("worlds/default/new.txt")).hasContent("new");
        assertThat(universe.resolve("worlds/default/old.txt")).doesNotExist();
        assertThat(platform.pendingRestore.restore).isEmpty();
        try (var siblings = Files.list(root)) {
            assertThat(siblings.map(path -> path.getFileName().toString()))
                    .anyMatch(name -> name.startsWith("universe-before-restore-"));
        }

        platform.scheduler.runAll();
        assertThat(history()).extracting(HistoryApi.Change::after).contains("restored at startup");
        PlayerInfo admin = platform.players.join("Admin");
        platform.connectedPublisher.publish(new PlayerJoined(admin));
        assertThat(platform.messenger.textsTo(admin.id())).anyMatch(text -> text.contains("good.zip"));
    }

    @Test
    void aBadBackupLeavesTheUniverseAloneAtStartup() throws IOException {
        Files.createDirectories(folder);
        FakeServerBackups.zip(folder.resolve("evil.zip"), "../evil.txt", "evil");
        platform.pendingRestore.restore = Optional.of(new PendingRestore("evil.zip", "Admin", Instant.EPOCH));

        startUp();

        assertThat(universe.resolve("worlds/default/old.txt")).exists();
        assertThat(root.resolve("evil.txt")).doesNotExist();
        assertThat(platform.pendingRestore.restore).isEmpty();
        assertThat(platform.log.contains("could not be restored")).isTrue();
        platform.scheduler.runAll();
        assertThat(history()).extracting(HistoryApi.Change::after).contains("restore failed at startup");
    }

    @Test
    void theRestoreIsSkippedWhenItsMarkerCannotBeDeleted() throws IOException {
        Files.createDirectories(folder);
        FakeServerBackups.zip(folder.resolve("good.zip"), "worlds/default/new.txt", "new");
        platform.pendingRestore.restore = Optional.of(new PendingRestore("good.zip", "Admin", Instant.EPOCH));
        platform.pendingRestore.failClear = true;

        startUp();

        assertThat(universe.resolve("worlds/default/old.txt")).exists();
        assertThat(universe.resolve("worlds/default/new.txt")).doesNotExist();
    }

    @Test
    void theApiActsWithThePlayersRights() {
        startUp();
        PlayerInfo guest = platform.players.join("Guest");
        platform.deniedNodesFor.put(guest.id(), List.of("admin", "settings", "backups"));
        BackupsApi asGuest = api().actingFor(guest.id());

        assertThat(asGuest.create().join().error().code()).isEqualTo("Backup.Forbidden");
        assertThat(asGuest.restoreOnNextStart("x.zip").error().code()).isEqualTo("Backup.Forbidden");
        api().create().join();
        assertThat(asGuest.list()).isEmpty();
        assertThat(platform.commands.run("cc-backups", guest, Map.of()).error().code())
                .isEqualTo("Backup.Forbidden");

        platform.deniedNodesFor.put(guest.id(), List.of("admin", "settings"));
        assertThat(asGuest.list()).hasSize(1);
    }

    @Test
    void aBackupRunThatWritesNoZipIsNotReportedAsDone() throws IOException {
        Files.createDirectories(folder);
        Path old = folder.resolve("old.zip");
        FakeServerBackups.zip(old, "a.txt", "a");
        Files.setLastModifiedTime(old, FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));
        startUp();
        platform.backups.next = CompletableFuture.completedFuture(null);

        Result<BackupsApi.Backup> made = api().create().join();

        assertThat(made.isSuccess())
                .as(
                        "run() produced no new zip, yet create() returned %s",
                        made.isSuccess() ? made.value().name() : "")
                .isFalse();
        assertThat(made.error().code()).isEqualTo("Backup.Missing");
        assertThat(history()).isEmpty();
        assertThat(api().create().join().error().code()).isNotEqualTo("Backup.Running");
    }

    @Test
    void aFailingBackupStillFreesTheRunningFlag() {
        startUp();
        platform.backups.next = CompletableFuture.failedFuture(new IllegalStateException("disk gone"));

        assertThat(api().create().join().error().code()).isEqualTo("Backup.Failed");

        platform.backups.next = null;
        assertThat(api().create().join().isSuccess()).isTrue();
    }

    @Test
    void onlyAdminsOrTheConsoleSetTheDirectoryAndOnlyInsideTheServerFolder() {
        startUp();
        PlayerInfo moderator = platform.players.join("Moderator");
        platform.deniedNodesFor.put(moderator.id(), List.of("admin", "settings"));

        assertThat(api().actingFor(moderator.id())
                        .setDirectory("backups")
                        .error()
                        .code())
                .isEqualTo("Backup.DirectoryForbidden");
        assertThat(platform.commands
                        .run("cc-backup", moderator, Map.of("directory", "backups"))
                        .error()
                        .code())
                .isEqualTo("Backup.DirectoryForbidden");
        for (String bad :
                List.of("\\\\host\\share", "C:\\", "C:/backups", "..", "backups/../..", "/srv/x", "\\x", ".")) {
            assertThat(api().setDirectory(bad).error().code()).as(bad).isEqualTo("Backup.BadDirectory");
        }
        assertThat(api().setDirectory("backups/nightly").error().code()).isNotEqualTo("Backup.BadDirectory");
    }

    @Test
    void theDirectoryCannotChangeWhileABackupRuns() {
        startUp();
        CompletableFuture<Void> running = new CompletableFuture<>();
        platform.backups.next = running;
        CompletableFuture<Result<BackupsApi.Backup>> first = api().create();
        awaitRun(1);

        assertThat(platform.commands
                        .runAsConsole("cc-backup", Map.of("directory", "elsewhere"))
                        .error()
                        .code())
                .isEqualTo("Backup.Running");

        platform.backups.next = null;
        running.complete(null);
        first.join();
    }

    @Test
    void aPlannedRestoreKeepsItsOwnCopyAndCancellingDeletesIt() throws IOException {
        platform.pendingRestore.copies = root.resolve("pending-restore");
        startUp();
        String name = api().create().join().value().name();

        assertThat(api().restoreOnNextStart(name).isSuccess()).isTrue();
        Path copy = Path.of(platform.pendingRestore.restore.orElseThrow().copy());
        assertThat(copy).exists().hasParent(root.resolve("pending-restore"));

        assertThat(api().cancelRestore().isSuccess()).isTrue();
        assertThat(copy).doesNotExist();
    }

    @Test
    void aRestoreNeedsRoomForItsCopy() {
        platform.pendingRestore.copies = root.resolve("pending-restore");
        startUp();
        String name = api().create().join().value().name();
        platform.backups.usable = 1;

        assertThat(api().restoreOnNextStart(name).error().code()).isEqualTo("Backup.NoSpace");
        assertThat(api().pendingRestore()).isEmpty();
    }

    @Test
    void theCopyIsRestoredEvenWhenHytaleRotatedTheOriginalAway() throws IOException {
        Path copies = Files.createDirectories(root.resolve("pending-restore"));
        Path copy = copies.resolve("gone.zip");
        FakeServerBackups.zip(copy, "worlds/default/new.txt", "new");
        platform.pendingRestore.copies = copies;
        platform.pendingRestore.restore =
                Optional.of(new PendingRestore("gone.zip", "Admin", Instant.EPOCH, copy.toString()));

        startUp();

        assertThat(universe.resolve("worlds/default/new.txt")).hasContent("new");
        assertThat(copy).doesNotExist();
    }

    @Test
    void aFailedRestoreKeepsTheCopyAndSaysWhere() throws IOException {
        Path copies = Files.createDirectories(root.resolve("pending-restore"));
        Path copy = copies.resolve("bad.zip");
        FakeServerBackups.zip(copy, "notes.txt", "no worlds here");
        platform.pendingRestore.copies = copies;
        platform.pendingRestore.restore =
                Optional.of(new PendingRestore("bad.zip", "Admin", Instant.EPOCH, copy.toString()));

        startUp();

        assertThat(universe.resolve("worlds/default/old.txt")).exists();
        assertThat(copy).exists();
        assertThat(platform.log.contains("not a Hytale universe backup")).isTrue();
        PlayerInfo admin = platform.players.join("Admin");
        platform.connectedPublisher.publish(new PlayerJoined(admin));
        assertThat(platform.messenger.textsTo(admin.id()))
                .anyMatch(text -> text.contains("left as it was") && text.contains(copy.toString()));
    }

    @Test
    void aZipThatIsNotAUniverseCannotBePlanned() throws IOException {
        startUp();
        Files.createDirectories(folder);
        FakeServerBackups.zip(folder.resolve("other.zip"), "notes.txt", "x");

        assertThat(api().restoreOnNextStart("other.zip").error().code()).isEqualTo("Backup.NotUniverse");
    }

    @Test
    void aListedBackupWithAnUnsupportedNameGetsAClearError() throws IOException {
        startUp();
        Files.createDirectories(folder);
        FakeServerBackups.zip(folder.resolve("my backup.zip"), "worlds/a.txt", "x");

        assertThat(api().list()).extracting(BackupsApi.Backup::name).contains("my backup.zip");
        assertThat(api().delete("my backup.zip").error().code()).isEqualTo("Backup.BadName");
    }

    private void awaitRun(int runs) {
        long until = System.currentTimeMillis() + 5_000;
        while (platform.backups.runs.get() < runs && System.currentTimeMillis() < until) {
            Thread.onSpinWait();
        }
        assertThat(platform.backups.runs.get()).isEqualTo(runs);
    }

    private List<HistoryApi.Change> history() {
        return ControlCoreApi.get().history().list(Optional.of(HistoryApi.Kind.BACKUP), "", 20);
    }

    private static BackupsApi api() {
        return ControlCoreApi.get().backups();
    }
}
