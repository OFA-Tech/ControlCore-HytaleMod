package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.ControlPanelTab;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.mods.ModDependency;
import net.ofatech.controlcore.domain.model.mods.ModsView;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.fakes.RecordingControlPanelView;
import org.junit.jupiter.api.Test;

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

        assertThat(set.value().text().orElse("")).contains("controlcore:control-panel-enabled is now false.");
        assertThat(platform.settingStore.values).isEmpty();
        assertThat(blocked.error().code()).isEqualTo("ControlPanel.Disabled");
        assertThat(reset.value().text().orElse(""))
                .contains("controlcore:control-panel-enabled is now true (default).");
        assertThat(opened.isSuccess()).isTrue();
        assertThat(platform.log.contains("Admin set controlcore:control-panel-enabled = false"))
                .isTrue();
    }

    @Test
    void settingsCommandsAlsoReadAndChangeTheServersConfig() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");

        var get = platform.commands.run("cc-get", admin, Map.of("id", "server:server-name"));
        var set = platform.commands.run("cc-set", admin, Map.of("id", "server:server-name", "value", "New Name"));
        var restart =
                platform.commands.run("cc-set", admin, Map.of("id", "server:defaults.game-mode", "value", "Adventure"));
        var locked = platform.commands.run("cc-set", admin, Map.of("id", "server:version", "value", "5"));
        var lockedDetails = platform.commands.run("cc-get", admin, Map.of("id", "server:version"));
        var reset = platform.commands.run("cc-reset", admin, Map.of("id", "server:server-name"));

        assertThat(get.value().text().orElse("")).contains("server:server-name", "Test Server");
        assertThat(set.value().text().orElse("")).contains("server:server-name is now New Name.");
        assertThat(restart.value().text().orElse("")).contains("Applies after a restart.");
        assertThat(locked.isFailure()).isTrue();
        assertThat(lockedDetails.value().text().orElse("")).contains("Read-only.");
        assertThat(reset.value().text().orElse("")).contains("server:server-name is now");
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
                .filteredOn(
                        id -> !id.startsWith("controlcore:hud-rule.") && !id.equals("controlcore:hud-context-rules"))
                .containsExactly(
                        "controlcore:control-panel-enabled",
                        "controlcore:empty-hand-keys",
                        "controlcore:history-days",
                        "controlcore:keys-keep-game-action",
                        "controlcore:welcome-message",
                        "controlcore:welcome-message-color",
                        "controlcore:automation-enabled",
                        "controlcore:player-heads",
                        "controlcore:all-keys-in-lists",
                        "controlcore:key-detect-mode",
                        "controlcore:mods-hidden",
                        "controlcore:player-jump",
                        "controlcore:player-speed",
                        "controlcore:chunk-sections-per-second",
                        "controlcore:chunk-sections-per-tick",
                        "controlcore:pause-empty-worlds");
    }

    @Test
    void turningModsOnAndOffFollowsTheirDependencies() {
        platform.modCatalog.add("Acme:Core", false);
        platform.modCatalog.add(
                "Acme:Homes",
                false,
                new ModDependency("Acme:Core", ">=1.0.0", false, ModDependency.Status.OK),
                new ModDependency("Acme:Maps", "", true, ModDependency.Status.MISSING));
        platform.modCatalog.add(
                "Acme:Warps", false, new ModDependency("Acme:Homes", "", false, ModDependency.Status.OK));
        platform.modCatalog.add(
                "Acme:Broken", false, new ModDependency("Other:Lib", ">=2.0.0", false, ModDependency.Status.MISSING));
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        platform.commands.run(COMMAND, admin, Map.of());
        var mods = platform.controlPanel.lastContent.mods();

        assertThat(entry(mods, "Acme:Core").neededBy()).containsExactly("Homes");
        assertThat(entry(mods, "Acme:Homes").hasProblems())
                .as("optional missing is fine")
                .isFalse();
        assertThat(entry(mods, "Acme:Broken").hasProblems()).isTrue();
        assertThat(mods.alsoChanged("Acme:Core", false))
                .extracting(e -> e.mod().id())
                .containsExactlyInAnyOrder("Acme:Homes", "Acme:Warps");

        assertThat(mods.setEnabled("Acme:Core", false).isSuccess()).isTrue();
        assertThat(entry(mods, "Acme:Warps").mod().enabledAfterRestart()).isFalse();
        assertThat(platform.changeLog.records).hasSize(3);

        assertThat(mods.alsoChanged("Acme:Warps", true))
                .extracting(e -> e.mod().id())
                .containsExactlyInAnyOrder("Acme:Homes", "Acme:Core");
        assertThat(mods.setEnabled("Acme:Warps", true).isSuccess()).isTrue();
        assertThat(entry(mods, "Acme:Core").mod().enabledAfterRestart()).isTrue();
        assertThat(entry(mods, "Acme:Homes").mod().dependencies())
                .extracting(ModDependency::status)
                .containsExactly(ModDependency.Status.OK, ModDependency.Status.MISSING);
    }

    private static ModsView.Entry entry(ModsView mods, String id) {
        return mods.list().stream()
                .filter(e -> e.mod().id().equals(id))
                .findFirst()
                .orElseThrow();
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
