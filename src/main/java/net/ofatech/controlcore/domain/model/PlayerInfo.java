package net.ofatech.controlcore.domain.model;

import net.ofatech.controlcore.domain.guard.Guard;

/** A player as seen by our code: identity plus display name. */
public record PlayerInfo(PlayerId id, String name) {

    public PlayerInfo {
        Guard.notNull(id, "id");
        Guard.notBlank(name, "name");
    }
}
