package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.server.PendingRestore;
import net.ofatech.controlcore.fakes.FakeServerBackups;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExpiredPlanTest extends RuntimeTestBase {

    @TempDir
    Path root;

    @Test
    void anExpiredPlanThatCouldNotBeClearedIsNotRefreshedWhileRunning() throws IOException {
        Path universe = root.resolve("universe");
        Path folder = root.resolve("backups");
        Files.createDirectories(universe.resolve("worlds/default"));
        Files.writeString(universe.resolve("worlds/default/old.txt"), "old");
        platform.backups.universe = universe;
        platform.backups.directory = Optional.of(folder);
        Files.createDirectories(folder);
        FakeServerBackups.zip(folder.resolve("good.zip"), "worlds/default/new.txt", "new");
        Instant old = platform.clock.now.minus(Duration.ofDays(8));
        platform.pendingRestore.restore = Optional.of(new PendingRestore("good.zip", "Admin", old));
        platform.pendingRestore.failClear = true;
        runtime.setup();
        runtime.start();

        platform.clock.now = platform.clock.now.plus(Duration.ofHours(1));
        platform.scheduler.runAll();

        assertThat(platform.pendingRestore.restore.orElseThrow().seen())
                .as("an expired plan must stay expired, or the next start applies the stale restore")
                .isBefore(platform.clock.now.minus(Duration.ofDays(7)));
    }
}
