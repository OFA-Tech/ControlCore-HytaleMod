package net.ofatech.controlcore.fakes;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.AccessLists;

public final class FakeAccessLists implements AccessLists {

    public volatile boolean required;
    public final Set<PlayerId> allowed = ConcurrentHashMap.newKeySet();
    public final Map<PlayerId, Set<String>> members = new ConcurrentHashMap<>();
    public final Map<String, Set<String>> nodes = new ConcurrentHashMap<>(
            Map.of("hytale:Adventurer", new TreeSet<>(), "hytale:Admin", new TreeSet<>(Set.of("*"))));

    @Override
    public String operatorGroup() {
        return "hytale:Admin";
    }

    @Override
    public String label(String group) {
        return group.startsWith("hytale:") ? group.substring("hytale:".length()) : group;
    }

    @Override
    public boolean whitelistRequired() {
        return required;
    }

    @Override
    public Set<PlayerId> whitelisted() {
        return Set.copyOf(allowed);
    }

    @Override
    public boolean allow(PlayerId player) {
        return allowed.add(player);
    }

    @Override
    public boolean disallow(PlayerId player) {
        return allowed.remove(player);
    }

    @Override
    public Set<String> groups() {
        return new TreeSet<>(nodes.keySet());
    }

    @Override
    public Set<String> groupsOf(PlayerId player) {
        return new TreeSet<>(members.getOrDefault(player, Set.of()));
    }

    @Override
    public void addToGroup(PlayerId player, String group) {
        members.computeIfAbsent(player, id -> ConcurrentHashMap.newKeySet()).add(group);
        nodes.computeIfAbsent(group, name -> new TreeSet<>());
    }

    @Override
    public void removeFromGroup(PlayerId player, String group) {
        members.getOrDefault(player, ConcurrentHashMap.newKeySet()).remove(group);
    }

    @Override
    public Set<String> groupPermissions(String group) {
        return new TreeSet<>(nodes.getOrDefault(group, Set.of()));
    }

    @Override
    public void addGroupPermission(String group, String node) {
        nodes.computeIfAbsent(group, name -> new TreeSet<>()).add(node);
    }

    @Override
    public void removeGroupPermission(String group, String node) {
        nodes.getOrDefault(group, new TreeSet<>()).remove(node);
    }
}
