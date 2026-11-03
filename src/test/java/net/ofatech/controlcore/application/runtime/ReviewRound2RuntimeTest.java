package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.HistoryApi;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigEntry;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigValueKind;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReviewRound2RuntimeTest extends RuntimeTestBase {

    private static final String START_TAB = "controlcore:panel-start-tab";
    private static final String DAYS = "controlcore:history-days";

    private PlayerInfo admin;
    private PlayerInfo builder;

    @BeforeEach
    void startUp() {
        platform.serverConfig.entries.add(new ConfigEntry(
                "MotdText",
                "General",
                "Message of the day",
                "Shown zyzzyva to everyone",
                ConfigValueKind.TEXT,
                List.of(),
                0,
                0,
                "hi",
                Optional.of("hi"),
                SettingAccess.EDITABLE));
        platform.modCatalog.add("Test:Base", false);
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
        builder = platform.players.join("Builder");
        platform.deniedNodesFor.put(builder.id(), List.of("settings", "admin"));
    }

    private static String text(Result<Reply> result) {
        assertThat(result.isSuccess()).as("%s", result).isTrue();
        return result.value().text().orElse("");
    }

    private Result<Reply> console(String name, Map<String, Object> args) {
        return platform.commands.runAsConsole(name, args);
    }

    @Test
    void ccConfigsSearchFindsAKeyByItsDescriptionLikeThePanel() {
        assertThat(text(console("cc-configs", Map.of("search", "zyzzyva"))))
                .contains("server:motd-text")
                .doesNotContain("server:server-name");
    }

    @Test
    void restoringPermissionsWithASearchMatchesTheSettingsDescription() {
        text(console("cc-perm", Map.of("target", START_TAB, "side", "edit", "node", "staff.tab")));
        text(console("cc-perm", Map.of("target", DAYS, "side", "edit", "node", "staff.days")));

        text(console(
                "cc-restore-defaults", Map.of("section", "permissions", "step", "confirm", "search", "preference")));

        assertThat(platform.settingPermissions.rules)
                .doesNotContainKey("setting:" + START_TAB)
                .containsKey("setting:" + DAYS);
    }

    @Test
    void theWhitelistListStopsAtTwentyAndSaysHowManyMore() {
        for (int i = 0; i < 22; i++) {
            PlayerInfo player = platform.players.join("Listed" + i);
            text(console("cc-whitelist-add", Map.of("player", player.name())));
        }

        String listed = text(console("cc-whitelist", Map.of()));

        assertThat(listed.lines().filter(line -> line.startsWith("- "))).hasSize(20);
        assertThat(listed).contains("22", "2 more");
    }

    @Test
    void historyShowsAHiddenModsNameOnlyToThoseWhoSeeTheMod() {
        text(console("cc-mod-off", Map.of("mod", "Test:Base", "confirm", "check")));
        text(console("cc-set", Map.of("id", "controlcore:mods-hidden", "value", "Test:Base")));
        HistoryApi history = ControlCoreApi.get().history();

        List<String> adminSees = history.actingFor(admin.id()).list(Optional.of(HistoryApi.Kind.MOD), "", 0).stream()
                .map(HistoryApi.Change::what)
                .toList();
        List<String> builderSees =
                history.actingFor(builder.id()).list(Optional.of(HistoryApi.Kind.MOD), "", 0).stream()
                        .map(HistoryApi.Change::what)
                        .toList();

        assertThat(adminSees).hasSize(1).allMatch(what -> what.contains("Base") && !what.contains("Test:Base"));
        assertThat(builderSees).noneMatch(what -> what.contains("Base") && !what.contains("Test:Base"));
    }
}
