package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.ofatech.controlcore.application.text.PlainText;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.event.PlayerLeft;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.players.PlayerAction;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin.Spot;
import org.junit.jupiter.api.Test;

class PlayersTesterRound4Test extends RuntimeTestBase {

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
        assertThat(view.ban("L".repeat(64), "1d", "spam").join().error().code()).isEqualTo("Bans.UnknownPlayer");
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
