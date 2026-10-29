package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.event.PlayerLeft;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.domain.ports.game.KeyDetection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeyDetectBancaTest extends RuntimeTestBase {

    private PlayerInfo admin;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
        platform.connectedPublisher.publish(new PlayerJoined(admin));
    }

    @Test
    void leavingWhileDetectingFromTheGameDropsTheDetection() {
        List<Optional<String>> results = new ArrayList<>();
        detection().detect(results::add);

        platform.connectedPublisher.publish(new PlayerLeft(admin));
        platform.scheduler.runAll();
        boolean cancelled = platform.input.press(admin.id(), KeySignal.ABILITY_3);

        assertThat(results).isEmpty();
        assertThat(cancelled).isFalse();
    }

    @Test
    void leavingWhileOnTheCaptureScreenIgnoresTheLateKey() {
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
        List<Optional<String>> results = new ArrayList<>();
        detection().detect(results::add);

        platform.connectedPublisher.publish(new PlayerLeft(admin));
        platform.keyCapture.press(admin.id(), "NUMPAD_5");

        assertThat(results).isEmpty();
    }

    @Test
    void escapeOnTheCaptureScreenEndsWithNothing() {
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
        List<Optional<String>> results = new ArrayList<>();
        detection().detect(results::add);

        platform.keyCapture.press(admin.id(), null);

        assertThat(results).containsExactly(Optional.empty());
    }

    @Test
    void aNewDetectAfterTheOldOneEndedWorks() {
        List<Optional<String>> first = new ArrayList<>();
        List<Optional<String>> second = new ArrayList<>();
        KeyDetection detection = detection();
        detection.detect(first::add);
        platform.scheduler.runAll();

        detection.detect(second::add);
        platform.input.press(admin.id(), KeySignal.USE);

        assertThat(first).containsExactly(Optional.empty());
        assertThat(second).containsExactly(Optional.of("F"));
    }

    @Test
    void settingsChangeIsSeenByAnAlreadyBuiltPanel() {
        KeyDetection detection = detection();
        assertThat(detection.mode()).isEqualTo(KeyDetection.Mode.GAME);
        assertThat(detection.allKeys()).isFalse();

        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:all-keys-in-lists", "value", "true"));

        assertThat(detection.mode()).isEqualTo(KeyDetection.Mode.SCREEN);
        assertThat(detection.allKeys()).isTrue();
    }

    private KeyDetection detection() {
        platform.commands.run(COMMAND, admin, Map.of());
        return platform.controlPanel.lastContent.keyDetection();
    }
}
