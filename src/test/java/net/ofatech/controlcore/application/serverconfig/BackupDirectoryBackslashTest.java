package net.ofatech.controlcore.application.serverconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BackupDirectoryBackslashTest {

    private static final Path UNIVERSE = Path.of("universe");
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");

    @ParameterizedTest
    @ValueSource(
            strings = {
                "universe\\worlds",
                "universe\\",
                ".\\universe",
                "backups\\..\\universe",
                "backups\\..\\..",
                "\\",
                "\\\\",
                "\\\\?\\C:\\x",
                "//?/C:/x",
                "/\\host",
                "\\/host",
                " \\\\host\\share",
                "C:x",
                "C:\\x:stream",
                "backups\\x:stream",
                "UNIVERSE\\x"
            })
    void backslashBypassesAreRefusedOnEveryOs(String value) {
        if (value.startsWith("UNIVERSE")) {
            assumeTrue(WINDOWS);
        }
        assertThat(BackupDirectory.allowed(value, UNIVERSE)).as(value).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"backups\\nightly", "backups/nightly", "backups\\universe"})
    void relativeFoldersWithEitherSeparatorAreAccepted(String value) {
        assertThat(BackupDirectory.allowed(value, UNIVERSE)).as(value).isTrue();
    }

    @Test
    void linuxAbsoluteFolderIsAccepted() {
        assumeFalse(WINDOWS);
        assertThat(BackupDirectory.allowed("/srv/backups", UNIVERSE)).isTrue();
        assertThat(BackupDirectory.allowed("/", UNIVERSE)).isFalse();
    }

    @Test
    void windowsAbsoluteFolderIsAcceptedButRootsAreNot() {
        assumeTrue(WINDOWS);
        assertThat(BackupDirectory.allowed("D:\\Backups", UNIVERSE)).isTrue();
        assertThat(BackupDirectory.allowed("D:\\Backups\\nightly", UNIVERSE)).isTrue();
        assertThat(BackupDirectory.allowed("C:\\", UNIVERSE)).isFalse();
        assertThat(BackupDirectory.allowed("/srv/backups", UNIVERSE)).isFalse();
    }
}
