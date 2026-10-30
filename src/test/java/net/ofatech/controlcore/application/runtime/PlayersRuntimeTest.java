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

    @Test
    void whitelistOperatorsAndGroupsAreChangedRecordedAndRolledBack() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);

        assertThat(view.whitelist("Builder", true).join().isSuccess()).isTrue();
        assertThat(platform.access.allowed).contains(builder.id());
        assertThat(view.whitelist("Builder", true).join().error().code()).isEqualTo("Players.NothingToDo");
        assertThat(view.operator("Builder", true).join().isSuccess()).isTrue();
        assertThat(platform.access.groupsOf(builder.id())).contains("hytale:Admin");
        assertThat(view.groupPermission("hytale:Adventurer", "homes.use", true).isSuccess())
                .isTrue();
        assertThat(view.groupPermission("hytale:Adventurer", "bad node!", true)
                        .error()
                        .code())
                .isEqualTo("Players.BadNode");
        assertThat(view.group("Builder", "nope", true).join().error().code()).isEqualTo("Players.UnknownGroup");

        PlayersView.Entry entry = viewFor(admin).list().stream()
                .filter(found -> found.name().equals("Builder"))
                .findFirst()
                .orElseThrow();
        assertThat(entry.whitelisted()).isTrue();
        assertThat(entry.operator()).isTrue();
        assertThat(entry.matches(Filter.OPERATORS, "")).isTrue();

        long node = platform.changeLog.records.getLast().id();
        platform.commands.run("cc-rollback", admin, Map.of("change", (int) node));
        assertThat(platform.access.groupPermissions("hytale:Adventurer")).doesNotContain("homes.use");
    }

    @Test
    void onlyAdminsMayChangeOperatorsAndGroups() {
        runtime.setup();
        runtime.start();
        PlayerInfo helper = platform.players.join("Helper");
        platform.deniedNodes.add("settings");
        platform.deniedNodes.add("admin");
        platform.players.join("Builder");
        platform.commands.run(COMMAND, helper, Map.of());
        PlayersView view = platform.controlPanel.lastContent.players().orElseThrow();

        assertThat(view.rights().groups()).isFalse();
        assertThat(view.operator("Builder", true).join().error().code()).isEqualTo("Players.AdminsOnly");
        assertThat(view.whitelist("Builder", true).join().isSuccess()).isTrue();
    }

    @Test
    void mutedPlayersCannotChatUntilUnmutedAndChatIsKept() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);

        assertThat(platform.chat.say(builder.id(), "Builder", "hello all")).isTrue();
        assertThat(view.mute("Builder", "1h", true).join().isSuccess()).isTrue();
        assertThat(platform.chat.say(builder.id(), "Builder", "spam")).isFalse();
        assertThat(view.list().stream()
                        .filter(entry -> entry.name().equals("Builder"))
                        .findFirst()
                        .orElseThrow()
                        .muted())
                .isTrue();

        long mute = platform.changeLog.records.getLast().id();
        platform.commands.run("cc-rollback", admin, Map.of("change", (int) mute));
        assertThat(platform.chat.say(builder.id(), "Builder", "back")).isTrue();

        assertThat(view.chat().lines())
                .extracting(PlayersView.Line::text)
                .anyMatch(text -> text.endsWith("Builder: hello all"))
                .noneMatch(text -> text.contains("spam"));
        assertThat(view.mute("Builder", "soon", true).join().error().code()).isEqualTo("Bans.BadLength");
    }

    @Test
    void aFreezeSurvivesLeavingAndIsAppliedAgainOnJoin() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        PlayersView view = viewFor(admin);

        assertThat(view.freeze("Builder", true).join().isSuccess()).isTrue();
        assertThat(view.freeze("Builder", true).join().error().code()).isEqualTo("Players.NothingToDo");

        platform.players.leave(builder);
        platform.connectedPublisher.publish(new net.ofatech.controlcore.domain.event.PlayerLeft(builder));
        PlayerInfo back = platform.players.join("Builder");
        platform.connectedPublisher.publish(new net.ofatech.controlcore.domain.event.PlayerJoined(back));
        platform.scheduler.runAll();

        assertThat(view.list().stream()
                        .filter(entry -> entry.name().equals("Builder"))
                        .findFirst()
                        .orElseThrow()
                        .frozen())
                .isTrue();
        platform.admin.spots.put(
                back.id(), new net.ofatech.controlcore.domain.ports.game.PlayerAdmin.Spot("default", 40, 64, 0));
        platform.scheduler.runAll();
        assertThat(platform.admin.heldBack).isNotEmpty();

        assertThat(view.freeze("Builder", false).join().isSuccess()).isTrue();
        assertThat(view.list().stream()
                        .filter(entry -> entry.name().equals("Builder"))
                        .findFirst()
                        .orElseThrow()
                        .frozen())
                .isFalse();
    }

    @Test
    void detailsShowTheInventoryOfOnlinePlayers() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerInfo builder = platform.players.join("Builder");
        platform.admin.inventories.put(
                builder.id(),
                java.util.List.of(
                        new net.ofatech.controlcore.domain.ports.game.PlayerAdmin.Item("Weapon_Sword_Iron", 1)));
        PlayersView view = viewFor(admin);

        var details = view.details(builder.id().value().toString()).join().value();

        assertThat(details.title()).isEqualTo("Builder");
        assertThat(details.lines()).extracting(PlayersView.Line::text).contains("Weapon_Sword_Iron x1");
    }
}
