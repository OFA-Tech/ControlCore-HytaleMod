package net.ofatech.controlcore.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot;
import net.ofatech.controlcore.fakes.RecordingLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonSnapshotStoreTest {

    @TempDir
    Path folder;

    @Test
    void aSavedSnapshotReadsBackTheSameAndFilesCopiedInAppear() throws IOException {
        var store = new JsonSnapshotStore(folder, new RecordingLog());
        Snapshot snapshot = new Snapshot(
                "pvp",
                Instant.parse("2026-10-24T12:00:00Z"),
                "Caio",
                EnumSet.allOf(Snapshot.Part.class),
                Set.of("controlcore"),
                Map.of("server:motd", "Fight!"),
                Map.of("controlcore:welcome-message", "Hi"),
                Map.of("custom:warp", "CTRL + R"),
                List.of(new CustomKeyBind("warp", "CTRL + R", "warp spawn", true)),
                Optional.of(Map.of("custom:warp", "replace")),
                Map.of("setting:server:motd", SettingPermission.of("staff.view", "staff.edit")),
                Map.of("Test:Mod", false),
                Map.of("3a030bb2-462f-4acd-93e2-7d2f76cbba79", Map.of("keybind:controlcore:open-preferences", "F")),
                true,
                false);

        store.save(snapshot);
        Files.copy(folder.resolve("pvp.json"), folder.resolve("copied.json"));
        Files.writeString(folder.resolve("Bad Name.json"), "{}");
        Files.writeString(folder.resolve("broken.json"), "{not json");

        var reloaded = new JsonSnapshotStore(folder, new RecordingLog());
        assertThat(reloaded.find("pvp")).contains(snapshot);
        assertThat(reloaded.all()).extracting(Snapshot::name).containsExactlyInAnyOrder("pvp", "copied");
        assertThat(reloaded.delete("copied")).isTrue();
        assertThat(reloaded.find("copied")).isEmpty();
    }
}
