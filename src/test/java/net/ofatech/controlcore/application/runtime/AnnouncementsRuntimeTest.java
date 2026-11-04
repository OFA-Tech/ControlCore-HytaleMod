package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.AnnouncementsApi;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.HistoryApi;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnnouncementsRuntimeTest extends RuntimeTestBase {

    private PlayerInfo admin;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
    }

    @Test
    void inTurnSkipsAnnouncementsThatAreOff() {
        AnnouncementsApi api = api();
        api.add("One {online}");
        api.add("Two");
        api.add("Three");
        api.setEnabled("2", false);
        api.setSettings(new AnnouncementsApi.Settings(true, 5, "in-turn", "chat", 0));

        platform.scheduler.runAll();
        for (int i = 0; i < 3; i++) {
            later(5);
        }

        assertThat(platform.messenger.broadcasts).containsExactly("One 1", "Three", "One 1");
    }

    @Test
    void randomNeverSendsTheSameOneTwiceInARow() {
        AnnouncementsApi api = api();
        api.add("A");
        api.add("B");
        api.add("C");
        api.setSettings(new AnnouncementsApi.Settings(false, 5, "random", "chat", 0));

        for (int i = 0; i < 40; i++) {
            assertThat(api.sendNow().isSuccess()).isTrue();
        }

        List<String> sent = platform.messenger.broadcasts;
        assertThat(sent).hasSize(40);
        for (int i = 1; i < sent.size(); i++) {
            assertThat(sent.get(i)).isNotEqualTo(sent.get(i - 1));
        }
    }

    @Test
    void nothingIsSentWithTooFewPlayersOnline() {
        api().add("Hello");
        api().setSettings(new AnnouncementsApi.Settings(true, 1, "in-turn", "chat", 2));

        platform.scheduler.runAll();
        later(1);
        assertThat(platform.messenger.broadcasts).isEmpty();

        platform.players.join("Second");
        later(1);
        assertThat(platform.messenger.broadcasts).containsExactly("Hello");
    }

    @Test
    void commandsManageTheList() {
        platform.commands.run("cc-announce-add", admin, Map.of("text", "Vote for us"));
        platform.commands.run("cc-announce-add", admin, Map.of("text", "Join Discord"));
        platform.commands.run("cc-announce-move", admin, Map.of("id", "2", "direction", "up"));
        var set = platform.commands.run(
                "cc-announce-set", admin, Map.of("interval", "15", "order", "random", "min-players", "3"));
        var bad = platform.commands.run("cc-announce-set", admin, Map.of("channel", "radio"));
        var listed = platform.commands.run("cc-announce-list", admin, Map.of());

        assertThat(set.isSuccess()).isTrue();
        assertThat(bad.error().code()).isEqualTo("Announcements.InvalidChannel");
        assertThat(api().list()).extracting(AnnouncementsApi.Announcement::id).containsExactly("2", "1");
        assertThat(api().settings()).isEqualTo(new AnnouncementsApi.Settings(false, 15, "random", "chat", 3));
        assertThat(listed.value().text().orElseThrow()).contains("Join Discord").contains("Vote for us");

        platform.commands.run("cc-announce-now", admin, Map.of("id", "1"));
        assertThat(platform.messenger.broadcasts).containsExactly("Vote for us");
    }

    @Test
    void playersNeedTheRight() {
        PlayerInfo guest = platform.players.join("Guest");
        platform.deniedNodesFor.put(guest.id(), List.of("admin", "settings", "announcements"));
        api().add("Hi");

        AnnouncementsApi asGuest = api().actingFor(guest.id());

        assertThat(asGuest.list()).isEmpty();
        assertThat(asGuest.add("Mine").error().code()).isEqualTo("Announcements.Forbidden");
        assertThat(platform.commands
                        .run("cc-announce-remove", guest, Map.of("id", "1"))
                        .error()
                        .code())
                .isEqualTo("Announcements.Forbidden");
    }

    @Test
    void removingCanBeRolledBackAndIsSaved() {
        api().add("Keep me");
        api().remove("1");
        assertThat(api().list()).isEmpty();

        HistoryApi history = ControlCoreApi.get().history();
        HistoryApi.Change removal =
                history.list(Optional.of(HistoryApi.Kind.ANNOUNCEMENT), "", 5).getFirst();
        assertThat(history.rollback(removal.id()).isSuccess()).isTrue();

        assertThat(api().list()).containsExactly(new AnnouncementsApi.Announcement("1", "Keep me", true));
        assertThat(platform.announcements.state.items()).hasSize(1);
    }

    private void later(int minutes) {
        platform.clock.now = platform.clock.now.plus(Duration.ofMinutes(minutes));
        platform.scheduler.runAll();
    }

    private static AnnouncementsApi api() {
        return ControlCoreApi.get().announcements();
    }
}
