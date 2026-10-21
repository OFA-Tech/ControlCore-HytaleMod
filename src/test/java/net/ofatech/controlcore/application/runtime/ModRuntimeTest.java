package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.ControlPanelTab;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.hud.HudElement;
import net.ofatech.controlcore.domain.model.hud.HudPart;
import net.ofatech.controlcore.domain.model.keys.Key;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.fakes.FakePlatform;
import net.ofatech.controlcore.fakes.RecordingCommandRunner;
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
    void playersRunAndRebindKeyActionsOfOtherMods() {
        runtime.setup();
        runtime.start();
        PlayerInfo player = platform.players.join("Player");
        ControlCoreApi.get()
                .keymaps()
                .register(KeyAction.of("homes", "home")
                        .displayName("Go home")
                        .command("home")
                        .defaultKeys(Key.R));

        platform.input.press(player.id(), KeySignal.ABILITY_3);
        var bind = platform.commands.run("cc-bind", player, Map.of("id", "homes:home", "keys", "ctrl+f"));
        platform.clock.now = platform.clock.now.plusSeconds(1);
        platform.input.hold(player.id(), KeySignal.CROUCHING);
        platform.input.press(player.id(), KeySignal.USE);
        var list = platform.commands.run("cc-keys", player, Map.of());

        assertThat(bind.isSuccess()).isTrue();
        assertThat(platform.commandRunner.runs)
                .extracting(run -> run.command())
                .containsExactly("home", "prefs", "home");
        assertThat(list.value().text().orElseThrow())
                .contains("Go home: CTRL + F (yours)", "Open my preferences: CTRL + F");
        assertThat(platform.commands.commands.get("cc-bind-server").spec().permission())
                .isPresent();
    }

    @Test
    void adminsCreateServerKeyBindsThatOnlyPermittedPlayersCanUse() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");

        var add = platform.commands.run(
                "cc-keybind-add-server",
                admin,
                Map.of("name", "announce", "keys", "ctrl+r", "command", "/say {player} pressed it"));
        platform.input.hold(admin.id(), KeySignal.CROUCHING);
        platform.input.press(admin.id(), KeySignal.ABILITY_3);
        platform.deniedNodes.add("keys.server");
        platform.clock.now = platform.clock.now.plusSeconds(1);
        platform.input.press(admin.id(), KeySignal.ABILITY_3);
        var remove = platform.commands.run("cc-keybind-remove", admin, Map.of("name", "announce"));

        assertThat(add.value().text().orElseThrow()).contains("custom:announce", "as the server");
        assertThat(platform.commandRunner.runs)
                .containsExactly(new RecordingCommandRunner.Run(null, "say Admin pressed it"));
        assertThat(remove.isSuccess()).isTrue();
        assertThat(platform.customKeyBinds.binds).isEmpty();
        assertThat(platform.commands.commands.get("cc-keybind-add").spec().permission())
                .isPresent();
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
    void settingsCommandsAreRegisteredWithTheirPermissions() {
        runtime.setup();

        assertThat(platform.commands.commands)
                .containsKeys("cc-settings", "cc-get", "cc-set", "cc-reset", "cc-prefs", "cc-pref", "cc-pref-reset");
        assertThat(platform.commands.commands.get("cc-set").spec().permission())
                .as("checked per setting")
                .isEmpty();
        assertThat(platform.commands.commands.get("cc-perm").spec().permission())
                .contains("settings");
        assertThat(platform.commands.commands.get("cc-input-probe").spec().permission())
                .as("debug tools are not for every admin")
                .contains("debug");
        assertThat(platform.commands.commands.get("cc-key-probe").spec().permission())
                .contains("debug");
        assertThat(platform.commands.commands.get("cc-pref").spec().permission())
                .isEmpty();
        assertThat(platform.commands.commands.get("cc-pref").spec().playerOnly())
                .isTrue();
    }

    @Test
    void adminsLetOtherPlayersSeeOrChangeSingleSettings() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo moderator = platform.players.join("Moderator");
        String welcome = "controlcore:welcome-message";

        var rule = platform.commands.run(
                "cc-perm", admin, Map.of("target", welcome, "side", "edit", "node", "mods.welcome"));
        var viewRule = platform.commands.run(
                "cc-perm", admin, Map.of("target", "controlcore:show-welcome-message", "side", "both", "node", "x"));
        platform.deniedNodes.addAll(List.of("settings", "admin"));

        var changed = platform.commands.run("cc-set", moderator, Map.of("id", welcome, "value", "Hi"));
        var hidden = platform.commands.run(
                "cc-set", moderator, Map.of("id", "controlcore:control-panel-enabled", "value", "off"));
        var opened = platform.commands.run(COMMAND, moderator, Map.of());
        var listed = platform.commands.run("cc-settings", moderator, Map.of());

        assertThat(rule.value().text().orElseThrow())
                .contains("setting:" + welcome + ": see (inherits), change mods.welcome");
        assertThat(viewRule.isSuccess()).isTrue();
        assertThat(changed.value().text().orElseThrow()).contains(welcome + " is now Hi.");
        assertThat(hidden.error().code()).isEqualTo("Settings.UnknownSetting");
        assertThat(opened.isSuccess()).isTrue();
        assertThat(listed.value().text().orElseThrow())
                .contains("controlcore:welcome-message = Hi")
                .doesNotContain("controlcore:control-panel-enabled");

        platform.commands.run("cc-perm", admin, Map.of("target", welcome, "side", "edit", "node", "default"));
        platform.commands.run("cc-perm", admin, Map.of("target", welcome, "side", "view", "node", "everyone"));
        var readOnly = platform.commands.run("cc-set", moderator, Map.of("id", welcome, "value", "Bye"));
        var read = platform.commands.run("cc-get", moderator, Map.of("id", welcome));

        assertThat(readOnly.error().code()).isEqualTo("Settings.Forbidden");
        assertThat(read.isSuccess()).isTrue();

        platform.commands.run("cc-perm", admin, Map.of("target", welcome, "side", "view", "node", "default"));
        assertThat(platform.commands.run(COMMAND, moderator, Map.of()).error().code())
                .isEqualTo("ControlPanel.Forbidden");
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
        var compact = settings.register(SettingDefinition.player("hud", "compact", SettingType.bool(), false))
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
                .filteredOn(id -> !id.startsWith("controlcore:hud-rule."))
                .containsExactly(
                        "controlcore:control-panel-enabled",
                        "controlcore:empty-hand-keys",
                        "controlcore:welcome-message",
                        "controlcore:welcome-message-color",
                        "controlcore:mods-hidden");
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

    @Test
    void hudPartsFollowPreferencesServerRulesAndTheHideAllToggle() {
        runtime.setup();
        runtime.start();
        PlayerInfo player = platform.players.join("Player");
        var hud = ControlCoreApi.get().huds();
        var shown = new ArrayList<String>();
        hud.register(HudElement.of("minimap", "map")
                .displayName("Minimap")
                .onShow(id -> shown.add("show"))
                .onHide(id -> shown.add("hide")));
        platform.connectedPublisher.publish(new PlayerJoined(player));
        var compass = HudPart.COMPASS;
        var chat = HudPart.CHAT;

        assertThat(platform.hiddenHud.getOrDefault(player.id(), Set.of())).isEmpty();
        assertThat(shown).containsExactly("show");

        platform.commands.run("cc-pref", player, Map.of("id", "controlcore:hud.compass", "value", "false"));
        assertThat(platform.hiddenHud.get(player.id())).containsExactly(compass);

        PlayerInfo admin = platform.players.join("Admin");
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:hud-rule.chat", "value", "Always hidden"));
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:hud-rule.compass", "value", "Always shown"));
        assertThat(platform.hiddenHud.get(player.id())).containsExactly(chat);

        hud.setHidden(player.id(), true);
        assertThat(platform.hiddenHud.get(player.id())).containsExactlyInAnyOrder(HudPart.values());
        assertThat(hud.isVisible(player.id(), SettingId.of("minimap:map"))).isFalse();
        hud.setHidden(player.id(), false);
        assertThat(platform.hiddenHud.get(player.id())).containsExactly(chat);
        assertThat(shown).containsExactly("show", "hide", "show");

        platform.commands.run("cc-pref", player, Map.of("id", "minimap:hud.map", "value", "false"));
        assertThat(shown).containsExactly("show", "hide", "show", "hide");
        assertThat(ControlCoreApi.get().keymaps().find(SettingId.of("controlcore:toggle-hud")))
                .isPresent();
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
