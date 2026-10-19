package net.ofatech.controlcore.domain.model;

import java.util.UUID;
import net.ofatech.controlcore.domain.guard.Guard;

/** Stable identity of a player. */
public record PlayerId(UUID value) {

    public PlayerId {
        Guard.notNull(value, "value");
    }

    public static PlayerId of(UUID value) {
        return new PlayerId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
