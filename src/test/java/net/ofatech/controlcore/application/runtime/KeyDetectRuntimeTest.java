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

class KeyDetectRuntimeTest extends RuntimeTestBase {

    private PlayerInfo admin;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
        platform.connectedPublisher.publish(new PlayerJoined(admin));
    }

    @Test
    void defaultsAreFromTheGameAndShortLists() {
        assertThat(get("controlcore:key-detect-mode")).contains("From the game");
        assertThat(get("controlcore:all-keys-in-lists")).contains("Value: false (default)");

        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));

        assertThat(get("controlcore:key-detect-mode")).contains("Capture screen");
    }

    @Test
    void detectFromTheGameTakesTheNextPressAndShowsAToast() {
        List<Optional<String>> results = new ArrayList<>();
        KeyDetection detection = detection();
        assertThat(detection.mode()).isEqualTo(KeyDetection.Mode.GAME);

        detection.detect(detection.mode(), results::add);
        platform.input.hold(admin.id(), KeySignal.CROUCHING);
        boolean cancelled = platform.input.press(admin.id(), KeySignal.ABILITY_3);

        assertThat(cancelled).isTrue();
        assertThat(results).containsExactly(Optional.of("CTRL+R"));
        assertThat(platform.overlay.shown.get(admin.id()).toasts())
                .extracting(line -> line.text())
                .anyMatch(text -> text.contains("10 s"));
    }

    @Test
    void detectTimesOutWithNothing() {
        List<Optional<String>> results = new ArrayList<>();
        detection().detect(detection().mode(), results::add);

        platform.scheduler.runAll();

        assertThat(results).contains(Optional.empty());
    }

    @Test
    void screenModeUsesTheCaptureScreen() {
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
        List<Optional<String>> results = new ArrayList<>();
        KeyDetection detection = detection();
        assertThat(detection.mode()).isEqualTo(KeyDetection.Mode.SCREEN);

        detection.detect(detection.mode(), results::add);
        platform.input.press(admin.id(), KeySignal.ABILITY_3);
        platform.keyCapture.press(admin.id(), "CTRL+NUMPAD_5");

        assertThat(platform.keyCapture.opened).containsExactly(admin.id());
        assertThat(results).containsExactly(Optional.of("CTRL+NUMPAD_5"));
    }

    private KeyDetection detection() {
        platform.commands.run(COMMAND, admin, Map.of());
        return platform.controlPanel.lastContent.keyDetection();
    }

    private String get(String id) {
        return String.valueOf(platform.commands.run("cc-get", admin, Map.of("id", id)));
    }
}
