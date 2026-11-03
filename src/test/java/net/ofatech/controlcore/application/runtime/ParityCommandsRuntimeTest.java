package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.HistoryApi;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.automation.Rule;
import net.ofatech.controlcore.domain.model.history.HistoryView;
import net.ofatech.controlcore.domain.model.hud.Overlay;
import net.ofatech.controlcore.domain.model.mods.ModDependency;
import net.ofatech.controlcore.domain.model.mods.ModInfo;
import net.ofatech.controlcore.domain.model.settings.DefaultsView;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.mods.ModCatalog;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParityCommandsRuntimeTest extends RuntimeTestBase {

    private PlayerInfo admin;
    private PlayerInfo builder;

    @BeforeEach
    void startUp() {
        platform.modCatalog.add("Test:Base", false);
        platform.modCatalog.add(
                "Test:Addon", false, new ModDependency("Test:Base", "", false, ModDependency.Status.OK));
        platform.modCatalog.files.put("Test:Base", List.of(new ModCatalog.ConfigFile("config.json", List.of("{}"))));
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
        builder = platform.players.join("Builder");
        platform.deniedNodesFor.put(builder.id(), List.of("settings", "admin"));
        platform.connectedPublisher.publish(new PlayerJoined(admin));
        platform.connectedPublisher.publish(new PlayerJoined(builder));
    }

    private static String text(Result<Reply> result) {
        assertThat(result.isSuccess()).as("%s", result).isTrue();
        return result.value().text().orElseThrow();
    }

    private static String code(Result<Reply> result) {
        assertThat(result.isFailure()).as("%s", result).isTrue();
        return result.error().code();
    }

    private Result<Reply> console(String name, Map<String, Object> args) {
        return platform.commands.runAsConsole(name, args);
    }

    @Test
    void modsCanBeInspectedAndTurnedOffOnlyWithConfirmationWhenOthersFollow() {
        assertThat(text(console("cc-mod-info", Map.of("mod", "Base"))))
                .contains("Base", "Test:Base", "Requires", "Needed by", "Addon", "Hytale");

        assertThat(text(console("cc-mod-off", Map.of("mod", "Test:Base", "confirm", "check"))))
                .contains("Nothing changed", "Base", "Addon", "/cc-mod-off Test:Base all");
        assertThat(text(console("cc-mod-off", Map.of("mod", "Test:Addon", "confirm", "check"))))
                .contains("Nothing changed", "no other mod");
        assertThat(platform.modCatalog.mods).allMatch(ModInfo::enabledAfterRestart);

        assertThat(text(console("cc-mod-off", Map.of("mod", "Test:Base", "confirm", "all"))))
                .contains("Addon");
        assertThat(platform.modCatalog.mods).noneMatch(ModInfo::enabledAfterRestart);

        assertThat(text(console("cc-mod-on", Map.of("mod", "Test:Addon", "confirm", "all"))))
                .contains("Base");
        assertThat(text(console("cc-mod-configs", Map.of("mod", "Test:Base")))).contains("config.json", "{}");
        assertThat(code(console("cc-mod-on", Map.of("mod", "Test:Base", "confirm", "yes"))))
                .isEqualTo("Mods.BadChoice");

        assertThat(code(platform.commands.run("cc-mod-off", builder, Map.of("mod", "Test:Addon", "confirm", "check"))))
                .isEqualTo("Mods.Forbidden");
        assertThat(code(platform.commands.run("cc-mod-configs", builder, Map.of("mod", "Test:Base"))))
                .isEqualTo("Mods.ConfigForbidden");
        assertThat(code(console("cc-mod-info", Map.of("mod", "Nope")))).isEqualTo("Mods.Unknown");
    }

    @Test
    void turningAModToTheStateItIsAlreadyInSaysSoAndRecordsNothing() {
        assertThat(text(console("cc-mod-on", Map.of("mod", "Test:Base", "confirm", "check"))))
                .isEqualTo("Base is already on after the next restart.");
        assertThat(text(console("cc-mod-on", Map.of("mod", "Test:Base", "confirm", "all"))))
                .isEqualTo("Base is already on after the next restart.");
        text(console("cc-mod-off", Map.of("mod", "Test:Addon", "confirm", "all")));
        assertThat(text(console("cc-mod-off", Map.of("mod", "Test:Addon", "confirm", "all"))))
                .isEqualTo("Addon is already off after the next restart.");
        assertThat(text(console("cc-mod-off", Map.of("mod", "Test:Addon", "confirm", "check"))))
                .isEqualTo("Addon is already off after the next restart.");

        assertThat(ControlCoreApi.get().history().list(Optional.of(HistoryApi.Kind.MOD), "", 0))
                .hasSize(1);
    }

    @Test
    void aModThatCantChangeHereIsNamedTheRightWayRound() {
        platform.modCatalog.add("OFATech:ControlCore", false);
        platform.modCatalog.setEnabled("OFATech:ControlCore", false);
        platform.modCatalog.add(
                "Test:Needy", false, new ModDependency("OFATech:ControlCore", "", false, ModDependency.Status.OK));
        platform.modCatalog.setEnabled("Test:Needy", false);
        Result<Reply> on = console("cc-mod-on", Map.of("mod", "Test:Needy", "confirm", "check"));
        assertThat(code(on)).isEqualTo("Mods.Required");
        assertThat(on.error().message().fallbackText())
                .isEqualTo("Needy needs ControlCore, which can't be turned on here.");

        platform.modCatalog.setEnabled("OFATech:ControlCore", true);
        platform.modCatalog.add("Test:Lib", false);
        platform.modCatalog.mods.replaceAll(mod -> mod.id().equals("OFATech:ControlCore")
                ? mod.withDependencies(List.of(new ModDependency("Test:Lib", "", false, ModDependency.Status.OK)))
                : mod);
        Result<Reply> off = console("cc-mod-off", Map.of("mod", "Test:Lib", "confirm", "check"));
        assertThat(code(off)).isEqualTo("Mods.Required");
        assertThat(off.error().message().fallbackText())
                .isEqualTo("Lib is needed by ControlCore, which can't be turned off here.");
    }

    @Test
    void rulesAreCreatedAndEditedWithThePanelsValidation() {
        String added = text(
                console(
                        "cc-rule-add",
                        Map.of(
                                "rule",
                                "name=Hello; when=join; if=has-permission:vip; do=message-player:Hi {player}; do=toast-player:success:Hey; cooldown=30")));
        assertThat(added).contains("Hello", "hello");
        Rule rule = platform.automationRules.all().getFirst();
        assertThat(rule.actions()).hasSize(2);
        assertThat(rule.cooldownSeconds()).isEqualTo(30);
        assertThat(rule.enabled()).isTrue();

        text(console("cc-rule-edit", Map.of("id", "hello", "changes", "cooldown=60; on=no")));
        Rule edited = platform.automationRules.all().getFirst();
        assertThat(edited.cooldownSeconds()).isEqualTo(60);
        assertThat(edited.enabled()).isFalse();
        assertThat(edited.actions()).hasSize(2);
        assertThat(edited.conditions()).hasSize(1);

        assertThat(code(console(
                        "cc-rule-add", Map.of("rule", "name=Tick; when=every-minutes:5; do=message-player:hi"))))
                .isEqualTo("Automation.NeedsPlayer");
        assertThat(code(console("cc-rule-add", Map.of("rule", "name=Cron; when=cron:nope; do=message-all:hi"))))
                .isEqualTo("Automation.InvalidTriggerValue");
        assertThat(code(console("cc-rule-add", Map.of("rule", "name=X; when=join; colour=red"))))
                .isEqualTo("Automation.BadField");
        assertThat(code(console("cc-rule-add", Map.of("rule", "name=X; when=join; do=explode:now"))))
                .isEqualTo("Automation.UnknownAction");
        assertThat(code(console("cc-rule-add", Map.of("rule", "name=X; when=join"))))
                .isEqualTo("Automation.NoAction");
        assertThat(code(console("cc-rule-edit", Map.of("id", "nope", "changes", "on=yes"))))
                .isEqualTo("Automation.Unknown");
        assertThat(code(platform.commands.run(
                        "cc-rule-add", builder, Map.of("rule", "name=Mine; when=join; do=message-all:hi"))))
                .isEqualTo("Automation.Forbidden");
        assertThat(platform.automationRules.all()).hasSize(1);
    }

    @Test
    void ruleToastsAndWarningsWithNoTextSayWhereItIsMissing() {
        Result<Reply> toast = console("cc-rule-add", Map.of("rule", "name=T; when=join; do=toast-all:success:Title|"));
        assertThat(code(toast)).isEqualTo("Notify.EmptyText");
        assertThat(toast.error().message().fallbackText()).isEqualTo("Write the message after 'success:Title|'.");

        Result<Reply> warning = console("cc-rule-add", Map.of("rule", "name=W; when=join; do=warning-all:error:|5"));
        assertThat(code(warning)).isEqualTo("Notify.EmptyText");
        assertThat(warning.error().message().fallbackText()).isEqualTo("Write the message after 'error:'.");

        text(console("cc-rule-add", Map.of("rule", "name=Ok; when=join; do=warning-all:error:Closing|5")));
        assertThat(platform.automationRules.all()).hasSize(1);
    }

    @Test
    void onlyAdminsChangeAProfileOnceItIsOne() {
        text(platform.commands.run("cc-snapshot-save", admin, Map.of("name", "weekend", "parts", "config")));

        assertThat(text(platform.commands.run("cc-profile-mark", builder, Map.of("snapshot", "weekend"))))
                .contains("weekend");
        assertThat(platform.snapshots.find("weekend").orElseThrow().profile()).isTrue();
        assertThat(code(platform.commands.run("cc-profile-unmark", builder, Map.of("profile", "weekend"))))
                .isEqualTo("Snapshots.ProfileReplace");

        assertThat(text(console("cc-profile-unmark", Map.of("profile", "weekend"))))
                .contains("weekend");
        assertThat(platform.snapshots.find("weekend").orElseThrow().profile()).isFalse();
    }

    @Test
    void configKeysAreListedPerWorldAndFiltered() {
        assertThat(text(console("cc-configs", Map.of())))
                .contains("server:server-name", "LIVE", "RESTART", "READ-ONLY")
                .doesNotContain("world:");
        assertThat(text(console("cc-configs", Map.of("world", "default", "search", "pvp"))))
                .contains("world:default.is-pvp-enabled")
                .doesNotContain("server:");
        assertThat(code(console("cc-configs", Map.of("world", "moon")))).isEqualTo("ServerConfig.UnknownWorld");
        assertThat(code(platform.commands.run("cc-configs", builder, Map.of()))).isEqualTo("ServerConfig.NoneVisible");
    }

    @Test
    void changesToAHiddenModAreLeftOutOfTheHistoryOfThoseWhoCantSeeIt() {
        text(console("cc-mod-off", Map.of("mod", "Test:Base", "confirm", "all")));
        text(console("cc-set", Map.of("id", "controlcore:mods-hidden", "value", "Test:Addon")));
        HistoryApi history = ControlCoreApi.get().history();

        assertThat(history.list(Optional.of(HistoryApi.Kind.MOD), "", 0)).hasSize(2);
        assertThat(history.actingFor(builder.id()).list(Optional.of(HistoryApi.Kind.MOD), "", 0))
                .extracting(HistoryApi.Change::target)
                .containsExactly("Test:Base");
    }

    @Test
    void historyLeavesOutSettingsTheViewerCantSeeBeforeTakingTheNewest() {
        text(console("cc-mod-off", Map.of("mod", "Test:Addon", "confirm", "all")));
        text(console("cc-set", Map.of("id", "server:server-name", "value", "Renamed")));
        text(console("cc-set", Map.of("id", "controlcore:mods-hidden", "value", "Nothing")));
        HistoryApi history = ControlCoreApi.get().history();
        assertThat(history.list(Optional.empty(), "", 0)).hasSize(3);
        long config =
                history.list(Optional.empty(), "server-name", 0).getFirst().id();

        platform.commands.run(COMMAND, builder, Map.of());
        HistoryView view = platform.controlPanel.lastContent.history().orElseThrow();
        assertThat(view.entries(1)).extracting(HistoryView.Entry::target).containsExactly("Test:Addon");
        assertThat(view.total()).isEqualTo(1);
        assertThat(view.targets()).containsExactly("Test:Addon");
        assertThat(code(view.rollback(config).map(Reply::of))).isEqualTo("History.Unknown");
        assertThat(history.actingFor(builder.id()).list(Optional.empty(), "", 0))
                .extracting(HistoryApi.Change::target)
                .containsExactly("Test:Addon");
        assertThat(code(history.actingFor(builder.id()).rollback(config).map(Reply::of)))
                .isEqualTo("History.Unknown");
    }

    @Test
    void restartPendingListsWhatWaits() {
        assertThat(text(console("cc-restart-pending", Map.of()))).contains("Nothing");
        console("cc-set", Map.of("id", "server:backup.enabled", "value", "true"));
        console("cc-mod-off", Map.of("mod", "Test:Addon", "confirm", "all"));

        assertThat(text(console("cc-restart-pending", Map.of()))).contains("Addon");
        assertThat(code(platform.commands.run("cc-restart-pending", builder, Map.of())))
                .isEqualTo("ServerConfig.Forbidden");
    }

    @Test
    void restoringDefaultsNeedsConfirmAndSavesASnapshotFirst() {
        text(console("cc-set", Map.of("id", "server:server-name", "value", "Renamed")));

        assertThat(text(console(
                        "cc-restore-defaults", Map.of("section", "server-configs", "step", "check", "search", "name"))))
                .contains("1 row(s)", "snapshot");
        assertThat(code(console("cc-restore-defaults", Map.of("section", "server-configs", "step", "now"))))
                .isEqualTo("Defaults.Confirm");
        assertThat(text(console("cc-get", Map.of("id", "server:server-name")))).contains("Renamed");

        assertThat(text(console(
                        "cc-restore-defaults",
                        Map.of("section", "server-configs", "step", "confirm", "search", "name"))))
                .contains("1 row(s)");
        assertThat(text(console("cc-get", Map.of("id", "server:server-name")))).doesNotContain("Renamed");
        assertThat(platform.snapshots.all())
                .anyMatch(snapshot -> snapshot.name().contains("defaults"));

        assertThat(code(console("cc-restore-defaults", Map.of("section", "prefs", "step", "confirm"))))
                .isEqualTo("Commands.PlayersOnly");
        assertThat(code(platform.commands.run(
                        "cc-restore-defaults", builder, Map.of("section", "permissions", "step", "check"))))
                .isEqualTo("Defaults.Forbidden");
        assertThat(code(console("cc-restore-defaults", Map.of("section", "everything", "step", "check"))))
                .isEqualTo("Defaults.UnknownSection");
    }

    @Test
    void thePanelRestoresTheSameWayThroughItsDefaultsView() {
        platform.commands.run("cc-pref", builder, Map.of("id", "controlcore:panel-start-tab", "value", "mod settings"));
        platform.commands.run("prefs", builder, Map.of());
        DefaultsView defaults = platform.controlPanel.lastContent.defaults();

        assertThat(defaults.count(DefaultsView.Section.PREFS, id -> true)).isEqualTo(1);
        assertThat(defaults.savesFirst(DefaultsView.Section.PREFS)).isFalse();
        assertThat(defaults.restore(DefaultsView.Section.PREFS, id -> true).value())
                .contains("1 row(s)");
        assertThat(defaults.count(DefaultsView.Section.PREFS, id -> true)).isZero();
    }

    @Test
    void hudToastsAndWarningsFromCommands() {
        assertThat(code(console("cc-hud", Map.of("action", "hide")))).isEqualTo("Commands.PlayersOnly");
        text(platform.commands.run("cc-hud", admin, Map.of("action", "toggle")));
        assertThat(ControlCoreApi.get().huds().isHidden(admin.id())).isTrue();
        text(platform.commands.run("cc-hud", admin, Map.of("action", "show")));
        assertThat(ControlCoreApi.get().huds().isHidden(admin.id())).isFalse();

        assertThat(text(console("cc-toast", Map.of("target", "Builder", "style", "success", "text", "Hi|there"))))
                .contains("Builder");
        assertThat(overlay(builder).toasts()).extracting(Overlay.Line::text).containsExactly("there");
        assertThat(code(console("cc-toast", Map.of("target", "everyone", "style", "loud", "text", "x"))))
                .isEqualTo("Notify.BadStyle");

        text(console("cc-warn", Map.of("target", "everyone", "seconds", 0, "text", "warning: Restart soon")));
        assertThat(overlay(builder).warnings()).extracting(Overlay.Line::text).containsExactly("Restart soon");
        text(console("cc-warn-clear", Map.of("target", "everyone")));
        assertThat(overlay(builder).warnings()).isEmpty();
        text(console("cc-warn", Map.of("target", "admins", "seconds", 30, "text", "Only you")));
        assertThat(overlay(admin).warnings()).extracting(Overlay.Line::text).containsExactly("Only you");
        assertThat(overlay(builder).warnings()).isEmpty();

        assertThat(code(platform.commands.run(
                        "cc-toast", builder, Map.of("target", "everyone", "style", "info", "text", "x"))))
                .isEqualTo("Notify.Forbidden");
        assertThat(code(console("cc-warn", Map.of("target", "Nobody", "seconds", 0, "text", "x"))))
                .isEqualTo("Notify.NotOnline");
        assertThat(code(console("cc-warn", Map.of("target", "everyone", "seconds", -1, "text", "x"))))
                .isEqualTo("Notify.InvalidSeconds");
    }

    @Test
    void clearingAWarningSaysWhatIsLeftAndEmptyTextsAreRefused() {
        assertThat(code(console("cc-warn-clear", Map.of("target", "Builder")))).isEqualTo("Notify.NothingToClear");

        text(console("cc-warn", Map.of("target", "everyone", "seconds", 0, "text", "For all")));
        text(console("cc-warn", Map.of("target", "Builder", "seconds", 0, "text", "For you")));
        assertThat(text(console("cc-warn-clear", Map.of("target", "Builder")))).contains("/cc-warn-clear everyone");
        assertThat(console("cc-warn-clear", Map.of("target", "Builder")).error().description())
                .contains("/cc-warn-clear everyone");
        text(console("cc-warn-clear", Map.of("target", "everyone")));
        assertThat(code(console("cc-warn-clear", Map.of("target", "everyone")))).isEqualTo("Notify.NothingToClear");

        assertThat(console("cc-toast", Map.of("target", "Builder", "style", "info", "text", "Title|"))
                        .error()
                        .description())
                .contains("after 'Title|'");
        assertThat(console("cc-warn", Map.of("target", "Builder", "seconds", 0, "text", "warning:"))
                        .error()
                        .description())
                .contains("after 'warning:'");
        assertThat(code(console("cc-toast", Map.of("target", "Builder", "style", "info", "text", "x".repeat(201)))))
                .isEqualTo("Notify.InvalidText");
    }

    private Overlay overlay(PlayerInfo player) {
        return platform.overlay.shown.get(player.id());
    }
}
