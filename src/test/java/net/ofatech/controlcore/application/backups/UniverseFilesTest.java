package net.ofatech.controlcore.application.backups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.ofatech.controlcore.fakes.FakeServerBackups;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UniverseFilesTest {

    @TempDir
    Path root;

    private Path universe;
    private Path aside;
    private Path backups;

    @BeforeEach
    void folders() throws IOException {
        universe = root.resolve("universe");
        aside = UniverseFiles.asideOf(universe, "2026-01-01_00-00-00");
        backups = Files.createDirectories(root.resolve("backups"));
        Files.createDirectories(universe.resolve("worlds/default"));
        Files.writeString(universe.resolve("worlds/default/old.txt"), "old");
    }

    @Test
    void namesMustBeBackupsInsideTheBackupFolder() throws IOException {
        Files.writeString(backups.resolve("a.zip"), "x");
        Files.createDirectories(backups.resolve("archive"));
        Files.writeString(backups.resolve("archive/b.zip"), "x");
        Files.writeString(root.resolve("outside.zip"), "x");

        assertThat(UniverseFiles.resolve(backups, "a.zip"))
                .contains(backups.toAbsolutePath().resolve("a.zip"));
        assertThat(UniverseFiles.resolve(backups, "archive/b.zip")).isPresent();
        assertThat(UniverseFiles.resolve(backups, "../outside.zip")).isEmpty();
        assertThat(UniverseFiles.resolve(backups, "archive/../../outside.zip")).isEmpty();
        assertThat(UniverseFiles.resolve(backups, root.resolve("outside.zip").toString()))
                .isEmpty();
        assertThat(UniverseFiles.resolve(backups, "missing.zip")).isEmpty();
        assertThat(UniverseFiles.resolve(backups, "a.txt")).isEmpty();
        assertThat(UniverseFiles.resolve(backups, "other/a.zip")).isEmpty();
        assertThat(UniverseFiles.resolve(backups, null)).isEmpty();
    }

    @Test
    void aRestoreMovesTheUniverseAsideAndUnpacksTheBackup() throws IOException {
        Path zip = backups.resolve("b.zip");
        FakeServerBackups.zip(zip, "worlds/default/new.txt", "new", "players/p.json", "{}");

        UniverseFiles.restore(zip, universe, aside);

        assertThat(universe.resolve("worlds/default/new.txt")).hasContent("new");
        assertThat(universe.resolve("players/p.json")).exists();
        assertThat(universe.resolve("worlds/default/old.txt")).doesNotExist();
        assertThat(aside.resolve("worlds/default/old.txt")).hasContent("old");
    }

    @Test
    void anEntryOutsideTheUniverseIsRefusedBeforeAnythingMoves() throws IOException {
        Path zip = backups.resolve("evil.zip");
        FakeServerBackups.zip(zip, "worlds/ok.txt", "ok", "../evil.txt", "evil");

        assertThatThrownBy(() -> UniverseFiles.restore(zip, universe, aside)).isInstanceOf(IOException.class);

        assertThat(universe.resolve("worlds/default/old.txt")).hasContent("old");
        assertThat(aside).doesNotExist();
        assertThat(root.resolve("evil.txt")).doesNotExist();
    }

    @Test
    void aFailedUnpackPutsTheOldUniverseBack() throws IOException {
        Path zip = backups.resolve("broken.zip");
        FakeServerBackups.zip(zip, "worlds", "a file", "worlds/default/x.txt", "x");

        assertThatThrownBy(() -> UniverseFiles.restore(zip, universe, aside)).isInstanceOf(IOException.class);

        assertThat(universe.resolve("worlds/default/old.txt")).hasContent("old");
        assertThat(aside).doesNotExist();
    }

    @Test
    void anExistingAsideFolderStopsTheRestore() throws IOException {
        Path zip = backups.resolve("b.zip");
        FakeServerBackups.zip(zip, "worlds/default/new.txt", "new");
        Files.createDirectories(aside);

        assertThatThrownBy(() -> UniverseFiles.restore(zip, universe, aside)).isInstanceOf(IOException.class);

        assertThat(universe.resolve("worlds/default/old.txt")).hasContent("old");
    }

    @ParameterizedTest
    @ValueSource(strings = {"..\\evil.txt", "/evil.txt", "worlds/../../evil.txt", "C:/evil.txt", "./"})
    void escapingEntriesAreRefusedAndTheUniverseKept(String entry) throws IOException {
        Path zip = root.resolve("evil.zip");
        FakeServerBackups.zip(zip, "ok.txt", "ok", entry, "evil");

        assertThatThrownBy(() -> UniverseFiles.restore(zip, universe, aside)).isInstanceOf(IOException.class);

        assertThat(universe.resolve("worlds/default/old.txt")).hasContent("old");
        assertThat(root.resolve("evil.txt")).doesNotExist();
        assertThat(aside).doesNotExist();
    }

    @Test
    void aZipWithADuplicateEntryRollsBack() throws IOException {
        Path zip = root.resolve("dup.zip");
        FakeServerBackups.zip(zip, "a/x.txt", "1", "a\\x.txt", "2");

        try {
            UniverseFiles.restore(zip, universe, aside);
            assertThat(universe.resolve("a/x.txt")).exists();
        } catch (IOException e) {
            assertThat(universe.resolve("worlds/default/old.txt")).hasContent("old");
            assertThat(aside).doesNotExist();
        }
    }

    @Test
    void aZipWithOneTopFolderIsUnpackedWithoutThatFolder() throws IOException {
        Path zip = backups.resolve("hand.zip");
        FakeServerBackups.zip(zip, "universe/worlds/default/new.txt", "new", "universe/players/p.json", "{}");

        UniverseFiles.restore(zip, universe, aside);

        assertThat(universe.resolve("worlds/default/new.txt")).hasContent("new");
        assertThat(universe.resolve("players/p.json")).exists();
        assertThat(universe.resolve("universe")).doesNotExist();
    }

    @Test
    void aRealHytaleLayoutIsUnpackedAsIs() throws IOException {
        Path zip = backups.resolve("real.zip");
        FakeServerBackups.zip(
                zip,
                "events.json",
                "[]",
                "players/p.json",
                "{}",
                "resources/r.json",
                "{}",
                "worlds/default/config.json",
                "{}");

        UniverseFiles.restore(zip, universe, aside);

        assertThat(universe.resolve("events.json")).exists();
        assertThat(universe.resolve("worlds/default/config.json")).exists();
    }

    @Test
    void aZipWithoutAWorldsFolderIsRefusedBeforeAnythingMoves() throws IOException {
        Path zip = backups.resolve("other.zip");
        FakeServerBackups.zip(zip, "stuff/players/p.json", "{}");

        assertThatThrownBy(() -> UniverseFiles.restore(zip, universe, aside))
                .isInstanceOf(UniverseFiles.NotUniverse.class);

        assertThat(universe.resolve("worlds/default/old.txt")).hasContent("old");
        assertThat(aside).doesNotExist();
    }

    @Test
    void theUnpackedSizeIsTheSumOfTheEntries() throws IOException {
        Path zip = backups.resolve("b.zip");
        FakeServerBackups.zip(zip, "worlds/a.txt", "12345", "worlds/b.txt", "123");

        assertThat(UniverseFiles.layout(zip, universe).unpackedSize()).isEqualTo(8);
    }

    @Test
    void linksInTheBackupFolderAreRefused() throws IOException {
        Path outside = Files.writeString(root.resolve("outside.zip"), "x");
        Path link = backups.resolve("link.zip");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("symbolic links are not allowed here: " + e.getMessage());
        }

        assertThat(UniverseFiles.resolve(backups, "link.zip")).isEmpty();
    }

    @Test
    void anEmptyOrBrokenZipIsRefused() throws IOException {
        Path empty = backups.resolve("empty.zip");
        FakeServerBackups.zip(empty);
        Path junk = backups.resolve("junk.zip");
        Files.writeString(junk, "not a zip");

        assertThatThrownBy(() -> UniverseFiles.restore(empty, universe, aside)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> UniverseFiles.restore(junk, universe, aside)).isInstanceOf(IOException.class);
        assertThat(universe.resolve("worlds/default/old.txt")).hasContent("old");
    }
}
