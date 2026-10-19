package net.ofatech.controlcore.infrastructure.platforms.hytale.player;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.Permissions;

public final class HytalePermissions implements Permissions {

    @Override
    public boolean has(PlayerId player, String node) {
        return PermissionsModule.get().hasPermission(player.value(), node);
    }
}
