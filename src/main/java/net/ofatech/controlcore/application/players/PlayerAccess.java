package net.ofatech.controlcore.application.players;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.ofatech.controlcore.application.history.ChangeHistory;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Kind;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.AccessLists;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class PlayerAccess {

    private static final Pattern NODE = Pattern.compile("-?(?=[A-Za-z0-9_.*:-]*[A-Za-z0-9*])[A-Za-z0-9_.*:-]{1,120}");

    private final PlayerDirectory directory;
    private final PlayerGuard guard;
    private final AccessLists access;
    private final ChangeHistory history;
    private final PlayersChanges changes;

    public PlayerAccess(
            PlayerDirectory directory,
            PlayerGuard guard,
            AccessLists access,
            ChangeHistory history,
            PlayersChanges changes) {
        this.directory = directory;
        this.guard = guard;
        this.access = access;
        this.history = history;
        this.changes = changes;
    }

    public CompletableFuture<Result<String>> setWhitelisted(
            Optional<PlayerId> who, String player, boolean add, Translator tr) {
        if (!guard.may(who, PlayerGuard.WHITELIST)) {
            return PlayerDirectory.completed(Result.failure(PlayerErrors.forbidden()));
        }
        return directory.resolve(player, id -> whitelist(who, id, add, tr));
    }

    Result<String> whitelist(Optional<PlayerId> who, PlayerId id, boolean add, Translator tr) {
        if (!guard.may(who, PlayerGuard.WHITELIST)) {
            return Result.failure(PlayerErrors.forbidden());
        }
        String name = directory.nameOf(id);
        if (!add) {
            Optional<Error> refused = guard.protects(who, id, name);
            if (refused.isPresent()) {
                return Result.failure(refused.get());
            }
        }
        boolean before = access.whitelisted().contains(id);
        if (before == add) {
            return Result.failure(PlayerErrors.nothingToDo(
                    add
                            ? "controlcore.errors.players.already_whitelisted"
                            : "controlcore.errors.players.not_whitelisted",
                    add ? "{player} is already on the whitelist." : "{player} is not on the whitelist.",
                    "player",
                    name));
        }
        if (!(add ? access.allow(id) : access.disallow(id))) {
            return Result.failure(PlayerErrors.failed());
        }
        history.record(
                Kind.PLAYER,
                PlayerChange.WHITELIST.target(id, name),
                String.valueOf(before),
                String.valueOf(add),
                false);
        changes.fire();
        return Result.success(
                tr.t(add ? "controlcore.players.whitelisted" : "controlcore.players.unwhitelisted", "player", name));
    }

    public CompletableFuture<Result<String>> setGroup(
            Optional<PlayerId> who, String player, String group, boolean add, Translator tr) {
        Result<String> wanted = checkGroup(who, group);
        if (wanted.isFailure()) {
            return PlayerDirectory.completed(Result.failure(wanted.error()));
        }
        return directory.resolve(player, id -> group(who, id, wanted.value(), add, tr));
    }

    Result<String> group(Optional<PlayerId> who, PlayerId id, String group, boolean add, Translator tr) {
        Result<String> wanted = checkGroup(who, group);
        if (wanted.isFailure()) {
            return Result.failure(wanted.error());
        }
        String name = directory.nameOf(id);
        if (!add && wanted.value().equals(access.operatorGroup())) {
            Optional<Error> refused = PlayerGuard.self(who, id);
            if (refused.isPresent()) {
                return Result.failure(refused.get());
            }
        }
        boolean before = access.groupsOf(id).contains(wanted.value());
        String label = access.label(wanted.value());
        if (before == add) {
            return Result.failure(PlayerErrors.nothingToDo(
                    add ? "controlcore.errors.players.already_in_group" : "controlcore.errors.players.not_in_group",
                    add ? "{player} is already in {group}." : "{player} is not in {group}.",
                    "player",
                    name,
                    "group",
                    label));
        }
        if (add) {
            access.addToGroup(id, wanted.value());
        } else {
            access.removeFromGroup(id, wanted.value());
        }
        history.record(
                Kind.PLAYER,
                PlayerChange.GROUP.target(id, name, wanted.value()),
                String.valueOf(before),
                String.valueOf(add),
                false);
        changes.fire();
        return Result.success(tr.t(
                add ? "controlcore.players.group_added" : "controlcore.players.group_removed",
                "player",
                name,
                "group",
                label));
    }

    public Result<String> setGroupPermission(
            Optional<PlayerId> who, String group, String node, boolean add, Translator tr) {
        Result<String> wanted = checkGroup(who, group);
        if (wanted.isFailure()) {
            return Result.failure(wanted.error());
        }
        String permission = PlayerDirectory.clean(node);
        if (!NODE.matcher(permission).matches()) {
            return Result.failure(PlayerErrors.badNode(permission));
        }
        String label = access.label(wanted.value());
        boolean before = access.groupPermissions(wanted.value()).contains(permission);
        if (before == add) {
            return Result.failure(PlayerErrors.nothingToDo(
                    add ? "controlcore.errors.players.group_has_node" : "controlcore.errors.players.group_lacks_node",
                    add ? "{group} already has {node}." : "{group} doesn't have {node}.",
                    "group",
                    label,
                    "node",
                    permission));
        }
        if (add) {
            access.addGroupPermission(wanted.value(), permission);
        } else {
            access.removeGroupPermission(wanted.value(), permission);
        }
        history.record(
                Kind.PLAYER,
                PlayerChange.GROUP_NODE.groupNode(wanted.value(), permission),
                String.valueOf(before),
                String.valueOf(add),
                false);
        changes.fire();
        return Result.success(tr.t(
                add ? "controlcore.players.node_added" : "controlcore.players.node_removed",
                "group",
                label,
                "node",
                permission));
    }

    List<PlayersView.Choice> groups(Optional<PlayerId> who) {
        if (!guard.mayChangeGroups(who)) {
            return List.of();
        }
        return access.groups().stream()
                .map(group -> new PlayersView.Choice(group, access.label(group)))
                .toList();
    }

    List<String> groupPermissions(Optional<PlayerId> who, String group) {
        if (!guard.mayChangeGroups(who) || group == null || !access.groups().contains(group)) {
            return List.of();
        }
        return List.copyOf(access.groupPermissions(group));
    }

    String operatorGroup() {
        return access.operatorGroup();
    }

    String label(String group) {
        return access.label(group);
    }

    private Result<String> checkGroup(Optional<PlayerId> who, String group) {
        if (!guard.mayChangeGroups(who)) {
            return Result.failure(PlayerErrors.adminsOnly());
        }
        String wanted = PlayerDirectory.clean(group);
        if (wanted.isEmpty()) {
            return Result.failure(PlayerErrors.noGroup());
        }
        return access.groups().contains(wanted)
                ? Result.success(wanted)
                : Result.failure(PlayerErrors.unknownGroup(wanted));
    }
}
