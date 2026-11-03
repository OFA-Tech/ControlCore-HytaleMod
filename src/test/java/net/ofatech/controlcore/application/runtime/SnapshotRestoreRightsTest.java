package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.snapshot.SnapshotsView;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SnapshotRestoreRightsTest extends RuntimeTestBase {

    private static final String WELCOME = "controlcore:welcome-message";
    private static final String DAYS = "controlcore:history-days";

    private PlayerInfo admin;
    private PlayerInfo keeper;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
        keeper = platform.players.join("Keeper");
        platform.deniedNodesFor.put(keeper.id(), new ArrayList<>(List.of("settings", "admin", "staff.secret")));
        ok(as(admin, "cc-perm", Map.of("target", DAYS, "side", "both", "node", "everyone")));
        ok(as(admin, "cc-set", Map.of("id", WELCOME, "value", "Saved hello")));
        ok(as(admin, "cc-set", Map.of("id", DAYS, "value", "30")));
        ok(as(admin, "cc-snapshot-save", Map.of("name", "cfg", "parts", "settings")));
        ok(as(admin, "cc-set", Map.of("id", WELCOME, "value", "Live hello")));
        ok(as(admin, "cc-set", Map.of("id", DAYS, "value", "40")));
        ok(as(admin, "cc-perm", Map.of("target", WELCOME, "side", "both", "node", "staff.secret")));
    }

    @Test
    void theAdminStillSeesAndRestoresEverySetting() {
        SnapshotsView.Preview preview = snapshotsFor(admin).preview("cfg").value();

        assertThat(preview.changes()).isEqualTo(2);
        assertThat(String.join("\n", preview.lines())).contains("Saved hello");
    }

    @Test
    void aSnapshotsPlayerSkipsSettingsTheyMayNotEditAndDoesNotSeeTheirValues() {
        SnapshotsView view = snapshotsFor(keeper);

        SnapshotsView.Preview preview = view.preview("cfg").value();
        String restored = view.restore("cfg", false).value();

        assertThat(preview.changes()).isEqualTo(1);
        assertThat(preview.skipped()).isEqualTo(1);
        assertThat(String.join("\n", preview.lines())).doesNotContain("hello");
        assertThat(restored).doesNotContain("hello");
        assertThat(ok(as(admin, "cc-get", Map.of("id", WELCOME)))).contains("Live hello");
        assertThat(ok(as(admin, "cc-get", Map.of("id", DAYS)))).contains("30");
    }

    private SnapshotsView snapshotsFor(PlayerInfo who) {
        ok(as(who, COMMAND, Map.of()));
        return platform.controlPanel.lastContent.snapshots().orElseThrow();
    }

    private Result<Reply> as(PlayerInfo who, String command, Map<String, Object> args) {
        return platform.commands.run(command, who, args);
    }

    private static String ok(Result<Reply> result) {
        assertThat(result.isSuccess()).as(String.valueOf(result)).isTrue();
        return result.value().text().orElse("");
    }
}
