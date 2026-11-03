package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ofatech.controlcore.application.history.ChangeContext;
import net.ofatech.controlcore.application.text.PlainText;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.event.PlayerLeft;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.history.HistoryView;
import net.ofatech.controlcore.domain.model.players.PlayerAction;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.model.players.PlayersView.Filter;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin.Item;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin.Spot;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlayersRuntimeTest {

    @Nested
    class Basics extends RuntimeTestBase {

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

            assertThat(view.kick(admin.id().value().toString(), "").error().code())
                    .isEqualTo("Players.Self");
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
            assertThat(view.groupPermission("hytale:Adventurer", "homes.use", true)
                            .isSuccess())
                    .isTrue();
            assertThat(view.groupPermission("hytale:Adventurer", "bad node!", true)
                            .error()
                            .code())
                    .isEqualTo("Players.BadNode");
            assertThat(view.group("Builder", "nope", true).join().error().code())
                    .isEqualTo("Players.UnknownGroup");

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

    @Nested
    class NodesMutesChatAndRollbacks extends RuntimeTestBase {

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
            assertThat(platform.chat.say(builder.id(), "Builder", "still talking"))
                    .isTrue();
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
        void offlinePlayersCannotBeTeleportedButCanBeFrozenAndMuted() {
            start();
            PlayerInfo admin = platform.players.join("Admin");
            PlayerInfo builder = platform.players.join("Builder");
            platform.playerData.put(builder.id(), "session.name", "Builder");
            platform.playerData.put(builder.id(), "session.first-join", "2026-10-01T10:00:00Z");
            platform.players.leave(builder);
            PlayersView view = viewFor(admin);
            String id = builder.id().value().toString();

            assertThat(view.freeze("Builder", true).join().value()).contains("join");
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
            assertThat(platform.playerData.get(builder.id(), "players.muted-until"))
                    .isEmpty();
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
            assertThat(platform.playerData.get(builder.id(), "players.muted-until"))
                    .contains("permanent");

            rollback(admin, platform.changeLog.records.getLast());

            assertThat(platform.playerData.get(builder.id(), "players.muted-until"))
                    .contains(timed);
            platform.clock.now = platform.clock.now.plus(Duration.ofHours(2));
            assertThat(platform.chat.say(builder.id(), "Builder", "free")).isTrue();
        }

        @Test
        void rollingBackAnUnmuteMutesAgain() {
            start();
            PlayerInfo admin = platform.players.join("Admin");
            PlayerInfo builder = platform.players.join("Builder");
            PlayersView view = viewFor(admin);

            assertThat(view.mute("Builder", "permanent", true).join().isSuccess())
                    .isTrue();
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

            assertThat(view.groupPermission("hytale:Adventurer", "homes.use", true)
                            .isSuccess())
                    .isTrue();
            assertThat(view.groupPermission("hytale:Adventurer", "homes.use", false)
                            .isSuccess())
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

            assertThat(platform.chat.say(builder.id(), "Builder", "still muted"))
                    .isFalse();
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
            assertThat(view.mute("Builder", "99999d", true).join().error().code())
                    .isEqualTo("Bans.BadLength");
            assertThat(view.mute("Nobody", "1h", true).join().error().code()).isEqualTo("Players.Unknown");
            assertThat(view.mute("Builder", "9999w", true).join().isSuccess()).isTrue();
            for (int line = 0; line < 5; line++) {
                assertThat(platform.chat.say(builder.id(), "Builder", "hidden" + line))
                        .isFalse();
            }
            assertThat(view.chat().lines())
                    .extracting(PlayersView.Line::text)
                    .noneMatch(text -> text.contains("hidden"));
        }
    }

    @Nested
    class UnclearInputsAndSelfActions extends RuntimeTestBase {

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
            assertThat(view.mute("Builder", "permanent", true).join().isSuccess())
                    .isTrue();
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
            assertThat(view.mute("Builder", "permanent", true).join().isSuccess())
                    .isTrue();
            ChangeRecord reMute = platform.changeLog.records.getLast();
            platform.clock.now = platform.clock.now.plus(Duration.ofHours(2));
            int records = platform.changeLog.records.size();

            rollback(admin, reMute);

            assertThat(platform.changeLog.records).hasSize(records);
            assertThat(platform.chat.say(builder.id(), "Builder", "still muted"))
                    .isFalse();
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
            assertThat(apply(view, PlayerAction.MUTE_REMOVE, "José").error().code())
                    .isEqualTo("Players.NothingToDo");
            assertThat(apply(view, PlayerAction.FREEZE_REMOVE, "josé").error().code())
                    .isEqualTo("Players.NothingToDo");
            assertThat(apply(view, PlayerAction.GROUP_ADD, "José").error().code())
                    .isEqualTo("Players.NoGroup");
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

    @Nested
    class RollbacksNodesAndNames extends RuntimeTestBase {

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

            var added = view.group(ghost.value().toString(), "hytale:Adventurer", true)
                    .join();
            assertThat(added.isSuccess())
                    .as(
                            "adding failed with %s",
                            added.isFailure() ? added.error().code() : "-")
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
            assertThat(view.toggleGameMode(builder.id().value().toString())
                            .join()
                            .isSuccess())
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
            assertThat(platform.chat.say(other.id(), "OtherAdmin", "still free"))
                    .isTrue();
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
                                    action,
                                    new PlayerAction.Input(blank, "1h", "why", "hytale:Adventurer", "homes.use"))
                            .join();
                    assertThat(result.isFailure())
                            .as("%s with %s", action, blank)
                            .isTrue();
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
            assertThat(view.groupPermission("hytale:Adventurer", "homes.use", true)
                            .isSuccess())
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
            assertThat(view.details(builder.id().value().toString())
                            .join()
                            .value()
                            .lines())
                    .extracting(PlayersView.Line::text)
                    .noneMatch(text -> text.contains("cancelled-1") || text.contains("muted-1"));
        }
    }

    @Nested
    class InventoryMutesAndPlainText extends RuntimeTestBase {

        private static final String RLO = "\u202E";

        private PlayersView viewFor(PlayerInfo viewer) {
            platform.commands.run(COMMAND, viewer, Map.of());
            return platform.controlPanel.lastContent.players().orElseThrow();
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

        private List<String> texts(PlayersView.Details details) {
            return details.lines().stream().map(PlayersView.Line::text).toList();
        }

        @Test
        void aModeratorSeesAPlayersInventoryButNotAnAdmins() {
            start();
            PlayerInfo admin = platform.players.join("Admin");
            PlayerInfo builder = platform.players.join("Builder");
            PlayerInfo moderator = platform.players.join("Moderator");
            platform.deniedNodesFor.put(moderator.id(), List.of("settings", "admin"));
            platform.deniedNodesFor.put(builder.id(), List.of("settings", "admin"));
            platform.admin.inventories.put(admin.id(), List.of(new Item("Admin_Secret_Blade", 1)));
            platform.admin.inventories.put(builder.id(), List.of(new Item("Weapon_Sword_Iron", 1)));
            PlayersView view = viewFor(moderator);
            assertThat(view.rights().inventory()).isTrue();

            var theirs = view.details(builder.id().value().toString()).join();
            var adminsOwn = view.details(admin.id().value().toString()).join();

            assertThat(texts(theirs.value())).contains("Weapon_Sword_Iron x1");
            assertThat(adminsOwn.isSuccess()).isTrue();
            assertThat(texts(adminsOwn.value())).noneMatch(text -> text.contains("Admin_Secret_Blade"));
        }

        @Test
        void anAdminStillSeesAnotherAdminsInventory() {
            start();
            PlayerInfo admin = platform.players.join("Admin");
            PlayerInfo other = platform.players.join("OtherAdmin");
            platform.admin.inventories.put(other.id(), List.of(new Item("Admin_Secret_Blade", 1)));
            PlayersView view = viewFor(admin);

            assertThat(texts(view.details(other.id().value().toString()).join().value()))
                    .contains("Admin_Secret_Blade x1");
        }

        @Test
        void aBansOnlyViewerCannotOpenDetailsOrReadTheChat() {
            start();
            platform.players.join("Admin");
            PlayerInfo builder = platform.players.join("Builder");
            PlayerInfo banner = platform.players.join("Banner");
            platform.deniedNodesFor.put(
                    banner.id(), List.of("settings", "admin", "players", "players.inventory", "players.kick"));
            platform.admin.inventories.put(builder.id(), List.of(new Item("Weapon_Sword_Iron", 1)));
            platform.chat.say(builder.id(), "Builder", "private-ish words");
            PlayersView view = viewFor(banner);

            var details = view.details(builder.id().value().toString()).join();

            assertThat(details.isFailure()).isTrue();
            assertThat(details.error().code()).isEqualTo("Players.Forbidden");
            assertThat(texts(view.chat())).noneMatch(text -> text.contains("private-ish"));
            assertThat(view.rights().ban()).isTrue();
        }

        @Test
        void twoSavedPlayersWithTheSameNameAreNotGuessed() {
            start();
            PlayerInfo admin = platform.players.join("Admin");
            PlayerId first = PlayerId.of(UUID.randomUUID());
            PlayerId second = PlayerId.of(UUID.randomUUID());
            platform.playerData.put(first, "session.name", "Twin");
            platform.playerData.put(second, "session.name", "twin");
            PlayersView view = viewFor(admin);
            int before = platform.changeLog.records.size();

            var muted = view.mute("Twin", "1h", true).join();
            var whitelisted = view.whitelist("TWIN", true).join();

            assertThat(muted.isFailure()).isTrue();
            assertThat(whitelisted.isFailure()).isTrue();
            assertThat(platform.playerData.get(first, "players.muted-until")).isEmpty();
            assertThat(platform.playerData.get(second, "players.muted-until")).isEmpty();
            assertThat(platform.access.allowed).doesNotContain(first, second);
            assertThat(platform.changeLog.records).hasSize(before);
            assertThat(view.mute(first.value().toString(), "1h", true).join().isSuccess())
                    .isTrue();
        }

        @Test
        void aMuteThatRanOutIsNotPutBackByARollback() {
            start();
            PlayerInfo admin = platform.players.join("Admin");
            PlayerInfo builder = platform.players.join("Builder");
            PlayersView view = viewFor(admin);
            assertThat(view.mute("Builder", "30m", true).join().isSuccess()).isTrue();
            assertThat(view.mute("Builder", "", false).join().isSuccess()).isTrue();
            ChangeRecord unmute = last();
            platform.clock.now = platform.clock.now.plus(Duration.ofMinutes(31));
            int before = platform.changeLog.records.size();

            rollback(admin, unmute);

            assertThat(platform.changeLog.records).hasSize(before);
            assertThat(platform.playerData.get(builder.id(), "players.muted-until"))
                    .isEmpty();
            assertThat(platform.chat.say(builder.id(), "Builder", "free")).isTrue();
        }

        @Test
        void aMuteThatIsStillRunningIsPutBackByARollback() {
            start();
            PlayerInfo admin = platform.players.join("Admin");
            PlayerInfo builder = platform.players.join("Builder");
            PlayersView view = viewFor(admin);
            assertThat(view.mute("Builder", "30m", true).join().isSuccess()).isTrue();
            assertThat(view.mute("Builder", "", false).join().isSuccess()).isTrue();
            ChangeRecord unmute = last();
            platform.clock.now = platform.clock.now.plus(Duration.ofMinutes(10));

            rollback(admin, unmute);

            assertThat(last().undoes()).isEqualTo(unmute.id());
            assertThat(platform.chat.say(builder.id(), "Builder", "muted again"))
                    .isFalse();
        }

        @Test
        void nobodyCanUnmuteUnfreezeOrUnwhitelistThemselves() {
            start();
            PlayerInfo moderator = platform.players.join("Moderator");
            platform.deniedNodesFor.put(moderator.id(), List.of("settings", "admin"));
            platform.playerData.put(moderator.id(), "players.muted-until", "permanent");
            platform.playerData.put(moderator.id(), "players.frozen", "true");
            platform.access.allowed.add(moderator.id());
            PlayersView view = viewFor(moderator);
            int before = platform.changeLog.records.size();

            assertThat(view.mute("Moderator", "", false).join().error().code()).isEqualTo("Players.Self");
            assertThat(view.freeze("Moderator", false).join().error().code()).isEqualTo("Players.Self");
            assertThat(view.whitelist("Moderator", false).join().error().code()).isEqualTo("Players.Self");

            assertThat(platform.playerData.get(moderator.id(), "players.muted-until"))
                    .contains("permanent");
            assertThat(platform.playerData.get(moderator.id(), "players.frozen"))
                    .contains("true");
            assertThat(platform.access.allowed).contains(moderator.id());
            assertThat(platform.changeLog.records).hasSize(before);
        }

        @Test
        void nobodyCanRollBackTheirOwnMuteOrFreeze() {
            start();
            PlayerInfo admin = platform.players.join("Admin");
            PlayerInfo moderator = platform.players.join("Moderator");
            platform.deniedNodesFor.put(moderator.id(), List.of("settings", "admin"));
            PlayersView view = viewFor(admin);
            assertThat(view.mute("Moderator", "", true).join().isSuccess()).isTrue();
            ChangeRecord mute = last();
            assertThat(view.freeze("Moderator", true).join().isSuccess()).isTrue();
            ChangeRecord freeze = last();
            int before = platform.changeLog.records.size();

            rollback(moderator, mute);
            rollback(moderator, freeze);

            assertThat(platform.changeLog.records).hasSize(before);
            assertThat(platform.chat.say(moderator.id(), "Moderator", "let me out"))
                    .isFalse();
            assertThat(platform.playerData.get(moderator.id(), "players.frozen"))
                    .contains("true");
        }

        @Test
        void plainTextDropsDirectionOverridesAndOtherFormatCharacters() {
            assertThat(PlainText.oneLine("abc" + RLO + "def\u200B\u2066x\n\ty", 200))
                    .doesNotContain(RLO)
                    .doesNotContain("\u200B")
                    .doesNotContain("\u2066")
                    .doesNotContain("\n")
                    .doesNotContain("\t")
                    .startsWith("abc")
                    .endsWith("y");
            assertThat(PlainText.oneLine(RLO + RLO, 200)).isEmpty();
            assertThat(PlainText.oneLine(null, 200)).isEmpty();
            assertThat(PlainText.oneLine("x".repeat(500), 200)).hasSize(200).endsWith("…");
        }

        @Test
        void plainTextDoesNotCutAnEmojiInHalf() {
            String cut = PlainText.oneLine("a".repeat(198) + "\uD83D\uDE00" + "b", 200);

            assertThat(cut.length()).isLessThanOrEqualTo(200);
            assertThat(cut.chars().filter(c -> Character.isSurrogate((char) c)).count() % 2)
                    .as("lone surrogate in %s", cut.substring(190))
                    .isZero();
        }

        @Test
        void directionOverridesNeverReachTheChatKickReasonOrHistory() {
            start();
            PlayerInfo admin = platform.players.join("Admin");
            PlayerInfo builder = platform.players.join("Builder");
            PlayersView view = viewFor(admin);

            platform.chat.say(builder.id(), "Build" + RLO + "er", "hello " + RLO + "dlrow");
            assertThat(view.kick(builder.id().value().toString(), "bye " + RLO + "evil")
                            .isSuccess())
                    .isTrue();

            assertThat(texts(view.chat())).noneMatch(text -> text.contains(RLO));
            assertThat(platform.admin.kicked).noneMatch(text -> text.contains(RLO));
            assertThat(last().newValue()).doesNotContain(RLO);
        }

        @Test
        void anOfflineFreezeIsHeldOnJoinAndAnOfflineUnfreezeIsNot() {
            start();
            PlayerInfo admin = platform.players.join("Admin");
            PlayerInfo builder = platform.players.join("Builder");
            platform.playerData.put(builder.id(), "session.name", "Builder");
            platform.players.leave(builder);
            PlayersView view = viewFor(admin);

            assertThat(view.freeze("Builder", true).join().isSuccess()).isTrue();
            assertThat(platform.overlay.everToasted)
                    .noneMatch(text -> text.toLowerCase().contains("froze"));
            assertThat(platform.playerData.get(builder.id(), "players.frozen")).contains("true");

            PlayerInfo back = platform.players.join("Builder");
            platform.connectedPublisher.publish(new net.ofatech.controlcore.domain.event.PlayerJoined(back));
            platform.scheduler.runAll();
            platform.admin.spots.put(
                    back.id(), new net.ofatech.controlcore.domain.ports.game.PlayerAdmin.Spot("default", 40, 64, 0));
            platform.scheduler.runAll();

            assertThat(platform.admin.heldBack).isNotEmpty();
        }
    }

    @Nested
    class OfflineTargetsAndLongNames extends RuntimeTestBase {

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
            assertThat(view.mute("Builder", "permanent", true).join().isSuccess())
                    .isTrue();
            ChangeRecord mute = platform.changeLog.records.getLast();

            platform.commands.run("cc-rollback", admin, Map.of("change", (int) mute.id()));
            int records = platform.changeLog.records.size();
            platform.commands.run("cc-rollback", admin, Map.of("change", (int) mute.id()));

            assertThat(platform.changeLog.records).hasSize(records);
            assertThat(platform.chat.say(builder.id(), "Builder", "free")).isTrue();
        }
    }

    @Nested
    class NeverJoinedFreezesAndEmoji extends RuntimeTestBase {

        private PlayersView viewFor(PlayerInfo viewer) {
            platform.commands.run(COMMAND, viewer, Map.of());
            return platform.controlPanel.lastContent.players().orElseThrow();
        }

        private void start() {
            runtime.setup();
            runtime.start();
        }

        @Test
        void aBanOfANameLongerThanSixtyFourIsRefusedWithoutALookup() {
            start();
            PlayerInfo admin = platform.players.join("Admin");
            String longName = "L".repeat(65);
            PlayerId wouldBeFound = platform.bans.add(longName);
            PlayersView view = viewFor(admin);

            var result = view.ban(longName, "1d", "spam").join();

            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().code()).isEqualTo("Bans.UnknownPlayer");
            assertThat(platform.bans.bans).doesNotContainKey(wouldBeFound);
            assertThat(view.ban("L".repeat(64), "1d", "spam").join().error().code())
                    .isEqualTo("Bans.UnknownPlayer");
        }

        @Test
        void kickingABannedPlayerWhoNeverJoinedSaysTheyAreNotOnline() {
            start();
            PlayerInfo admin = platform.players.join("Admin");
            PlayerId ghost = platform.bans.add("Ghost");
            PlayersView view = viewFor(admin);
            assertThat(view.ban("Ghost", "1d", "x").join().isSuccess()).isTrue();
            assertThat(platform.bans.bans).containsKey(ghost);

            var kicked = view.apply(PlayerAction.KICK, new PlayerAction.Input("ghost", "", "bye", "", ""))
                    .join();
            var nobody = view.apply(PlayerAction.KICK, new PlayerAction.Input("Nobody", "", "bye", "", ""))
                    .join();

            assertThat(kicked.error().code()).isEqualTo("Players.NotOnline");
            assertThat(nobody.error().code()).isEqualTo("Players.Unknown");
            assertThat(platform.admin.kicked).isEmpty();
        }

        @Test
        void aWhitelistedPlayerWhoNeverJoinedCanBeMutedByName() {
            start();
            PlayerInfo admin = platform.players.join("Admin");
            PlayerId ghost = platform.bans.add("Ghost");
            PlayersView view = viewFor(admin);
            assertThat(view.whitelist("Ghost", true).join().isSuccess()).isTrue();
            platform.bans.known.clear();

            var muted = view.mute("GHOST", "1h", true).join();

            assertThat(muted.isSuccess())
                    .as("failed with %s", muted.isFailure() ? muted.error().code() : "-")
                    .isTrue();
            assertThat(platform.playerData.get(ghost, "players.muted-until")).isPresent();
        }

        @Test
        void aFrozenPlayerWhoLeftBeforeTheHoldRanIsNotHeldOrKeptInMemory() {
            start();
            PlayerInfo admin = platform.players.join("Admin");
            PlayerInfo builder = platform.players.join("Builder");
            PlayersView view = viewFor(admin);
            assertThat(view.freeze("Builder", true).join().isSuccess()).isTrue();

            platform.players.leave(builder);
            platform.connectedPublisher.publish(new PlayerLeft(builder));
            platform.admin.spots.put(builder.id(), new Spot("default", 99, 64, 99));
            platform.scheduler.runAll();

            assertThat(platform.admin.heldBack).isEmpty();
            assertThat(platform.playerData.get(builder.id(), "players.frozen")).contains("true");
        }

        @Test
        void aFrozenPlayerWhoRejoinsTwiceIsHeldAtTheLatestSpot() {
            start();
            PlayerInfo admin = platform.players.join("Admin");
            PlayerInfo builder = platform.players.join("Builder");
            PlayersView view = viewFor(admin);
            assertThat(view.freeze("Builder", true).join().isSuccess()).isTrue();

            for (int round = 1; round <= 2; round++) {
                platform.players.leave(builder);
                platform.connectedPublisher.publish(new PlayerLeft(builder));
                platform.admin.spots.put(builder.id(), new Spot("default", round * 100, 64, 0));
                PlayerInfo back = platform.players.join("Builder");
                platform.connectedPublisher.publish(new PlayerJoined(back));
                platform.scheduler.runAll();
            }
            platform.admin.spots.put(builder.id(), new Spot("default", 250, 64, 0));
            platform.scheduler.runAll();

            assertThat(platform.admin.heldBack).last().isEqualTo(builder.id() + "@200.0,64.0,0.0");
        }

        @Test
        void plainTextKeepsEmojiWholeAtEveryCutAndDropsLineSeparators() {
            String emoji = "\uD83D\uDE00";
            for (int max = 1; max <= 8; max++) {
                String cut = PlainText.oneLine(emoji.repeat(10), max);
                assertThat(cut.length()).as("max %s", max).isLessThanOrEqualTo(max);
                assertThat(cut.codePoints().noneMatch(cp -> Character.isSurrogate((char) cp) && cp < 0x10000))
                        .as("lone surrogate at max %s", max)
                        .isTrue();
                assertThat(cut).as("max %s", max).endsWith("…");
            }
            assertThat(PlainText.oneLine("a\u2028b\u2029c\u0085d", 200)).isEqualTo("a b c d");
        }

        @Test
        void chatLinesWithEmojiAtTheLimitStayWhole() {
            start();
            PlayerInfo admin = platform.players.join("Admin");
            PlayerInfo builder = platform.players.join("Builder");
            PlayersView view = viewFor(admin);

            platform.chat.say(builder.id(), "Builder", "z".repeat(254) + "\uD83D\uDE00" + "tail");

            List<String> lines =
                    view.chat().lines().stream().map(PlayersView.Line::text).toList();
            assertThat(lines)
                    .anyMatch(text -> text.contains("zzz"))
                    .allMatch(text -> text.codePoints().noneMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF));
        }
    }
}
