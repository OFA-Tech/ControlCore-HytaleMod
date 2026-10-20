package net.ofatech.controlcore.domain.model.keys;

import java.util.Optional;

/** One part of a key combination: a physical {@link Key} or a {@link GameAction}. */
public sealed interface KeyInput permits Key, GameAction {

    /** @return the name used in text, for example {@code CTRL} or {@code WALK_FORWARD} */
    String name();

    /** @return the name shown to players, for example {@code CTRL} or {@code Walk forward} */
    String label();

    /**
     * @param alone whether this input is the whole combination (an Alt tap differs from Alt held with another key)
     * @return how the server notices it during play; empty if the client never reports it
     */
    Optional<KeySignal> signal(boolean alone);
}
