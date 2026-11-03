package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.domain.model.keys.Key;
import net.ofatech.controlcore.domain.model.keys.KeyBinds;
import net.ofatech.controlcore.domain.model.keys.KeyCombo;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KeyCaptureLogicTest {

    private static final int CTRL = 1073742048;
    private static final int RIGHT_ALT = 1073742054;
    private static final int NUMPAD_5 = 1073741917;
    private static final int ESC = 27;

    @Test
    void sdlCodesMapToKeys() {
        assertThat(SdlKeys.key(97)).contains(Key.A);
        assertThat(SdlKeys.key(49)).contains(Key.NUM_1);
        assertThat(SdlKeys.key(1073741882)).contains(Key.F1);
        assertThat(SdlKeys.key(1073741893)).contains(Key.F12);
        assertThat(SdlKeys.key(NUMPAD_5)).contains(Key.NUMPAD_5);
        assertThat(SdlKeys.key(1073741922)).contains(Key.NUMPAD_0);
        assertThat(SdlKeys.key(1073741912)).contains(Key.NUMPAD_ENTER);
        assertThat(SdlKeys.key(1073741906)).contains(Key.UP);
        assertThat(SdlKeys.key(127)).contains(Key.DELETE);
        assertThat(SdlKeys.key(1073741925)).contains(Key.MENU);
        assertThat(SdlKeys.key(42424242)).isEmpty();
        assertThat(SdlKeys.isModifier(CTRL)).isTrue();
        assertThat(SdlKeys.hold(RIGHT_ALT)).contains(net.ofatech.controlcore.domain.model.keys.Key.ALT);
        assertThat(SdlKeys.hold(1073742051)).isEmpty();
    }

    @Test
    void ctrlThenNumpadFive() {
        KeyCaptureLogic logic = new KeyCaptureLogic(true);

        assertThat(logic.accept(CTRL, false, 0).captured()).isEmpty();
        assertThat(logic.accept(CTRL, true, 100).captured()).isEmpty();

        assertThat(logic.accept(NUMPAD_5, false, 300).captured()).contains("CTRL+NUMPAD_5");
    }

    @Test
    void aSlowRepeatDelayStillCountsAsHeld() {
        KeyCaptureLogic logic = new KeyCaptureLogic(true);
        logic.accept(CTRL, false, 0);

        assertThat(logic.accept(NUMPAD_5, false, 1000).captured()).contains("CTRL+NUMPAD_5");
    }

    @Test
    void staleModifierIsNotHeld() {
        KeyCaptureLogic logic = new KeyCaptureLogic(true);
        logic.accept(CTRL, false, 0);

        assertThat(logic.accept(NUMPAD_5, false, 3000).captured()).contains("NUMPAD_5");
    }

    @Test
    void digitsAreWrittenAsTheyLook() {
        assertThat(new KeyCaptureLogic(true).accept(51, false, 0).captured()).contains("3");
    }

    @Test
    void escapeCancels() {
        assertThat(new KeyCaptureLogic(true).accept(ESC, false, 0))
                .isInstanceOf(KeyCaptureLogic.Outcome.Cancelled.class);
    }

    @Test
    void modifiersAloneShowWhatIsHeldAndUnknownKeysSaySo() {
        KeyCaptureLogic logic = new KeyCaptureLogic(true);

        assertThat(logic.accept(CTRL, false, 0)).isEqualTo(new KeyCaptureLogic.Outcome.Holding("CTRL"));
        assertThat(logic.accept(RIGHT_ALT, false, 10)).isEqualTo(new KeyCaptureLogic.Outcome.Holding("CTRL+ALT"));
        assertThat(logic.accept(231, false, 20)).isEqualTo(new KeyCaptureLogic.Outcome.Unsupported(231));
        assertThat(logic.accept(NUMPAD_5, false, 30).captured()).contains("CTRL+ALT+NUMPAD_5");
    }

    @Test
    void repeatsAndUnknownKeysAreIgnored() {
        KeyCaptureLogic logic = new KeyCaptureLogic(true);

        assertThat(logic.accept(NUMPAD_5, true, 0).captured()).isEmpty();
        assertThat(logic.accept(42424242, false, 0).captured()).isEmpty();
        assertThat(logic.accept(1073742051, false, 0).captured()).isEmpty();
    }

    @Nested
    class SdlCodesAndRepeats {

        private static final int MASK = 1 << 30;
        private static final int L_CTRL = MASK | 224;
        private static final int L_SHIFT = MASK | 225;
        private static final int L_ALT = MASK | 226;
        private static final int L_GUI = MASK | 227;
        private static final int R_CTRL = MASK | 228;
        private static final int R_SHIFT = MASK | 229;
        private static final int R_GUI = MASK | 231;

        @Test
        void everyNumpadKeyMapsFromItsSdlCode() {
            int[] digits = {98, 89, 90, 91, 92, 93, 94, 95, 96, 97};
            for (int n = 0; n <= 9; n++) {
                assertThat(SdlKeys.key(MASK | digits[n])).as("KP_" + n).contains(Key.valueOf("NUMPAD_" + n));
            }
            assertThat(SdlKeys.key(MASK | 84)).contains(Key.NUMPAD_DIVIDE);
            assertThat(SdlKeys.key(MASK | 85)).contains(Key.NUMPAD_MULTIPLY);
            assertThat(SdlKeys.key(MASK | 86)).contains(Key.NUMPAD_MINUS);
            assertThat(SdlKeys.key(MASK | 87)).contains(Key.NUMPAD_PLUS);
            assertThat(SdlKeys.key(MASK | 88)).contains(Key.NUMPAD_ENTER);
            assertThat(SdlKeys.key(MASK | 99)).contains(Key.NUMPAD_PERIOD);
            assertThat(SdlKeys.key(MASK | 83)).contains(Key.NUM_LOCK);
        }

        @Test
        void everyFunctionKeyMapsFromItsSdlCode() {
            for (int f = 1; f <= 12; f++) {
                assertThat(SdlKeys.key(MASK | (57 + f))).as("F" + f).contains(Key.valueOf("F" + f));
            }
        }

        @Test
        void navigationAndLockKeysMapFromTheirSdlCodes() {
            assertThat(SdlKeys.key(MASK | 57)).contains(Key.CAPS);
            assertThat(SdlKeys.key(MASK | 70)).contains(Key.PRINT_SCREEN);
            assertThat(SdlKeys.key(MASK | 71)).contains(Key.SCROLL_LOCK);
            assertThat(SdlKeys.key(MASK | 72)).contains(Key.PAUSE);
            assertThat(SdlKeys.key(MASK | 73)).contains(Key.INSERT);
            assertThat(SdlKeys.key(MASK | 74)).contains(Key.HOME);
            assertThat(SdlKeys.key(MASK | 75)).contains(Key.PAGE_UP);
            assertThat(SdlKeys.key(MASK | 77)).contains(Key.END);
            assertThat(SdlKeys.key(MASK | 78)).contains(Key.PAGE_DOWN);
            assertThat(SdlKeys.key(MASK | 79)).contains(Key.RIGHT);
            assertThat(SdlKeys.key(MASK | 80)).contains(Key.LEFT);
            assertThat(SdlKeys.key(MASK | 81)).contains(Key.DOWN);
            assertThat(SdlKeys.key(MASK | 82)).contains(Key.UP);
            assertThat(SdlKeys.key(MASK | 101)).contains(Key.MENU);
        }

        @Test
        void everyCapturedKeyIsWrittenSoKeyBindsReadsItBack() {
            Set<Key> seen = EnumSet.noneOf(Key.class);
            for (int code = 0; code < 256; code++) {
                check(code, seen);
            }
            for (int code = MASK; code < MASK + 300; code++) {
                check(code, seen);
            }
            assertThat(seen).contains(Key.NUMPAD_5, Key.NUM_LOCK, Key.F12, Key.A, Key.NUM_3, Key.SLASH);
        }

        @Test
        void ctrlWithEveryNumpadKeyParses() {
            for (int code = MASK | 84; code <= (MASK | 99); code++) {
                KeyCaptureLogic logic = new KeyCaptureLogic(true);
                logic.accept(L_CTRL, false, 0);
                String written = logic.accept(code, false, 10).captured().orElseThrow();
                assertThat(written).startsWith("CTRL+");
                assertThat(KeyBinds.parse(written).isSuccess()).as(written).isTrue();
            }
        }

        @Test
        void guiKeysAreIgnoredAndDoNotBecomeAHold() {
            KeyCaptureLogic logic = new KeyCaptureLogic(true);

            assertThat(logic.accept(L_GUI, false, 0).captured()).isEmpty();
            assertThat(logic.accept(R_GUI, false, 0).captured()).isEmpty();

            assertThat(logic.accept('a', false, 10).captured()).contains("A");
        }

        @Test
        void rightSideModifiersCountLikeLeftOnes() {
            KeyCaptureLogic ctrl = new KeyCaptureLogic(true);
            ctrl.accept(R_CTRL, false, 0);
            assertThat(ctrl.accept('a', false, 10).captured()).contains("CTRL+A");

            KeyCaptureLogic shift = new KeyCaptureLogic(true);
            shift.accept(R_SHIFT, false, 0);
            assertThat(shift.accept(MASK | 58, false, 10).captured()).contains("SHIFT+F1");
        }

        @Test
        void aModifierHeldPastTheWindowStaysHeldThroughItsRepeats() {
            KeyCaptureLogic logic = new KeyCaptureLogic(true);
            logic.accept(L_CTRL, false, 0);
            logic.accept(L_CTRL, true, 1000);
            logic.accept(L_CTRL, true, 2000);

            assertThat(logic.accept(MASK | 93, false, 2500).captured()).contains("CTRL+NUMPAD_5");
        }

        @Test
        void theRepeatOfTheMainKeyIsIgnoredButTheFirstPressIsTaken() {
            KeyCaptureLogic logic = new KeyCaptureLogic(true);

            assertThat(logic.accept('q', true, 0).captured()).isEmpty();
            assertThat(logic.accept('q', false, 0).captured()).contains("Q");
        }

        @Test
        void aModifierTappedAndReleasedJustBeforeStillCountsAsHeld() {
            KeyCaptureLogic logic = new KeyCaptureLogic(true);
            logic.accept(L_SHIFT, false, 0);

            assertThat(logic.accept('a', false, 800).captured()).contains("SHIFT+A");
        }

        @Test
        void ctrlAndAltTogetherAreBothKept() {
            KeyCaptureLogic logic = new KeyCaptureLogic(true);
            logic.accept(L_CTRL, false, 0);
            logic.accept(L_ALT, false, 50);

            assertThat(logic.accept('a', false, 100).captured()).contains("CTRL+ALT+A");
        }

        private static void check(int code, Set<Key> seen) {
            if (code == SdlKeys.ESCAPE || SdlKeys.isModifier(code)) {
                return;
            }
            Optional<String> result =
                    new KeyCaptureLogic(true).accept(code, false, 0).captured();
            if (result.isEmpty()) {
                return;
            }
            String written = result.get();
            var parsed = KeyBinds.parse(written);
            assertThat(parsed.isSuccess()).as(code + " -> " + written).isTrue();
            assertThat(parsed.value().combos()).hasSize(1);
            Key key = (Key) parsed.value().combos().getFirst().inputs().getFirst();
            assertThat(SdlKeys.key(code)).as(code + " -> " + written).contains(key);
            seen.add(key);
        }
    }

    @Nested
    class HoldsAndEscape {

        private static final int BASE = 1073741824;
        private static final int LCTRL = BASE + 224;
        private static final int LSHIFT = BASE + 225;
        private static final int LALT = BASE + 226;
        private static final int RSHIFT = BASE + 229;

        private final KeyCaptureLogic logic = new KeyCaptureLogic(true);

        @Test
        void ctrlAndShiftTogetherAreBothKeptInCtrlAltShiftOrder() {
            logic.accept(LSHIFT, false, 0);
            logic.accept(LCTRL, false, 10);

            assertThat(logic.accept('f', false, 20)).isEqualTo(new KeyCaptureLogic.Outcome.Captured("CTRL+SHIFT+F"));
        }

        @Test
        void allThreeHoldsWithANumpadKey() {
            logic.accept(RSHIFT, false, 0);
            logic.accept(LALT, false, 0);
            logic.accept(LCTRL, false, 0);

            KeyCaptureLogic.Outcome outcome = logic.accept(BASE + 93, false, 100);

            assertThat(outcome.captured()).contains("CTRL+ALT+SHIFT+NUMPAD_5");
            assertThat(KeyCombo.parse(outcome.captured().orElseThrow()).isSuccess())
                    .isTrue();
        }

        @Test
        void holdingReportsEveryHeldModifier() {
            logic.accept(LCTRL, false, 0);

            assertThat(logic.accept(LSHIFT, false, 5)).isEqualTo(new KeyCaptureLogic.Outcome.Holding("CTRL+SHIFT"));
            assertThat(logic.accept(LSHIFT, false, 5).captured()).isEmpty();
        }

        @Test
        void escapeCancelsEvenAsARepeatAndAfterHolds() {
            logic.accept(LCTRL, false, 0);

            assertThat(logic.accept(SdlKeys.ESCAPE, true, 1)).isInstanceOf(KeyCaptureLogic.Outcome.Cancelled.class);
            assertThat(logic.accept(SdlKeys.ESCAPE, false, 1).captured()).isEmpty();
        }

        @Test
        void unsupportedReportsItsCodeButItsRepeatIsQuiet() {
            assertThat(logic.accept(BASE + 300, false, 0))
                    .isEqualTo(new KeyCaptureLogic.Outcome.Unsupported(BASE + 300));
            assertThat(logic.accept(BASE + 300, true, 0)).isInstanceOf(KeyCaptureLogic.Outcome.Waiting.class);
        }

        @Test
        void everyKeyboardKeyExceptModifiersAndEscapeCanBeCaptured() {
            Set<Key> reachable = EnumSet.noneOf(Key.class);
            for (int code = 0; code < 512; code++) {
                SdlKeys.key(code).ifPresent(reachable::add);
                SdlKeys.key(BASE + code).ifPresent(reachable::add);
            }
            Set<Key> expected = EnumSet.allOf(Key.class);
            expected.removeAll(EnumSet.of(
                    Key.CTRL,
                    Key.ALT,
                    Key.SHIFT,
                    Key.ESCAPE,
                    Key.MOUSE_LEFT,
                    Key.MOUSE_RIGHT,
                    Key.MOUSE_MIDDLE,
                    Key.MOUSE_4,
                    Key.MOUSE_5));

            assertThat(reachable).isEqualTo(expected);
        }

        @Test
        void everyCapturedKeyWithCtrlParsesBackToTheSameKey() {
            for (int code = 0; code < 512; code++) {
                for (int full : new int[] {code, BASE + code}) {
                    Optional<Key> key = SdlKeys.key(full);
                    if (key.isEmpty()) {
                        continue;
                    }
                    KeyCaptureLogic fresh = new KeyCaptureLogic(true);
                    fresh.accept(LCTRL, false, 0);
                    String keys = fresh.accept(full, false, 1).captured().orElseThrow();

                    assertThat(KeyCombo.parse(keys).value()).as(keys).isEqualTo(KeyCombo.of(Key.CTRL, key.get()));
                }
            }
        }
    }

    @Nested
    class HoldWindow {

        private static final int BASE = 1073741824;
        private static final int LCTRL = BASE + 224;
        private static final int LALT = BASE + 226;
        private static final int RCTRL = BASE + 228;
        private static final int RALT = BASE + 230;
        private static final int LGUI = BASE + 227;

        private final KeyCaptureLogic logic = new KeyCaptureLogic(true);

        @Test
        void sdlHoldsMapToTheKeyEnum() {
            assertThat(SdlKeys.hold(LCTRL)).contains(Key.CTRL);
            assertThat(SdlKeys.hold(RCTRL)).contains(Key.CTRL);
            assertThat(SdlKeys.hold(LALT)).contains(Key.ALT);
            assertThat(SdlKeys.hold(RALT)).contains(Key.ALT);
            assertThat(SdlKeys.hold(LGUI)).isEmpty();
            java.util.Set<Key> mapped = java.util.EnumSet.noneOf(Key.class);
            for (int code = BASE + 224; code <= BASE + 231; code++) {
                SdlKeys.hold(code).ifPresent(mapped::add);
            }
            assertThat(mapped).containsExactlyInAnyOrderElementsOf(Key.HOLDS);
        }

        @Test
        void altThenCtrlIsWrittenInHoldsOrder() {
            logic.accept(LALT, false, 0);
            logic.accept(LCTRL, false, 0);

            assertThat(logic.accept(BASE + 84, false, 10))
                    .isEqualTo(new KeyCaptureLogic.Outcome.Captured("CTRL+ALT+NUMPAD_DIVIDE"));
        }

        @Test
        void holdOlderThanTheWindowIsDropped() {
            logic.accept(LCTRL, false, 0);

            assertThat(logic.accept('f', false, KeyCaptureLogic.HOLD_WINDOW_MILLIS + 1))
                    .isEqualTo(new KeyCaptureLogic.Outcome.Captured("F"));
        }

        @Test
        void holdExactlyAtTheWindowStillCounts() {
            logic.accept(LCTRL, false, 0);

            assertThat(logic.accept('f', false, KeyCaptureLogic.HOLD_WINDOW_MILLIS))
                    .isEqualTo(new KeyCaptureLogic.Outcome.Captured("CTRL+F"));
        }

        @Test
        void modifierRepeatKeepsTheHoldAlive() {
            logic.accept(LCTRL, false, 0);
            logic.accept(LCTRL, true, 1000);
            logic.accept(LCTRL, true, 2000);

            assertThat(logic.accept('f', false, 3000)).isEqualTo(new KeyCaptureLogic.Outcome.Captured("CTRL+F"));
        }

        @Test
        void guiKeyIsIgnoredAndDoesNotCancel() {
            assertThat(logic.accept(LGUI, false, 0)).isInstanceOf(KeyCaptureLogic.Outcome.Waiting.class);
            assertThat(logic.accept('q', false, 1)).isEqualTo(new KeyCaptureLogic.Outcome.Captured("Q"));
        }

        @Test
        void digitsAreCapturedAsDigitsAndNumpadSymbolsByName() {
            assertThat(new KeyCaptureLogic(true).accept('7', false, 0))
                    .isEqualTo(new KeyCaptureLogic.Outcome.Captured("7"));
            assertThat(new KeyCaptureLogic(true).accept(BASE + 87, false, 0))
                    .isEqualTo(new KeyCaptureLogic.Outcome.Captured("NUMPAD_PLUS"));
            assertThat(new KeyCaptureLogic(true).accept(BASE + 99, false, 0))
                    .isEqualTo(new KeyCaptureLogic.Outcome.Captured("NUMPAD_PERIOD"));
        }
    }

    @Nested
    class RefusedKeys {

        private static final int NUMPAD_5 = 1073741917;
        private static final int ESC = 27;
        private static final int F = 'f';
        private static final int L = 'l';

        @Test
        void refusedKeyNamesTheKeyAndTheScreenKeepsWaitingForAnother() {
            KeyCaptureLogic logic = new KeyCaptureLogic(false);

            KeyCaptureLogic.Outcome first = logic.accept(NUMPAD_5, false, 0);
            KeyCaptureLogic.Outcome again = logic.accept(NUMPAD_5, false, 50);
            KeyCaptureLogic.Outcome picked = logic.accept(F, false, 100);

            assertThat(first).isEqualTo(new KeyCaptureLogic.Outcome.NotInLists(Key.NUMPAD_5.label()));
            assertThat(again).isInstanceOf(KeyCaptureLogic.Outcome.NotInLists.class);
            assertThat(picked.captured()).contains("F");
        }

        @Test
        void escapeStillCancelsWhenOnlyListedKeysAreAllowed() {
            KeyCaptureLogic logic = new KeyCaptureLogic(false);

            assertThat(logic.accept(ESC, false, 0)).isInstanceOf(KeyCaptureLogic.Outcome.Cancelled.class);
        }

        @Test
        void anyKeyTakesTheKeyTheOtherModeRefuses() {
            assertThat(new KeyCaptureLogic(true).accept(NUMPAD_5, false, 0).captured())
                    .contains("NUMPAD_5");
            assertThat(new KeyCaptureLogic(true).accept(L, false, 0).captured()).contains("L");
            assertThat(new KeyCaptureLogic(false).accept(L, false, 0).captured())
                    .isEmpty();
        }

        @Test
        void refusedKeysAreExactlyTheKeysTheServerCannotHear() {
            for (Key key : Key.values()) {
                assertThat(key.heardByServer())
                        .as(key.name())
                        .isEqualTo(key.signal(false).isPresent()
                                || key.signal(true).isPresent());
            }
            assertThat(Key.F.heardByServer()).isTrue();
            assertThat(Key.NUMPAD_5.heardByServer()).isFalse();
        }
    }

    @Nested
    class ShortAndFullLists {

        private static final int L = 'l';
        private static final int F = 'f';
        private static final int LCTRL = 1073742048;

        @Test
        void withoutEveryKeyAKeyTheServerCannotHearIsRefusedAndTheScreenKeepsWaiting() {
            KeyCaptureLogic logic = new KeyCaptureLogic(false);

            logic.accept(LCTRL, false, 0);
            KeyCaptureLogic.Outcome refused = logic.accept(L, false, 100);
            KeyCaptureLogic.Outcome picked = logic.accept(F, false, 200);

            assertThat(refused).isInstanceOf(KeyCaptureLogic.Outcome.NotInLists.class);
            assertThat(refused.captured()).isEmpty();
            assertThat(picked.captured()).contains("CTRL+F");
        }

        @Test
        void withEveryKeyAnyKeyIsCaptured() {
            KeyCaptureLogic logic = new KeyCaptureLogic(true);

            logic.accept(LCTRL, false, 0);

            assertThat(logic.accept(L, false, 100).captured()).contains("CTRL+L");
        }
    }
}
