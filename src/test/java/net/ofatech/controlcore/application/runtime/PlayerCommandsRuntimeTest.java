package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.Test;

class PlayerCommandsRuntimeTest extends RuntimeTestBase {

    private PlayerInfo admin;
    private PlayerInfo moderator;
    private PlayerInfo builder;

    private void start() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
        moderator = platform.players.join("Moderator");
        builder = platform.players.join("Builder");
        platform.deniedNodesFor.put(builder.id(), List.of("settings", "admin"));
    }

    private String text(Result<Reply> result) {
        assertThat(result.isSuccess()).as("%s", result).isTrue();
        return result.value().text().orElseThrow();
    }

    private String code(Result<Reply> result) {
        assertThat(result.isFailure()).as("%s", result).isTrue();
        return result.error().code();
    }

    @Test
    void theConsoleCanManagePlayersButNotTeleport() {
        start();
        platform.chat.say(builder.id(), "Builder", "hello there");

        assertThat(text(platform.commands.runAsConsole("cc-mute", Map.of("player", "Builder", "length", "1h"))))
                .contains("Builder");
        assertThat(platform.chat.say(builder.id(), "Builder", "still here?")).isFalse();
        text(platform.commands.runAsConsole("cc-unmute", Map.of("player", "Builder")));
        text(platform.commands.runAsConsole("cc-freeze", Map.of("player", "Builder")));
        text(platform.commands.runAsConsole("cc-op", Map.of("player", "Builder")));
        assertThat(platform.access.groupsOf(builder.id())).contains(platform.access.operatorGroup());
        text(platform.commands.runAsConsole("cc-gamemode", Map.of("player", "Builder")));
        assertThat(platform.admin.modes).containsEntry(builder.id(), "creative");
        assertThat(text(platform.commands.runAsConsole("cc-player", Map.of("player", "Builder"))))
                .startsWith("Builder")
                .contains("hello there")
                .doesNotContain("Inventory");
        assertThat(text(platform.commands.runAsConsole("cc-chat", Map.of()))).contains("Builder: hello there");
        text(platform.commands.runAsConsole("cc-whitelist", Map.of("action", "add", "player", "Builder")));
        assertThat(text(platform.commands.runAsConsole("cc-whitelist", Map.of("action", "list"))))
                .contains("Builder");
        assertThat(text(platform.commands.runAsConsole("cc-kick", Map.of("player", "Builder", "reason", "-"))))
                .contains("Builder");
        assertThat(platform.admin.kicked).hasSize(1);

        assertThat(code(platform.commands.runAsConsole("cc-tp", Map.of("player", "Admin"))))
                .isEqualTo("Commands.PlayersOnly");
        assertThat(code(platform.commands.runAsConsole("cc-bring", Map.of("player", "Admin"))))
                .isEqualTo("Commands.PlayersOnly");
        assertThat(code(platform.commands.runAsConsole("cc-whitelist", Map.of("action", "maybe"))))
                .isEqualTo("Players.BadChoice");
        assertThat(code(platform.commands.runAsConsole("cc-mute", Map.of("player", "Nobody", "length", "1h"))))
                .isEqualTo("Players.Unknown");
    }

    @Test
    void aModeratorWithoutTheNodesIsRefusedEverywhere() {
        start();
        platform.deniedNodesFor.put(
                moderator.id(),
                List.of(
                        "settings",
                        "admin",
                        "players.kick",
                        "players.gamemode",
                        "players.teleport",
                        "players.whitelist",
                        "players.mute",
                        "players.freeze",
                        "players.inventory"));

        assertThat(code(platform.commands.run("cc-kick", moderator, Map.of("player", "Builder", "reason", "-"))))
                .isEqualTo("Players.Forbidden");
        assertThat(code(platform.commands.run("cc-mute", moderator, Map.of("player", "Builder", "length", "1h"))))
                .isEqualTo("Players.Forbidden");
        assertThat(code(platform.commands.run("cc-freeze", moderator, Map.of("player", "Builder"))))
                .isEqualTo("Players.Forbidden");
        assertThat(code(platform.commands.run("cc-gamemode", moderator, Map.of("player", "Builder"))))
                .isEqualTo("Players.Forbidden");
        assertThat(code(platform.commands.run("cc-tp", moderator, Map.of("player", "Builder"))))
                .isEqualTo("Players.Forbidden");
        assertThat(code(platform.commands.run("cc-inventory", moderator, Map.of("player", "Builder"))))
                .isEqualTo("Players.Forbidden");
        assertThat(code(platform.commands.run("cc-whitelist", moderator, Map.of("action", "add", "player", "Builder"))))
                .isEqualTo("Players.Forbidden");
        assertThat(code(platform.commands.run("cc-op", moderator, Map.of("player", "Builder"))))
                .isEqualTo("Players.AdminsOnly");
        assertThat(code(platform.commands.run("cc-groups", moderator, Map.of())))
                .isEqualTo("Players.AdminsOnly");
        assertThat(code(platform.commands.run(
                        "cc-group-perm",
                        moderator,
                        Map.of("action", "give", "group", "hytale:Adventurer", "node", "a.b"))))
                .isEqualTo("Players.AdminsOnly");

        assertThat(platform.admin.kicked).isEmpty();
        assertThat(platform.admin.modes).isEmpty();
        assertThat(platform.admin.teleports).isEmpty();
        assertThat(platform.access.allowed).isEmpty();
        assertThat(platform.chat.say(builder.id(), "Builder", "free to talk")).isTrue();
    }

    @Test
    void onlyAnAdminActsOnAnAdminAndNobodyUndoesThemselves() {
        start();
        platform.deniedNodesFor.put(moderator.id(), List.of("settings", "admin"));

        assertThat(code(platform.commands.run("cc-kick", moderator, Map.of("player", "Admin", "reason", "bye"))))
                .isEqualTo("Players.ProtectedAdmin");
        assertThat(code(platform.commands.run("cc-mute", moderator, Map.of("player", "Admin", "length", "1h"))))
                .isEqualTo("Players.ProtectedAdmin");
        assertThat(code(platform.commands.run("cc-freeze", moderator, Map.of("player", "Admin"))))
                .isEqualTo("Players.ProtectedAdmin");
        assertThat(code(platform.commands.run("cc-inventory", moderator, Map.of("player", "Admin"))))
                .isEqualTo("Players.ProtectedAdmin");
        assertThat(text(platform.commands.run("cc-inventory", moderator, Map.of("player", "Builder"))))
                .contains("Inventory");
        assertThat(text(platform.commands.run("cc-mute", moderator, Map.of("player", "Builder", "length", "30m"))))
                .contains("Builder");
        assertThat(code(platform.commands.run("cc-unmute", moderator, Map.of("player", "Moderator"))))
                .isEqualTo("Players.Self");

        assertThat(text(platform.commands.run("cc-mute", admin, Map.of("player", "Moderator", "length", "1h"))))
                .contains("Moderator");
        assertThat(code(platform.commands.run("cc-deop", admin, Map.of("player", "Admin"))))
                .isEqualTo("Players.Self");
        assertThat(text(platform.commands.run("cc-tp", admin, Map.of("player", "Builder"))))
                .contains("Builder");
        assertThat(text(platform.commands.run("cc-bring", admin, Map.of("player", "Builder"))))
                .contains("Builder");
        assertThat(platform.admin.teleports)
                .containsExactly(admin.id() + "->" + builder.id(), builder.id() + "->" + admin.id());
        assertThat(text(platform.commands.run(
                        "cc-group-perm",
                        admin,
                        Map.of("action", "give", "group", "hytale:Adventurer", "node", "homes.use"))))
                .contains("homes.use");
        assertThat(text(platform.commands.run("cc-groups", admin, Map.of()))).contains("homes.use");
        assertThat(text(platform.commands.run(
                        "cc-group", admin, Map.of("action", "add", "player", "Builder", "group", "hytale:Adventurer"))))
                .contains("Builder");
    }
}
