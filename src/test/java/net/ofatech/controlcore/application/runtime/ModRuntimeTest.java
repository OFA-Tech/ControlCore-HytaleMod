package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.fakes.TestModPlatforms;
import org.junit.jupiter.api.Test;

class ModRuntimeTest extends RuntimeTestBase {

    @Test
    void setupResolvesAndRegistersEveryComponent() {
        runtime.setup();

        assertThat(platform.commands.commands).containsKey(COMMAND);
        var spec = platform.commands.commands.get(COMMAND).spec();
        assertThat(spec.permission())
                .as("checked per setting when the command runs")
                .isEmpty();
        assertThat(spec.playerOnly()).isTrue();
        assertThat(platform.connectedPublisher).isNotNull();
        assertThat(platform.log.contains("Set up on Fake")).isTrue();
    }

    @Test
    void anInstalledModIntegrationOffersItsCapability() {
        platform.mods.loaded.add(TestModPlatforms.WORKING);
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");

        var greeter = runtime.capabilities().find(TestModPlatforms.Greeter.class);

        assertThat(greeter).isPresent();
        assertThat(greeter.get().greet(admin.id()).isSuccess()).isTrue();
        assertThat(TestModPlatforms.OPENED).containsExactly(admin.id());
    }

    @Test
    void aBrokenModPlatformStaysIsolated() {
        platform.mods.loaded.add(TestModPlatforms.WORKING);
        platform.mods.loaded.add(TestModPlatforms.BROKEN);
        runtime.setup();

        runtime.start();

        assertThat(platform.log.contains("Integration with Test:Broken failed")).isTrue();
        assertThat(ControlCoreApi.get().controlPanel().isAvailable()).isTrue();
    }

    @Test
    void apiIsAvailableOnlyBetweenSetupAndShutdown() {
        assertThat(ControlCoreApi.find()).isEmpty();
        assertThatThrownBy(ControlCoreApi::get).isInstanceOf(IllegalStateException.class);

        runtime.setup();
        ControlCoreApi api = ControlCoreApi.get();
        assertThat(api.apiVersion()).isEqualTo(ControlCoreApi.VERSION);

        runtime.shutdown();
        assertThat(ControlCoreApi.find()).isEmpty();
    }

    @Test
    void otherModsCanOpenThePanelAndListenToGameEvents() {
        platform.mods.loaded.add(TestModPlatforms.WORKING);
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        var joined = new ArrayList<PlayerJoined>();
        ControlCoreApi.get().events().subscribe(PlayerJoined.class, joined::add);

        platform.connectedPublisher.publish(new PlayerJoined(admin));
        var result = ControlCoreApi.get().controlPanel().open(admin.id(), ControlPanelRequest.ALL);

        assertThat(joined).hasSize(1);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void tasksAreScheduledAndRunOnceMoreAtShutdown() {
        runtime.setup();
        runtime.start();

        assertThat(platform.scheduler.entries)
                .extracting(entry -> entry.period)
                .containsExactlyInAnyOrder(
                        Duration.ofMinutes(5),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(1));
        platform.scheduler.runAll();
        assertThat(platform.playerData.flushes).isEqualTo(1);

        runtime.shutdown();
        assertThat(platform.scheduler.entries)
                .allSatisfy(entry -> assertThat(entry.cancelled).isTrue());
        assertThat(platform.playerData.flushes).isEqualTo(2);
    }

    @Test
    void emptyHandKeysAreAppliedAtStartAndWhenTheSettingChanges() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");

        assertThat(platform.emptyHandKeys).as("on by default").isTrue();
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:empty-hand-keys", "value", "false"));
        assertThat(platform.emptyHandKeys).isFalse();
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:empty-hand-keys", "value", "true"));
        assertThat(platform.emptyHandKeys).as("and back on").isTrue();
    }
}
