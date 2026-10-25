package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import net.ofatech.controlcore.domain.event.PlayerEnteredWorld;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import org.junit.jupiter.api.Test;

class WorldsRuntimeTest extends RuntimeTestBase {

    @Test
    void worldSettingsAreChangedLikeConfigKeysAndRecorded() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");

        platform.commands.run("cc-set", admin, Map.of("id", "world:default.is-pvp-enabled", "value", "true"));

        assertThat(platform.worlds.values.get("default")).containsEntry("IsPvpEnabled", "true");
        ChangeRecord record = platform.changeLog.records.getLast();
        assertThat(record.target()).isEqualTo("world:default.is-pvp-enabled");
        assertThat(record.oldValue()).isEqualTo("false");
        assertThat(record.newValue()).isEqualTo("true");
    }

    @Test
    void worldsChangedByHytalesOwnCommandsAreNoticed() {
        runtime.setup();
        runtime.start();
        runEvery(Duration.ofSeconds(5));

        platform.worlds.values.get("default").put("Tps", "20");
        runEvery(Duration.ofSeconds(5));

        ChangeRecord record = platform.changeLog.records.getLast();
        assertThat(record.target()).isEqualTo("world:default.tps");
        assertThat(record.source()).isEqualTo(ChangeRecord.Source.OUTSIDE);
    }

    @Test
    void snapshotsKeepWorldsAsTheirOwnPart() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        platform.commands.run("cc-snapshot-save", admin, Map.of("name", "base", "parts", "worlds"));
        platform.commands.run("cc-set", admin, Map.of("id", "world:default.is-pvp-enabled", "value", "true"));
        platform.commands.run("cc-set", admin, Map.of("id", "server:max-view-radius", "value", "10"));

        platform.commands.run("cc-snapshot-restore", admin, Map.of("name", "base"));

        assertThat(platform.worlds.values.get("default")).containsEntry("IsPvpEnabled", "false");
        assertThat(platform.snapshots.snapshots.get("base").config()).containsKey("world:default.is-pvp-enabled");
        assertThat(platform.snapshots.snapshots.get("base").config()).doesNotContainKey("server:max-view-radius");
    }

    @Test
    void emptyWorldsArePausedAndResumeWhenSomeoneArrives() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:pause-empty-worlds", "value", "true"));

        runEvery(Duration.ofSeconds(10));
        assertThat(platform.worlds.isPaused("default")).isTrue();

        platform.worlds.players.put("default", 1);
        platform.connectedPublisher.publish(new PlayerEnteredWorld(admin, "default"));
        assertThat(platform.worlds.isPaused("default")).isFalse();

        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:pause-empty-worlds", "value", "false"));
        platform.worlds.players.put("default", 0);
        runEvery(Duration.ofSeconds(10));
        assertThat(platform.worlds.isPaused("default")).isFalse();
    }

    @Test
    void speedAndChunkLimitsFollowTheServerAndTheWorldOverride() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        platform.tuning.worlds.put(admin.id(), "default");

        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:player-speed", "value", "150"));
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:chunk-sections-per-second", "value", "200"));
        assertThat(platform.tuning.applied.get(admin.id()).speedPercent()).isEqualTo(150);
        assertThat(platform.tuning.applied.get(admin.id()).sectionsPerSecond()).isEqualTo(200);

        platform.commands.run("cc-set", admin, Map.of("id", "world:default.speed-percent", "value", "250"));
        assertThat(platform.tuning.applied.get(admin.id()).speedPercent()).isEqualTo(250);
        assertThat(platform.tuning.applied.get(admin.id()).jumpPercent()).isEqualTo(100);
    }

    private void runEvery(Duration period) {
        platform.scheduler.entries.stream()
                .filter(entry -> period.equals(entry.period))
                .forEach(entry -> entry.work.run());
    }
}
