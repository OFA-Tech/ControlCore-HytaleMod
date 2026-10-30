package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.ofatech.controlcore.fakes.TestTexts;
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

    @Test
    void shortListByDefault() {
        assertThat(KeyPicker.keys(TestTexts.english(), KeyPicker.read("q"), false))
                .extracting(KeyPicker.Option::value)
                .doesNotContain("NUMPAD_5", "H");
    }

    @Test
    void allKeysAddsTheRestWithAWarning() {
        var options = KeyPicker.keys(TestTexts.english(), KeyPicker.read("q", true), true);

        assertThat(options).extracting(KeyPicker.Option::value).contains("NUMPAD_5", "H", "F1", "MOUSE_5");
        assertThat(options).extracting(KeyPicker.Option::value).doesNotContain("CTRL", "SHIFT", "SPACE", "NUM_1");
        assertThat(options)
                .filteredOn(option -> option.value().equals("NUMPAD_5"))
                .extracting(KeyPicker.Option::label)
                .containsExactly("NUMPAD 5 (server can't hear it)");
        assertThat(options)
                .filteredOn(option -> option.value().equals("Q"))
                .extracting(KeyPicker.Option::label)
                .noneMatch(label -> label.contains("can't hear"));
    }

    @Test
    void allKeysReadsExtraKeysAsPicks() {
        assertThat(KeyPicker.read("ctrl+numpad_5", true))
                .isEqualTo(new Picked("CTRL", "NUMPAD_5", "CTRL + NUMPAD_5", ""));
        assertThat(KeyPicker.read("ctrl+numpad_5", false).custom()).isTrue();
        assertThat(KeyPicker.read("ctrl+alt+3", true).custom()).isTrue();
        assertThat(KeyPicker.compose("ALT", "NUMPAD_5", "none")).isEqualTo("ALT+NUMPAD_5");
    }
}
