package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SnapshotRightsRuntimeBanca9HackerTest extends RuntimeTestBase {

    private PlayerInfo admin;
    private PlayerInfo moderator;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
        moderator = platform.players.join("Mod");
        platform.deniedNodesFor.put(moderator.id(), List.of("settings", "admin"));
    }

    @Test
    void aModeratorCannotUnmarkDeleteOrReplaceAProfile() {
        assertThat(platform.commands
                        .run("cc-snapshot-save", admin, Map.of("name", "lobby", "parts", "config"))
                        .isSuccess())
                .isTrue();
        platform.snapshots.save(platform.snapshots.snapshots.get("lobby").asProfile(true));

        assertThat(platform.commands
                        .run("cc-snapshot-delete", moderator, Map.of("name", "lobby"))
                        .error()
                        .code())
                .isEqualTo("Snapshots.ProfileReplace");
        assertThat(platform.commands
                        .run("cc-snapshot-save", moderator, Map.of("name", "lobby", "parts", "config"))
                        .error()
                        .code())
                .isEqualTo("Snapshots.ProfileReplace");
        assertThat(platform.snapshots.snapshots.get("lobby").profile()).isTrue();
        assertThat(platform.commands
                        .run("cc-snapshot-delete", admin, Map.of("name", "lobby"))
                        .isSuccess())
                .isTrue();
    }
}
