package net.ofatech.controlcore.infrastructure.platforms.hytale.mapping;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;

public final class Ids {

    private Ids() {}

    public static PlayerInfo player(PlayerRef ref) {
        return new PlayerInfo(PlayerId.of(ref.getUuid()), ref.getUsername());
    }
}
