package net.ofatech.controlcore.domain.model;

import net.ofatech.controlcore.domain.guard.Guard;

/**
 * An online player.
 *
 * @param id the player's id
 * @param name the player's name
 */
public record PlayerInfo(PlayerId id, String name) {

    /**
     * Creates player info.
     *
     * @param id the player's id
     * @param name the player's name
     * @throws net.ofatech.controlcore.domain.guard.DomainException if {@code id} is {@code null} or {@code name} is
     *     blank
     */
    public PlayerInfo {
        Guard.notNull(id, "id");
        Guard.notBlank(name, "name");
    }
}
