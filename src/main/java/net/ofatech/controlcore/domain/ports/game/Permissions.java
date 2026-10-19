package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.PlayerId;

public interface Permissions {

    boolean has(PlayerId player, String node);
}
