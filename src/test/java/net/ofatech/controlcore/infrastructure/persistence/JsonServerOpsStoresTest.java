package net.ofatech.controlcore.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.automation.Announcements;
import net.ofatech.controlcore.domain.model.server.MaintenanceState;
import net.ofatech.controlcore.fakes.RecordingLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonServerOpsStoresTest {

    @TempDir
    Path folder;

    @Test
    void announcementsReadBackTheSame() {
        Announcements saved = new Announcements(
                true,
                7,
                Announcements.Order.RANDOM,
                Announcements.Channel.WARNING,
                3,
                List.of(
                        new Announcements.Item("1", "Vote {online}", true),
                        new Announcements.Item("4", "Rules", false)));

        assertThat(new JsonAnnouncementStore(folder.resolve("a.json"), new RecordingLog()).save(saved))
                .isTrue();

        assertThat(new JsonAnnouncementStore(folder.resolve("a.json"), new RecordingLog()).load())
                .isEqualTo(saved);
        assertThat(new JsonAnnouncementStore(folder.resolve("missing.json"), new RecordingLog()).load())
                .isEqualTo(Announcements.DEFAULT);
    }

    @Test
    void maintenanceReadsBackTheSame() {
        MaintenanceState saved = new MaintenanceState(
                true,
                "Back soon",
                "Closed",
                Optional.of(Instant.parse("2026-11-04T10:00:00Z")),
                Optional.of(Instant.parse("2026-11-04T11:00:00Z")),
                Optional.of("Welcome"));

        assertThat(new JsonMaintenanceStore(folder.resolve("m.json"), new RecordingLog()).save(saved))
                .isTrue();

        assertThat(new JsonMaintenanceStore(folder.resolve("m.json"), new RecordingLog()).load())
                .isEqualTo(saved);
        assertThat(new JsonMaintenanceStore(folder.resolve("missing.json"), new RecordingLog()).load())
                .isEqualTo(MaintenanceState.OFF);
    }
}
