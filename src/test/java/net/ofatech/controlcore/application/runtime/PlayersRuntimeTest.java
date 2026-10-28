package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.model.players.PlayersView.Filter;
import org.junit.jupiter.api.Test;

class PlayersRuntimeTest extends RuntimeTestBase {

    private PlayersView viewFor(PlayerInfo admin) {
        platform.commands.run(COMMAND, admin, Map.of());
        return platform.controlPanel.lastContent.players().orElseThrow();
    }

    @Test
    void theListShowsOnlineAndBannedPlayersAndFilters() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        platform.players.join("Builder");
        platform.bans.add("Griefer");
        platform.commands.run("cc-ban", admin, Map.of("player", "Griefer", "length", "1d", "reason", "grief"));

        var entries = viewFor(admin).list();

        assertThat(entries).extracting(PlayersView.Entry::name).contains("Admin", "Builder");
        PlayersView.Entry griefer =
                entries.stream().filter(entry -> entry.banned()).findFirst().orElseThrow();
        assertThat(griefer.online()).isFalse();
        assertThat(griefer.ban()).contains("grief");
        assertThat(entries.stream().filter(entry -> entry.matches(Filter.ONLINE, "")))
                .hasSize(2);
        assertThat(entries.stream().filter(entry -> entry.matches(Filter.BANNED, "")))
                .hasSize(1);
        assertThat(entries.stream().filter(entry -> entry.matches(Filter.ALL, "build")))
                .extracting(PlayersView.Entry::name)
                .containsExactly("Builder");
        assertThat(entries.getFirst().online()).isTrue();
    }

    @Test
    void gameModeChangesAreRecordedToastedAndCanBeRolledBack() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);

        var result = view.toggleGameMode(builder.id().value().toString()).join();

        assertThat(result.isSuccess()).isTrue();
        assertThat(platform.admin.modes).containsEntry(builder.id(), "creative");
        ChangeRecord record = platform.changeLog.records.getLast();
        assertThat(record.kind()).isEqualTo(ChangeRecord.Kind.PLAYER);
        assertThat(record.oldValue()).isEqualTo("adventure");
        assertThat(record.newValue()).isEqualTo("creative");
        assertThat(platform.overlay.everToasted).anyMatch(text -> text.contains("Creative"));

        platform.commands.run("cc-rollback", admin, Map.of("change", (int) record.id()));

        assertThat(platform.admin.modes).containsEntry(builder.id(), "adventure");
        assertThat(platform.changeLog.records.getLast().newValue()).isEqualTo("adventure");
    }

    @Test
    void kicksNeedAnotherOnlinePlayerAndCannotBeUndone() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);

        assertThat(view.kick(admin.id().value().toString(), "").error().code()).isEqualTo("Players.Self");
        assertThat(view.kick(builder.id().value().toString(), "spam").isSuccess())
                .isTrue();
        assertThat(platform.admin.kicked).containsExactly(builder.id() + ":spam");

        long kick = platform.changeLog.records.getLast().id();
        int before = platform.changeLog.records.size();
        platform.commands.run("cc-rollback", admin, Map.of("change", (int) kick));

        assertThat(platform.changeLog.records).hasSize(before);
    }

    @Test
    void teleportAndBringMoveTheRightPlayer() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);
        String id = builder.id().value().toString();

        assertThat(view.teleportTo(id).join().isSuccess()).isTrue();
        assertThat(view.bring(id).join().isSuccess()).isTrue();
        assertThat(view.teleportTo(admin.id().value().toString()).join().isFailure())
                .isTrue();

        assertThat(platform.admin.teleports)
                .containsExactly(admin.id() + "->" + builder.id(), builder.id() + "->" + admin.id());
    }

    @Test
    void offlinePlayersCannotBeKickedOrChanged() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayerId id = builder.id();
        platform.players.leave(builder);
        PlayersView view = viewFor(admin);

        assertThat(view.kick(id.value().toString(), "").error().code()).isEqualTo("Players.NotOnline");
        assertThat(view.toggleGameMode(id.value().toString()).join().error().code())
                .isEqualTo("Players.NotOnline");
    }
}
