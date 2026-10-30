package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.players.PlayerAction;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin.Spot;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.Test;

class PlayersConfusedRound3Test extends RuntimeTestBase {

    private PlayersView viewFor(PlayerInfo viewer) {
        platform.commands.run(COMMAND, viewer, Map.of());
        return platform.controlPanel.lastContent.players().orElseThrow();
    }

    private void start() {
        runtime.setup();
        runtime.start();
    }

    private Result<String> apply(PlayersView view, PlayerAction action, String player) {
        return view.apply(action, new PlayerAction.Input(player, "", "", "", ""))
                .join();
    }

    @Test
    void kickingABannedPlayerWhoNeverJoinedSaysTheyAreOffline() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerId ghost = platform.bans.add("Ghost");
        PlayersView view = viewFor(admin);
        assertThat(view.ban("Ghost", "permanent", "").join().isSuccess()).isTrue();
        assertThat(view.list())
                .extracting(PlayersView.Entry::id)
                .contains(ghost.value().toString());

        Result<String> kicked = apply(view, PlayerAction.KICK, "Ghost");

        assertThat(kicked.error().code())
                .as("Ghost is in the list as banned, so 'No Hytale player is called Ghost' is wrong")
                .isEqualTo("Players.NotOnline");
    }

    @Test
    void aPlayerFrozenWhileOfflineIsHeldWhenTheyJoin() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        platform.playerData.put(builder.id(), "session.name", "Builder");
        platform.players.leave(builder);
        PlayersView view = viewFor(admin);

        assertThat(view.freeze("Builder", true).join().isSuccess()).isTrue();
        platform.connectedPublisher.publish(new PlayerJoined(platform.players.join("Builder")));
        platform.scheduler.runAll();
        platform.admin.spots.put(builder.id(), new Spot("default", 50, 64, 50));
        platform.scheduler.runAll();

        assertThat(platform.admin.heldBack).isNotEmpty();
    }

    @Test
    void aModeratorCannotFreezeAnOfflineAdminNorRollBackTheirUnfreeze() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo boss = platform.players.join("Boss");
        PlayerInfo moderator = platform.players.join("Moderator");
        platform.playerData.put(boss.id(), "session.name", "Boss");
        PlayersView adminView = viewFor(admin);
        assertThat(adminView.freeze("Boss", true).join().isSuccess()).isTrue();
        assertThat(adminView.freeze("Boss", false).join().isSuccess()).isTrue();
        ChangeRecord unfreeze = platform.changeLog.records.getLast();
        platform.players.leave(boss);
        platform.deniedNodesFor.put(moderator.id(), List.of("settings", "admin"));
        PlayersView view = viewFor(moderator);

        assertThat(view.freeze("Boss", true).join().error().code()).isEqualTo("Players.ProtectedAdmin");
        platform.commands.run("cc-rollback", moderator, Map.of("change", (int) unfreeze.id()));

        assertThat(platform.playerData.get(boss.id(), "players.frozen")).isEmpty();
    }

    @Test
    void anAdminCanStillSwitchTheirOwnGameMode() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayersView view = viewFor(admin);

        assertThat(view.toggleGameMode(admin.id().value().toString()).join().isSuccess())
                .isTrue();
    }

    @Test
    void longNamesAreCutInEveryError() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayersView view = viewFor(admin);
        String huge = "y".repeat(3000);

        for (PlayerAction action : List.of(
                PlayerAction.KICK,
                PlayerAction.UNBAN,
                PlayerAction.MUTE_ADD,
                PlayerAction.FREEZE_ADD,
                PlayerAction.WHITELIST_ADD,
                PlayerAction.BAN)) {
            Result<String> result = view.apply(action, new PlayerAction.Input(huge, "1h", "", "", ""))
                    .join();
            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().toString().length())
                    .as("%s answers with %s", action, result.error().code())
                    .isLessThan(400);
        }
    }

    @Test
    void rollingBackAMuteTwiceIsRefusedTheSecondTime() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);
        assertThat(view.mute("Builder", "permanent", true).join().isSuccess()).isTrue();
        ChangeRecord mute = platform.changeLog.records.getLast();

        platform.commands.run("cc-rollback", admin, Map.of("change", (int) mute.id()));
        int records = platform.changeLog.records.size();
        platform.commands.run("cc-rollback", admin, Map.of("change", (int) mute.id()));

        assertThat(platform.changeLog.records).hasSize(records);
        assertThat(platform.chat.say(builder.id(), "Builder", "free")).isTrue();
    }
}
