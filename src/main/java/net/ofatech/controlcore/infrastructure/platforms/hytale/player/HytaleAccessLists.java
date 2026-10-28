package net.ofatech.controlcore.infrastructure.platforms.hytale.player;

import com.hypixel.hytale.server.core.modules.accesscontrol.AccessControlModule;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.permissions.provider.PermissionProvider;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.AccessLists;

public final class HytaleAccessLists implements AccessLists {

    private static final String HYTALE_PREFIX = "hytale:";

    @Override
    public String operatorGroup() {
        return HYTALE_PREFIX + "Admin";
    }

    @Override
    public String label(String group) {
        return group.startsWith(HYTALE_PREFIX) ? group.substring(HYTALE_PREFIX.length()) : group;
    }

    @Override
    public Set<PlayerId> whitelisted() {
        return AccessControlModule.get().getUsersWithJoinGrant().stream()
                .map(PlayerId::of)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean allow(PlayerId player) {
        return AccessControlModule.get().allowJoin(player.value());
    }

    @Override
    public boolean disallow(PlayerId player) {
        return AccessControlModule.get().disallowJoin(player.value());
    }

    @Override
    public Set<String> groups() {
        return new TreeSet<>(PermissionsModule.get().getAllRegisteredGroups());
    }

    @Override
    public Set<String> groupsOf(PlayerId player) {
        return new TreeSet<>(PermissionsModule.get().getGroupsForUser(player.value()));
    }

    @Override
    public void addToGroup(PlayerId player, String group) {
        PermissionsModule.get().addUserToGroup(player.value(), group);
    }

    @Override
    public void removeFromGroup(PlayerId player, String group) {
        PermissionsModule.get().removeUserFromGroup(player.value(), group);
    }

    @Override
    public Set<String> groupPermissions(String group) {
        PermissionProvider provider = PermissionsModule.get().getFirstPermissionProvider();
        Set<String> nodes = provider == null ? null : provider.getGroupPermissions(group);
        return nodes == null ? Set.of() : new TreeSet<>(nodes);
    }

    @Override
    public void addGroupPermission(String group, String node) {
        PermissionsModule.get().addGroupPermission(group, Set.of(node));
    }

    @Override
    public void removeGroupPermission(String group, String node) {
        PermissionsModule.get().removeGroupPermission(group, Set.of(node));
    }
}
