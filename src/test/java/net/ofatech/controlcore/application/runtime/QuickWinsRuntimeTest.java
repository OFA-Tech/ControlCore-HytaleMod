package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import net.ofatech.controlcore.domain.event.PlayerGameModeChanged;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.hud.HudPart;
import org.junit.jupiter.api.Test;

class QuickWinsRuntimeTest extends RuntimeTestBase {

    @Test
    void configChangedByHytalesOwnCommandsIsNoticedAndRecorded() {
        runtime.setup();
        runtime.start();
        runOutsideCheck();

        platform.serverConfig.write("MaxViewRadius", "12");
        int before = platform.changeLog.records.size();
        runOutsideCheck();

        assertThat(platform.changeLog.records).hasSize(before + 1);
        ChangeRecord record = platform.changeLog.records.getLast();
        assertThat(record.target()).isEqualTo("server:max-view-radius");
        assertThat(record.newValue()).isEqualTo("12");
        assertThat(record.source()).isEqualTo(ChangeRecord.Source.OUTSIDE);

        runOutsideCheck();
        assertThat(platform.changeLog.records).hasSize(before + 1);
    }

    @Test
    void changesMadeThroughControlCoreAreNotCountedTwice() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        runOutsideCheck();

        platform.commands.run("cc-set", admin, Map.of("id", "server:max-view-radius", "value", "10"));
        int before = platform.changeLog.records.size();
        runOutsideCheck();

        assertThat(platform.changeLog.records).hasSize(before);
    }

    @Test
    void hiddenHudPartsAreHiddenAgainAfterAGameModeChange() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        platform.commands.run("cc-pref", admin, Map.of("id", "controlcore:hud.compass", "value", "false"));
        platform.hiddenHud.clear();

        platform.connectedPublisher.publish(new PlayerGameModeChanged(admin));
        platform.scheduler.entries.stream()
                .filter(entry -> entry.period == null && entry.delay.equals(Duration.ofMillis(500)))
                .forEach(entry -> entry.work.run());

        assertThat(platform.hiddenHud.get(admin.id())).contains(HudPart.COMPASS);
    }

    private void runOutsideCheck() {
        platform.scheduler.entries.stream()
                .filter(entry -> Duration.ofSeconds(5).equals(entry.period))
                .forEach(entry -> entry.work.run());
    }
}
