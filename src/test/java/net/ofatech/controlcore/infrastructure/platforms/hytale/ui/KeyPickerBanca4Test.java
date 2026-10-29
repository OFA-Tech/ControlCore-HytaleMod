package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KeyPickerBanca4Test {

    @Test
    void altWithAnExtraKeyDoesNotFollowAGameActionButAltAloneDoes() {
        assertThat(KeyPicker.followsGameAction("ALT+NUMPAD_5")).isFalse();
        assertThat(KeyPicker.followsGameAction("SHIFT+NUMPAD_5")).isFalse();
        assertThat(KeyPicker.followsGameAction("ALT")).isTrue();
        assertThat(KeyPicker.followsGameAction("ALT+F")).isTrue();
    }

    @Test
    void ctrlClickFollowsAGameAction() {
        assertThat(KeyPicker.followsGameAction("CTRL+MOUSE_LEFT")).isTrue();
        assertThat(KeyPicker.heard("CTRL+MOUSE_LEFT")).isTrue();
    }

    @Test
    void sharesKeysIsSymmetric() {
        String[][] pairs = {
            {"CTRL+F | Q", "E | F+CTRL"}, {"NUMPAD_5", "NUMPAD_5 | F"}, {"Q", "E"}, {"CROUCH+F", "CTRL+F"}
        };
        for (String[] pair : pairs) {
            assertThat(KeyPicker.sharesKeys(pair[0], pair[1]))
                    .as(pair[0] + " vs " + pair[1])
                    .isEqualTo(KeyPicker.sharesKeys(pair[1], pair[0]));
        }
    }

    @Test
    void sharesKeysFindsAMatchInALaterCombo() {
        assertThat(KeyPicker.sharesKeys("Q | E | CTRL+F", "R | CTRL+F")).isTrue();
    }

    @Test
    void unheardKeysCompareByTheirExactKeys() {
        assertThat(KeyPicker.sharesKeys("CTRL+NUMPAD_5", "NUMPAD_5+CTRL")).isTrue();
        assertThat(KeyPicker.sharesKeys("CTRL+NUMPAD_5", "NUMPAD_5")).isFalse();
        assertThat(KeyPicker.sharesKeys("NUMPAD_5", "NUMPAD_6")).isFalse();
    }

    @Test
    void anUnheardComboDoesNotMatchAHeardOne() {
        assertThat(KeyPicker.sharesKeys("NUMPAD_5", "F")).isFalse();
        assertThat(KeyPicker.sharesKeys("ALT+NUMPAD_5", "ALT")).isFalse();
    }
}
