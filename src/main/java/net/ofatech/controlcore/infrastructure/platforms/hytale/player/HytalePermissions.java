package net.ofatech.controlcore.infrastructure.platforms.hytale.player;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.Permissions;

public final class HytalePermissions implements Permissions {

    private final String basePermission;

    public HytalePermissions(String basePermission) {
        this.basePermission = basePermission;
    }

    @Override
    public boolean has(PlayerId player, String node) {
        return PermissionsModule.get().hasPermission(player.value(), node);
    }

    @Override
    public boolean hasOwn(PlayerId player, String node) {
        return has(player, basePermission + "." + node);
    }
}
