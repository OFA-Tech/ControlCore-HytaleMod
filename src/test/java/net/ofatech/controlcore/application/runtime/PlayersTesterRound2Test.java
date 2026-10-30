package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ofatech.controlcore.application.history.ChangeContext;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.event.PlayerLeft;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.history.HistoryView;
import net.ofatech.controlcore.domain.model.players.PlayerAction;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin.Spot;
import org.junit.jupiter.api.Test;

class PlayersTesterRound2Test extends RuntimeTestBase {

    private PlayersView viewFor(PlayerInfo viewer) {
        platform.commands.run(COMMAND, viewer, Map.of());
        return platform.controlPanel.lastContent.players().orElseThrow();
    }

    private List<HistoryView.Entry> historyFor(PlayerInfo viewer) {
        platform.commands.run(COMMAND, viewer, Map.of());
        return platform.controlPanel.lastContent.history().orElseThrow().entries();
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

    private ChangeRecord last() {
        return platform.changeLog.records.getLast();
    }

    private void start() {
        runtime.setup();
        runtime.start();
    }

    private PlayerInfo moderator(String name) {
        PlayerInfo moderator = platform.players.join(name);
        platform.deniedNodesFor.put(moderator.id(), List.of("settings", "admin"));
        return moderator;
    }

    @Test
    void aGroupChangeForSomeoneWhoNeverJoinedRollsBack() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerId ghost = PlayerId.of(UUID.randomUUID());
        platform.access.allowed.add(ghost);
        PlayersView view = viewFor(admin);

        var added =
                view.group(ghost.value().toString(), "hytale:Adventurer", true).join();
        assertThat(added.isSuccess())
                .as("adding failed with %s", added.isFailure() ? added.error().code() : "-")
                .isTrue();
        assertThat(platform.access.groupsOf(ghost)).contains("hytale:Adventurer");

        rollback(admin, last());

        assertThat(platform.access.groupsOf(ghost)).doesNotContain("hytale:Adventurer");
        assertThat(last().undoes()).isPositive();
    }

    @Test
    void aRollbackIsAttributedToWhoRolledBack() {
        start();
        PlayerInfo first = platform.players.join("Admin");
        PlayerInfo second = platform.players.join("Second");
        platform.players.join("Builder");
        PlayersView view = viewFor(first);
        assertThat(ChangeContext.fromPanel(
                                first.id(),
                                "Admin",
                                () -> view.whitelist("Builder", true).join())
                        .isSuccess())
                .isTrue();
        ChangeRecord original = last();

        rollback(second, original);

        ChangeRecord undo = last();
        assertThat(undo.id()).isNotEqualTo(original.id());
        assertThat(undo.undoes()).isEqualTo(original.id());
        assertThat(undo.actor().name()).isEqualTo("Second");
    }

    @Test
    void aGameModeRollbackIsAttributedToWhoRolledBack() {
        start();
        PlayerInfo first = platform.players.join("Admin");
        PlayerInfo second = platform.players.join("Second");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(first);
        assertThat(view.toggleGameMode(builder.id().value().toString()).join().isSuccess())
                .isTrue();
        ChangeRecord original = last();

        rollback(second, original);

        assertThat(last().undoes()).isEqualTo(original.id());
        assertThat(last().actor().name()).isEqualTo("Second");
        assertThat(platform.admin.modes).containsEntry(builder.id(), "adventure");
    }

    @Test
    void aModeratorCannotRollBackChangesThatActAgainstAnAdmin() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo other = platform.players.join("OtherAdmin");
        PlayerInfo moderator = moderator("Moderator");
        PlayersView view = viewFor(admin);

        assertThat(view.mute("OtherAdmin", "1h", true).join().isSuccess()).isTrue();
        assertThat(view.mute("OtherAdmin", "", false).join().isSuccess()).isTrue();
        ChangeRecord unmute = last();
        assertThat(view.whitelist("OtherAdmin", true).join().isSuccess()).isTrue();
        ChangeRecord whitelist = last();
        assertThat(view.freeze("OtherAdmin", true).join().isSuccess()).isTrue();
        assertThat(view.freeze("OtherAdmin", false).join().isSuccess()).isTrue();
        ChangeRecord unfreeze = last();
        int before = platform.changeLog.records.size();

        rollback(moderator, unmute);
        rollback(moderator, whitelist);
        rollback(moderator, unfreeze);

        assertThat(platform.changeLog.records).hasSize(before);
        assertThat(platform.chat.say(other.id(), "OtherAdmin", "still free")).isTrue();
        assertThat(platform.access.allowed).contains(other.id());
        assertThat(entry(viewFor(admin), "OtherAdmin").frozen()).isFalse();
    }

    @Test
    void aModeratorCannotChangeAnAdminsGameMode() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo moderator = moderator("Moderator");
        PlayersView view = viewFor(moderator);

        var result = view.toggleGameMode(admin.id().value().toString()).join();

        assertThat(result.isFailure())
                .as("the moderator changed an admin's game mode")
                .isTrue();
        assertThat(result.error().code()).isEqualTo("Players.ProtectedAdmin");
        assertThat(platform.admin.modes).doesNotContainKey(admin.id());
    }

    @Test
    void aModeratorCannotRollBackAnAdminsGameMode() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo other = platform.players.join("OtherAdmin");
        PlayerInfo moderator = moderator("Moderator");
        PlayersView view = viewFor(admin);
        assertThat(view.toggleGameMode(other.id().value().toString()).join().isSuccess())
                .isTrue();
        ChangeRecord mode = last();

        rollback(moderator, mode);

        assertThat(platform.admin.modes).containsEntry(other.id(), "creative");
    }

    @Test
    void aFreezeLiftedWhileOfflineIsNotAppliedOnJoin() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);
        platform.playerData.put(builder.id(), "session.name", "Builder");
        assertThat(view.freeze("Builder", true).join().isSuccess()).isTrue();
        platform.players.leave(builder);
        platform.connectedPublisher.publish(new PlayerLeft(builder));

        assertThat(view.freeze("Builder", false).join().isSuccess()).isTrue();
        PlayerInfo back = platform.players.join("Builder");
        platform.connectedPublisher.publish(new PlayerJoined(back));
        platform.scheduler.runAll();
        platform.admin.spots.put(back.id(), new Spot("default", 40, 64, 0));
        platform.scheduler.runAll();

        assertThat(platform.admin.heldBack).isEmpty();
        assertThat(entry(view, "Builder").frozen()).isFalse();
    }

    @Test
    void aFrozenPlayerIsHeldWhereTheyRejoinedNotWhereTheyLeft() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);
        platform.admin.spots.put(builder.id(), new Spot("default", 1, 64, 1));
        assertThat(view.freeze("Builder", true).join().isSuccess()).isTrue();
        platform.players.leave(builder);
        platform.connectedPublisher.publish(new PlayerLeft(builder));

        platform.admin.spots.put(builder.id(), new Spot("default", 500, 70, 500));
        PlayerInfo back = platform.players.join("Builder");
        platform.connectedPublisher.publish(new PlayerJoined(back));
        platform.scheduler.runAll();
        platform.admin.spots.put(back.id(), new Spot("default", 510, 70, 500));
        platform.scheduler.runAll();

        assertThat(platform.admin.heldBack).containsExactly(back.id() + "@500.0,70.0,500.0");
    }

    @Test
    void anExpiredMuteClearsTheBadgeWithoutAnyChat() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        platform.players.join("Builder");
        PlayersView view = viewFor(admin);
        assertThat(view.mute("Builder", "30m", true).join().isSuccess()).isTrue();
        assertThat(entry(view, "Builder").muted()).isTrue();

        platform.clock.now = platform.clock.now.plus(Duration.ofMinutes(31));

        assertThat(entry(viewFor(admin), "Builder").muted()).isFalse();
        assertThat(view.mute("Builder", "1h", true).join().value()).doesNotContain("changed from");
    }

    @Test
    void everyPlayerActionRefusesABlankPlayer() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        platform.players.join("Builder");
        PlayersView view = viewFor(admin);
        int before = platform.changeLog.records.size();

        for (PlayerAction action : PlayerAction.values()) {
            if (!action.needs(PlayerAction.Field.PLAYER)) {
                continue;
            }
            for (String blank : new String[] {"", "   ", null}) {
                var result = view.apply(
                                action, new PlayerAction.Input(blank, "1h", "why", "hytale:Adventurer", "homes.use"))
                        .join();
                assertThat(result.isFailure()).as("%s with %s", action, blank).isTrue();
                assertThat(result.error().code())
                        .as("%s with %s", action, blank)
                        .isEqualTo("Players.NoPlayer");
            }
        }

        assertThat(platform.changeLog.records).hasSize(before);
        assertThat(platform.admin.kicked).isEmpty();
        assertThat(platform.bans.bans).isEmpty();
    }

    @Test
    void nodeRulesAcceptRealNodes() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayersView view = viewFor(admin);

        for (String good : List.of("*", "-x", "homes.use", "homes.*", "x-y", "a".repeat(120), "  homes.list  ")) {
            var result = view.groupPermission("hytale:Adventurer", good, true);
            assertThat(result.isSuccess())
                    .as(
                            "%s refused: %s",
                            good, result.isFailure() ? result.error().code() : "-")
                    .isTrue();
        }
        assertThat(platform.access.groupPermissions("hytale:Adventurer")).contains("homes.list");
    }

    @Test
    void nodeRulesRefuseJunk() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayersView view = viewFor(admin);

        for (String bad : List.of("", "-", ".", "...", "a b", "a|b", "a".repeat(121), "-".repeat(3))) {
            var result = view.groupPermission("hytale:Adventurer", bad, true);
            assertThat(result.isFailure()).as("%s accepted", bad).isTrue();
        }
        assertThat(platform.access.groupPermissions("hytale:Adventurer")).isEmpty();
    }

    @Test
    void nodeRulesRefuseEmptySegmentsAndDoubleNegation() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayersView view = viewFor(admin);

        for (String odd : List.of("a..", "--x", ".a", "a..b")) {
            var result = view.groupPermission("hytale:Adventurer", odd, true);
            assertThat(result.isFailure()).as("%s accepted", odd).isTrue();
        }
    }

    @Test
    void namesWithAColonKeepTheirHistoryAndRollBack() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo odd = platform.players.join("Odd:Name");
        PlayersView view = viewFor(admin);

        assertThat(view.group("Odd:Name", "hytale:Adventurer", true).join().isSuccess())
                .isTrue();
        String what = historyFor(admin).getFirst().what();
        rollback(admin, last());

        assertThat(what).contains("Odd:Name").contains("Adventurer");
        assertThat(platform.access.groupsOf(odd.id())).doesNotContain("hytale:Adventurer");
    }

    @Test
    void namesWithAPipeKeepTheirHistoryAndRollBack() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo odd = platform.players.join("Odd|Name");
        PlayersView view = viewFor(admin);

        assertThat(view.group("Odd|Name", "hytale:Adventurer", true).join().isSuccess())
                .isTrue();
        String what = historyFor(admin).getFirst().what();
        rollback(admin, last());

        assertThat(platform.access.groupsOf(odd.id())).as("rollback").doesNotContain("hytale:Adventurer");
        assertThat(what).contains("Odd|Name").contains("Adventurer");
    }

    @Test
    void historyDescribesEveryPlayerChange() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);
        String id = builder.id().value().toString();

        assertThat(view.toggleGameMode(id).join().isSuccess()).isTrue();
        assertThat(view.whitelist("Builder", true).join().isSuccess()).isTrue();
        assertThat(view.group("Builder", "hytale:Adventurer", true).join().isSuccess())
                .isTrue();
        assertThat(view.groupPermission("hytale:Adventurer", "homes.use", true).isSuccess())
                .isTrue();
        assertThat(view.mute("Builder", "1h", true).join().isSuccess()).isTrue();
        assertThat(view.freeze("Builder", true).join().isSuccess()).isTrue();
        assertThat(view.kick(id, "bye").isSuccess()).isTrue();

        List<HistoryView.Entry> players = historyFor(admin).stream()
                .filter(entry -> entry.kind() == ChangeRecord.Kind.PLAYER)
                .toList();

        assertThat(players).hasSize(7);
        for (HistoryView.Entry entry : players) {
            assertThat(entry.what())
                    .as(entry.target())
                    .doesNotContain("controlcore.")
                    .doesNotContain("player:");
            assertThat(entry.before()).as(entry.target()).doesNotContain("controlcore.");
            assertThat(entry.after()).as(entry.target()).doesNotContain("controlcore.");
        }
        assertThat(players)
                .extracting(HistoryView.Entry::what)
                .anySatisfy(what -> assertThat(what).startsWith("Game mode").contains("Builder"))
                .anySatisfy(what -> assertThat(what).startsWith("Whitelist").contains("Builder"))
                .anySatisfy(what -> assertThat(what).contains("Adventurer").contains("Builder"))
                .anySatisfy(what -> assertThat(what).contains("Adventurer").contains("homes.use"))
                .anySatisfy(what -> assertThat(what).startsWith("Mute").contains("Builder"))
                .anySatisfy(what -> assertThat(what).startsWith("Freeze").contains("Builder"))
                .anySatisfy(what -> assertThat(what).startsWith("Kick").contains("Builder"));
    }

    @Test
    void aKickCannotBeRolledBackAndLeavesNoRecord() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);
        assertThat(view.kick(builder.id().value().toString(), "bye").isSuccess())
                .isTrue();
        int before = platform.changeLog.records.size();

        rollback(admin, last());

        assertThat(platform.changeLog.records).hasSize(before);
    }

    @Test
    void anUnfreezeOfSomeoneOfflineRollsBackAndAppliesOnJoin() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);
        assertThat(view.freeze("Builder", true).join().isSuccess()).isTrue();
        assertThat(view.freeze("Builder", false).join().isSuccess()).isTrue();
        ChangeRecord unfreeze = last();
        platform.players.leave(builder);
        platform.connectedPublisher.publish(new PlayerLeft(builder));
        int before = platform.changeLog.records.size();

        rollback(admin, unfreeze);

        assertThat(platform.changeLog.records).hasSize(before + 1);
        assertThat(platform.playerData.get(builder.id(), "players.frozen")).contains("true");
    }

    @Test
    void cancelledAndMutedLinesNeverReachTheChatButAllowedOnesDo() {
        start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);

        platform.chat.say(builder.id(), "Builder", "visible-1");
        platform.chat.say(builder.id(), "Builder", "cancelled-1", true);
        assertThat(view.mute("Builder", "1h", true).join().isSuccess()).isTrue();
        platform.chat.say(builder.id(), "Builder", "muted-1");
        assertThat(view.mute("Builder", "", false).join().isSuccess()).isTrue();
        platform.chat.say(builder.id(), "Builder", "line\nbreak");

        var lines = view.chat().lines().stream().map(PlayersView.Line::text).toList();
        assertThat(lines).anyMatch(text -> text.contains("visible-1"));
        assertThat(lines).anyMatch(text -> text.contains("line break"));
        assertThat(lines).noneMatch(text -> text.contains("cancelled-1") || text.contains("muted-1"));
        assertThat(view.details(builder.id().value().toString()).join().value().lines())
                .extracting(PlayersView.Line::text)
                .noneMatch(text -> text.contains("cancelled-1") || text.contains("muted-1"));
    }
}
