package net.ofatech.controlcore.domain.model.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.ofatech.controlcore.domain.guard.DomainException;
import net.ofatech.controlcore.domain.model.PlayerId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KeyModelTest {

    @Test
    void parsesKeysActionsDigitsAndAliases() {
        assertThat(KeyCombo.parse("ctrl+f").value()).isEqualTo(KeyCombo.of(Key.CTRL, Key.F));
        assertThat(KeyCombo.parse(" Alt + 3 ").value()).isEqualTo(KeyCombo.of(Key.ALT, Key.NUM_3));
        assertThat(KeyCombo.parse("walk forward+caps lock").value())
                .isEqualTo(KeyCombo.of(GameAction.WALK_FORWARD, Key.CAPS));
        assertThat(KeyCombo.parse("control+mmb").value()).isEqualTo(KeyCombo.of(Key.CTRL, Key.MOUSE_MIDDLE));
        assertThat(KeyCombo.of(Key.CTRL, Key.NUM_3)).hasToString("CTRL + 3");
    }

    @Test
    void rejectsUnknownAndRepeatedKeys() {
        assertThat(KeyCombo.parse("ctrl+banana").error().code()).isEqualTo("Keys.InvalidCombo");
        assertThat(KeyCombo.parse("f+F").error().code()).isEqualTo("Keys.InvalidCombo");
        assertThat(KeyCombo.parse("").error().code()).isEqualTo("Keys.InvalidCombo");
        assertThatThrownBy(() -> KeyCombo.of(Key.F, Key.F)).isInstanceOf(DomainException.class);
    }

    @Test
    void detectableCombosBecomeTriggers() {
        assertThat(KeyCombo.of(Key.CTRL, Key.F).trigger().value())
                .isEqualTo(new KeyTrigger(Optional.of(KeySignal.USE), Set.of(KeySignal.CROUCHING)));
        assertThat(KeyCombo.of(Key.ALT).trigger().value())
                .isEqualTo(new KeyTrigger(Optional.of(KeySignal.WALK_TAP), Set.of()));
        assertThat(KeyCombo.of(Key.ALT, Key.NUM_3).trigger().value())
                .isEqualTo(new KeyTrigger(Optional.of(KeySignal.HOTBAR_3), Set.of(KeySignal.WALKING)));
        assertThat(KeyCombo.of(GameAction.CROUCH, GameAction.SPRINT)
                        .trigger()
                        .value()
                        .press())
                .isEmpty();
    }

    @Test
    void undetectableCombosExplainWhy() {
        var missing = KeyCombo.of(Key.CTRL, Key.H).trigger();
        assertThat(missing.error().code()).isEqualTo("Keys.NotDetectable");
        assertThat(missing.error().description()).contains("H");
        assertThat(KeyCombo.of(Key.Q, Key.E).trigger().error().code()).isEqualTo("Keys.NotDetectable");
    }

    @Test
    void numpadKeysParseAndPrint() {
        assertThat(KeyCombo.parse("ctrl+numpad_5").value().toString()).isEqualTo("CTRL + NUMPAD_5");
        assertThat(KeyCombo.parse("NUMPAD 5").value().inputs()).containsExactly(Key.NUMPAD_5);
        assertThat(KeyCombo.parse("numpad_plus").value().inputs()).containsExactly(Key.NUMPAD_PLUS);
        assertThat(Key.NUMPAD_5.label()).isEqualTo("NUMPAD 5");
        assertThat(Key.PRINT_SCREEN.signal(true)).isEmpty();
        assertThat(KeyCombo.parse("numpad_5").value().trigger().error().code()).isEqualTo("Keys.NotDetectable");
    }

    @Test
    void triggersFireOnTheirPressWhileHeldKeysAreDown() {
        KeyTrigger ctrlF = KeyCombo.of(Key.CTRL, Key.F).trigger().value();
        assertThat(ctrlF.firesOn(KeySignal.USE, Set.of(KeySignal.CROUCHING, KeySignal.MOVING)))
                .isTrue();
        assertThat(ctrlF.firesOn(KeySignal.USE, Set.of())).isFalse();
        KeyTrigger crouchSprint =
                KeyCombo.of(GameAction.CROUCH, GameAction.SPRINT).trigger().value();
        assertThat(crouchSprint.firesOnChange(
                        Set.of(KeySignal.SPRINTING), Set.of(KeySignal.SPRINTING, KeySignal.CROUCHING)))
                .isTrue();
        assertThat(crouchSprint.firesOnChange(
                        Set.of(KeySignal.SPRINTING, KeySignal.CROUCHING),
                        Set.of(KeySignal.SPRINTING, KeySignal.CROUCHING, KeySignal.MOVING)))
                .isFalse();
    }

    @Test
    void bindsParseAlternativesAndNone() {
        KeyBinds binds = KeyBinds.parse("alt+3 | mouse_middle").value();
        assertThat(binds.combos()).containsExactly(KeyCombo.of(Key.ALT, Key.NUM_3), KeyCombo.of(Key.MOUSE_MIDDLE));
        assertThat(binds).hasToString("ALT + 3 | MOUSE_MIDDLE");
        assertThat(KeyBinds.parse("None").value()).isEqualTo(KeyBinds.UNBOUND);
        assertThat(KeyBinds.UNBOUND).hasToString("none");
    }

    @Test
    void keyActionsAreBuiltByComposition() {
        KeyAction action = KeyAction.of("Homes", "go-home")
                .displayName("Go home")
                .command("/home {player}")
                .defaultKeys(Key.CTRL, Key.NUM_3)
                .alternativeKeys(Key.MOUSE_MIDDLE);

        assertThat(action.id()).hasToString("homes:go-home");
        assertThat(action.defaultKeys()).hasToString("CTRL + 3 | MOUSE_MIDDLE");
        assertThat(action.blocksGameAction()).isTrue();
        PlayerId player = PlayerId.of(UUID.randomUUID());
        assertThat(action.commandFor(player, "Caio")).contains("home Caio");
        assertThat(action.keepGameAction().blocksGameAction()).isFalse();
    }

    @Nested
    class NewKeys {

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
                assertThat(KeyBinds.parse(key.name()).isSuccess())
                        .as(key.name())
                        .isTrue();
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
            for (Key key : List.of(
                    Key.NUMPAD_DIVIDE, Key.NUMPAD_MULTIPLY, Key.NUMPAD_MINUS, Key.NUMPAD_PLUS, Key.NUMPAD_PERIOD)) {
                assertThat(key.label()).startsWith("NUMPAD ").hasSize(8);
                assertThat(KeyBinds.parse(KeyCombo.write(key)).isSuccess())
                        .as(key.name())
                        .isTrue();
            }
        }
    }

    @Nested
    class WriteRoundTrips {

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
            assertThat(KeyCombo.parse("ctrl + numpad-period").value())
                    .isEqualTo(KeyCombo.of(Key.CTRL, Key.NUMPAD_PERIOD));
            assertThat(KeyCombo.parse("NUM_LOCK").value()).isEqualTo(KeyCombo.of(Key.NUM_LOCK));
        }
    }

    @Nested
    class NumpadAndHolds {

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
}
