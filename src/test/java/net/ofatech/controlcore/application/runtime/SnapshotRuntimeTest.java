package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SnapshotRuntimeTest extends RuntimeTestBase {

    private static final String WELCOME = "controlcore:welcome-message";
    private static final String SERVER_NAME = "server:server-name";

    private PlayerInfo admin;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
    }

    @Test
    void gameActionChoicesAreRecordedAndRestored() {
        String id = "controlcore:open-preferences";
        assertThat(run("cc-key-game-action", Map.of("id", id, "mode", "keep")).isSuccess())
                .isTrue();
        assertThat(platform.changeLog.records.getLast().kind())
                .isEqualTo(net.ofatech.controlcore.domain.model.history.ChangeRecord.Kind.GAME_ACTION);
        assertThat(run("cc-snapshot-save", Map.of("name", "modes")).isSuccess()).isTrue();
        assertThat(platform.snapshots.snapshots.get("modes").gameActions()).contains(Map.of(id, "keep"));

        run("cc-key-game-action", Map.of("id", id, "mode", "replace"));
        assertThat(reply("cc-snapshot-diff", Map.of("name", "modes")))
                .contains("replace -> ")
                .contains("keep");
        reply("cc-snapshot-restore", Map.of("name", "modes"));
        assertThat(reply("cc-history", Map.of())).contains("keep");

        long last = platform.changeLog.records.getLast().id();
        assertThat(run("cc-rollback", Map.of("change", (int) last)).isSuccess()).isTrue();
        run("cc-snapshot-save", Map.of("name", "after"));
        assertThat(platform.snapshots.snapshots.get("after").gameActions()).contains(Map.of(id, "replace"));
    }

    @Test
    void someoneWithOnlyTheSnapshotsNodeCannotRestoreKeysOrPermissions() {
        run("cc-keybind-add", Map.of("name", "warp", "keys", "ctrl+r", "command", "warp spawn"));
        run("cc-set", Map.of("id", WELCOME, "value", "Saved"));
        assertThat(run("cc-snapshot-save", Map.of("name", "base")).isSuccess()).isTrue();
        run("cc-keybind-remove", Map.of("name", "warp"));
        run("cc-set", Map.of("id", WELCOME, "value", "Changed"));
        PlayerInfo moderator = platform.players.join("Mod");
        platform.deniedNodesFor.put(moderator.id(), java.util.List.of("settings", "admin"));

        var restored = platform.commands.run("cc-snapshot-restore", moderator, Map.of("name", "base"));

        assertThat(restored.isSuccess()).as(restored.toString()).isTrue();
        assertThat(platform.customKeyBinds.all())
                .extracting(bind -> bind.name())
                .doesNotContain("warp");
        assertThat(value(WELCOME)).isEqualTo("Saved");
    }

    @Test
    void restoringAllPutsEverythingBack() {
        run("cc-set", Map.of("id", WELCOME, "value", "Saved"));
        run("cc-keybind-add", Map.of("name", "warp", "keys", "ctrl+r", "command", "warp spawn"));
        assertThat(run("cc-snapshot-save", Map.of("name", "Base")).isSuccess()).isTrue();
        Snapshot saved = platform.snapshots.snapshots.get("base");
        assertThat(saved.parts()).doesNotContain(Snapshot.Part.PLAYERS);
        assertThat(saved.config()).containsKey(SERVER_NAME).doesNotContainKey("server:version");

        run("cc-set", Map.of("id", WELCOME, "value", "Changed"));
        run("cc-set", Map.of("id", SERVER_NAME, "value", "Other name"));
        run("cc-keybind-remove", Map.of("name", "warp"));
        run("cc-keybind-add", Map.of("name", "extra", "keys", "ctrl+e", "command", "say extra"));
        run("cc-perm", Map.of("target", WELCOME, "side", "edit", "node", "staff.motd"));

        String diff = reply("cc-snapshot-diff", Map.of("name", "base"));
        assertThat(diff)
                .contains("Changed -> Saved")
                .contains("Other name -> ")
                .contains("warp")
                .contains("extra");
        assertThat(value(WELCOME)).isEqualTo("Changed");

        assertThat(reply("cc-snapshot-restore", Map.of("name", "base"))).contains("0 failed");

        assertThat(value(WELCOME)).isEqualTo("Saved");
        assertThat(serverName()).isEqualTo(saved.config().get(SERVER_NAME));
        assertThat(platform.customKeyBinds.all())
                .extracting(bind -> bind.name())
                .containsExactly("warp");
        assertThat(platform.settingPermissions.all().getOrDefault("setting:" + WELCOME, null))
                .satisfiesAnyOf(
                        rule -> assertThat(rule).isNull(),
                        rule -> assertThat(rule.isInherit()).isTrue());
        assertThat(platform.changeLog.records.getLast().source()).isEqualTo(ChangeRecord.Source.SNAPSHOT);
        assertThat(reply("cc-snapshot-diff", Map.of("name", "base"))).contains("nothing to restore");
    }

    @Test
    void aSnapshotOfOneModLeavesOtherPartsAlone() {
        run("cc-snapshot-save", Map.of("name", "cc-only", "parts", "settings:controlcore"));
        run("cc-set", Map.of("id", WELCOME, "value", "Changed"));
        run("cc-set", Map.of("id", SERVER_NAME, "value", "Other name"));

        run("cc-snapshot-restore", Map.of("name", "cc-only"));

        assertThat(value(WELCOME)).isEmpty();
        assertThat(serverName()).isEqualTo("Other name");
        assertThat(platform.snapshots.snapshots.get("cc-only").mods()).containsExactly("controlcore");
    }

    @Test
    void playersPreferencesAreRestoredWhenIncluded() {
        run("cc-snapshot-save", Map.of("name", "prefs", "parts", "players"));
        run("cc-pref", Map.of("id", "controlcore:show-welcome-message", "value", "false"));
        assertThat(reply("cc-snapshot-diff", Map.of("name", "prefs"))).contains("Admin");

        run("cc-snapshot-restore", Map.of("name", "prefs"));

        assertThat(ControlCoreApi.get()
                        .settings()
                        .read(SettingId.of("controlcore:show-welcome-message"), Optional.of(admin.id()))
                        .value()
                        .overridden())
                .isFalse();
    }

    @Test
    void saveFirstKeepsTheCurrentState() {
        run("cc-snapshot-save", Map.of("name", "a", "parts", "settings+players"));
        run("cc-set", Map.of("id", WELCOME, "value", "Now"));

        assertThat(reply("cc-snapshot-restore-safe", Map.of("name", "a"))).contains("before-a");

        assertThat(platform.snapshots.snapshots.get("before-a").settings()).containsEntry(WELCOME, "Now");
        assertThat(value(WELCOME)).isEmpty();
    }

    @Test
    void badInputIsRefused() {
        assertThat(run("cc-snapshot-save", Map.of("name", "no spaces!")).error().code())
                .isEqualTo("Snapshots.InvalidName");
        assertThat(run("cc-snapshot-save", Map.of("name", "x", "parts", "weather"))
                        .error()
                        .code())
                .isEqualTo("Snapshots.UnknownPart");
        assertThat(run("cc-snapshot-restore", Map.of("name", "missing")).error().code())
                .isEqualTo("Snapshots.Unknown");
        run("cc-snapshot-save", Map.of("name", "gone"));
        assertThat(run("cc-snapshot-delete", Map.of("name", "gone")).isSuccess())
                .isTrue();
        assertThat(reply("cc-snapshots", Map.of())).contains("No snapshots");
    }

    @Test
    void onlyAdminsMayUseSnapshots() {
        platform.deniedNodes.add("settings");
        platform.deniedNodes.add("admin");
        platform.deniedNodes.add("snapshots");

        assertThat(run("cc-snapshot-save", Map.of("name", "x")).error().code()).isEqualTo("Snapshots.Forbidden");
        assertThat(run("cc-snapshots", Map.of()).error().code()).isEqualTo("Snapshots.Forbidden");
    }

    private net.ofatech.controlcore.domain.result.Result<net.ofatech.controlcore.domain.ports.command.Reply> run(
            String command, Map<String, Object> args) {
        return platform.commands.run(command, admin, args);
    }

    private String reply(String command, Map<String, Object> args) {
        var result = run(command, args);
        assertThat(result.isSuccess()).as(command + " " + args + ": " + result).isTrue();
        return result.value().text().orElse("");
    }

    private String serverName() {
        return platform.serverConfig.entry("ServerName").orElseThrow().value();
    }

    private String value(String id) {
        return ControlCoreApi.get()
                .settings()
                .read(SettingId.of(id), Optional.empty())
                .value()
                .value();
    }
}
