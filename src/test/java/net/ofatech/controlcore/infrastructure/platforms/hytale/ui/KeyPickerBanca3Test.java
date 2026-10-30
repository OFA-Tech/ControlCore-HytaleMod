package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.fakes.TestTexts;
import org.junit.jupiter.api.Test;

class KeyPickerBanca3Test {

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
