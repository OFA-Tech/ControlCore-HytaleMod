package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.domain.ports.game.KeyDetection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeyDetectRuntimeBanca2Test extends RuntimeTestBase {

    private PlayerInfo admin;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
        platform.connectedPublisher.publish(new PlayerJoined(admin));
    }

    @Test
    void aSecondScreenDetectReplacesTheFirstAndOnlyTheNewOneFinishes() {
        screenMode();
        KeyDetection detection = detection();
        List<Optional<String>> first = new ArrayList<>();
        List<Optional<String>> second = new ArrayList<>();

        assertThat(detection.detect(first::add)).isEqualTo(KeyDetection.Mode.SCREEN);
        assertThat(detection.detect(second::add)).isEqualTo(KeyDetection.Mode.SCREEN);
        assertThat(platform.keyCapture.opened).hasSize(2);

        platform.keyCapture.press(admin.id(), "CTRL+SHIFT+NUMPAD_DIVIDE");

        assertThat(first).isEmpty();
        assertThat(second).containsExactly(Optional.of("CTRL+SHIFT+NUMPAD_DIVIDE"));
    }

    @Test
    void screenModeIgnoresGamePressesWhileCapturing() {
        screenMode();
        List<Optional<String>> results = new ArrayList<>();
        detection().detect(results::add);

        platform.input.press(admin.id(), KeySignal.USE);

        assertThat(results).isEmpty();
    }

    @Test
    void gameModeDetectsAbility4AndDodgeByName() {
        List<Optional<String>> results = new ArrayList<>();
        KeyDetection detection = detection();
        detection.detect(results::add);
        platform.input.press(admin.id(), KeySignal.ABILITY_4);
        detection.detect(results::add);
        platform.input.press(admin.id(), KeySignal.DODGE);

        assertThat(results).containsExactly(Optional.of("ABILITY_4"), Optional.of("DODGE"));
    }

    @Test
    void aDetectionStillAllowedWhileRightsAreUnchanged() {
        assertThat(detection().rightsUnchanged()).isTrue();
    }

    private void screenMode() {
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
    }

    private KeyDetection detection() {
        platform.commands.run(COMMAND, admin, Map.of());
        return platform.controlPanel.lastContent.keyDetection();
    }
}
