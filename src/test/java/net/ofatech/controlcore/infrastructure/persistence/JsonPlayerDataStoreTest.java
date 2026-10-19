package net.ofatech.controlcore.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.fakes.RecordingLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonPlayerDataStoreTest {

    @TempDir
    Path folder;

    private final PlayerId player = PlayerId.of(UUID.randomUUID());

    @Test
    void persistsOnFlushAndReloads() {
        Path file = folder.resolve("data/players.json");
        JsonPlayerDataStore store = new JsonPlayerDataStore(file, new RecordingLog());
        store.put(player, "key", "value");
        assertThat(file).doesNotExist();

        store.flush();

        assertThat(file).exists();
        assertThat(new JsonPlayerDataStore(file, new RecordingLog()).get(player, "key"))
                .contains("value");
    }

    @Test
    void startsEmptyWhenTheFileIsCorrupt() throws Exception {
        Path file = folder.resolve("players.json");
        Files.writeString(file, "{ not json");
        RecordingLog log = new RecordingLog();

        JsonPlayerDataStore store = new JsonPlayerDataStore(file, log);

        assertThat(store.get(player, "key")).isEmpty();
        assertThat(log.contains("Could not read player data")).isTrue();
    }
}
