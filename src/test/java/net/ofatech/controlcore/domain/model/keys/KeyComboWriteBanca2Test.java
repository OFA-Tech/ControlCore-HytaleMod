package net.ofatech.controlcore.domain.model.keys;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class KeyComboWriteBanca2Test {

    @Test
    void everyKeyAndGameActionRoundTripsThroughWrite() {
        Stream.<KeyInput>concat(Stream.of(Key.values()), Stream.of(GameAction.values()))
                .forEach(input -> assertThat(
                                KeyCombo.parse(KeyCombo.write(input)).value())
                        .as(input.name())
                        .isEqualTo(KeyCombo.of(input)));
    }

    @Test
    void everyKeyRoundTripsWithAHoldThroughToString() {
        for (Key key : Key.values()) {
            if (key == Key.CTRL) {
                continue;
            }
            KeyCombo combo = KeyCombo.of(Key.CTRL, key);
            assertThat(KeyCombo.parse(combo.toString()).value())
                    .as(combo.toString())
                    .isEqualTo(combo);
        }
    }

    @Test
    void noGameActionSharesANameWithAKeyOrAlias() {
        for (GameAction action : GameAction.values()) {
            for (Key key : Key.values()) {
                assertThat(action.name()).isNotEqualTo(key.name());
            }
        }
    }

    @Test
    void numpadNamesInEveryCase() {
        assertThat(KeyCombo.parse("numpad_divide").value()).isEqualTo(KeyCombo.of(Key.NUMPAD_DIVIDE));
        assertThat(KeyCombo.parse("Numpad Multiply").value()).isEqualTo(KeyCombo.of(Key.NUMPAD_MULTIPLY));
        assertThat(KeyCombo.parse("ctrl + numpad-period").value()).isEqualTo(KeyCombo.of(Key.CTRL, Key.NUMPAD_PERIOD));
        assertThat(KeyCombo.parse("NUM_LOCK").value()).isEqualTo(KeyCombo.of(Key.NUM_LOCK));
    }
}
