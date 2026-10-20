package net.ofatech.controlcore.domain.model.keys;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.domain.guard.Guard;

/**
 * How the server notices a {@link KeyCombo}: one press while some states are held, or, without a press, the
 * moment all states are on together.
 *
 * @param press the press that fires the combination; empty if it fires when {@code held} becomes true
 * @param held states that must be on, for example {@link KeySignal#CROUCHING}
 */
public record KeyTrigger(Optional<KeySignal> press, Set<KeySignal> held) {

    public KeyTrigger {
        Guard.notNull(press, "press");
        Guard.notNull(held, "held");
        held = held.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(held));
        if (press.isEmpty() && held.isEmpty()) {
            throw new IllegalArgumentException("A trigger needs a press or a held state");
        }
    }

    /** @return whether {@code signal}, pressed while {@code current} states are on, fires this trigger */
    public boolean firesOn(KeySignal signal, Set<KeySignal> current) {
        return press.isPresent() && press.get() == signal && current.containsAll(held);
    }

    /** @return whether the states changing from {@code before} to {@code after} fire this (press-less) trigger */
    public boolean firesOnChange(Set<KeySignal> before, Set<KeySignal> after) {
        return press.isEmpty() && after.containsAll(held) && !before.containsAll(held);
    }
}
