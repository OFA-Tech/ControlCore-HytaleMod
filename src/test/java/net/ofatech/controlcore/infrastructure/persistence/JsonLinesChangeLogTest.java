package net.ofatech.controlcore.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.fakes.RecordingLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonLinesChangeLogTest {

    @TempDir
    Path folder;

    @Test
    void recordsSurviveARestartInMonthlyFiles() {
        var log = new JsonLinesChangeLog(folder, new RecordingLog());
        PlayerId caio = PlayerId.of(UUID.randomUUID());
        log.append(record(1, "2026-09-30T23:00:00Z", ChangeRecord.Actor.player(caio, "Caio")));
        log.append(record(2, "2026-10-02T10:00:00Z", ChangeRecord.Actor.MOD));

        var reloaded = new JsonLinesChangeLog(folder, new RecordingLog());

        assertThat(folder.resolve("2026-09.jsonl")).exists();
        assertThat(folder.resolve("2026-10.jsonl")).exists();
        assertThat(reloaded.since(Instant.parse("2026-01-01T00:00:00Z")))
                .extracting(ChangeRecord::id)
                .containsExactly(1L, 2L);
        ChangeRecord first =
                reloaded.since(Instant.parse("2026-01-01T00:00:00Z")).getFirst();
        assertThat(first.actor().player()).contains(caio);
        assertThat(first.oldValue()).isEqualTo("a\u001fb");
        assertThat(reloaded.since(Instant.parse("2026-10-01T00:00:00Z")))
                .extracting(ChangeRecord::id)
                .containsExactly(2L);
    }

    @Test
    void oldMonthsAreDeletedAndBrokenLinesSkipped() throws IOException {
        var log = new JsonLinesChangeLog(folder, new RecordingLog());
        log.append(record(1, "2026-08-15T00:00:00Z", ChangeRecord.Actor.MOD));
        log.append(record(2, "2026-10-15T00:00:00Z", ChangeRecord.Actor.MOD));
        Files.writeString(folder.resolve("2026-10.jsonl"), "not json\n", java.nio.file.StandardOpenOption.APPEND);

        log.deleteBefore(Instant.parse("2026-09-10T00:00:00Z"));

        assertThat(folder.resolve("2026-08.jsonl")).doesNotExist();
        assertThat(log.since(Instant.EPOCH)).extracting(ChangeRecord::id).containsExactly(2L);
    }

    private static ChangeRecord record(long id, String at, ChangeRecord.Actor actor) {
        return new ChangeRecord(
                id,
                Instant.parse(at),
                actor,
                ChangeRecord.Source.PANEL,
                ChangeRecord.Kind.CUSTOM_BIND,
                "custom:warp",
                "a\u001fb",
                "",
                false,
                0);
    }
}
