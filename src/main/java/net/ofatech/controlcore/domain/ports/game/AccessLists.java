package net.ofatech.controlcore.domain.ports.game;

import java.util.Set;
import net.ofatech.controlcore.domain.model.PlayerId;

public interface AccessLists {

    String operatorGroup();

    String label(String group);

    Set<PlayerId> whitelisted();

    boolean allow(PlayerId player);

    boolean disallow(PlayerId player);

    Set<String> groups();

    Set<String> groupsOf(PlayerId player);

    void addToGroup(PlayerId player, String group);

    void removeFromGroup(PlayerId player, String group);

    Set<String> groupPermissions(String group);

    void addGroupPermission(String group, String node);

    void removeGroupPermission(String group, String node);
}
