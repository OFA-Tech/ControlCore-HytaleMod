package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSender;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SnapshotRightsRuntimeBanca6Test extends RuntimeTestBase {

    private static final String WELCOME = "controlcore:welcome-message";
    private static final String RULE = "setting:" + WELCOME;

    private PlayerInfo admin;
    private PlayerInfo moderator;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
        moderator = platform.players.join("Mod");
        platform.deniedNodesFor.put(moderator.id(), List.of("settings", "admin"));
    }

    private void savedThenChanged(String name) {
        ok(as(admin, "cc-keybind-add", Map.of("name", "warp", "keys", "ctrl+r", "command", "warp spawn")));
        ok(as(admin, "cc-set", Map.of("id", WELCOME, "value", "Saved")));
        ok(as(admin, "cc-snapshot-save", Map.of("name", name)));
        ok(as(admin, "cc-keybind-remove", Map.of("name", "warp")));
        ok(as(admin, "cc-keybind-add", Map.of("name", "extra", "keys", "ctrl+e", "command", "say extra")));
        ok(as(admin, "cc-perm", Map.of("target", WELCOME, "side", "edit", "node", "staff.motd")));
        ok(as(admin, "cc-set", Map.of("id", WELCOME, "value", "Changed")));
        assertThat(ruleChanged()).isTrue();
    }

    @Test
    void moderatorCannotOverwriteDeleteOrRepurposeASnapshotWithKeysOrPermissions() {
        savedThenChanged("staff");

        assertThat(as(moderator, "cc-snapshot-save", Map.of("name", "staff"))
                        .error()
                        .code())
                .isEqualTo("Snapshots.AdminParts");
        assertThat(as(moderator, "cc-snapshot-delete", Map.of("name", "staff"))
                        .error()
                        .code())
                .isEqualTo("Snapshots.AdminParts");
        assertThat(platform.snapshots.snapshots).containsKey("staff");
        assertThat(as(moderator, "cc-snapshot-save", Map.of("name", "mine"))
                        .error()
                        .code())
                .as("a full snapshot includes keys and permissions")
                .isEqualTo("Snapshots.AdminParts");
        assertThat(ok(as(moderator, "cc-snapshot-save", Map.of("name", "mine", "parts", "config+settings"))))
                .isNotBlank();
        assertThat(ok(as(moderator, "cc-snapshot-delete", Map.of("name", "mine"))))
                .isNotBlank();
    }

    @Test
    void moderatorRestoreLeavesModsAsTheAdminSetThem() {
        platform.modCatalog.add("Someone:Extra", false);
        ok(as(admin, "cc-snapshot-save", Map.of("name", "mods")));
        platform.modCatalog.setEnabled("Someone:Extra", false);

        String restored = ok(as(moderator, "cc-snapshot-restore", Map.of("name", "mods")));

        assertThat(restored).contains("left out");
        assertThat(platform.modCatalog.mods())
                .filteredOn(mod -> mod.id().equals("Someone:Extra"))
                .allMatch(mod -> !mod.enabledAfterRestart());
        ok(as(admin, "cc-snapshot-restore", Map.of("name", "mods")));
        assertThat(platform.modCatalog.mods())
                .filteredOn(mod -> mod.id().equals("Someone:Extra"))
                .allMatch(mod -> mod.enabledAfterRestart());
    }

    @Test
    void moderatorRestoreSkipsKeysAndPermissionsButRestoresTheRest() {
        savedThenChanged("base");

        String restored = ok(as(moderator, "cc-snapshot-restore", Map.of("name", "base")));

        assertThat(restored).contains("0 failed");
        assertThat(value()).isEqualTo("Saved");
        assertThat(binds()).contains("extra").doesNotContain("warp");
        assertThat(ruleChanged()).as("permission rule must stay").isTrue();
    }

    @Test
    void moderatorDiffListsOnlyWhatItMayRestoreAndCountsTheRestAsSkipped() {
        savedThenChanged("base");

        String adminDiff = ok(as(admin, "cc-snapshot-diff", Map.of("name", "base")));
        String modDiff = ok(as(moderator, "cc-snapshot-diff", Map.of("name", "base")));

        assertThat(adminDiff).contains("Changed -> Saved").contains("warp").contains("extra");
        assertThat(adminDiff).doesNotContain("skipped");
        assertThat(modDiff).contains("Changed -> Saved").doesNotContain("warp").doesNotContain("extra");
        assertThat(modDiff)
                .contains("3 change(s) to keys, permissions or mods are left out")
                .doesNotContain("saved value(s) are skipped");
    }

    @Test
    void moderatorSkippedCountMatchesTheChangesAnAdminWouldSee() {
        savedThenChanged("base");
        String adminDiff = ok(as(admin, "cc-snapshot-diff", Map.of("name", "base")));
        String modDiff = ok(as(moderator, "cc-snapshot-diff", Map.of("name", "base")));

        int adminChanges = count(adminDiff);
        int modChanges = count(modDiff);
        assertThat(adminChanges - modChanges).isEqualTo(3);
    }

    @Test
    void adminRestoresEverything() {
        savedThenChanged("base");

        assertThat(ok(as(admin, "cc-snapshot-restore", Map.of("name", "base")))).contains("0 failed");

        assertThat(value()).isEqualTo("Saved");
        assertThat(binds()).containsExactly("warp");
        assertThat(ruleChanged()).isFalse();
    }

    @Test
    void consoleRestoresEverything() {
        savedThenChanged("base");

        String diff = ok(console("cc-snapshot-diff", Map.of("name", "base")));
        assertThat(diff).contains("warp").contains("extra").doesNotContain("skipped");
        assertThat(ok(console("cc-snapshot-restore", Map.of("name", "base")))).contains("0 failed");

        assertThat(value()).isEqualTo("Saved");
        assertThat(binds()).containsExactly("warp");
        assertThat(ruleChanged()).isFalse();
    }

    @Test
    void moderatorSafetySnapshotLeavesOutKeysAndPermissions() {
        savedThenChanged("base");

        ok(as(moderator, "cc-snapshot-restore-safe", Map.of("name", "base")));

        var safety = platform.snapshots.snapshots.values().stream()
                .filter(snapshot -> snapshot.name().startsWith("before-base"))
                .findFirst();
        assertThat(safety).isPresent();
        assertThat(safety.get().parts())
                .doesNotContain(
                        net.ofatech.controlcore.domain.model.snapshot.Snapshot.Part.KEYS,
                        net.ofatech.controlcore.domain.model.snapshot.Snapshot.Part.PERMISSIONS);
        assertThat(binds()).contains("extra").doesNotContain("warp");
    }

    @Test
    void moderatorSwitchingToAProfileSkipsKeysAndPermissions() {
        savedThenChanged("staff");
        platform.snapshots.save(platform.snapshots.snapshots.get("staff").asProfile(true));

        String switched = ok(as(moderator, "cc-profile", Map.of("profile", "staff")));

        assertThat(switched).contains("0 failed");
        assertThat(value()).isEqualTo("Saved");
        assertThat(binds()).contains("extra").doesNotContain("warp");
        assertThat(ruleChanged()).isTrue();
    }

    @Test
    void adminSwitchingToAProfileRestoresKeysAndPermissions() {
        savedThenChanged("staff");
        platform.snapshots.save(platform.snapshots.snapshots.get("staff").asProfile(true));

        ok(as(admin, "cc-profile", Map.of("profile", "staff")));

        assertThat(value()).isEqualTo("Saved");
        assertThat(binds()).containsExactly("warp");
        assertThat(ruleChanged()).isFalse();
    }

    @Test
    void moderatorSwitchSaysWhatWasLeftOutAndTheirStatusMatches() {
        savedThenChanged("staff");
        platform.snapshots.save(platform.snapshots.snapshots.get("staff").asProfile(true));
        String switched = ok(as(moderator, "cc-profile", Map.of("profile", "staff")));

        assertThat(switched).contains("3 change(s) to keys, permissions or mods are left out");
        assertThat(ok(as(moderator, "cc-profiles", Map.of())))
                .contains("Active profile: staff")
                .contains("Everything still matches it");
    }

    @Test
    void moderatorRestoreReplySaysKeysAndPermissionsWereLeftOut() {
        savedThenChanged("base");

        String restored = ok(as(moderator, "cc-snapshot-restore", Map.of("name", "base")));

        assertThat(restored)
                .contains("1 change(s), 0 failed")
                .contains("3 change(s) to keys, permissions or mods are left out");
    }

    @Test
    void onlyAdminOnlyDifferencesTellTheModeratorOnlyAdminsCanRestoreThem() {
        ok(as(admin, "cc-snapshot-save", Map.of("name", "keysonly")));
        ok(as(admin, "cc-keybind-add", Map.of("name", "extra", "keys", "ctrl+e", "command", "say extra")));

        String modDiff = ok(as(moderator, "cc-snapshot-diff", Map.of("name", "keysonly")));

        assertThat(modDiff)
                .contains("only differs in keys, permissions or mods")
                .doesNotContain("left out")
                .doesNotContain("nothing to restore");
    }

    private static int count(String diff) {
        return (int) diff.lines().filter(line -> line.contains(" -> ")).count();
    }

    private boolean ruleChanged() {
        SettingPermission rule = platform.settingPermissions.all().get(RULE);
        return rule != null && !rule.isInherit();
    }

    private List<String> binds() {
        return platform.customKeyBinds.all().stream().map(bind -> bind.name()).toList();
    }

    private String value() {
        return ControlCoreApi.get()
                .settings()
                .read(SettingId.of(WELCOME), Optional.empty())
                .value()
                .value();
    }

    private Result<Reply> as(PlayerInfo who, String command, Map<String, Object> args) {
        return platform.commands.run(command, who, args);
    }

    private Result<Reply> console(String command, Map<String, Object> args) {
        CommandSender sender = CommandSender.console("Console");
        CommandContext context = new CommandContext() {
            @Override
            public CommandSender sender() {
                return sender;
            }

            @Override
            public Optional<Object> arg(String name) {
                return Optional.ofNullable(args.get(name));
            }
        };
        return platform.commands.commands.get(command).handler().handle(context);
    }

    private static String ok(Result<Reply> result) {
        assertThat(result.isSuccess()).as(String.valueOf(result)).isTrue();
        return result.value().text().orElse("");
    }
}
