package net.ofatech.controlcore.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.fakes.RecordingLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonSettingStoreTest {

    private static final SettingId ENABLED = SettingId.of("controlcore:control-panel-enabled");

    @TempDir
    Path folder;

    private final RecordingLog log = new RecordingLog();

    @Test
    void valuesSurviveARestart() throws IOException {
        Path file = folder.resolve("SettingValues.json");
        var store = new JsonSettingStore(file, log);
        store.put(ENABLED, "false");
        store.put(SettingId.of("homes:motd"), "Hi <b>all</b>");

        store.flush();
        var reloaded = new JsonSettingStore(file, log);

        assertThat(reloaded.get(ENABLED)).contains("false");
        assertThat(reloaded.all()).hasSize(2);
        assertThat(Files.readString(file)).contains("\"controlcore:control-panel-enabled\": \"false\"", "<b>");
    }

    @Test
    void removedValuesAreWrittenOut() {
        Path file = folder.resolve("SettingValues.json");
        var store = new JsonSettingStore(file, log);
        store.put(ENABLED, "false");
        store.flush();

        store.remove(ENABLED);
        store.flush();

        assertThat(new JsonSettingStore(file, log).get(ENABLED)).isEmpty();
    }

    @Test
    void badEntriesAndCorruptFilesDoNotStopTheServer() throws IOException {
        Path file = folder.resolve("SettingValues.json");
        Files.writeString(file, "{\"not an id\": \"1\", \"controlcore:control-panel-enabled\": \"true\"}");
        assertThat(new JsonSettingStore(file, log).all()).containsOnlyKeys(ENABLED);
        assertThat(log.contains("Ignoring 'not an id'")).isTrue();

        Files.writeString(file, "{ broken");
        assertThat(new JsonSettingStore(file, log).all()).isEmpty();
        assertThat(log.contains("Could not read settings")).isTrue();
    }
}
