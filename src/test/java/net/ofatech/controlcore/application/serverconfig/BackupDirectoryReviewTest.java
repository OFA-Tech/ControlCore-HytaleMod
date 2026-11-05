package net.ofatech.controlcore.application.serverconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BackupDirectoryReviewTest {

    private static final Path UNIVERSE = Path.of("universe");
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");

    @ParameterizedTest
    @ValueSource(strings = {"universe2", "  backups  ", "universe-backups", "backups/universe"})
    void lookalikesOfTheUniverseAreFine(String value) {
        assertThat(BackupDirectory.allowed(value, UNIVERSE)).as(value).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"universe/", "universe\\worlds", " universe ", "backups/ .. /x", "/"})
    void universeVariantsAndRootsAreRefused(String value) {
        assertThat(BackupDirectory.allowed(value, UNIVERSE)).as(value).isFalse();
    }

    @Test
    void windowsDriveRelativeRootAndCaseVariantsAreRefused() {
        assumeTrue(WINDOWS);
        assertThat(BackupDirectory.allowed("D:Backups", UNIVERSE)).isFalse();
        assertThat(BackupDirectory.allowed("C:\\", UNIVERSE)).isFalse();
        assertThat(BackupDirectory.allowed("C:/", UNIVERSE)).isFalse();
        assertThat(BackupDirectory.allowed("UNIVERSE", UNIVERSE)).isFalse();
        assertThat(BackupDirectory.allowed("D:/Backups", UNIVERSE)).isTrue();
    }
}
