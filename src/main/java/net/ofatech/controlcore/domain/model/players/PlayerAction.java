package net.ofatech.controlcore.domain.model.players;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.ofatech.controlcore.domain.model.players.PlayersView.Rights;

public enum PlayerAction {
    BAN(Rights::ban, Field.PLAYER, Field.LENGTH, Field.REASON),
    UNBAN(Rights::ban, Field.PLAYER),
    KICK(Rights::kick, Field.PLAYER, Field.REASON),
    WHITELIST_ADD(Rights::whitelist, Field.PLAYER),
    WHITELIST_REMOVE(Rights::whitelist, Field.PLAYER),
    MUTE_ADD(Rights::mute, Field.PLAYER, Field.LENGTH),
    MUTE_REMOVE(Rights::mute, Field.PLAYER),
    FREEZE_ADD(Rights::freeze, Field.PLAYER),
    FREEZE_REMOVE(Rights::freeze, Field.PLAYER),
    OP_ADD(Rights::groups, Field.PLAYER),
    OP_REMOVE(Rights::groups, Field.PLAYER),
    GROUP_ADD(Rights::groups, Field.PLAYER, Field.GROUP),
    GROUP_REMOVE(Rights::groups, Field.PLAYER, Field.GROUP),
    NODE_ADD(Rights::groups, Field.GROUP, Field.NODE),
    NODE_REMOVE(Rights::groups, Field.GROUP, Field.NODE);

    public enum Field {
        PLAYER,
        LENGTH,
        REASON,
        GROUP,
        NODE
    }

    private final Predicate<Rights> allowed;
    private final Set<Field> fields;

    PlayerAction(Predicate<Rights> allowed, Field first, Field... more) {
        this.allowed = allowed;
        this.fields = EnumSet.of(first, more);
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

    public boolean needs(Field field) {
        return fields.contains(field);
    }

    public record Input(String player, String length, String reason, String group, String node) {}
}
