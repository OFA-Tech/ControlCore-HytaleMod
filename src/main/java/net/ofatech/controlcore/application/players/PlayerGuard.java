package net.ofatech.controlcore.application.players;

import java.util.Optional;
import net.ofatech.controlcore.application.bans.BansService;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.ports.game.Permissions;
import net.ofatech.controlcore.domain.result.Error;

public final class PlayerGuard {

    public static final String NODE = "players";
    public static final String KICK = "players.kick";
    public static final String GAME_MODE = "players.gamemode";
    public static final String TELEPORT = "players.teleport";
    public static final String WHITELIST = "players.whitelist";
    public static final String MUTE = "players.mute";
    public static final String FREEZE = "players.freeze";
    public static final String INVENTORY = "players.inventory";

    private final SettingPermissionPolicy policy;
    private final Permissions permissions;
    private final BansService bans;

    public PlayerGuard(SettingPermissionPolicy policy, Permissions permissions, BansService bans) {
        this.policy = policy;
        this.permissions = permissions;
        this.bans = bans;
    }

    public boolean maySee(Optional<PlayerId> who) {
        return may(who, NODE) || bans.mayManage(who);
    }

    boolean may(Optional<PlayerId> who, String node) {
        return who.isEmpty() || policy.isAdmin(who.get()) || permissions.hasOwn(who.get(), node);
    }

    boolean mayChangeGroups(Optional<PlayerId> who) {
        return who.isEmpty() || policy.isAdmin(who.get());
    }

    Optional<Error> protects(Optional<PlayerId> who, PlayerId target, String targetName) {
        boolean actorIsAdmin = who.isEmpty() || policy.isAdmin(who.get());
        return !actorIsAdmin && policy.isAdmin(target)
                ? Optional.of(PlayerErrors.protectedAdmin(targetName))
                : Optional.empty();
    }

    static Optional<Error> self(Optional<PlayerId> who, PlayerId target) {
        return who.filter(target::equals).map(same -> PlayerErrors.self());
    }

    PlayersView.Rights rights(Optional<PlayerId> who) {
        return new PlayersView.Rights(
                may(who, KICK),
                may(who, GAME_MODE),
                may(who, TELEPORT),
                bans.mayManage(who),
                may(who, WHITELIST),
                mayChangeGroups(who),
                may(who, MUTE),
                may(who, FREEZE),
                may(who, INVENTORY));
    }
}
