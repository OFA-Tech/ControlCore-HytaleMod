package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.RulesApi;
import net.ofatech.controlcore.api.ServerApi;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RestartRuntimeTest extends RuntimeTestBase {

    private PlayerInfo player;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        player = platform.players.join("Steve");
    }

    @Test
    void playersAreWarnedOnTheWayAndKickedAtZero() {
        assertThat(api().restart(Duration.ofMinutes(5), "Update").isSuccess()).isTrue();
        assertThat(chat()).containsExactly("Server restarting in 5:00: Update");

        after(Duration.ofSeconds(200));
        assertThat(chat()).hasSize(1);
        after(Duration.ofSeconds(40));
        assertThat(chat()).last().isEqualTo("Server restarting in 1:00: Update");
        after(Duration.ofSeconds(30));
        after(Duration.ofSeconds(20));
        assertThat(chat()).hasSize(4).last().isEqualTo("Server restarting in 0:10: Update");
        assertThat(platform.control.stops).isEmpty();
        assertThat(api().restartStatus().remaining()).contains(Duration.ofSeconds(10));

        after(Duration.ofSeconds(10));
        assertThat(platform.admin.kicked).containsExactly(player.id() + ":Server restarting, back soon!");
        assertThat(platform.control.stops).hasSize(1);
        assertThat(api().cancelRestart().error().code()).isEqualTo("Restart.TooLate");
    }

    @Test
    void onlyTheWarningsThatFitAreSent() {
        api().restart(Duration.ofSeconds(45), "");
        after(Duration.ofSeconds(15));
        after(Duration.ofSeconds(20));
        after(Duration.ofSeconds(10));

        assertThat(chat())
                .containsExactly(
                        "Server restarting in 0:45.", "Server restarting in 0:30.", "Server restarting in 0:10.");
        assertThat(platform.control.stops).hasSize(1);
    }

    @Test
    void cancellingStopsTheCountdownAndTellsEveryone() {
        platform.commands.runAsConsole("cc-restart", Map.of("in", "1m"));
        assertThat(platform.commands
                        .runAsConsole("cc-restart", Map.of())
                        .error()
                        .code())
                .isEqualTo("Restart.Running");
        assertThat(platform.commands
                        .runAsConsole("cc-restart-status", Map.of())
                        .value()
                        .text()
                        .orElseThrow())
                .contains("1:00");

        assertThat(platform.commands.runAsConsole("cc-restart-cancel", Map.of()).isSuccess())
                .isTrue();
        after(Duration.ofMinutes(2));

        assertThat(chat()).last().isEqualTo("Restart cancelled.");
        assertThat(platform.control.stops).isEmpty();
        assertThat(api().restartStatus().counting()).isFalse();
    }

    @Test
    void theCommandChecksItsCountdown() {
        assertThat(platform.commands
                        .runAsConsole("cc-restart", Map.of("in", "soon"))
                        .error()
                        .code())
                .isEqualTo("Restart.BadDelay");
        assertThat(platform.commands
                        .runAsConsole("cc-restart", Map.of("in", "5s"))
                        .error()
                        .code())
                .isEqualTo("Restart.BadDelay");
        assertThat(platform.commands
                        .runAsConsole("cc-restart", Map.of("in", "90"))
                        .isSuccess())
                .isTrue();
        assertThat(api().restartStatus().remaining()).contains(Duration.ofSeconds(90));
    }

    @Test
    void theDefaultCountdownComesFromTheSetting() {
        platform.commands.runAsConsole("cc-set", Map.of("id", "controlcore:restart-countdown", "value", "120"));

        api().restart(null, null);

        assertThat(api().restartStatus().remaining()).contains(Duration.ofSeconds(120));
    }

    @Test
    void theApiActsWithThePlayersRights() {
        platform.deniedNodesFor.put(player.id(), List.of("admin", "settings", "restart"));
        assertThat(api().actingFor(player.id())
                        .restart(Duration.ofMinutes(1), "")
                        .error()
                        .code())
                .isEqualTo("Restart.Forbidden");

        platform.deniedNodesFor.put(player.id(), List.of("admin", "settings"));
        assertThat(api().actingFor(player.id())
                        .restart(Duration.ofMinutes(1), "")
                        .isSuccess())
                .isTrue();
        assertThat(api().actingFor(player.id()).cancelRestart().isSuccess()).isTrue();
    }

    @Test
    void aScheduledRestartStartsSoTheServerStopsOnTime() {
        LocalDateTime now = LocalDateTime.ofInstant(platform.clock.now, ZoneId.systemDefault());
        LocalDateTime target = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(5);
        platform.commands.runAsConsole(
                "cc-set",
                Map.of(
                        "id",
                        "controlcore:restart-schedule",
                        "value",
                        target.format(DateTimeFormatter.ofPattern("HH:mm"))));
        platform.scheduler.runAll();
        assertThat(api().restartStatus().counting()).isFalse();

        platform.clock.now = target.atZone(ZoneId.systemDefault())
                .toInstant()
                .minusSeconds(60)
                .plusMillis(500);
        platform.scheduler.runAll();

        assertThat(api().restartStatus().counting()).isTrue();
        assertThat(api().restartStatus().remaining()).contains(Duration.ofMillis(59_500));
    }

    @Test
    void automationSeesTheCountdownAndTheStop() {
        RulesApi rules = ControlCoreApi.get().rules();
        rules.save(Optional.empty(), rule("Countdown", "restart-started", "Restart in {seconds}"));
        rules.save(Optional.empty(), rule("Stopping", "server-stopping", "Bye"));

        api().restart(Duration.ofSeconds(10), "");
        after(Duration.ofSeconds(10));
        platform.control.shutdown();

        assertThat(platform.messenger.broadcasts).containsExactly("Restart in 10", "Bye");
    }

    @Test
    void aBackupIsMadeFirstWhenAsked(@TempDir Path root) {
        platform.backups.universe = root.resolve("universe");
        platform.backups.directory = Optional.of(root.resolve("backups"));
        platform.commands.runAsConsole("cc-set", Map.of("id", "controlcore:restart-backup-first", "value", "on"));

        api().restart(Duration.ofSeconds(10), "");
        after(Duration.ofSeconds(10));

        awaitStop();
        assertThat(platform.backups.runs.get()).isEqualTo(1);
    }

    @Test
    void aBackupBeforeRestartingThatNeverEndsTimesOut(@TempDir Path root) {
        platform.backups.universe = root.resolve("universe");
        platform.backups.directory = Optional.of(root.resolve("backups"));
        platform.backups.next = new java.util.concurrent.CompletableFuture<>();
        platform.commands.runAsConsole("cc-set", Map.of("id", "controlcore:restart-backup-first", "value", "on"));

        api().restart(Duration.ofSeconds(10), "");
        after(Duration.ofSeconds(10));
        awaitRuns(1);
        after(Duration.ofMinutes(5));
        assertThat(platform.control.stops).isEmpty();

        after(Duration.ofMinutes(5));
        assertThat(platform.control.stops).hasSize(1);
        assertThat(chat()).anyMatch(text -> text.contains("did not finish within 10 minutes"));
    }

    @Test
    void aManualBackupThatNeverEndsDoesNotHoldTheRestartForever(@TempDir Path root) {
        platform.backups.universe = root.resolve("universe");
        platform.backups.directory = Optional.of(root.resolve("backups"));
        platform.backups.next = new java.util.concurrent.CompletableFuture<>();
        ControlCoreApi.get().backups().create();
        awaitRuns(1);

        api().restart(Duration.ofSeconds(10), "");
        after(Duration.ofSeconds(10));
        after(Duration.ofMinutes(9));
        assertThat(platform.control.stops).isEmpty();

        after(Duration.ofMinutes(1));
        assertThat(platform.control.stops).hasSize(1);
    }

    @Test
    void aStopThatFailsCanBeRetried() {
        platform.control.failStop = true;
        api().restart(Duration.ofSeconds(10), "");
        after(Duration.ofSeconds(10));

        assertThat(api().restartStatus().counting()).isFalse();
        assertThat(platform.log.contains("Could not stop the server")).isTrue();

        platform.control.failStop = false;
        assertThat(api().restart(Duration.ofSeconds(10), "").isSuccess()).isTrue();
        after(Duration.ofSeconds(10));
        assertThat(platform.control.stops).hasSize(1);
    }

    @Test
    void aBackupThatEndsAfterAFailedStopDoesNotStopTheServerLater(@TempDir Path root) {
        platform.backups.universe = root.resolve("universe");
        platform.backups.directory = Optional.of(root.resolve("backups"));
        java.util.concurrent.CompletableFuture<Void> backup = new java.util.concurrent.CompletableFuture<>();
        platform.backups.next = backup;
        platform.commands.runAsConsole("cc-set", Map.of("id", "controlcore:restart-backup-first", "value", "on"));
        api().restart(Duration.ofSeconds(10), "");
        after(Duration.ofSeconds(10));
        awaitRuns(1);

        platform.control.failStop = true;
        after(Duration.ofMinutes(10));
        assertThat(api().restartStatus().counting()).isFalse();
        platform.control.failStop = false;
        platform.backups.next = null;
        backup.complete(null);
        after(Duration.ofSeconds(1));

        assertThat(platform.control.stops).isEmpty();
        assertThat(api().restart(Duration.ofSeconds(10), "").isSuccess()).isTrue();
        after(Duration.ofSeconds(10));
        awaitStop();
        assertThat(platform.control.stops).hasSize(1);
    }

    @Test
    void noBackupIsMadeBeforeRestartingWhileARestoreIsPlanned(@TempDir Path root) {
        platform.backups.universe = root.resolve("universe");
        platform.backups.directory = Optional.of(root.resolve("backups"));
        platform.pendingRestore.restore = Optional.of(
                new net.ofatech.controlcore.domain.model.server.PendingRestore("a.zip", "Admin", platform.clock.now));
        platform.commands.runAsConsole("cc-set", Map.of("id", "controlcore:restart-backup-first", "value", "on"));

        api().restart(Duration.ofSeconds(10), "");
        after(Duration.ofSeconds(10));

        assertThat(platform.backups.runs.get()).isZero();
        assertThat(platform.control.stops).hasSize(1);
        assertThat(platform.log.contains("a backup restore is planned")).isTrue();
    }

    @Test
    void aScheduledRestartStillStartsWhenTheServerComesUpInsideTheCountdown() {
        LocalDateTime now = LocalDateTime.ofInstant(platform.clock.now, ZoneId.systemDefault());
        LocalDateTime target = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(5);
        platform.commands.runAsConsole("cc-set", Map.of("id", "controlcore:restart-countdown", "value", "300"));
        platform.commands.runAsConsole(
                "cc-set",
                Map.of(
                        "id",
                        "controlcore:restart-schedule",
                        "value",
                        target.format(DateTimeFormatter.ofPattern("HH:mm")) + "; " + target.getMinute() + " "
                                + target.getHour() + " * * *"));

        platform.clock.now = target.atZone(ZoneId.systemDefault()).toInstant().minusSeconds(30);
        platform.scheduler.runAll();

        assertThat(api().restartStatus().remaining()).contains(Duration.ofSeconds(30));
        api().cancelRestart();
        platform.scheduler.runAll();
        assertThat(api().restartStatus().counting()).isFalse();
    }

    @Test
    void theAutomationActionRestarts() {
        RulesApi rules = ControlCoreApi.get().rules();
        var saved = rules.save(
                Optional.empty(),
                new RulesApi.Form(
                        "Nightly", "every-minutes", "1", "none", "", List.of(new RulesApi.Step("restart", "30")), ""));

        assertThat(rules.test(saved.value()).isSuccess()).isTrue();
        assertThat(api().restartStatus().remaining()).contains(Duration.ofSeconds(30));
    }

    private static RulesApi.Form rule(String name, String trigger, String text) {
        return new RulesApi.Form(name, trigger, "", "none", "", List.of(new RulesApi.Step("message-all", text)), "");
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

    private void awaitStop() {
        long until = System.currentTimeMillis() + 5_000;
        while (platform.control.stops.isEmpty() && System.currentTimeMillis() < until) {
            Thread.onSpinWait();
        }
        assertThat(platform.control.stops).hasSize(1);
    }

    private List<String> chat() {
        return platform.messenger.textsTo(player.id());
    }

    private static ServerApi api() {
        return ControlCoreApi.get().server();
    }
}
