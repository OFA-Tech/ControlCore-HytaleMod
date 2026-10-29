package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.ofatech.controlcore.application.keymaps.KeyDetector;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.PanelRights;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.game.KeyDetection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeyDetectRuntimeBanca3Test extends RuntimeTestBase {

    private PlayerInfo admin;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
        platform.connectedPublisher.publish(new PlayerJoined(admin));
    }

    @Test
    void rightsChangeWhenAdminIsTakenAway() {
        Supplier<PanelRights> rights = rights();
        PanelRights atStart = rights.get();

        platform.deniedNodes.addAll(List.of("settings", "admin"));

        assertThat(rights.get()).isNotEqualTo(atStart);
    }

    @Test
    void rightsChangeWhenOnlyTheHistoryPermissionIsTakenAwayFromAModerator() {
        PlayerInfo moderator = platform.players.join("Mod");
        platform.connectedPublisher.publish(new PlayerJoined(moderator));
        platform.deniedNodesFor.put(moderator.id(), List.of("settings", "admin"));
        platform.commands.run(COMMAND, moderator, Map.of());
        Supplier<PanelRights> rights = platform.controlPanel.lastContent.rights();
        PanelRights atStart = rights.get();

        platform.deniedNodesFor.put(moderator.id(), List.of("settings", "admin", "history"));

        assertThat(rights.get()).isNotEqualTo(atStart);
    }

    @Test
    void rightsChangeWhenThePanelIsTurnedOff() {
        Supplier<PanelRights> rights = rights();

        PanelRights atStart = rights.get();

        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:control-panel-enabled", "value", "off"));

        assertThat(rights.get()).isNotEqualTo(atStart);
    }

    @Test
    void rightsComeBackUnchangedWhenThePermissionIsGivenBack() {
        Supplier<PanelRights> rights = rights();
        PanelRights atStart = rights.get();
        platform.deniedNodes.add("history");
        platform.deniedNodes.remove("history");

        assertThat(rights.get()).isEqualTo(atStart);
    }

    @Test
    void unrelatedSettingsDoNotCountAsARightsChange() {
        Supplier<PanelRights> rights = rights();

        PanelRights atStart = rights.get();

        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:all-keys-in-lists", "value", "true"));

        assertThat(rights.get()).isEqualTo(atStart);
    }

    @Test
    void timeoutSecondsMatchesTheGameTimeoutAndTheToast() {
        assertThat(detection().gameTimeoutSeconds()).isEqualTo(KeyDetector.TIMEOUT.toSeconds());
    }

    @Test
    void screenSafetyTimeoutEndsWithNothingAndALateKeyIsIgnored() {
        screenMode();
        List<Optional<String>> results = new ArrayList<>();
        detection().detect(detection().mode(), results::add);

        platform.scheduler.runAll();
        platform.keyCapture.press(admin.id(), "NUMPAD_5");

        assertThat(results).containsExactly(Optional.empty());
    }

    @Test
    void secondScreenDetectReplacesTheFirstEvenWhenTheFirstScreenReportsLate() {
        screenMode();
        KeyDetection detection = detection();
        List<Optional<String>> first = new ArrayList<>();
        List<Optional<String>> second = new ArrayList<>();
        detection.detect(detection.mode(), first::add);
        detection.detect(detection.mode(), second::add);

        platform.scheduler.runAll();

        assertThat(first).isEmpty();
        assertThat(second).containsExactly(Optional.empty());
    }

    @Test
    void noneDetectionAnswersRightAwayWithNothing() {
        List<Optional<String>> results = new ArrayList<>();

        KeyDetection.NONE.detect(KeyDetection.NONE.mode(), results::add);

        assertThat(KeyDetection.NONE.mode()).isEqualTo(KeyDetection.Mode.SCREEN);
        assertThat(results).containsExactly(Optional.empty());
    }

    private void screenMode() {
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
    }

    private Supplier<PanelRights> rights() {
        platform.commands.run(COMMAND, admin, Map.of());
        return platform.controlPanel.lastContent.rights();
    }

    private KeyDetection detection() {
        platform.commands.run(COMMAND, admin, Map.of());
        return platform.controlPanel.lastContent.keyDetection();
    }
}
