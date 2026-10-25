package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.system.RecentProblems;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProfilesAndHealthRuntimeTest extends RuntimeTestBase {

    private static final String WELCOME = "controlcore:welcome-message";
    private static final String PVP = "world:default.is-pvp-enabled";

    private PlayerInfo admin;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
    }

    @Test
    void aBuiltInProfileChangesOnlyItsOwnValues() {
        run("cc-set", Map.of("id", WELCOME, "value", "Hello"));

        String switched = reply("cc-profile", Map.of("profile", "@pvp"));

        assertThat(switched).contains("PvP").contains("before-pvp");
        assertThat(platform.worlds.values.get("default").get("IsPvpEnabled")).isEqualTo("true");
        assertThat(value(WELCOME)).isEqualTo("Hello");
        assertThat(platform.activeProfile.active).contains("@pvp");
        assertThat(platform.snapshots.snapshots).containsKey("before-pvp");
        assertThat(reply("cc-profiles", Map.of())).contains("Everything still matches");

        run("cc-set", Map.of("id", PVP, "value", "false"));

        assertThat(reply("cc-profiles", Map.of())).contains("1 value(s) changed");
    }

    @Test
    void vanillaPutsSettingsBackToTheirDefaults() {
        run("cc-set", Map.of("id", WELCOME, "value", "Hello"));

        reply("cc-profile", Map.of("profile", "@vanilla"));

        assertThat(value(WELCOME)).isEmpty();
        assertThat(platform.snapshots.snapshots.get("before-vanilla").settings())
                .containsEntry(WELCOME, "Hello");
    }

    @Test
    void deletingAllSnapshotsKeepsProfiles() {
        reply("cc-snapshot-save", Map.of("name", "keep"));
        reply("cc-snapshot-save", Map.of("name", "drop"));
        platform.snapshots.save(platform.snapshots.snapshots.get("keep").asProfile(true));
        run("cc-profile", Map.of("profile", "drop"));

        assertThat(reply("cc-snapshots-clear", Map.of())).contains("1 snapshot");

        assertThat(platform.snapshots.snapshots).containsOnlyKeys("keep");
        assertThat(run("cc-profile", Map.of("profile", "keep")).isSuccess()).isTrue();
        assertThat(run("cc-profile", Map.of("profile", "drop")).isFailure()).isTrue();
    }

    @Test
    void historyCanBeClearedByAgeOrCompletely() {
        run("cc-set", Map.of("id", WELCOME, "value", "Old"));
        platform.clock.now = platform.clock.now.plus(Duration.ofDays(10));
        run("cc-set", Map.of("id", WELCOME, "value", "New"));

        assertThat(reply("cc-history-clear", Map.of("age", "7d"))).contains("1 change");
        assertThat(platform.changeLog.records).hasSize(1);

        assertThat(reply("cc-history-clear", Map.of("age", "all"))).contains("1 change");
        assertThat(platform.changeLog.records).isEmpty();
        assertThat(run("cc-history-clear", Map.of("age", "soon")).error().code())
                .isEqualTo("History.InvalidAge");
    }

    @Test
    void theReportHidesAddressesAndSecrets() {
        platform.problems.add(new RecentProblems.Problem(
                platform.clock.now, false, "Could not reach 192.168.0.12:5520 with token=abc123secret"));

        String shown = reply("cc-diagnostics", Map.of());

        assertThat(shown).contains("Hytale version").contains("0.7.4").contains("Report written to");
        String report = platform.reports.files.values().iterator().next();
        assertThat(report)
                .contains("[ip]")
                .contains("token=[hidden]")
                .doesNotContain("192.168")
                .doesNotContain("abc123");
    }

    @Test
    void onlyAdminsMaySeeDiagnostics() {
        platform.deniedNodes.add("settings");
        platform.deniedNodes.add("admin");

        assertThat(run("cc-diagnostics", Map.of()).error().code()).isEqualTo("Diagnostics.Forbidden");
        assertThat(run("cc-history-clear", Map.of("age", "all")).error().code()).isEqualTo("History.ClearForbidden");
    }

    private Result<Reply> run(String command, Map<String, Object> args) {
        return platform.commands.run(command, admin, args);
    }

    private String reply(String command, Map<String, Object> args) {
        var result = run(command, args);
        assertThat(result.isSuccess()).as(command + " " + args + ": " + result).isTrue();
        return result.value().text().orElse("");
    }

    private String value(String id) {
        return ControlCoreApi.get()
                .settings()
                .read(SettingId.of(id), Optional.empty())
                .value()
                .value();
    }
}
