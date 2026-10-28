package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ofatech.controlcore.application.text.PlainText;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin.Item;
import org.junit.jupiter.api.Test;

class PlayersTesterRound3Test extends RuntimeTestBase {

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
        assertThat(platform.playerData.get(builder.id(), "players.muted-until")).isEmpty();
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
        assertThat(platform.chat.say(builder.id(), "Builder", "muted again")).isFalse();
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
        assertThat(platform.playerData.get(moderator.id(), "players.frozen")).contains("true");
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
        assertThat(platform.chat.say(moderator.id(), "Moderator", "let me out")).isFalse();
        assertThat(platform.playerData.get(moderator.id(), "players.frozen")).contains("true");
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
