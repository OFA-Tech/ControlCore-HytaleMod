package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.ofatech.controlcore.infrastructure.platforms.hytale.ui.KeyPicker.Picked;
import org.junit.jupiter.api.Test;

class KeyPickerTest {

    @Test
    void readsAHeldKeyAndAMainKey() {
        assertThat(KeyPicker.read("ctrl+f")).isEqualTo(new Picked("CTRL", "F", "CTRL + F", ""));
        assertThat(KeyPicker.read("ALT + 3")).isEqualTo(new Picked("ALT", "3", "ALT + 3", ""));
        assertThat(KeyPicker.read("mouse_middle")).isEqualTo(new Picked("NONE", "MOUSE_MIDDLE", "MOUSE_MIDDLE", ""));
    }

    @Test
    void altAloneIsATap() {
        assertThat(KeyPicker.read("alt").key()).isEqualTo("ALT");
        assertThat(KeyPicker.read("alt").hold()).isEqualTo("NONE");
    }

    @Test
    void unboundAndUndetectableKeys() {
        assertThat(KeyPicker.read("none")).isEqualTo(new Picked("NONE", "NONE", "", ""));
        assertThat(KeyPicker.read("h").custom()).isTrue();
        assertThat(KeyPicker.read("ctrl+alt+3").custom()).isTrue();
        assertThat(KeyPicker.read("not a key").custom()).isTrue();
    }

    @Test
    void keepsAlternativesWhenTheFirstCombinationChanges() {
        assertThat(KeyPicker.read("ctrl+f | mouse_middle").rest()).isEqualTo("MOUSE_MIDDLE");
        assertThat(KeyPicker.compose("ALT", "R", "ctrl+f | mouse_middle")).isEqualTo("ALT+R | MOUSE_MIDDLE");
        assertThat(KeyPicker.compose("NONE", "Q", "none")).isEqualTo("Q");
    }

    @Test
    void notBoundClearsAndCustomKeepsTheText() {
        assertThat(KeyPicker.compose("CTRL", "NONE", "ctrl+f | mouse_middle")).isEqualTo("none");
        assertThat(KeyPicker.compose("NONE", "CUSTOM", "ctrl+alt+3")).isEqualTo("ctrl+alt+3");
    }
}
