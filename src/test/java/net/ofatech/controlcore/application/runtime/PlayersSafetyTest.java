package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.players.PlayerAction;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import org.junit.jupiter.api.Test;

class PlayersSafetyTest extends RuntimeTestBase {

    private PlayersView viewFor(PlayerInfo viewer) {
        platform.commands.run(COMMAND, viewer, Map.of());
        return platform.controlPanel.lastContent.players().orElseThrow();
    }

    private void start() {
        runtime.setup();
        runtime.start();
    }

    @Test
    void moderatorsCannotActAgainstAdmins() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo moderator = platform.players.join("Moderator");
        PlayerInfo builder = platform.players.join("Builder");
        platform.deniedNodesFor.put(moderator.id(), List.of("settings", "admin"));
        platform.deniedNodesFor.put(builder.id(), List.of("settings", "admin"));
        PlayersView view = viewFor(moderator);
        String adminId = admin.id().value().toString();

        assertThat(view.kick(adminId, "bye").error().code()).isEqualTo("Players.ProtectedAdmin");
        assertThat(view.mute("Admin", "1h", true).join().error().code()).isEqualTo("Players.ProtectedAdmin");
        assertThat(view.freeze("Admin", true).join().error().code()).isEqualTo("Players.ProtectedAdmin");
        assertThat(view.bring(adminId).join().error().code()).isEqualTo("Players.ProtectedAdmin");
        platform.bans.known.put("admin", admin.id());
        assertThat(view.ban("Admin", "1d", "x").join().error().code()).isEqualTo("Players.ProtectedAdmin");
        assertThat(platform.admin.kicked).isEmpty();

        assertThat(view.kick(
                                platform.players.online().stream()
                                        .filter(player -> player.name().equals("Builder"))
                                        .findFirst()
                                        .orElseThrow()
                                        .id()
                                        .value()
                                        .toString(),
                                "spam")
                        .isSuccess())
                .isTrue();
    }

    @Test
    void theNodeFieldIsNotTheNameField() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayersView view = viewFor(admin);

        var result = view.apply(
                        PlayerAction.NODE_ADD,
                        new PlayerAction.Input("Steve", "permanent", "", "hytale:Adventurer", "homes.use"))
                .join();

        assertThat(result.isSuccess()).isTrue();
        assertThat(platform.access.groupPermissions("hytale:Adventurer"))
                .contains("homes.use")
                .doesNotContain("Steve");
        assertThat(view.apply(PlayerAction.NODE_ADD, new PlayerAction.Input("", "", "", "hytale:Adventurer", "..."))
                        .join()
                        .error()
                        .code())
                .isEqualTo("Players.BadNode");
        assertThat(view.apply(PlayerAction.UNBAN, new PlayerAction.Input("", "", "", "", ""))
                        .join()
                        .error()
                        .code())
                .isEqualTo("Players.NoPlayer");
    }

    @Test
    void chatCancelledByAnotherModIsNotKeptAndLongTextIsCut() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);

        platform.chat.say(builder.id(), "Builder", "secret", true);
        platform.chat.say(builder.id(), "Builder", "x".repeat(5000));

        var lines = view.chat().lines().stream().map(PlayersView.Line::text).toList();
        assertThat(lines).noneMatch(text -> text.contains("secret"));
        assertThat(lines).allMatch(text -> text.length() < 400);
    }

    @Test
    void historyKeepsWhoDidItAndAFailedGameModeRollbackIsNotRecorded() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);

        assertThat(net.ofatech.controlcore.application.history.ChangeContext.fromPanel(
                                admin.id(),
                                "Admin",
                                () -> view.whitelist("Builder", true).join())
                        .isSuccess())
                .isTrue();
        ChangeRecord whitelist = platform.changeLog.records.getLast();
        assertThat(whitelist.actor().name()).isEqualTo("Admin");

        assertThat(view.toggleGameMode(builder.id().value().toString()).join().isSuccess())
                .isTrue();
        ChangeRecord mode = platform.changeLog.records.getLast();
        int before = platform.changeLog.records.size();
        platform.admin.failGameMode = true;
        platform.commands.run("cc-rollback", admin, Map.of("change", (int) mode.id()));

        assertThat(platform.changeLog.records).hasSize(before);
        assertThat(platform.admin.modes).containsEntry(builder.id(), "creative");
    }

    @Test
    void reMutingSaysWhatChangedAndTheSameMuteIsRefused() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        platform.players.join("Builder");
        PlayersView view = viewFor(admin);

        assertThat(view.mute("Builder", "permanent", true).join().isSuccess()).isTrue();
        assertThat(view.mute("Builder", "permanent", true).join().error().code())
                .isEqualTo("Players.NothingToDo");
        assertThat(view.mute("Builder", "1h", true).join().value()).contains("Builder");
    }
}
