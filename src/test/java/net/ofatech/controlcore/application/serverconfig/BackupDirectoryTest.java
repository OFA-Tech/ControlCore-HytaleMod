package net.ofatech.controlcore.application.serverconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BackupDirectoryTest {

    private static final Path UNIVERSE = Path.of("universe");
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");

    @TempDir
    Path root;

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
            strings = {
                "\\\\host\\share",
                "//host/share",
                "/\\host\\share",
                "\\/host/share",
                "..",
                "backups/../..",
                "a:b",
                "backups:stream",
                "\\x",
                ""
            })
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

    @Test
    void aNewFolderThatWouldLandInsideTheUniverseIsRefused() throws IOException {
        Path universe = Files.createDirectories(root.resolve("universe"));
        Path link = root.resolve("link");
        try {
            Files.createSymbolicLink(link, universe);
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "symbolic links are not allowed here");
        }

        assertThat(BackupDirectory.allowed(link.resolve("b").toString(), universe))
                .isFalse();
        assertThat(BackupDirectory.allowed(root.resolve("elsewhere/b").toString(), universe))
                .isTrue();
    }

    @Test
    void windowsAliasesOfTheUniverseAreRefusedEvenWhenTheFolderIsNew() throws IOException {
        assumeTrue(WINDOWS);
        Path universe = Files.createDirectories(root.resolve("universe"));

        assertThat(BackupDirectory.allowed(root + "\\universe.\\backups", universe))
                .isFalse();
    }
}
