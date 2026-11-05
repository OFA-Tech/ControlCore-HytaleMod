package net.ofatech.controlcore.application.serverconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BackupDirectoryTest {

    private static final Path UNIVERSE = Path.of("universe");

    @ParameterizedTest
    @ValueSource(strings = {"universe", "universe/backups", "./universe", "."})
    void theBackupDirectoryMayNotBeTheUniverseOrInsideIt(String value) {
        assertThat(BackupDirectory.allowed(value, UNIVERSE)).as(value).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"backups", "backups/nightly", "./backups"})
    void ordinaryFoldersAreAccepted(String value) {
        assertThat(BackupDirectory.allowed(value, UNIVERSE)).as(value).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"\\\\host\\share", "//host/share", "..", "backups/../..", "a:b", "backups:stream", "\\x", ""})
    void networkParentStreamAndEmptyFoldersAreRefused(String value) {
        assertThat(BackupDirectory.allowed(value, UNIVERSE)).as(value).isFalse();
    }

    @Test
    void anAbsoluteLocalFolderIsAcceptedButNotADriveRootOrTheAbsoluteUniverse() {
        Path server = Path.of("").toAbsolutePath();
        Path outside = server.getRoot().resolve("ControlCoreBackups");

        assertThat(BackupDirectory.allowed(outside.toString(), UNIVERSE)).isTrue();
        assertThat(BackupDirectory.allowed(server.getRoot().toString(), UNIVERSE))
                .isFalse();
        assertThat(BackupDirectory.allowed(server.resolve("universe/b").toString(), UNIVERSE))
                .isFalse();
    }
}
