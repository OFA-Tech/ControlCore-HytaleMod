package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.ofatech.controlcore.domain.model.keys.KeyBinds;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.fakes.TestTexts;
import net.ofatech.controlcore.infrastructure.platforms.hytale.ui.KeyPicker.Option;
import net.ofatech.controlcore.infrastructure.platforms.hytale.ui.KeyPicker.Picked;
import org.junit.jupiter.api.Nested;
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

    @Nested
    class AllKeysAndLongList {

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
            assertThat(KeyPicker.read("alt+numpad_5", true))
                    .isEqualTo(new Picked("ALT", "NUMPAD_5", "ALT + NUMPAD_5", ""));
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

    @Nested
    class GameActionsAndDescribe {

        private final Translator tr = TestTexts.english();

        @Test
        void isGameActionForKeysTheServerHears() {
            assertThat(KeyPicker.followsGameAction("F")).isTrue();
            assertThat(KeyPicker.followsGameAction("CTRL+R")).isTrue();
            assertThat(KeyPicker.followsGameAction("ABILITY_4")).isTrue();
            assertThat(KeyPicker.followsGameAction("1")).isTrue();
            assertThat(KeyPicker.followsGameAction("ALT")).isTrue();
        }

        @Test
        void isGameActionIsFalseForExtraKeysNoneAndJunk() {
            assertThat(KeyPicker.followsGameAction("NUMPAD_5")).isFalse();
            assertThat(KeyPicker.followsGameAction("CTRL+NUMPAD_5")).isFalse();
            assertThat(KeyPicker.followsGameAction("SHIFT+F5")).isFalse();
            assertThat(KeyPicker.followsGameAction("NONE")).isFalse();
            assertThat(KeyPicker.followsGameAction("")).isFalse();
            assertThat(KeyPicker.followsGameAction("not a key")).isFalse();
        }

        @Test
        void isGameActionOnlyLooksAtTheFirstCombo() {
            assertThat(KeyPicker.followsGameAction("NUMPAD_5 | F")).isFalse();
            assertThat(KeyPicker.followsGameAction("F | NUMPAD_5")).isTrue();
        }

        @Test
        void altHeldWithAnExtraKeyIsNotAGameAction() {
            assertThat(KeyPicker.followsGameAction("ALT+NUMPAD_5")).isFalse();
            assertThat(KeyPicker.heard("ALT+NUMPAD_5")).isFalse();
        }

        @Test
        void sharesKeysComparesWhatTheServerHearsInAnyCombo() {
            assertThat(KeyPicker.sharesKeys("CTRL+F", "f + ctrl")).isTrue();
            assertThat(KeyPicker.sharesKeys("CTRL+F | Q", "CTRL+F | E")).isTrue();
            assertThat(KeyPicker.sharesKeys("CROUCH+F", "CTRL+F")).isTrue();
            assertThat(KeyPicker.sharesKeys("F", "INTERACT")).isTrue();
            assertThat(KeyPicker.sharesKeys("CTRL+F", "F")).isFalse();
            assertThat(KeyPicker.sharesKeys("Q | CTRL+F", "CTRL+F")).isTrue();
            assertThat(KeyPicker.sharesKeys("Q", "E")).isFalse();
        }

        @Test
        void sharesKeysIsFalseForNoneAndInvalid() {
            assertThat(KeyPicker.sharesKeys("NONE", "NONE")).isFalse();
            assertThat(KeyPicker.sharesKeys("", "")).isFalse();
            assertThat(KeyPicker.sharesKeys("bad key", "bad key")).isFalse();
            assertThat(KeyPicker.sharesKeys("NUMPAD_5", "numpad 5")).isTrue();
        }

        @Test
        void describeUsesFriendlyLabelsAndNumpadSymbols() {
            assertThat(KeyPicker.describe(tr, "NUMPAD_DIVIDE")).isEqualTo("NUMPAD /");
            assertThat(KeyPicker.describe(tr, "NUMPAD_PLUS")).isEqualTo("NUMPAD +");
            assertThat(KeyPicker.describe(tr, "CTRL+NUMPAD_MULTIPLY"))
                    .endsWith(" + NUMPAD *")
                    .isNotEqualTo("CTRL+NUMPAD_MULTIPLY");
            assertThat(KeyPicker.describe(tr, "F")).isNotBlank().isNotEqualTo("F");
        }

        @Test
        void describeFallsBackToTheTextForCustomCombos() {
            assertThat(KeyPicker.describe(tr, "CTRL+ALT+SHIFT+NUMPAD_5")).isEqualTo("CTRL+ALT+SHIFT+NUMPAD_5");
            assertThat(KeyPicker.describe(tr, "nonsense")).isEqualTo("nonsense");
        }

        @Test
        void heardOnlyForTheFirstComboTheServerCanSee() {
            assertThat(KeyPicker.heard("CTRL+F")).isTrue();
            assertThat(KeyPicker.heard("NUMPAD_5")).isFalse();
            assertThat(KeyPicker.heard("NUMPAD_5 | F")).isFalse();
            assertThat(KeyPicker.heard("F | NUMPAD_5")).isTrue();
            assertThat(KeyPicker.heard("NONE")).isFalse();
            assertThat(KeyPicker.heard("Q+E")).isFalse();
        }
    }

    @Nested
    class SharesKeys {

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
}
