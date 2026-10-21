package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.ControlPanelTab;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.fakes.RecordingControlPanelView;
import org.junit.jupiter.api.Test;

/** The control panel and its commands, wired as on a server. */
class ControlPanelRuntimeTest extends RuntimeTestBase {

    @Test
    void commandFallsBackToTheBuiltInUiWithoutAUiMod() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");

        var result = platform.commands.run(COMMAND, admin, Map.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(platform.controlPanel.opened)
                .containsExactly(new RecordingControlPanelView.Opened(admin.id(), platform.serverConfig.settings));
        assertThat(platform.log.contains("Control panel is available (built-in Hytale UI)"))
                .isTrue();
    }

    @Test
    void anyPlayerCanOpenTheirPreferences() {
        runtime.setup();
        runtime.start();
        PlayerInfo player = platform.players.join("Player");
        var spec = platform.commands.commands.get("prefs").spec();

        var result = platform.commands.run("prefs", player, Map.of());

        assertThat(spec.permission()).isEmpty();
        assertThat(spec.playerOnly()).isTrue();
        assertThat(result.isSuccess()).isTrue();
        assertThat(platform.controlPanel.players()).containsExactly(player.id());
        assertThat(platform.controlPanel.lastRequest).isEqualTo(ControlPanelRequest.PLAYER_PREFERENCES);
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
                .filteredOn(id -> !id.startsWith("controlcore:hud-rule."))
                .containsExactly(
                        "controlcore:control-panel-enabled",
                        "controlcore:empty-hand-keys",
                        "controlcore:welcome-message",
                        "controlcore:welcome-message-color",
                        "controlcore:mods-hidden");
    }

    @Test
    void theModsTabLinksModsToTheirSettingsAndLetsAdminsTurnThemOff() {
        platform.modCatalog.add("OFATech:ControlCore", false);
        platform.modCatalog.add("Acme:Homes", false);
        platform.modCatalog.add("Hytale:Weather", true);
        runtime.setup();
        runtime.start();
        ControlCoreApi.get()
                .settings()
                .register(SettingDefinition.server("homes", "max", SettingType.integer(1, 9), 3));
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo player = platform.players.join("Player");

        platform.commands.run(COMMAND, admin, Map.of());
        var mods = platform.controlPanel.lastContent.mods();
        var homes = mods.list().stream()
                .filter(e -> e.mod().id().equals("Acme:Homes"))
                .findFirst()
                .orElseThrow();
        var self = mods.list().stream()
                .filter(e -> e.mod().id().equals("OFATech:ControlCore"))
                .findFirst()
                .orElseThrow();
        var weather =
                mods.list().stream().filter(e -> e.mod().core()).findFirst().orElseThrow();

        assertThat(homes.namespace()).contains("homes");
        assertThat(homes.hasServerSettings()).isTrue();
        assertThat(self.namespace()).contains("controlcore");
        assertThat(self.hasKeys()).isTrue();
        assertThat(self.manageable()).as("cannot turn itself off").isFalse();
        assertThat(weather.manageable()).isFalse();
        assertThat(homes.manageable()).isTrue();

        assertThat(mods.setEnabled("Acme:Homes", false).isSuccess()).isTrue();
        assertThat(platform.modCatalog.mods().stream()
                        .filter(m -> m.id().equals("Acme:Homes"))
                        .findFirst()
                        .orElseThrow()
                        .changesAfterRestart())
                .isTrue();
        assertThat(mods.setEnabled("Hytale:Weather", false).error().code()).isEqualTo("Mods.Forbidden");

        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:mods-hidden", "value", "Acme:Homes"));
        platform.deniedNodes.addAll(List.of("settings", "admin"));
        platform.commands.run("prefs", player, Map.of());
        var playersView = platform.controlPanel.lastContent.mods();
        assertThat(playersView.canManage()).isFalse();
        assertThat(playersView.list()).extracting(e -> e.mod().id()).doesNotContain("Acme:Homes");
        assertThat(playersView.setEnabled("OFATech:ControlCore", false).isFailure())
                .isTrue();

        var opened = platform.commands.run("cc-config", player, Map.of("mod", "controlcore"));
        assertThat(opened.isSuccess()).isTrue();
        assertThat(platform.controlPanel.lastContent.focus()).contains("controlcore");
        assertThat(platform.commands
                        .run("cc-config", player, Map.of("mod", "nothing"))
                        .error()
                        .code())
                .isEqualTo("Mods.Unknown");
    }
}
