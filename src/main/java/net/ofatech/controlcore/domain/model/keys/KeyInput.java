package net.ofatech.controlcore.domain.model.keys;

import java.util.Optional;

/** One part of a key combination: a physical {@link Key} or a {@link GameAction}. */
public sealed interface KeyInput permits Key, GameAction {

    /**
     * Returns the name used in written combinations, the enum constant's name.
     *
     * @return the name, for example {@code CTRL} or {@code WALK_FORWARD}
     */
    String name();

    /**
     * Returns the name shown to players.
     *
     * @return the label, for example {@code CTRL} or {@code Walk forward}
     */
    String label();

    /**
     * Returns how the server notices this input during play.
     *
     * @param alone whether this input is the whole combination (an Alt tap differs from Alt held with another key)
     * @return the signal; empty if the client never reports this input
     */
    Optional<KeySignal> signal(boolean alone);
}
