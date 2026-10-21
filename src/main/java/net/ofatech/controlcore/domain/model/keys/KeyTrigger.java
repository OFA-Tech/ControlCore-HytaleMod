package net.ofatech.controlcore.domain.model.keys;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.domain.guard.Guard;

/**
 * How the server notices a {@link KeyCombo}: one press while some states are held, or, without a press, the
 * moment all states are on together. Usually obtained from {@link KeyCombo#trigger()}.
 *
 * @param press the press that fires the combination; empty if it fires when all {@code held} states become on
 * @param held states that must be on, for example {@link KeySignal#CROUCHING}; may be empty if there is a press.
 *     Stored as an immutable copy
 */
public record KeyTrigger(Optional<KeySignal> press, Set<KeySignal> held) {

    /**
     * Creates a trigger.
     *
     * @param press the press that fires the combination; empty if it fires when all {@code held} states become on
     * @param held states that must be on; copied
     * @throws net.ofatech.controlcore.domain.guard.DomainException if {@code press} or {@code held} is null
     * @throws IllegalArgumentException if {@code press} is empty and {@code held} is empty
     */
    public KeyTrigger {
        Guard.notNull(press, "press");
        Guard.notNull(held, "held");
        held = held.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(held));
        if (press.isEmpty() && held.isEmpty()) {
            throw new IllegalArgumentException("A trigger needs a press or a held state");
        }
    }

    /**
     * Tells whether a press fires this trigger.
     *
     * @param signal the signal just pressed
     * @param current the states on at that moment
     * @return {@code true} if this trigger has a press equal to {@code signal} and all {@link #held} states are in
     *     {@code current}
     */
    public boolean firesOn(KeySignal signal, Set<KeySignal> current) {
        return press.isPresent() && press.get() == signal && current.containsAll(held);
    }

    /**
     * Tells whether a change of held states fires this press-less trigger.
     *
     * @param before the states on before the change
     * @param after the states on after the change
     * @return {@code true} if this trigger has no press and all {@link #held} states are on in {@code after} but
     *     were not all on in {@code before}
     */
    public boolean firesOnChange(Set<KeySignal> before, Set<KeySignal> after) {
        return press.isEmpty() && after.containsAll(held) && !before.containsAll(held);
    }
}
