package net.ofatech.controlcore.domain.model;

import java.util.UUID;
import net.ofatech.controlcore.domain.guard.Guard;

/**
 * Stable identity of a player: their UUID.
 *
 * @param value the player's UUID
 */
public record PlayerId(UUID value) {

    /**
     * Creates a player id.
     *
     * @param value the player's UUID
     * @throws net.ofatech.controlcore.domain.guard.DomainException if {@code value} is {@code null}
     */
    public PlayerId {
        Guard.notNull(value, "value");
    }

    /**
     * Returns the id of the player with a UUID.
     *
     * @param value the player's UUID
     * @return the player id
     * @throws net.ofatech.controlcore.domain.guard.DomainException if {@code value} is {@code null}
     */
    public static PlayerId of(UUID value) {
        return new PlayerId(value);
    }

    /**
     * Returns the UUID as text.
     *
     * @return the UUID, for example {@code 123e4567-e89b-12d3-a456-426614174000}
     */
    @Override
    public String toString() {
        return value.toString();
    }
}
