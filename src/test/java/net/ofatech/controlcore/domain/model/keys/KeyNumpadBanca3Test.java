package net.ofatech.controlcore.domain.model.keys;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class KeyNumpadBanca3Test {

    @Test
    void holdsAreCtrlAltShiftInWritingOrderAndUnmodifiable() {
        assertThat(Key.HOLDS).containsExactly(Key.CTRL, Key.ALT, Key.SHIFT);
        assertThat(Key.HOLDS.stream().map(KeyCombo::write)).containsExactly("CTRL", "ALT", "SHIFT");
        try {
            Key.HOLDS.add(Key.SPACE);
            assertThat(false).as("HOLDS must be unmodifiable").isTrue();
        } catch (UnsupportedOperationException expected) {
        }
    }

    @Test
    void numpadSymbolLabelsAreShownButNamesAreWritten() {
        Map<Key, String> labels = Map.of(
                Key.NUMPAD_DIVIDE, "NUMPAD /",
                Key.NUMPAD_MULTIPLY, "NUMPAD *",
                Key.NUMPAD_MINUS, "NUMPAD -",
                Key.NUMPAD_PLUS, "NUMPAD +",
                Key.NUMPAD_PERIOD, "NUMPAD .",
                Key.NUMPAD_ENTER, "NUMPAD ENTER");
        labels.forEach((key, label) -> {
            assertThat(key.label()).isEqualTo(label);
            assertThat(KeyCombo.write(key)).isEqualTo(key.name());
            assertThat(KeyCombo.parse(KeyCombo.write(key)).value()).isEqualTo(KeyCombo.of(key));
        });
    }

    @Test
    void oldWrittenNamesStillParse() {
        assertThat(KeyCombo.parse("NUMPAD DIVIDE").value()).isEqualTo(KeyCombo.of(Key.NUMPAD_DIVIDE));
        assertThat(KeyCombo.parse("numpad multiply").value()).isEqualTo(KeyCombo.of(Key.NUMPAD_MULTIPLY));
        assertThat(KeyCombo.parse("NUMPAD-MINUS").value()).isEqualTo(KeyCombo.of(Key.NUMPAD_MINUS));
        assertThat(KeyCombo.parse("shift+numpad plus").value()).isEqualTo(KeyCombo.of(Key.SHIFT, Key.NUMPAD_PLUS));
        assertThat(KeyCombo.parse("NUMPAD PERIOD").value()).isEqualTo(KeyCombo.of(Key.NUMPAD_PERIOD));
    }

    @Test
    void comboWithNumpadPlusRoundTripsThroughToString() {
        KeyCombo combo = KeyCombo.of(Key.CTRL, Key.NUMPAD_PLUS);

        assertThat(combo.toString()).isEqualTo("CTRL + NUMPAD_PLUS");
        assertThat(KeyCombo.parse(combo.toString()).value()).isEqualTo(combo);
    }

    @Test
    void digitsAreStillWrittenAsDigitsAndNumpadDigitsByName() {
        assertThat(KeyCombo.write(Key.NUM_0)).isEqualTo("0");
        assertThat(KeyCombo.write(Key.NUM_9)).isEqualTo("9");
        assertThat(KeyCombo.write(Key.NUMPAD_0)).isEqualTo("NUMPAD_0");
        assertThat(KeyCombo.write(Key.NUMPAD_9)).isEqualTo("NUMPAD_9");
    }

    @Test
    void notDetectableMessageNamesTheSymbolLabel() {
        var trigger = KeyCombo.parse("CTRL+NUMPAD_DIVIDE").value().trigger();

        assertThat(trigger.isFailure()).isTrue();
        assertThat(trigger.error().message().params()).containsValue("NUMPAD /");
    }
}
