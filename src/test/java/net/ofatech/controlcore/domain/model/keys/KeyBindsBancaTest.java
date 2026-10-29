package net.ofatech.controlcore.domain.model.keys;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeyBindsBancaTest {

    private static final List<Key> NEW_KEYS = List.of(
            Key.NUMPAD_0,
            Key.NUMPAD_1,
            Key.NUMPAD_2,
            Key.NUMPAD_3,
            Key.NUMPAD_4,
            Key.NUMPAD_5,
            Key.NUMPAD_6,
            Key.NUMPAD_7,
            Key.NUMPAD_8,
            Key.NUMPAD_9,
            Key.NUMPAD_DIVIDE,
            Key.NUMPAD_MULTIPLY,
            Key.NUMPAD_MINUS,
            Key.NUMPAD_PLUS,
            Key.NUMPAD_ENTER,
            Key.NUMPAD_PERIOD,
            Key.NUM_LOCK,
            Key.PRINT_SCREEN,
            Key.SCROLL_LOCK,
            Key.PAUSE,
            Key.MENU);

    @Test
    void everyNewKeyParsesByNameAloneAndWithAHold() {
        for (Key key : NEW_KEYS) {
            assertThat(KeyBinds.parse(key.name()).isSuccess()).as(key.name()).isTrue();
            assertThat(KeyBinds.parse(key.name().toLowerCase()).isSuccess())
                    .as(key.name())
                    .isTrue();
            assertThat(KeyBinds.parse("ctrl+" + key.name()).isSuccess())
                    .as(key.name())
                    .isTrue();
        }
    }

    @Test
    void everyNewKeyRoundTripsThroughToString() {
        for (Key key : NEW_KEYS) {
            KeyBinds parsed = KeyBinds.parse("alt+" + key.name() + " | q").value();
            KeyBinds again = KeyBinds.parse(parsed.toString()).value();
            assertThat(again).as(key.name()).isEqualTo(parsed);
        }
    }

    @Test
    void newKeysAreNotHeardAndTheWarningNamesThem() {
        for (Key key : NEW_KEYS) {
            var trigger = KeyCombo.parse(key.name()).value().trigger();
            assertThat(trigger.isFailure()).as(key.name()).isTrue();
            assertThat(trigger.error().code()).isEqualTo("Keys.NotDetectable");
        }
    }

    @Test
    void numpadWithSpaceAndHyphenSpellings() {
        assertThat(KeyBinds.parse("NUMPAD 5").isSuccess()).isTrue();
        assertThat(KeyBinds.parse("numpad-5").isSuccess()).isTrue();
        assertThat(KeyBinds.parse("print screen").isSuccess()).isTrue();
        assertThat(KeyBinds.parse("num lock").isSuccess()).isTrue();
    }

    @Test
    void numpadSymbolKeysShowTheirSymbolAndAreWrittenByName() {
        for (Key key :
                List.of(Key.NUMPAD_DIVIDE, Key.NUMPAD_MULTIPLY, Key.NUMPAD_MINUS, Key.NUMPAD_PLUS, Key.NUMPAD_PERIOD)) {
            assertThat(key.label()).startsWith("NUMPAD ").hasSize(8);
            assertThat(KeyBinds.parse(KeyCombo.write(key)).isSuccess())
                    .as(key.name())
                    .isTrue();
        }
    }
}
