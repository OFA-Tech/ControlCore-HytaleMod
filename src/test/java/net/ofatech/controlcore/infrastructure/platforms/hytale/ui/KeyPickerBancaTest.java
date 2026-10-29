package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.ofatech.controlcore.domain.model.keys.KeyBinds;
import net.ofatech.controlcore.fakes.TestTexts;
import net.ofatech.controlcore.infrastructure.platforms.hytale.ui.KeyPicker.Option;
import net.ofatech.controlcore.infrastructure.platforms.hytale.ui.KeyPicker.Picked;
import org.junit.jupiter.api.Test;

class KeyPickerBancaTest {

    @Test
    void allKeysReadsTheFirstOfSeveralCombosAndKeepsTheRest() {
        assertThat(KeyPicker.read("ctrl+numpad_5 | f1 | q", true))
                .isEqualTo(new Picked("CTRL", "NUMPAD_5", "CTRL + NUMPAD_5", "F1 | Q"));
        assertThat(KeyPicker.read("q | numpad_5", true)).isEqualTo(new Picked("NONE", "Q", "Q", "NUMPAD_5"));
    }

    @Test
    void allKeysStillRefusesTwoMainKeys() {
        assertThat(KeyPicker.read("numpad_5+numpad_6", true).custom()).isTrue();
        assertThat(KeyPicker.read("f1+q", true).custom()).isTrue();
        assertThat(KeyPicker.read("q+f1", true).custom()).isTrue();
        assertThat(KeyPicker.read("ctrl+shift+numpad_5", true).custom()).isTrue();
    }

    @Test
    void allKeysTakesAltAsAHoldNextToAnExtraKey() {
        assertThat(KeyPicker.read("alt+numpad_5", true)).isEqualTo(new Picked("ALT", "NUMPAD_5", "ALT + NUMPAD_5", ""));
        assertThat(KeyPicker.read("stand_still+f5", true).hold()).isEqualTo("STAND_STILL");
    }

    @Test
    void anExtraKeyAloneIsAPick() {
        assertThat(KeyPicker.read("numpad_enter", true))
                .isEqualTo(new Picked("NONE", "NUMPAD_ENTER", "NUMPAD_ENTER", ""));
        assertThat(KeyPicker.read("num_lock", true)).isEqualTo(new Picked("NONE", "NUM_LOCK", "NUM_LOCK", ""));
    }

    @Test
    void withDetectedReplacesOnlyTheFirstCombo() {
        assertThat(KeyPicker.withDetected("CTRL+NUMPAD_5", "q | numpad_1")).isEqualTo("CTRL+NUMPAD_5 | NUMPAD_1");
        assertThat(KeyPicker.withDetected("R", "none")).isEqualTo("R");
        assertThat(KeyPicker.withDetected("R", "not a key")).isEqualTo("R");
        assertThat(KeyBinds.parse(KeyPicker.withDetected("CTRL+NUMPAD_5", "q | numpad_1"))
                        .isSuccess())
                .isTrue();
    }

    @Test
    void everyOptionInTheLongListIsUniqueAndParses() {
        List<Option> options = KeyPicker.keys(TestTexts.english(), KeyPicker.read("q", true), true);

        assertThat(options).extracting(Option::value).doesNotHaveDuplicates();
        for (Option option : options) {
            if (option.value().equals(KeyPicker.NONE)) {
                continue;
            }
            assertThat(KeyBinds.parse(option.value()).isSuccess())
                    .as(option.value())
                    .isTrue();
            String composed = KeyPicker.compose("CTRL", option.value(), "none");
            assertThat(KeyBinds.parse(composed).isSuccess()).as(composed).isTrue();
        }
    }

    @Test
    void everyExtraKeyRoundTripsThroughComposeAndRead() {
        List<Option> options = KeyPicker.keys(TestTexts.english(), KeyPicker.read("q", true), true);
        for (Option option : options) {
            if (option.value().equals(KeyPicker.NONE) || option.value().equals("ALT")) {
                continue;
            }
            String composed = KeyPicker.compose("SHIFT", option.value(), "none");
            Picked picked = KeyPicker.read(composed, true);
            assertThat(picked.key()).as(composed).isEqualTo(option.value());
            assertThat(picked.hold()).as(composed).isEqualTo("SHIFT");
        }
    }

    @Test
    void theLongListContainsTheKeysTheSpecPromises() {
        List<Option> options = KeyPicker.keys(TestTexts.english(), KeyPicker.read("q", true), true);

        assertThat(options)
                .extracting(Option::value)
                .contains(
                        "NUMPAD_0",
                        "NUMPAD_9",
                        "NUMPAD_DIVIDE",
                        "NUMPAD_MULTIPLY",
                        "NUMPAD_MINUS",
                        "NUMPAD_PLUS",
                        "NUMPAD_PERIOD",
                        "NUMPAD_ENTER",
                        "F1",
                        "F12",
                        "UP",
                        "DOWN",
                        "LEFT",
                        "RIGHT",
                        "INSERT",
                        "DELETE",
                        "HOME",
                        "END",
                        "PAGE_UP",
                        "PAGE_DOWN",
                        "ESCAPE",
                        "TAB",
                        "CAPS",
                        "ENTER",
                        "BACKSPACE",
                        "PRINT_SCREEN",
                        "SCROLL_LOCK",
                        "PAUSE",
                        "NUM_LOCK",
                        "MENU",
                        "MOUSE_4",
                        "MOUSE_5",
                        "GRAVE",
                        "SLASH");
    }
}
