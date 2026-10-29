package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.PlayerInfo;
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

    private String get(String id) {
        return String.valueOf(platform.commands.run("cc-get", admin, Map.of("id", id)));
    }
}
