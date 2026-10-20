package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.PlayerId;

public interface Permissions {

    /** @param node a full permission node, for example {@code homes.admin} */
    boolean has(PlayerId player, String node);

    /**
     * @param node a node relative to ControlCore's base permission, for example {@code keys.server}
     * @return whether the player has {@code <base>.<node>}
     */
    default boolean hasOwn(PlayerId player, String node) {
        return has(player, node);
    }
}
