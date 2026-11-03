package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.event.PlayerLeft;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParityEdgesRuntimeTest extends RuntimeTestBase {

    private static final String WELCOME = "controlcore:welcome-message";
    private static final String DAYS = "controlcore:history-days";
    private static final SettingId START_TAB = SettingId.of("controlcore:panel-start-tab");

    private PlayerInfo admin;
    private PlayerInfo keeper;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
        keeper = platform.players.join("Keeper");
        platform.deniedNodesFor.put(keeper.id(), new ArrayList<>(List.of("settings", "admin", "staff.secret")));
    }

    private static String text(Result<Reply> result) {
        assertThat(result.isSuccess()).as("%s", result).isTrue();
        return result.value().text().orElse("");
    }

    private Result<Reply> console(String name, Map<String, Object> args) {
        return platform.commands.runAsConsole(name, args);
    }

    private String config(String path) {
        return platform.serverConfig.entry(path).orElseThrow().value();
    }

    @Test
    void restoringOneWorldLeavesTheServerRowsAndOtherRowsAlone() {
        text(console("cc-set", Map.of("id", "server:server-name", "value", "Renamed")));
        text(console("cc-set", Map.of("id", "world:default.is-pvp-enabled", "value", "true")));

        text(console("cc-restore-defaults", Map.of("section", "world:default", "step", "confirm")));

        assertThat(platform.worlds.values.get("default").get("IsPvpEnabled")).isEqualTo("false");
        assertThat(config("ServerName")).isEqualTo("Renamed");
    }

    @Test
    void restoringServerConfigsLeavesWorldRowsAndRowsOutsideTheSearch() {
        text(console("cc-set", Map.of("id", "server:server-name", "value", "Renamed")));
        text(console("cc-set", Map.of("id", "server:max-view-radius", "value", "12")));
        text(console("cc-set", Map.of("id", "world:default.is-pvp-enabled", "value", "true")));

        text(console(
                "cc-restore-defaults",
                Map.of("section", "server-configs", "step", "confirm", "search", "server name")));

        assertThat(config("ServerName")).isEqualTo("Hytale Server");
        assertThat(config("MaxViewRadius")).isEqualTo("12");
        assertThat(platform.worlds.values.get("default").get("IsPvpEnabled")).isEqualTo("true");
    }

    @Test
    void theCommandsSearchIsThePanelsSearch() {
        text(console("cc-set", Map.of("id", "server:server-name", "value", "Renamed")));
        text(console("cc-set", Map.of("id", "server:max-view-radius", "value", "12")));

        assertThat(text(console("cc-configs", Map.of("search", "-name is:changed"))))
                .contains("server:max-view-radius")
                .doesNotContain("server:server-name");
        text(console("cc-restore-defaults", Map.of("section", "server-configs", "step", "confirm", "search", "-name")));

        assertThat(config("ServerName")).isEqualTo("Renamed");
        assertThat(config("MaxViewRadius")).isNotEqualTo("12");
    }

    @Test
    void anUnknownWorldIsRefusedBeforeAnythingChanges() {
        text(console("cc-set", Map.of("id", "server:server-name", "value", "Renamed")));

        Result<Reply> result = console("cc-restore-defaults", Map.of("section", "world:moon", "step", "confirm"));

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo("ServerConfig.UnknownWorld");
        assertThat(config("ServerName")).isEqualTo("Renamed");
    }

    @Test
    void aNonAdminRestoringModSettingsOnlyResetsRowsTheyMayEdit() {
        text(platform.commands.run("cc-perm", admin, Map.of("target", DAYS, "side", "both", "node", "everyone")));
        text(platform.commands.run("cc-set", admin, Map.of("id", WELCOME, "value", "Kept hello")));
        text(platform.commands.run("cc-set", admin, Map.of("id", DAYS, "value", "40")));
        text(platform.commands.run(
                "cc-perm", admin, Map.of("target", WELCOME, "side", "both", "node", "staff.secret")));

        text(platform.commands.run(
                "cc-restore-defaults", keeper, Map.of("section", "mod-settings", "step", "confirm")));

        assertThat(text(platform.commands.run("cc-get", admin, Map.of("id", WELCOME))))
                .contains("Kept hello");
        assertThat(text(platform.commands.run("cc-get", admin, Map.of("id", DAYS))))
                .doesNotContain("40");
    }

    @Test
    void leavingForgetsHeldKeysAndJoiningOrLeavingDoesNotFlushPlayerData() {
        int flushes = platform.playerData.flushes;

        platform.connectedPublisher.publish(new PlayerJoined(keeper));
        platform.connectedPublisher.publish(new PlayerLeft(keeper));

        assertThat(platform.input.forgotten).containsExactly(keeper.id());
        assertThat(platform.playerData.flushes).isEqualTo(flushes);
        assertThat(platform.playerData.get(keeper.id(), "session.last-seen")).isPresent();
    }

    @Test
    void aSnapshotsPlayerRestoringThePlayersPartDoesNotRewriteAnAdminsPreferences() {
        text(platform.commands.run("cc-pref", admin, Map.of("id", START_TAB.toString(), "value", "mod settings")));
        text(platform.commands.run("cc-snapshot-save", admin, Map.of("name", "prefs", "parts", "players")));
        text(platform.commands.run("cc-pref-reset", admin, Map.of("id", START_TAB.toString())));
        String before = ControlCoreApi.get()
                .settings()
                .read(START_TAB, Optional.of(admin.id()))
                .value()
                .value();

        platform.commands.run("cc-snapshot-restore", keeper, Map.of("name", "prefs"));

        assertThat(ControlCoreApi.get()
                        .settings()
                        .read(START_TAB, Optional.of(admin.id()))
                        .value()
                        .value())
                .isEqualTo(before);
    }
}
