package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.ControlPanelTab;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.fakes.FakePlatform;
import net.ofatech.controlcore.fakes.RecordingControlPanelView;
import net.ofatech.controlcore.fakes.TestModPlatforms;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Builds every discovered component against fake ports: the "does it all resolve" test. */
class ModRuntimeTest {

    private static final String COMMAND = "open-admin-config";

    private final FakePlatform platform = new FakePlatform();
    private final ModRuntime runtime = new ModRuntime(platform, ModRuntimeTest.class.getClassLoader());

    @BeforeEach
    void reset() {
        TestModPlatforms.OPENED.clear();
    }

    @AfterEach
    void shutDown() {
        runtime.shutdown();
    }

    @Test
    void setupResolvesAndRegistersEveryComponent() {
        runtime.setup();

        assertThat(platform.commands.commands).containsKey(COMMAND);
        var spec = platform.commands.commands.get(COMMAND).spec();
        assertThat(spec.permission()).contains("admin");
        assertThat(spec.playerOnly()).isTrue();
        assertThat(platform.connectedPublisher).isNotNull();
        assertThat(platform.log.contains("Set up on Fake")).isTrue();
    }

    @Test
    void commandOpensThePanelWhenAUiModIsLoaded() {
        platform.mods.loaded.add(TestModPlatforms.WORKING);
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");

        var result = platform.commands.run(COMMAND, admin, Map.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().text()).isEmpty();
        assertThat(TestModPlatforms.OPENED).containsExactly(admin.id());
        assertThat(platform.controlPanel.opened).isEmpty();
        assertThat(platform.log.contains("Control panel is available (HyUI)")).isTrue();
    }

    @Test
    void commandFallsBackToTheBuiltInUiWithoutAUiMod() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");

        var result = platform.commands.run(COMMAND, admin, Map.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(platform.controlPanel.opened)
                .containsExactly(new RecordingControlPanelView.Opened(admin.id(), platform.server));
        assertThat(platform.log.contains("Ellie:HyUI is not installed")).isTrue();
        assertThat(platform.log.contains("Control panel is available (built-in Hytale UI)"))
                .isTrue();
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
        var joined = new java.util.ArrayList<PlayerJoined>();
        ControlCoreApi.get().events().subscribe(PlayerJoined.class, joined::add);

        platform.connectedPublisher.publish(new PlayerJoined(admin));
        var result = ControlCoreApi.get().controlPanel().open(admin.id(), ControlPanelRequest.ALL);

        assertThat(joined).hasSize(1);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void settingsCommandsAreRegisteredWithTheirPermissions() {
        runtime.setup();

        assertThat(platform.commands.commands)
                .containsKeys("cc-settings", "cc-get", "cc-set", "cc-reset", "cc-prefs", "cc-pref", "cc-pref-reset");
        assertThat(platform.commands.commands.get("cc-set").spec().permission()).contains("settings");
        assertThat(platform.commands.commands.get("cc-pref").spec().permission())
                .isEmpty();
        assertThat(platform.commands.commands.get("cc-pref").spec().playerOnly())
                .isTrue();
    }

    @Test
    void adminsCanSwitchThePanelOffAndOnWithSettingsCommands() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");

        var set = platform.commands.run(
                "cc-set", admin, Map.of("id", "ControlCore:Control-Panel-Enabled", "value", "off"));
        var blocked = platform.commands.run(COMMAND, admin, Map.of());
        var reset = platform.commands.run("cc-reset", admin, Map.of("id", "controlcore:control-panel-enabled"));
        var opened = platform.commands.run(COMMAND, admin, Map.of());

        assertThat(set.value().text()).contains("controlcore:control-panel-enabled is now false.");
        assertThat(platform.settingStore.values).isEmpty();
        assertThat(blocked.error().code()).isEqualTo("ControlPanel.Disabled");
        assertThat(reset.value().text()).contains("controlcore:control-panel-enabled is now true (default).");
        assertThat(opened.isSuccess()).isTrue();
        assertThat(platform.log.contains("Admin set controlcore:control-panel-enabled = false"))
                .isTrue();
    }

    @Test
    void listAndGetDescribeSettings() {
        runtime.setup();
        PlayerInfo admin = platform.players.join("Admin");

        var list = platform.commands.run("cc-settings", admin, Map.of());
        var get = platform.commands.run("cc-get", admin, Map.of("id", "controlcore:control-panel-enabled"));
        var bad = platform.commands.run("cc-get", admin, Map.of("id", "nope"));

        assertThat(list.value().text().orElseThrow())
                .contains("[ControlCore]", "controlcore:control-panel-enabled = true (default)");
        assertThat(get.value().text().orElseThrow()).contains("Control panel enabled", "Type: boolean");
        assertThat(bad.error().code()).isEqualTo("Settings.InvalidId");
    }

    @Test
    void otherModsCanRegisterSettingsAndPlayersSetTheirPreferences() {
        runtime.setup();
        PlayerInfo player = platform.players.join("Player");
        var settings = ControlCoreApi.get().settings();
        var compact = settings.register(SettingDefinition.player("hud:compact", SettingType.bool(), false))
                .value();

        var set = platform.commands.run("cc-pref", player, Map.of("id", "hud:compact", "value", "yes"));
        var prefs = platform.commands.run("cc-prefs", player, Map.of());
        var wrongScope = platform.commands.run(
                "cc-pref", player, Map.of("id", "controlcore:control-panel-enabled", "value", "false"));

        assertThat(set.isSuccess()).isTrue();
        assertThat(compact.get(player.id())).isTrue();
        assertThat(prefs.value().text().orElseThrow()).contains("hud:compact = true");
        assertThat(wrongScope.error().code()).isEqualTo("Settings.WrongScope");
    }

    @Test
    void joiningPlayersGetTheWelcomeMessageUnlessTheyTurnItOff() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo quiet = platform.players.join("Quiet");

        platform.connectedPublisher.publish(new PlayerJoined(admin));
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:welcome-message", "value", "Hi {player}!"));
        platform.commands.run("cc-pref", quiet, Map.of("id", "controlcore:show-welcome-message", "value", "off"));
        platform.connectedPublisher.publish(new PlayerJoined(admin));
        platform.connectedPublisher.publish(new PlayerJoined(quiet));

        assertThat(platform.messenger.textsTo(admin.id())).containsExactly("Hi Admin!");
        assertThat(platform.messenger.textsTo(quiet.id())).isEmpty();
    }

    @Test
    void thePanelOpensOnEachPlayersStartTab() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");

        platform.commands.run(COMMAND, admin, Map.of());
        var first = platform.controlPanel.lastContent.startTab();
        platform.commands.run("cc-pref", admin, Map.of("id", "controlcore:panel-start-tab", "value", "mod settings"));
        platform.commands.run(COMMAND, admin, Map.of());

        assertThat(first).isEqualTo(ControlPanelTab.SERVER_CONFIGS);
        assertThat(platform.controlPanel.lastContent.startTab()).isEqualTo(ControlPanelTab.MOD_SETTINGS);
        assertThat(platform.controlPanel.lastContent.settings().snapshot(SettingScope.SERVER, Optional.empty()))
                .extracting(snapshot -> snapshot.id().toString())
                .containsExactly("controlcore:control-panel-enabled", "controlcore:welcome-message");
    }

    @Test
    void tasksAreScheduledAndRunOnceMoreAtShutdown() {
        runtime.setup();
        runtime.start();

        assertThat(platform.scheduler.entries).hasSize(1);
        platform.scheduler.runAll();
        assertThat(platform.playerData.flushes).isEqualTo(1);

        runtime.shutdown();
        assertThat(platform.scheduler.entries.getFirst().cancelled).isTrue();
        assertThat(platform.playerData.flushes).isEqualTo(2);
    }
}
