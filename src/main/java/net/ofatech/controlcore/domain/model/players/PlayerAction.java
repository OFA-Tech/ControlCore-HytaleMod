package net.ofatech.controlcore.domain.model.players;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import net.ofatech.controlcore.domain.model.players.PlayersView.Rights;

public enum PlayerAction {
    BAN(Rights::ban, true, true, true, false, false),
    UNBAN(Rights::ban, true, false, false, false, false),
    KICK(Rights::kick, true, false, true, false, false),
    WHITELIST_ADD(Rights::whitelist, true, false, false, false, false),
    WHITELIST_REMOVE(Rights::whitelist, true, false, false, false, false),
    MUTE_ADD(Rights::mute, true, true, false, false, false),
    MUTE_REMOVE(Rights::mute, true, false, false, false, false),
    FREEZE_ADD(Rights::freeze, true, false, false, false, false),
    FREEZE_REMOVE(Rights::freeze, true, false, false, false, false),
    OP_ADD(Rights::groups, true, false, false, false, false),
    OP_REMOVE(Rights::groups, true, false, false, false, false),
    GROUP_ADD(Rights::groups, true, false, false, true, false),
    GROUP_REMOVE(Rights::groups, true, false, false, true, false),
    NODE_ADD(Rights::groups, false, false, false, true, true),
    NODE_REMOVE(Rights::groups, false, false, false, true, true);

    private final Predicate<Rights> allowed;
    private final boolean needsPlayer;
    private final boolean needsLength;
    private final boolean needsReason;
    private final boolean needsGroup;
    private final boolean needsNode;

    PlayerAction(
            Predicate<Rights> allowed,
            boolean needsPlayer,
            boolean needsLength,
            boolean needsReason,
            boolean needsGroup,
            boolean needsNode) {
        this.allowed = allowed;
        this.needsPlayer = needsPlayer;
        this.needsLength = needsLength;
        this.needsReason = needsReason;
        this.needsGroup = needsGroup;
        this.needsNode = needsNode;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<PlayerAction> of(String id) {
        return Arrays.stream(values()).filter(action -> action.id().equals(id)).findFirst();
    }

    public static List<PlayerAction> allowedBy(Rights rights) {
        return Arrays.stream(values())
                .filter(action -> action.allowed.test(rights))
                .toList();
    }

    public boolean needsPlayer() {
        return needsPlayer;
    }

    public boolean needsLength() {
        return needsLength;
    }

    public boolean needsReason() {
        return needsReason;
    }

    public boolean needsGroup() {
        return needsGroup;
    }

    public boolean needsNode() {
        return needsNode;
    }

    public record Input(String player, String length, String reason, String group, String node) {}
}
