package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.players.PlayerAction;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.Test;

class PlayersConfusedTest extends RuntimeTestBase {

    private PlayersView viewFor(PlayerInfo viewer) {
        platform.commands.run(COMMAND, viewer, Map.of());
        return platform.controlPanel.lastContent.players().orElseThrow();
    }

    private void start() {
        runtime.setup();
        runtime.start();
    }

    private void rollback(PlayerInfo who, ChangeRecord record) {
        platform.commands.run("cc-rollback", who, Map.of("change", (int) record.id()));
    }

    private Result<String> apply(PlayersView view, PlayerAction action, String player) {
        return view.apply(action, new PlayerAction.Input(player, "", "", "", ""))
                .join();
    }

    @Test
    void aModeratorCannotFlipAnAdminsGameMode() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo moderator = platform.players.join("Moderator");
        platform.deniedNodesFor.put(moderator.id(), List.of("settings", "admin"));
        PlayersView view = viewFor(moderator);

        Result<String> flipped =
                view.toggleGameMode(admin.id().value().toString()).join();

        assertThat(flipped.error().code()).isEqualTo("Players.ProtectedAdmin");
        assertThat(platform.admin.modes).doesNotContainKey(admin.id());
    }

    @Test
    void rollingBackAMuteThatWasAlreadyLiftedChangesNothing() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        platform.players.join("Builder");
        PlayersView view = viewFor(admin);
        assertThat(view.mute("Builder", "permanent", true).join().isSuccess()).isTrue();
        ChangeRecord mute = platform.changeLog.records.getLast();
        assertThat(view.mute("Builder", "", false).join().isSuccess()).isTrue();
        int records = platform.changeLog.records.size();

        rollback(admin, mute);

        assertThat(platform.changeLog.records).hasSize(records);
    }

    @Test
    void rollingBackAReMuteWhoseOldMuteRanOutIsRefused() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);
        assertThat(view.mute("Builder", "1h", true).join().isSuccess()).isTrue();
        assertThat(view.mute("Builder", "permanent", true).join().isSuccess()).isTrue();
        ChangeRecord reMute = platform.changeLog.records.getLast();
        platform.clock.now = platform.clock.now.plus(Duration.ofHours(2));
        int records = platform.changeLog.records.size();

        rollback(admin, reMute);

        assertThat(platform.changeLog.records).hasSize(records);
        assertThat(platform.chat.say(builder.id(), "Builder", "still muted")).isFalse();
    }

    @Test
    void oddNamesAndEmptyFieldsGetClearAnswers() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        platform.players.join("José");
        PlayersView view = viewFor(admin);

        Result<String> hugeMute = view.mute("x".repeat(5000), "1h", true).join();
        assertThat(hugeMute.error().code()).isEqualTo("Players.Unknown");
        assertThat(hugeMute.error().toString().length()).isLessThan(400);
        assertThat(apply(view, PlayerAction.KICK, "Ghost").error().code()).isEqualTo("Players.Unknown");
        assertThat(apply(view, PlayerAction.KICK, "Admin").error().code()).isEqualTo("Players.Self");
        assertThat(apply(view, PlayerAction.MUTE_REMOVE, "José").error().code()).isEqualTo("Players.NothingToDo");
        assertThat(apply(view, PlayerAction.FREEZE_REMOVE, "josé").error().code())
                .isEqualTo("Players.NothingToDo");
        assertThat(apply(view, PlayerAction.GROUP_ADD, "José").error().code()).isEqualTo("Players.NoGroup");
        assertThat(apply(view, PlayerAction.BAN, "   ").error().code()).isEqualTo("Players.NoPlayer");
    }

    @Test
    void nobodyTakesThemselvesOffTheWhitelistOrUnmutesThemselves() {
        start();
        PlayerInfo first = platform.players.join("First");
        platform.players.join("Second");
        PlayersView view = viewFor(first);
        assertThat(view.whitelist("First", true).join().isSuccess()).isTrue();

        assertThat(view.whitelist("First", false).join().error().code()).isEqualTo("Players.Self");
        assertThat(viewFor(first).mute("Second", "permanent", true).join().isSuccess())
                .isTrue();
        assertThat(viewFor(platform.players.online().stream()
                                .filter(player -> player.name().equals("Second"))
                                .findFirst()
                                .orElseThrow())
                        .mute("Second", "", false)
                        .join()
                        .error()
                        .code())
                .isEqualTo("Players.Self");
    }

    @Test
    void anOfflinePlayerCanBeFrozenAndAnUnfreezeRolledBack() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);
        assertThat(view.freeze("Builder", true).join().isSuccess()).isTrue();
        assertThat(view.freeze("Builder", false).join().isSuccess()).isTrue();
        ChangeRecord unfreeze = platform.changeLog.records.getLast();
        platform.players.leave(builder);

        rollback(admin, unfreeze);

        assertThat(platform.playerData.get(builder.id(), "players.frozen")).contains("true");
    }
}
