package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.hud.Overlay;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParityGapsRuntimeTest extends RuntimeTestBase {

    private PlayerInfo admin;
    private PlayerInfo builder;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
        builder = platform.players.join("Builder");
        platform.deniedNodesFor.put(builder.id(), List.of("settings", "admin"));
        platform.connectedPublisher.publish(new PlayerJoined(admin));
        platform.connectedPublisher.publish(new PlayerJoined(builder));
    }

    @Test
    void aBuiltInProfileCanBePreviewedWithoutChangingAnything() {
        text(console("cc-set", Map.of("id", "world:default.is-pvp-enabled", "value", "false")));

        assertThat(text(console("cc-profile-preview", Map.of("profile", "@pvp"))))
                .startsWith("Switching to PvP changes");
        assertThat(platform.worlds.values.get("default").get("IsPvpEnabled")).isEqualTo("false");
        assertThat(platform.snapshots.snapshots).doesNotContainKey("before-pvp");

        assertThat(code(console("cc-profile-preview", Map.of("profile", "nothing"))))
                .isEqualTo("Snapshots.Unknown");
        platform.deniedNodesFor.put(builder.id(), List.of("settings", "admin", "snapshots"));
        assertThat(code(platform.commands.run("cc-profile-preview", builder, Map.of("profile", "@pvp"))))
                .isEqualTo("Snapshots.Forbidden");
    }

    @Test
    void playersAreListedWithThePanelsFiltersAndACap() {
        text(console("cc-mute", Map.of("player", "Builder", "length", "30m")));

        assertThat(text(console("cc-players", Map.of()))).contains("Admin", "Builder");
        assertThat(text(console("cc-players", Map.of("filter", "muted"))))
                .contains("Builder", "[Muted]", builder.id().value().toString().substring(0, 8))
                .doesNotContain("Admin");
        assertThat(text(console("cc-players", Map.of("filter", "everyone", "search", "adm"))))
                .contains("Admin")
                .doesNotContain("Builder");
        assertThat(text(console("cc-players", Map.of("filter", "frozen")))).isEqualTo("No players match.");
        assertThat(code(console("cc-players", Map.of("filter", "loud")))).isEqualTo("Players.BadChoice");

        for (int i = 0; i < 25; i++) {
            platform.players.join("Guest" + i);
        }
        String all = text(console("cc-players", Map.of("filter", "online")));
        assertThat(all).contains("(27)", "and 7 more");
        assertThat(all.lines()).hasSize(1 + 20 + 1);

        platform.deniedNodesFor.put(builder.id(), List.of("settings", "admin", "players", "bans"));
        assertThat(code(platform.commands.run("cc-players", builder, Map.of()))).isEqualTo("Players.Forbidden");
    }

    @Test
    void theApiReachesOnlyTheAdmins() {
        var notifications = ControlCoreApi.get().notifications();

        notifications.toastAdmins("Staff", "Backup done", ToastLevel.INFO, 5);
        notifications.warningAdmins("backup", "Backup running", ToastLevel.WARNING, 0);

        assertThat(overlay(admin).toasts()).extracting(Overlay.Line::text).containsExactly("Backup done");
        assertThat(overlay(admin).warnings()).extracting(Overlay.Line::text).containsExactly("Backup running");
        assertThat(platform.overlay.shown.get(builder.id()))
                .satisfiesAnyOf(
                        overlay -> assertThat(overlay).isNull(),
                        overlay -> assertThat(overlay.toasts().isEmpty()
                                        && overlay.warnings().isEmpty())
                                .isTrue());

        notifications.clearWarningAdmins("backup");
        assertThat(overlay(admin).warnings()).isEmpty();
    }

    @Test
    void historyCanBeFilteredByKind() {
        text(console("cc-set", Map.of("id", "controlcore:welcome-message", "value", "Hello")));
        text(console("cc-set", Map.of("id", "world:default.is-pvp-enabled", "value", "true")));

        assertThat(text(console("cc-history", Map.of("kind", "settings")))).contains("Hello");
        assertThat(text(console("cc-history", Map.of("kind", "worlds")))).doesNotContain("Hello");
        assertThat(text(console("cc-history", Map.of("kind", "bans")))).isEqualTo("No changes match.");
        assertThat(code(console("cc-history", Map.of("kind", "weather")))).isEqualTo("History.BadKind");
    }

    private Result<Reply> console(String name, Map<String, Object> args) {
        return platform.commands.runAsConsole(name, args);
    }

    private Overlay overlay(PlayerInfo player) {
        return platform.overlay.shown.get(player.id());
    }

    private static String text(Result<Reply> result) {
        assertThat(result.isSuccess()).as("%s", result).isTrue();
        return result.value().text().orElseThrow();
    }

    private static String code(Result<Reply> result) {
        assertThat(result.isFailure()).as("%s", result).isTrue();
        return result.error().code();
    }
}
