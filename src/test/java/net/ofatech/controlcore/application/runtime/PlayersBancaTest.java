package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import org.junit.jupiter.api.Test;

class PlayersBancaTest extends RuntimeTestBase {

    private PlayersView viewFor(PlayerInfo viewer) {
        platform.commands.run(COMMAND, viewer, Map.of());
        return platform.controlPanel.lastContent.players().orElseThrow();
    }

    private PlayersView.Entry entry(PlayersView view, String name) {
        return view.list().stream()
                .filter(found -> found.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private void rollback(PlayerInfo who, ChangeRecord record) {
        platform.commands.run("cc-rollback", who, Map.of("change", (int) record.id()));
    }

    private void start() {
        runtime.setup();
        runtime.start();
    }

    @Test
    void eachActionNeedsItsOwnNode() {
        start();
        PlayerInfo helper = platform.players.join("Helper");
        PlayerInfo builder = platform.players.join("Builder");
        platform.deniedNodes.addAll(java.util.List.of(
                "settings",
                "admin",
                "players.kick",
                "players.gamemode",
                "players.teleport",
                "players.whitelist",
                "players.mute",
                "players.freeze",
                "players.inventory"));
        PlayersView view = viewFor(helper);
        String id = builder.id().value().toString();

        PlayersView.Rights rights = view.rights();
        assertThat(rights.kick()).isFalse();
        assertThat(rights.gameMode()).isFalse();
        assertThat(rights.teleport()).isFalse();
        assertThat(rights.whitelist()).isFalse();
        assertThat(rights.mute()).isFalse();
        assertThat(rights.freeze()).isFalse();
        assertThat(rights.inventory()).isFalse();
        assertThat(view.kick(id, "").error().code()).isEqualTo("Players.Forbidden");
        assertThat(view.toggleGameMode(id).join().error().code()).isEqualTo("Players.Forbidden");
        assertThat(view.teleportTo(id).join().error().code()).isEqualTo("Players.Forbidden");
        assertThat(view.bring(id).join().error().code()).isEqualTo("Players.Forbidden");
        assertThat(view.whitelist("Builder", true).join().error().code()).isEqualTo("Players.Forbidden");
        assertThat(view.mute("Builder", "1h", true).join().error().code()).isEqualTo("Players.Forbidden");
        assertThat(view.freeze("Builder", true).join().error().code()).isEqualTo("Players.Forbidden");
        assertThat(view.groupPermission("hytale:Adventurer", "homes.use", true)
                        .error()
                        .code())
                .isEqualTo("Players.AdminsOnly");
        assertThat(view.group("Builder", "hytale:Adventurer", true)
                        .join()
                        .error()
                        .code())
                .isEqualTo("Players.AdminsOnly");

        assertThat(platform.admin.kicked).isEmpty();
        assertThat(platform.admin.teleports).isEmpty();
        assertThat(platform.admin.modes).isEmpty();
        assertThat(platform.access.allowed).isEmpty();
        assertThat(platform.access.groupPermissions("hytale:Adventurer")).isEmpty();
        assertThat(platform.chat.say(builder.id(), "Builder", "still talking")).isTrue();
        assertThat(view.details(id).join().value().lines())
                .extracting(PlayersView.Line::text)
                .noneMatch(text -> text.toLowerCase().contains("inventory"));
    }

    @Test
    void noPlayersOrBansNodeMeansNoPlayersTab() {
        start();
        PlayerInfo guest = platform.players.join("Guest");
        platform.deniedNodes.addAll(java.util.List.of("settings", "admin", "players", "bans"));

        platform.commands.run(COMMAND, guest, Map.of());

        assertThat(platform.controlPanel.lastContent == null
                        || platform.controlPanel.lastContent.players().isEmpty())
                .isTrue();
    }

    @Test
    void nobodyCanMuteFreezeOrDeOpThemselves() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        platform.access.addToGroup(admin.id(), "hytale:Admin");
        PlayersView view = viewFor(admin);

        assertThat(view.mute("Admin", "1h", true).join().error().code()).isEqualTo("Players.Self");
        assertThat(view.freeze("Admin", true).join().error().code()).isEqualTo("Players.Self");
        assertThat(view.operator("Admin", false).join().error().code()).isEqualTo("Players.Self");
        assertThat(view.bring(admin.id().value().toString()).join().error().code())
                .isEqualTo("Players.Self");
        assertThat(platform.access.groupsOf(admin.id())).contains("hytale:Admin");
        assertThat(platform.chat.say(admin.id(), "Admin", "hi")).isTrue();
        assertThat(entry(view, "Admin").frozen()).isFalse();
    }

    @Test
    void offlinePlayersCannotBeFrozenOrTeleportedButCanBeMuted() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        platform.playerData.put(builder.id(), "session.name", "Builder");
        platform.playerData.put(builder.id(), "session.first-join", "2026-10-01T10:00:00Z");
        platform.players.leave(builder);
        PlayersView view = viewFor(admin);
        String id = builder.id().value().toString();

        assertThat(view.freeze("Builder", true).join().error().code()).isEqualTo("Players.NotOnline");
        assertThat(view.teleportTo(id).join().error().code()).isEqualTo("Players.NotOnline");
        assertThat(view.bring(id).join().error().code()).isEqualTo("Players.NotOnline");
        assertThat(view.mute("Builder", "1d", true).join().isSuccess()).isTrue();
        assertThat(platform.overlay.everToasted)
                .noneMatch(text -> text.toLowerCase().contains("mute"));

        platform.players.join("Builder");
        assertThat(platform.chat.say(builder.id(), "Builder", "I am back")).isFalse();
        assertThat(view.chat().lines()).extracting(PlayersView.Line::text).noneMatch(text -> text.contains("back"));
    }

    @Test
    void timedMutesRunOutAndPermanentOnesDoNot() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayerInfo other = platform.players.join("Other");
        PlayersView view = viewFor(admin);

        assertThat(view.mute("Builder", "30m", true).join().isSuccess()).isTrue();
        assertThat(view.mute("Other", "", true).join().isSuccess()).isTrue();
        assertThat(platform.chat.say(builder.id(), "Builder", "one")).isFalse();
        assertThat(platform.messenger.textsTo(builder.id())).isNotEmpty();

        platform.clock.now = platform.clock.now.plus(Duration.ofMinutes(29));
        assertThat(platform.chat.say(builder.id(), "Builder", "two")).isFalse();

        platform.clock.now = platform.clock.now.plus(Duration.ofMinutes(2));
        assertThat(platform.chat.say(builder.id(), "Builder", "three")).isTrue();
        assertThat(entry(view, "Builder").muted()).isFalse();
        assertThat(platform.playerData.get(builder.id(), "players.muted-until")).isEmpty();
        assertThat(view.mute("Builder", "", false).join().error().code()).isEqualTo("Players.NothingToDo");

        platform.clock.now = platform.clock.now.plus(Duration.ofDays(3650));
        assertThat(platform.chat.say(other.id(), "Other", "let me talk")).isFalse();
        assertThat(entry(view, "Other").muted()).isTrue();
    }

    @Test
    void theChatShowsTheLastFiftyLinesAndDetailsTheLastTwenty() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);

        for (int line = 1; line <= 260; line++) {
            platform.chat.say(builder.id(), "Builder", "msg-" + line + ".");
        }

        var lines = view.chat().lines().stream().map(PlayersView.Line::text).toList();
        assertThat(lines).hasSize(50);
        assertThat(lines.getFirst()).endsWith("Builder: msg-211.");
        assertThat(lines.getLast()).endsWith("Builder: msg-260.");

        var details = view.details(builder.id().value().toString()).join().value().lines().stream()
                .map(PlayersView.Line::text)
                .filter(text -> text.contains("msg-"))
                .toList();
        assertThat(details).hasSize(20);
        assertThat(details.getFirst()).endsWith("msg-241.");
        assertThat(details.getLast()).endsWith("msg-260.");
    }

    @Test
    void anEmptyChatSaysSo() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayersView view = viewFor(admin);

        assertThat(view.chat().lines()).hasSize(1);
        assertThat(view.chat().lines().getFirst().text()).doesNotContain(":");
    }

    @Test
    void rollingBackAReMuteRestoresTheEarlierMute() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);

        assertThat(view.mute("Builder", "1h", true).join().isSuccess()).isTrue();
        String timed =
                platform.playerData.get(builder.id(), "players.muted-until").orElseThrow();
        assertThat(view.mute("Builder", "", true).join().isSuccess()).isTrue();
        assertThat(platform.playerData.get(builder.id(), "players.muted-until")).contains("permanent");

        rollback(admin, platform.changeLog.records.getLast());

        assertThat(platform.playerData.get(builder.id(), "players.muted-until")).contains(timed);
        platform.clock.now = platform.clock.now.plus(Duration.ofHours(2));
        assertThat(platform.chat.say(builder.id(), "Builder", "free")).isTrue();
    }

    @Test
    void rollingBackAnUnmuteMutesAgain() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);

        assertThat(view.mute("Builder", "permanent", true).join().isSuccess()).isTrue();
        assertThat(view.mute("Builder", "", false).join().isSuccess()).isTrue();
        assertThat(platform.chat.say(builder.id(), "Builder", "yay")).isTrue();

        rollback(admin, platform.changeLog.records.getLast());

        assertThat(platform.chat.say(builder.id(), "Builder", "nay")).isFalse();
    }

    @Test
    void whitelistOperatorAndFreezeRollBack() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);

        assertThat(view.whitelist("Builder", true).join().isSuccess()).isTrue();
        rollback(admin, platform.changeLog.records.getLast());
        assertThat(platform.access.allowed).doesNotContain(builder.id());

        assertThat(view.operator("Builder", true).join().isSuccess()).isTrue();
        rollback(admin, platform.changeLog.records.getLast());
        assertThat(platform.access.groupsOf(builder.id())).doesNotContain("hytale:Admin");

        assertThat(view.freeze("Builder", true).join().isSuccess()).isTrue();
        rollback(admin, platform.changeLog.records.getLast());
        assertThat(entry(view, "Builder").frozen()).isFalse();

        assertThat(view.groupPermission("hytale:Adventurer", "homes.use", true).isSuccess())
                .isTrue();
        assertThat(view.groupPermission("hytale:Adventurer", "homes.use", false).isSuccess())
                .isTrue();
        rollback(admin, platform.changeLog.records.getLast());
        assertThat(platform.access.groupPermissions("hytale:Adventurer")).contains("homes.use");
    }

    @Test
    void rollbacksStillCheckTheActionsNode() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);
        assertThat(view.mute("Builder", "1h", true).join().isSuccess()).isTrue();
        ChangeRecord mute = platform.changeLog.records.getLast();
        assertThat(view.operator("Builder", true).join().isSuccess()).isTrue();
        ChangeRecord op = platform.changeLog.records.getLast();

        platform.deniedNodes.addAll(java.util.List.of("settings", "admin", "players.mute"));
        rollback(admin, mute);
        rollback(admin, op);

        assertThat(platform.chat.say(builder.id(), "Builder", "still muted")).isFalse();
        assertThat(platform.access.groupsOf(builder.id())).contains("hytale:Admin");
    }

    @Test
    void aWhitelistedPlayerWhoNeverJoinedCanBeRemovedByIdAndRolledBack() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerId ghost = platform.bans.add("Ghost");
        PlayersView view = viewFor(admin);

        assertThat(view.whitelist("Ghost", true).join().isSuccess()).isTrue();
        assertThat(view.list())
                .extracting(PlayersView.Entry::id)
                .contains(ghost.value().toString());

        rollback(admin, platform.changeLog.records.getLast());

        assertThat(platform.access.allowed).doesNotContain(ghost);
    }

    @Test
    void aWhitelistedPlayerWhoNeverJoinedCanBeRemovedByTheirId() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerId ghost = platform.bans.add("Ghost");
        PlayersView view = viewFor(admin);
        assertThat(view.whitelist("Ghost", true).join().isSuccess()).isTrue();

        var removed = view.whitelist(ghost.value().toString(), false).join();

        assertThat(removed.isSuccess())
                .as(
                        "removing by id failed with %s",
                        removed.isFailure() ? removed.error().code() : "-")
                .isTrue();
        assertThat(platform.access.allowed).doesNotContain(ghost);
    }

    @Test
    void mutedLinesAreNotKeptAndTheLengthIsValidated() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);

        assertThat(view.mute("Builder", "0m", true).join().error().code()).isEqualTo("Bans.BadLength");
        assertThat(view.mute("Builder", "99999d", true).join().error().code()).isEqualTo("Bans.BadLength");
        assertThat(view.mute("Nobody", "1h", true).join().error().code()).isEqualTo("Players.Unknown");
        assertThat(view.mute("Builder", "9999w", true).join().isSuccess()).isTrue();
        for (int line = 0; line < 5; line++) {
            assertThat(platform.chat.say(builder.id(), "Builder", "hidden" + line))
                    .isFalse();
        }
        assertThat(view.chat().lines()).extracting(PlayersView.Line::text).noneMatch(text -> text.contains("hidden"));
    }
}
