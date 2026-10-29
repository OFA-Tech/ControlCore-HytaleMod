package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.domain.model.keys.Key;
import net.ofatech.controlcore.domain.model.keys.KeyCombo;
import org.junit.jupiter.api.Test;

class KeyCaptureLogicBanca2Test {

    private static final int BASE = 1073741824;
    private static final int LCTRL = BASE + 224;
    private static final int LSHIFT = BASE + 225;
    private static final int LALT = BASE + 226;
    private static final int RSHIFT = BASE + 229;

    private final KeyCaptureLogic logic = new KeyCaptureLogic();

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
        assertThat(KeyCombo.parse(outcome.captured().orElseThrow()).isSuccess()).isTrue();
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
        assertThat(logic.accept(BASE + 300, false, 0)).isEqualTo(new KeyCaptureLogic.Outcome.Unsupported(BASE + 300));
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
                KeyCaptureLogic fresh = new KeyCaptureLogic();
                fresh.accept(LCTRL, false, 0);
                String keys = fresh.accept(full, false, 1).captured().orElseThrow();

                assertThat(KeyCombo.parse(keys).value()).as(keys).isEqualTo(KeyCombo.of(Key.CTRL, key.get()));
            }
        }
    }
}
