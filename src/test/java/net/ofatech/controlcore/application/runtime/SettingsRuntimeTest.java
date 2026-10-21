package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import org.junit.jupiter.api.Test;

class SettingsRuntimeTest extends RuntimeTestBase {

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
}
