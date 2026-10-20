package net.ofatech.controlcore.domain.model.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.ofatech.controlcore.domain.guard.DomainException;
import net.ofatech.controlcore.domain.model.PlayerId;
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
}
