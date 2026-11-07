package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.ServerApi;
import net.ofatech.controlcore.application.restart.RestartService;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RestartWaitTest extends RuntimeTestBase {

    @TempDir
    Path root;

    private PlayerInfo player;

    @BeforeEach
    void startUp() {
        platform.backups.universe = root.resolve("universe");
        platform.backups.directory = Optional.of(root.resolve("backups"));
        platform.pendingRestore.copies = root.resolve("pending-restore");
        runtime.setup();
        runtime.start();
        player = platform.players.join("Steve");
    }

    @Test
    void cancellingWhileWaitingForABackupDoesNotShortenTheNextWait() {
        platform.backups.next = new CompletableFuture<>();
        ControlCoreApi.get().backups().create();
        awaitRuns(1);

        api().restart(Duration.ofSeconds(10), "");
        after(Duration.ofSeconds(10));
        after(Duration.ofMinutes(1));
        assertThat(api().restartStatus().counting()).isTrue();
        assertThat(api().cancelRestart().isSuccess()).isTrue();

        after(Duration.ofHours(1));
        assertThat(api().restart(Duration.ofSeconds(10), "").isSuccess()).isTrue();
        after(Duration.ofSeconds(10));

        assertThat(platform.control.stops)
                .as("the second restart should wait up to 10 minutes for the running backup")
                .isEmpty();
        after(Duration.ofMinutes(10));
        assertThat(platform.control.stops).hasSize(1);
    }

    @Test
    void aRestartWaitsWhileTheRestoreCopyIsBeingMade() {
        String name = ControlCoreApi.get().backups().create().join().value().name();
        CompletableFuture<Void> gate = new CompletableFuture<>();
        platform.backups.spaceGate = gate;
        CompletableFuture<Result<String>> planning =
                ControlCoreApi.get().backups().restoreOnNextStart(name);

        ControlCoreApi.get().server().restart(Duration.ofSeconds(10), "");
        after(Duration.ofSeconds(11));

        try {
            assertThat(platform.control.stops)
                    .as("stopping now kills the copy, so the planned restore silently never happens")
                    .isEmpty();
        } finally {
            platform.backups.spaceGate = null;
            gate.complete(null);
            planning.join();
        }
        assertThat(planning.join().isSuccess()).isTrue();
        after(Duration.ofSeconds(10));
        assertThat(platform.control.stops)
                .as("the copy is done, so the restart goes on")
                .hasSize(1);
    }

    @Test
    void thePanelIsRefreshedAboutOnceAMinuteWhileWaitingForABackup() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        restartService().viewedBy(player.id()).onChange(changes::incrementAndGet);
        platform.backups.next = new CompletableFuture<>();
        ControlCoreApi.get().backups().create();
        awaitRuns(1);
        api().restart(Duration.ofSeconds(10), "");
        after(Duration.ofSeconds(10));
        assertThat(platform.control.stops).isEmpty();

        changes.set(0);
        for (int i = 0; i < 30; i++) {
            after(Duration.ofSeconds(10));
        }

        assertThat(changes.get()).as("5 minutes of waiting").isBetween(4, 6);
    }

    @Test
    void thePanelIsRefreshedAboutOnceAMinuteWhileTheBackupBeforeRestartingRuns() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        restartService().viewedBy(player.id()).onChange(changes::incrementAndGet);
        platform.backups.next = new CompletableFuture<>();
        platform.commands.runAsConsole(
                "cc-set", java.util.Map.of("id", "controlcore:restart-backup-first", "value", "on"));
        api().restart(Duration.ofSeconds(10), "");
        after(Duration.ofSeconds(10));
        awaitRuns(1);

        changes.set(0);
        for (int i = 0; i < 30; i++) {
            after(Duration.ofSeconds(10));
        }

        assertThat(platform.control.stops).isEmpty();
        assertThat(changes.get()).as("5 minutes of backing up").isBetween(4, 6);
    }

    private static RestartService restartService() throws IllegalAccessException {
        Object server = ControlCoreApi.get().server();
        for (Class<?> type = server.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() == RestartService.class) {
                    field.setAccessible(true);
                    return (RestartService) field.get(server);
                }
            }
        }
        throw new IllegalStateException("no RestartService in " + server.getClass());
    }

    private void after(Duration time) {
        platform.clock.now = platform.clock.now.plus(time);
        platform.scheduler.runAll();
    }

    private void awaitRuns(int runs) {
        long until = System.currentTimeMillis() + 5_000;
        while (platform.backups.runs.get() < runs && System.currentTimeMillis() < until) {
            Thread.onSpinWait();
        }
        assertThat(platform.backups.runs.get()).isEqualTo(runs);
    }

    private static ServerApi api() {
        return ControlCoreApi.get().server();
    }
}
