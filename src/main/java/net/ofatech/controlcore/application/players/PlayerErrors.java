package net.ofatech.controlcore.application.players;

import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.result.Error;

final class PlayerErrors {

    private PlayerErrors() {}

    static Error forbidden() {
        return Error.forbidden(
                "Players.Forbidden",
                Text.key("controlcore.errors.players.forbidden", "You are not allowed to do that."));
    }

    static Error adminsOnly() {
        return Error.forbidden(
                "Players.AdminsOnly",
                Text.key(
                        "controlcore.errors.players.admins_only",
                        "Only admins (operators) can change operators and groups."));
    }

    static Error protectedAdmin(String player) {
        return Error.forbidden(
                "Players.ProtectedAdmin",
                Text.key("controlcore.errors.players.protected_admin", "Only admins can do that to {player}.")
                        .with("player", player));
    }

    static Error self() {
        return Error.validation(
                "Players.Self", Text.key("controlcore.errors.players.self", "You can't do that to yourself."));
    }

    static Error failed() {
        return Error.failure(
                "Players.Failed",
                Text.key("controlcore.errors.players.failed", "That didn't work. The server log has the details."));
    }

    static Error noPlayer() {
        return Error.validation(
                "Players.NoPlayer", Text.key("controlcore.errors.players.no_player", "Type a player name first."));
    }

    static Error notOnline(String player) {
        return Error.validation(
                "Players.NotOnline",
                Text.key("controlcore.errors.players.not_online", "{player} is not online.")
                        .with("player", player));
    }

    static Error unknown(String player) {
        return Error.notFound(
                "Players.Unknown",
                Text.key("controlcore.errors.players.unknown", "No Hytale player is called {player}.")
                        .with("player", player));
    }

    static Error unknownGroup(String group) {
        return Error.validation(
                "Players.UnknownGroup",
                Text.key("controlcore.errors.players.unknown_group", "There is no group {group}.")
                        .with("group", group));
    }

    static Error noGroup() {
        return Error.validation(
                "Players.NoGroup", Text.key("controlcore.errors.players.no_group", "Pick a group first."));
    }

    static Error badNode(String node) {
        return Error.validation(
                "Players.BadNode",
                Text.key(
                                "controlcore.errors.players.bad_node",
                                "'{node}' is not a permission node. Use letters, digits and . _ : - *.")
                        .with("node", node));
    }

    static Error nothingToDo(String key, String fallback, Object... arguments) {
        return Error.validation("Players.NothingToDo", Text.key(key, fallback).with(arguments));
    }

    static Error kickUndo() {
        return Error.validation(
                "Players.KickUndo", Text.key("controlcore.errors.players.kick_undo", "A kick can't be undone."));
    }

    static Error playersOnly() {
        return Error.validation(
                "Players.PlayersOnly",
                Text.key("controlcore.errors.players.players_only", "Only players in the game can do that."));
    }
}
