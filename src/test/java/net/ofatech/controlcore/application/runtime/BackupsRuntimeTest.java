package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.ofatech.controlcore.api.BackupsApi;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.HistoryApi;
import net.ofatech.controlcore.application.backups.BackupService;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.server.BackupsView;
import net.ofatech.controlcore.domain.model.server.PendingRestore;
import net.ofatech.controlcore.domain.result.Result;
import net.ofatech.controlcore.fakes.FakePlatform;
import net.ofatech.controlcore.fakes.FakeServerBackups;
import org.junit.jupiter.api.Assumptions;
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
            assertThat(api().restoreOnNextStart(name).join().error().code()).isEqualTo("Backup.Unknown");
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
        awaitPending();
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
        platform.pendingRestore.restore = Optional.of(new PendingRestore("good.zip", "Admin", platform.clock.now));

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
        platform.pendingRestore.restore = Optional.of(new PendingRestore("evil.zip", "Admin", platform.clock.now));

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
        platform.pendingRestore.restore = Optional.of(new PendingRestore("good.zip", "Admin", platform.clock.now));
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
        assertThat(asGuest.restoreOnNextStart("x.zip").join().error().code()).isEqualTo("Backup.Forbidden");
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
        for (String bad : List.of(
                "\\\\host\\share",
                "//host/share",
                "C:\\",
                "a:b",
                "..",
                "backups/../..",
                "\\x",
                ".",
                universe.toString(),
                universe.resolve("backups").toString())) {
            assertThat(api().setDirectory(bad).error().code()).as(bad).isEqualTo("Backup.BadDirectory");
        }
        assertThat(api().setDirectory("backups/nightly").isSuccess()).isTrue();
        assertThat(api().setDirectory(root.resolve("elsewhere").toString()).isSuccess())
                .isTrue();
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
        assertThat(api().setDirectory("elsewhere").error().code()).isEqualTo("Backup.Running");

        platform.backups.next = null;
        running.complete(null);
        first.join();
    }

    @Test
    void aPlannedRestoreKeepsItsOwnCopyAndCancellingDeletesIt() throws IOException {
        platform.pendingRestore.copies = root.resolve("pending-restore");
        startUp();
        String name = api().create().join().value().name();

        assertThat(api().restoreOnNextStart(name).join().isSuccess()).isTrue();
        String stored = platform.pendingRestore.restore.orElseThrow().copy();
        assertThat(stored).doesNotContain("/").doesNotContain("\\");
        Path copy = root.resolve("pending-restore").resolve(stored);
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

        assertThat(api().restoreOnNextStart(name).join().error().code()).isEqualTo("Backup.NoSpace");
        assertThat(api().pendingRestore()).isEmpty();
    }

    @Test
    void theCopyIsRestoredEvenWhenHytaleRotatedTheOriginalAway() throws IOException {
        Path copies = Files.createDirectories(root.resolve("pending-restore"));
        Path copy = copies.resolve("gone.zip");
        FakeServerBackups.zip(copy, "worlds/default/new.txt", "new");
        platform.pendingRestore.copies = copies;
        platform.pendingRestore.restore =
                Optional.of(new PendingRestore("gone.zip", "Admin", platform.clock.now, copy.toString()));

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
                Optional.of(new PendingRestore("bad.zip", "Admin", platform.clock.now, copy.toString()));

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

        assertThat(api().restoreOnNextStart("other.zip").join().error().code()).isEqualTo("Backup.NotUniverse");
    }

    @Test
    void aListedBackupWithAnUnsupportedNameGetsAClearError() throws IOException {
        startUp();
        Files.createDirectories(folder);
        FakeServerBackups.zip(folder.resolve("my backup.zip"), "worlds/a.txt", "x");

        assertThat(api().list()).extracting(BackupsApi.Backup::name).contains("my backup.zip");
        assertThat(api().delete("my backup.zip").error().code()).isEqualTo("Backup.BadName");
    }

    @Test
    void aLeftoverStagingFolderNextToAnIntactUniverseIsDeletedAndStartupGoesOn() throws IOException {
        Path staging = root.resolve("universe-restoring-2026-01-01_00-00-00");
        Files.createDirectories(staging.resolve("worlds"));

        startUp();

        assertThat(staging).doesNotExist();
        assertThat(universe.resolve("worlds/default/old.txt")).exists();
        assertThat(platform.control.stops).isEmpty();
        assertThat(platform.log.contains("left over from an interrupted backup restore"))
                .isTrue();
    }

    @Test
    void aMissingUniverseAfterAnInterruptedSwapStopsTheServerAndTouchesNothing() throws IOException {
        Path aside = root.resolve("universe-before-restore-2026-01-01_00-00-00");
        Files.move(universe, aside);
        Path staging = Files.createDirectories(root.resolve("universe-restoring-2026-01-01_00-00-00/worlds"));
        Files.createDirectories(folder);
        FakeServerBackups.zip(folder.resolve("good.zip"), "worlds/default/new.txt", "new");
        platform.pendingRestore.restore = Optional.of(new PendingRestore("good.zip", "Admin", platform.clock.now));

        startUp();

        assertThat(platform.control.stops).hasSize(1);
        assertThat(platform.log.contains("is missing")).isTrue();
        assertThat(universe).doesNotExist();
        assertThat(staging).exists();
        assertThat(aside.resolve("worlds/default/old.txt")).exists();
        assertThat(platform.pendingRestore.restore).isPresent();
    }

    @Test
    void aHalfSwappedRestoreNextToAUniverseKeepsEveryFolderAndStopsTheServer() throws IOException {
        Path aside = Files.createDirectories(root.resolve("universe-before-restore-2026-01-01_00-00-00/worlds"));
        Path staging = root.resolve("universe-restoring-2026-01-01_00-00-00");
        Files.createDirectories(staging.resolve("worlds/default"));
        Files.writeString(staging.resolve("worlds/default/new.txt"), "new");
        Path other = Files.createDirectories(root.resolve("universe-restoring-2025-01-01_00-00-00/worlds"));

        startUp();
        runtime.shutdown();
        FakePlatform next = new FakePlatform();
        next.backups.universe = universe;
        next.backups.directory = Optional.of(folder);
        ModRuntime again = new ModRuntime(next, BackupsRuntimeTest.class.getClassLoader());
        try {
            again.setup();
            again.start();
        } finally {
            again.shutdown();
        }

        assertThat(platform.control.stops).hasSize(1);
        assertThat(next.control.stops)
                .as("the next start stops too, until the admin moves the folders")
                .hasSize(1);
        assertThat(staging.resolve("worlds/default/new.txt")).hasContent("new");
        assertThat(root.resolve("universe-failed-restore-2026-01-01_00-00-00")).doesNotExist();
        assertThat(aside).exists();
        assertThat(universe.resolve("worlds/default/old.txt")).exists();
        assertThat(other)
                .as("a staging folder with no before-restore partner is junk")
                .doesNotExist();
        assertThat(platform.log.contains(staging.toString())).isTrue();
        assertThat(platform.log.contains("Rename the one you want to universe")).isTrue();
    }

    @Test
    void rollingBackACancelledRestoreDoesNotWaitForTheCopy() throws Exception {
        platform.pendingRestore.copies = root.resolve("pending-restore");
        startUp();
        String name = api().create().join().value().name();
        assertThat(api().restoreOnNextStart(name).join().isSuccess()).isTrue();
        assertThat(api().cancelRestore().isSuccess()).isTrue();
        HistoryApi.Change cancelled = history().getFirst();
        CompletableFuture<Void> gate = new CompletableFuture<>();
        platform.backups.spaceGate = gate;

        try {
            Result<?> rolledBack = CompletableFuture.supplyAsync(
                            () -> ControlCoreApi.get().history().rollback(cancelled.id()))
                    .get(5, TimeUnit.SECONDS);
            assertThat(rolledBack.isSuccess()).isTrue();
        } finally {
            platform.backups.spaceGate = null;
            gate.complete(null);
        }
        awaitPending();
        assertThat(api().pendingRestore()).map(BackupsApi.PendingRestore::name).contains(name);
    }

    @Test
    void aRestoreCannotBePlannedWhileABackupRuns() {
        startUp();
        String name = api().create().join().value().name();
        CompletableFuture<Void> running = new CompletableFuture<>();
        platform.backups.next = running;
        CompletableFuture<Result<BackupsApi.Backup>> backup = api().create();
        awaitRun(2);

        assertThat(api().restoreOnNextStart(name).join().error().code()).isEqualTo("Backup.Running");

        platform.backups.next = null;
        running.complete(null);
        backup.join();
    }

    @Test
    void aPlanOlderThanSevenDaysIsNotAppliedAndAdminsAreTold() throws IOException {
        Path copies = Files.createDirectories(root.resolve("pending-restore"));
        Path copy = copies.resolve("old.zip");
        FakeServerBackups.zip(copy, "worlds/default/new.txt", "new");
        platform.pendingRestore.copies = copies;
        platform.pendingRestore.restore = Optional.of(
                new PendingRestore("old.zip", "Admin", platform.clock.now.minus(Duration.ofDays(8)), "old.zip"));

        startUp();

        assertThat(universe.resolve("worlds/default/old.txt")).exists();
        assertThat(universe.resolve("worlds/default/new.txt")).doesNotExist();
        assertThat(copy).exists();
        assertThat(platform.pendingRestore.restore)
                .as("an expired plan is dropped")
                .isEmpty();
        assertThat(api().restoreOnNextStart("old.zip").join().error().code())
                .as("planning again is not blocked by the old plan")
                .isNotEqualTo("Backup.RestorePending");
        PlayerInfo admin = platform.players.join("Admin");
        platform.connectedPublisher.publish(new PlayerJoined(admin));
        assertThat(platform.messenger.textsTo(admin.id()))
                .anyMatch(text -> text.contains("7 days")
                        && text.contains(copy.toAbsolutePath().toString()));
        platform.scheduler.runAll();
        assertThat(history()).extracting(HistoryApi.Change::after).anyMatch(after -> after.contains("dropped"));
    }

    @Test
    void anOldPlanIsAppliedWhenTheServerRanWithItUntilRecently() throws IOException {
        Files.createDirectories(folder);
        FakeServerBackups.zip(folder.resolve("good.zip"), "worlds/default/new.txt", "new");
        Instant planned = platform.clock.now.minus(Duration.ofDays(8));
        platform.pendingRestore.restore = Optional.of(
                new PendingRestore("good.zip", "Admin", planned, "", platform.clock.now.minus(Duration.ofMinutes(1))));

        startUp();

        assertThat(universe.resolve("worlds/default/new.txt")).hasContent("new");
    }

    @Test
    void stoppingTheServerNotesThatThePlanIsStillWanted() {
        platform.pendingRestore.copies = root.resolve("pending-restore");
        startUp();
        String name = api().create().join().value().name();
        assertThat(api().restoreOnNextStart(name).join().isSuccess()).isTrue();
        platform.clock.now = platform.clock.now.plus(Duration.ofDays(8));

        platform.control.shutdown();

        assertThat(platform.pendingRestore.restore.orElseThrow().seen()).isEqualTo(platform.clock.now);
    }

    @Test
    void theSeenTimeIsAlsoSavedWhileTheServerRuns() {
        platform.pendingRestore.copies = root.resolve("pending-restore");
        startUp();
        String name = api().create().join().value().name();
        assertThat(api().restoreOnNextStart(name).join().isSuccess()).isTrue();
        platform.clock.now = platform.clock.now.plus(Duration.ofDays(8));

        platform.scheduler.runAll();

        assertThat(platform.pendingRestore.restore.orElseThrow().seen())
                .as("a crash after long uptime does not expire the plan")
                .isEqualTo(platform.clock.now);
    }

    @Test
    void anExpiredPlanWhoseMarkerCannotBeDeletedIsNotApplied() throws IOException {
        Files.createDirectories(folder);
        FakeServerBackups.zip(folder.resolve("good.zip"), "worlds/default/new.txt", "new");
        platform.pendingRestore.restore =
                Optional.of(new PendingRestore("good.zip", "Admin", platform.clock.now.minus(Duration.ofDays(8))));
        platform.pendingRestore.failClear = true;

        startUp();

        assertThat(universe.resolve("worlds/default/old.txt")).exists();
        assertThat(universe.resolve("worlds/default/new.txt")).doesNotExist();
        assertThat(platform.log.contains("PendingRestore.json could not be deleted"))
                .isTrue();
    }

    @Test
    void leftoverCopiesAreKeptAtStartAndDeletedWhenPlanning() throws IOException {
        Path copies = Files.createDirectories(root.resolve("pending-restore"));
        Path stale = copies.resolve("stale.zip");
        FakeServerBackups.zip(stale, "worlds/a.txt", "a");
        platform.pendingRestore.copies = copies;

        startUp();
        assertThat(stale).as("it may be the copy a failed restore said it kept").exists();

        FakeServerBackups.zip(stale, "worlds/a.txt", "a");
        String name = api().create().join().value().name();
        assertThat(api().restoreOnNextStart(name).join().isSuccess()).isTrue();
        assertThat(stale).doesNotExist();
        assertThat(copies.resolve(name)).exists();
    }

    @Test
    void aUniverseThatIsALinkCannotBeRestored() throws IOException {
        Path real = root.resolve("real-universe");
        Files.move(universe, real);
        try {
            Files.createSymbolicLink(universe, real);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("symbolic links are not allowed here: " + e.getMessage());
        }
        startUp();
        String name = api().create().join().value().name();

        assertThat(api().restoreOnNextStart(name).join().error().code()).isEqualTo("Backup.UniverseNotSwappable");
    }

    @Test
    void thePlannedBackupKeepsItsBadgeAfterHytaleMovesItToTheArchive() throws Exception {
        platform.pendingRestore.copies = root.resolve("pending-restore");
        startUp();
        String name = api().create().join().value().name();
        assertThat(api().restoreOnNextStart(name).join().isSuccess()).isTrue();
        Files.createDirectories(folder.resolve("archive"));
        Files.move(folder.resolve(name), folder.resolve("archive").resolve(name));
        PlayerInfo admin = platform.players.join("Admin");

        assertThat(service().viewedBy(admin.id()).backups())
                .singleElement()
                .extracting(BackupsView.Row::name, BackupsView.Row::pending)
                .containsExactly("archive/" + name, true);
    }

    @Test
    void whileARestoreIsBeingPreparedBackupsPlansAndCancelsWait() {
        platform.pendingRestore.copies = root.resolve("pending-restore");
        startUp();
        String name = api().create().join().value().name();
        CompletableFuture<Void> gate = new CompletableFuture<>();
        platform.backups.spaceGate = gate;

        CompletableFuture<Result<String>> planning = api().restoreOnNextStart(name);

        assertThat(api().create().join().error().code()).isEqualTo("Backup.PreparingRestore");
        assertThat(api().restoreOnNextStart(name).join().error().code()).isEqualTo("Backup.PreparingRestore");
        assertThat(api().cancelRestore().error().code()).isEqualTo("Backup.PreparingRestore");
        platform.backups.spaceGate = null;
        gate.complete(null);
        assertThat(planning.join().isSuccess()).isTrue();
        assertThat(api().pendingRestore()).isPresent();
    }

    @Test
    void theBackupDirectoryRuleAlsoGuardsTheGenericConfigPath() {
        startUp();
        PlayerInfo moderator = platform.players.join("Moderator");
        platform.deniedNodesFor.put(moderator.id(), List.of("admin", "settings"));
        platform.commands.runAsConsole(
                "cc-perm", Map.of("target", "category:backup", "side", "both", "node", "everyone"));

        assertThat(platform.commands
                        .run("cc-set", moderator, Map.of("id", "server:backup.enabled", "value", "true"))
                        .isSuccess())
                .isTrue();
        assertThat(platform.commands
                        .run("cc-set", moderator, Map.of("id", "server:backup.directory", "value", "\\\\share\\x"))
                        .error()
                        .code())
                .isEqualTo("Backup.DirectoryForbidden");
        assertThat(platform.commands
                        .runAsConsole("cc-set", Map.of("id", "server:backup.directory", "value", "\\\\share\\x"))
                        .error()
                        .code())
                .isEqualTo("Backup.BadDirectory");
        assertThat(platform.commands
                        .runAsConsole("cc-set", Map.of("id", "server:backup.directory", "value", universe.toString()))
                        .error()
                        .code())
                .isEqualTo("Backup.BadDirectory");
    }

    private static BackupService service() throws IllegalAccessException {
        Object backups = ControlCoreApi.get().backups();
        for (var field : backups.getClass().getDeclaredFields()) {
            if (field.getType() == BackupService.class) {
                field.setAccessible(true);
                return (BackupService) field.get(backups);
            }
        }
        throw new IllegalStateException("no BackupService in " + backups.getClass());
    }

    private void awaitPending() {
        long until = System.currentTimeMillis() + 5_000;
        while (api().pendingRestore().isEmpty() && System.currentTimeMillis() < until) {
            Thread.onSpinWait();
        }
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
