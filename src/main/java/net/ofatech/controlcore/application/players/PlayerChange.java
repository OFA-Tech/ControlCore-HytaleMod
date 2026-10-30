package net.ofatech.controlcore.application.players;

import java.util.Arrays;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;

enum PlayerChange {
    GAME_MODE("gamemode", "controlcore.history.what.player_gamemode"),
    KICK("kick", "controlcore.history.what.player_kick"),
    WHITELIST("whitelist", "controlcore.history.what.player_whitelist"),
    GROUP("group", "controlcore.history.what.player_group"),
    GROUP_NODE("groupperm", "controlcore.history.what.group_node"),
    MUTE("mute", "controlcore.history.what.player_mute"),
    FREEZE("freeze", "controlcore.history.what.player_freeze");

    static final String TARGET = "player:";
    private static final String EXTRA = "\u001f";

    private final String id;
    private final String whatKey;

    PlayerChange(String id, String whatKey) {
        this.id = id;
        this.whatKey = whatKey;
    }

    String whatKey() {
        return whatKey;
    }

    private String prefix() {
        return TARGET + id + ":";
    }

    String target(PlayerId player, String name) {
        return prefix() + player.value() + ":" + name;
    }

    String target(PlayerId player, String name, String extra) {
        return target(player, name) + EXTRA + extra;
    }

    String groupNode(String group, String node) {
        return prefix() + group + EXTRA + node;
    }

    static Optional<PlayerChange> of(String target) {
        return Arrays.stream(values())
                .filter(change -> target.startsWith(change.prefix()))
                .findFirst();
    }

    Parts parts(String target) {
        String rest = target.substring(prefix().length());
        int split = rest.indexOf(EXTRA);
        String main = split < 0 ? rest : rest.substring(0, split);
        String extra = split < 0 ? "" : rest.substring(split + 1);
        if (this == GROUP_NODE) {
            return new Parts(Optional.empty(), "", main, extra);
        }
        int colon = main.indexOf(':');
        String id = colon < 0 ? main : main.substring(0, colon);
        String name = colon < 0 ? "" : main.substring(colon + 1);
        return new Parts(PlayerDirectory.parse(id), name.isEmpty() ? id : name, extra, "");
    }

    record Parts(Optional<PlayerId> player, String name, String extra, String node) {}
}
