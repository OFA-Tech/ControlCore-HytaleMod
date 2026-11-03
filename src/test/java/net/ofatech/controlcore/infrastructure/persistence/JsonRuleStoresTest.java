package net.ofatech.controlcore.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.fakes.RecordingLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonRuleStoresTest {

    @TempDir
    Path folder;

    private final RecordingLog log = new RecordingLog();

    @Test
    void customKeyBindsSurviveARestart() {
        Path file = folder.resolve("CustomKeyBinds.json");
        var store = new JsonCustomKeyBindStore(file, log);
        store.put(new CustomKeyBind("warp-spawn", "CTRL + R", "warp spawn {player}", false));
        store.put(new CustomKeyBind("announce", "ALT + 3", "say <b>Restart</b>", true));
        store.remove("warp-spawn");

        var reloaded = new JsonCustomKeyBindStore(file, log);

        assertThat(reloaded.all())
                .containsExactly(new CustomKeyBind("announce", "ALT + 3", "say <b>Restart</b>", true));
    }

    @Test
    void settingPermissionsSurviveARestart() {
        Path file = folder.resolve("SettingPermissions.json");
        var store = new JsonSettingPermissionStore(file, log);
        store.put("setting:homes:max", SettingPermission.of("everyone", "homes.moderator"));
        store.put("mod:chat", SettingPermission.of("chat.staff"));
        store.put("mod:chat", SettingPermission.INHERIT);

        var reloaded = new JsonSettingPermissionStore(file, log);

        assertThat(reloaded.all()).containsOnlyKeys("setting:homes:max");
        assertThat(reloaded.all().get("setting:homes:max").edit()).contains("homes.moderator");
    }

    @Test
    void anUnreadableFileStartsEmptyAndIsLogged() throws IOException {
        Path file = folder.resolve("CustomKeyBinds.json");
        Files.writeString(file, "{ not json");

        var store = new JsonCustomKeyBindStore(file, log);

        assertThat(store.all()).isEmpty();
        assertThat(log.contains("Could not read custom key binds")).isTrue();
        assertMovedAside("CustomKeyBinds.json", "{ not json");
    }

    @Test
    void anUnreadableAutomationFileIsMovedAsideNotOverwritten() throws IOException {
        Path file = folder.resolve("AutomationRules.json");
        Files.writeString(file, "[ broken");

        var store = new JsonAutomationRuleStore(file, log);

        assertThat(store.all()).isEmpty();
        store.saveAll(List.of());
        assertThat(log.contains("Could not read automation rules")).isTrue();
        assertMovedAside("AutomationRules.json", "[ broken");
    }

    @Test
    void aFileWithBrokenTextIsMovedAside() throws IOException {
        Path file = folder.resolve("SettingValues.json");
        Files.write(file, new byte[] {'{', '"', (byte) 0xC3, (byte) 0x28, '"', '}'});

        var store = new JsonSettingStore(file, log);

        assertThat(store.all()).isEmpty();
        try (var files = Files.list(folder)) {
            assertThat(files.filter(p -> p.getFileName().toString().startsWith("SettingValues.json.broken-")))
                    .hasSize(1);
        }
    }

    @Test
    void aFileThatCannotBeOpenedIsNeverWrittenOver() {
        Path file = folder.resolve("SettingValues.json");
        assertThat(file.toFile().mkdir()).isTrue();

        var store = new JsonSettingStore(file, log);
        store.put(SettingId.of("homes:max"), "5");
        store.flush();

        assertThat(file).isDirectory();
        assertThat(folder.toFile().list()).containsExactly("SettingValues.json");
        assertThat(log.contains("left untouched")).isTrue();
    }

    private void assertMovedAside(String name, String original) throws IOException {
        try (var files = Files.list(folder)) {
            assertThat(files.filter(p -> p.getFileName().toString().startsWith(name + ".broken-")))
                    .singleElement()
                    .satisfies(p -> assertThat(p).hasContent(original));
        }
    }
}
