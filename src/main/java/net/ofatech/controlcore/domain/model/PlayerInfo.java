package net.ofatech.controlcore.domain.model;

import net.ofatech.controlcore.domain.guard.Guard;

public record PlayerInfo(PlayerId id, String name) {

    public PlayerInfo {
        Guard.notNull(id, "id");
        Guard.notBlank(name, "name");
    }
}
