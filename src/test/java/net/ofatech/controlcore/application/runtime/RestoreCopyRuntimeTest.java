package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.ofatech.controlcore.api.BackupsApi;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.HistoryApi;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RestoreCopyRuntimeTest extends RuntimeTestBase {

    @TempDir
    Path root;

    @BeforeEach
    void startUp() throws IOException {
        Path universe = root.resolve("universe");
        Files.createDirectories(universe.resolve("worlds/default"));
        Files.writeString(universe.resolve("worlds/default/old.txt"), "old");
        platform.backups.universe = universe;
        platform.backups.directory = Optional.of(root.resolve("backups"));
        platform.pendingRestore.copies = root.resolve("pending-restore");
        runtime.setup();
        runtime.start();
    }

    @Test
    void playersSeeTheRestoreCopyWaitTextNotTheBackupOne() {
        PlayerInfo player = platform.players.join("Steve");
        String name = api().create().join().value().name();
        CompletableFuture<Void> gate = new CompletableFuture<>();
        platform.backups.spaceGate = gate;
        CompletableFuture<Result<String>> planning = api().restoreOnNextStart(name);
        try {
            ControlCoreApi.get().server().restart(Duration.ofSeconds(10), "");
            after(Duration.ofSeconds(11));

            assertThat(String.valueOf(platform.overlay.shown.get(player.id())))
                    .contains("Waiting for the restore copy to finish before restarting")
                    .doesNotContain("Waiting for a backup");
        } finally {
            platform.backups.spaceGate = null;
            gate.complete(null);
            planning.join();
        }
    }

    @Test
    void aRestartThatOutwaitsTheRestoreCopyTellsAdminsThePlanWasNotMade() {
        PlayerInfo admin = platform.players.join("Admin");
        String name = api().create().join().value().name();
        CompletableFuture<Void> gate = new CompletableFuture<>();
        platform.backups.spaceGate = gate;
        CompletableFuture<Result<String>> planning = api().restoreOnNextStart(name);
        try {
            ControlCoreApi.get().server().restart(Duration.ofSeconds(10), "");
            after(Duration.ofSeconds(11));
            after(Duration.ofMinutes(11));

            assertThat(platform.messenger.textsTo(admin.id()))
                    .anyMatch(text -> text.contains("The restore copy did not finish within 10 minutes")
                            && text.contains("Plan it again after the restart"))
                    .noneMatch(text -> text.contains("The backup before restarting did not finish"));
        } finally {
            platform.backups.spaceGate = null;
            gate.complete(null);
            planning.join();
        }
    }

    @Test
    void anAsyncRollbackStillMarksTheEntryRolledBackAndReportsTheOutcome() throws Exception {
        String name = api().create().join().value().name();
        assertThat(api().restoreOnNextStart(name).join().isSuccess()).isTrue();
        assertThat(api().cancelRestore().isSuccess()).isTrue();
        HistoryApi.Change cancelled = history().getFirst();
        CompletableFuture<Void> gate = new CompletableFuture<>();
        platform.backups.spaceGate = gate;

        Result<?> reply;
        try {
            reply = CompletableFuture.supplyAsync(
                            () -> ControlCoreApi.get().history().rollback(cancelled.id()))
                    .get(5, TimeUnit.SECONDS);
            assertThat(reply.isSuccess()).isTrue();
            assertThat(ControlCoreApi.get()
                            .history()
                            .rollback(cancelled.id())
                            .error()
                            .code())
                    .as("a second rollback while copying is refused, not doubled")
                    .isEqualTo("Backup.PreparingRestore");
            assertThat(String.valueOf(reply.value()))
                    .as("the reply says the copy is still running, not that it is done")
                    .contains("Copying the backup for the restore")
                    .doesNotContain("Rolled back");
        } finally {
            platform.backups.spaceGate = null;
            gate.complete(null);
        }
        awaitPending();

        assertThat(api().pendingRestore()).map(BackupsApi.PendingRestore::name).contains(name);
        assertThat(history().stream().filter(change -> change.id() == cancelled.id()))
                .singleElement()
                .extracting(HistoryApi.Change::rolledBack)
                .as("the History row shows it was rolled back once the copy is done")
                .isEqualTo(true);
        long until = System.currentTimeMillis() + 5_000;
        while (!platform.log.contains("will be restored at the next start") && System.currentTimeMillis() < until) {
            Thread.onSpinWait();
        }
        assertThat(platform.log.contains(name + " will be restored at the next start"))
                .isTrue();
    }

    private void awaitPending() {
        long until = System.currentTimeMillis() + 5_000;
        while (api().pendingRestore().isEmpty() && System.currentTimeMillis() < until) {
            Thread.onSpinWait();
        }
    }

    private void after(Duration time) {
        platform.clock.now = platform.clock.now.plus(time);
        platform.scheduler.runAll();
    }

    private List<HistoryApi.Change> history() {
        return ControlCoreApi.get().history().list(Optional.of(HistoryApi.Kind.BACKUP), "", 20);
    }

    private static BackupsApi api() {
        return ControlCoreApi.get().backups();
    }
}
