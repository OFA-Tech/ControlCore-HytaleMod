package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.ControlPanelContent;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.keys.GameActionMode;
import net.ofatech.controlcore.domain.model.keys.Key;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.ports.game.KeyDetection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeymapsRightsRuntimeBanca5Test extends RuntimeTestBase {

    private static final SettingId HOME = SettingId.of("homes:home");

    private PlayerInfo admin;
    private PlayerInfo player;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
        player = platform.players.join("Player");
        platform.connectedPublisher.publish(new PlayerJoined(admin));
        platform.connectedPublisher.publish(new PlayerJoined(player));
        platform.deniedNodesFor.put(player.id(), List.of("settings", "admin"));
        ControlCoreApi.get()
                .keymaps()
                .register(KeyAction.of("homes", "home")
                        .displayName("Go home")
                        .command("home")
                        .defaultKeys(Key.R));
    }

    @Test
    void aPlayerWithoutAdminCannotChangeServerKeysThroughThePrefsPanelContent() {
        ControlPanelContent content = open(player, "prefs");

        assertThat(content.keymaps()
                        .write(HOME, Optional.empty(), "ctrl+f")
                        .error()
                        .code())
                .isEqualTo("Keys.ServerOnlyAdmins");
        assertThat(content.keymaps().reset(HOME, Optional.empty()).error().code())
                .isEqualTo("Keys.ServerOnlyAdmins");
        assertThat(content.customKeyBinds()
                        .add("evil", "ctrl+o", "op Player", true)
                        .error()
                        .code())
                .isEqualTo("Keys.ServerOnlyAdmins");
        assertThat(content.customKeyBinds()
                        .setGameAction(HOME, GameActionMode.REPLACE)
                        .error()
                        .code())
                .isEqualTo("Keys.ServerOnlyAdmins");

        assertThat(platform.customKeyBinds.binds).isEmpty();
        assertThat(content.customKeyBinds().gameAction(HOME)).isEqualTo(GameActionMode.SERVER_SETTING);
        assertThat(content.keymaps().read(HOME, Optional.empty()).value().value())
                .isEqualTo("R");
    }

    @Test
    void aPlayerWithoutAdminCannotRemoveTheAdminsBind() {
        open(admin, COMMAND).customKeyBinds().add("warp", "ctrl+r", "warp spawn", false);
        ControlPanelContent content = open(player, "prefs");

        assertThat(content.customKeyBinds().remove("warp").error().code()).isEqualTo("Keys.ServerOnlyAdmins");
        assertThat(content.customKeyBinds().find("warp")).isPresent();
    }

    @Test
    void aPlayerWithoutAdminStillChangesTheirOwnKeys() {
        ControlPanelContent content = open(player, "prefs");

        var own = content.keymaps().write(HOME, Optional.of(player.id()), "alt+3");

        assertThat(own.isSuccess()).isTrue();
        assertThat(own.value().value()).isEqualTo("ALT + 3");
    }

    @Test
    void anAdminChangesServerKeysThroughTheSameContent() {
        ControlPanelContent content = open(admin, COMMAND);

        assertThat(content.keymaps().write(HOME, Optional.empty(), "ctrl+f").isSuccess())
                .isTrue();
        assertThat(content.customKeyBinds()
                        .setGameAction(HOME, GameActionMode.REPLACE)
                        .isSuccess())
                .isTrue();
        assertThat(content.customKeyBinds()
                        .add("warp", "ctrl+r", "warp spawn", false)
                        .isSuccess())
                .isTrue();
    }

    @Test
    void anAdminWhoLosesAdminWhileThePanelIsOpenIsRefusedOnTheNextChange() {
        ControlPanelContent content = open(admin, COMMAND);
        platform.deniedNodesFor.put(admin.id(), List.of("settings", "admin"));

        assertThat(content.keymaps()
                        .write(HOME, Optional.empty(), "ctrl+f")
                        .error()
                        .code())
                .isEqualTo("Keys.ServerOnlyAdmins");
        assertThat(content.keymaps()
                        .write(HOME, Optional.of(admin.id()), "ctrl+f")
                        .isSuccess())
                .isTrue();
    }

    @Test
    void aFailedOpenDoesNotCancelAPendingDetect() {
        KeyDetection detection = open(admin, COMMAND).keyDetection();
        List<Optional<String>> results = new ArrayList<>();
        detection.detect(KeyDetection.Mode.GAME, results::add);
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:control-panel-enabled", "value", "off"));
        int openedBefore = platform.controlPanel.opened.size();

        var blocked = platform.commands.run(COMMAND, admin, Map.of());
        platform.input.press(admin.id(), KeySignal.USE);

        assertThat(blocked.isFailure()).isTrue();
        assertThat(platform.controlPanel.opened).hasSize(openedBefore);
        assertThat(results).containsExactly(Optional.of("F"));
    }

    @Test
    void reopeningDuringAGameDetectNeverClosesACaptureScreen() {
        KeyDetection detection = open(admin, COMMAND).keyDetection();
        detection.detect(KeyDetection.Mode.GAME, result -> {});

        open(admin, COMMAND);

        assertThat(platform.keyCapture.closed).isEmpty();
    }

    @Test
    void openingThePrefsPanelAlsoCancelsAScreenDetectAndClosesTheCapture() {
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
        KeyDetection detection = open(admin, COMMAND).keyDetection();
        List<Optional<String>> results = new ArrayList<>();
        detection.detect(KeyDetection.Mode.SCREEN, results::add);

        open(admin, "prefs");
        platform.keyCapture.press(admin.id(), "F");
        platform.scheduler.runAll();

        assertThat(platform.keyCapture.closed).containsExactly(admin.id());
        assertThat(results).isEmpty();
    }

    private ControlPanelContent open(PlayerInfo who, String command) {
        var result = platform.commands.run(command, who, Map.of());
        assertThat(result.isSuccess()).as(command + " for " + who.name()).isTrue();
        return platform.controlPanel.lastContent;
    }
}
