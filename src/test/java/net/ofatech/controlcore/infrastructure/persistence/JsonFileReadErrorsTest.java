package net.ofatech.controlcore.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import net.ofatech.controlcore.fakes.RecordingLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonFileReadErrorsTest {

    @TempDir
    Path folder;

    private final RecordingLog log = new RecordingLog();

    @Test
    void aFileLockedWhileReadingIsNotMovedAsideNorWrittenOver() throws IOException {
        Path file = folder.resolve("SettingValues.json");
        String original = "{\"homes:max\":\"7\"}";
        Files.writeString(file, original, StandardCharsets.UTF_8);

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
                FileLock lock = channel.lock()) {
            assertThat(lock.isValid()).isTrue();
            var store = new JsonSettingStore(file, log);
            store.all();
            store.flush();
        }

        try (var files = Files.list(folder)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("SettingValues.json");
        }
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(original);
    }

    @Test
    void aBrokenFileThatCantBeMovedAsideIsNotWrittenOver() throws IOException {
        Path file = folder.resolve("a".repeat(240) + ".json");
        String original = "{ not json";
        Files.writeString(file, original, StandardCharsets.UTF_8);

        JsonFile json = new JsonFile(file, log, "test data");
        assertThat(json.<Map<String, String>>read(Map.class)).isEmpty();
        assertThat(json.write(Map.of("a", "b"), Map.class)).isFalse();

        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(original);
        assertThat(log.contains("not saved until the file is fixed and the server restarted"))
                .isTrue();
    }
}
