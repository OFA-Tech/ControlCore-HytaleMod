package net.ofatech.controlcore.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.ofatech.controlcore.domain.model.automation.ActionKind;
import net.ofatech.controlcore.domain.model.automation.Rule;
import net.ofatech.controlcore.domain.model.automation.Trigger;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.fakes.RecordingLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PanelRound3StoresTest {

    @TempDir
    Path folder;

    private final RecordingLog log = new RecordingLog();

    private void blockWrites(Path file) throws IOException {
        Files.createDirectory(file.resolveSibling(file.getFileName() + ".tmp"));
    }

    @Test
    void automationRulesKeepTheSavedListWhenAWriteFails() throws IOException {
        Path file = folder.resolve("AutomationRules.json");
        var store = new JsonAutomationRuleStore(file, log);
        Rule ping = new Rule(
                "ping",
                "Ping",
                true,
                Trigger.JOIN,
                "",
                List.of(),
                List.of(new Rule.Action(ActionKind.MESSAGE_ALL, "p")),
                0);
        assertThat(store.saveAll(List.of(ping))).isTrue();
        blockWrites(file);

        assertThat(store.saveAll(List.of())).isFalse();

        assertThat(store.all()).containsExactly(ping);
    }

    @Test
    void customBindsKeepAnExistingBindWhenRemovingItFails() throws IOException {
        Path file = folder.resolve("CustomKeyBinds.json");
        var store = new JsonCustomKeyBindStore(file, log);
        CustomKeyBind warp = new CustomKeyBind("warp", "CTRL + R", "warp", false);
        assertThat(store.put(warp)).isTrue();
        blockWrites(file);

        assertThat(store.remove("warp")).isFalse();

        assertThat(store.all()).containsExactly(warp);
    }

    @Test
    void permissionRulesKeepAnExistingRuleWhenClearingItFails() throws IOException {
        Path file = folder.resolve("SettingPermissions.json");
        var store = new JsonSettingPermissionStore(file, log);
        assertThat(store.put("mod:chat", SettingPermission.of("chat.staff"))).isTrue();
        blockWrites(file);

        assertThat(store.put("mod:chat", SettingPermission.INHERIT)).isFalse();

        assertThat(store.all()).containsKey("mod:chat");
    }
}
