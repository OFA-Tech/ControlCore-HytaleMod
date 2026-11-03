package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.model.snapshot.ProfilesView;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot;
import net.ofatech.controlcore.domain.model.snapshot.SnapshotsView;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSender;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SnapshotRightsRuntimeTest {

    @Nested
    class SettingRights extends RuntimeTestBase {

        private static final String WELCOME = "controlcore:welcome-message";
        private static final String DAYS = "controlcore:history-days";

        private PlayerInfo admin;
        private PlayerInfo keeper;

        @BeforeEach
        void startUp() {
            runtime.setup();
            runtime.start();
            admin = platform.players.join("Admin");
            keeper = platform.players.join("Keeper");
            platform.deniedNodesFor.put(keeper.id(), new ArrayList<>(List.of("settings", "admin", "staff.secret")));
            ok(as(admin, "cc-perm", Map.of("target", DAYS, "side", "both", "node", "everyone")));
            ok(as(admin, "cc-set", Map.of("id", WELCOME, "value", "Saved hello")));
            ok(as(admin, "cc-set", Map.of("id", DAYS, "value", "30")));
            ok(as(admin, "cc-snapshot-save", Map.of("name", "cfg", "parts", "settings")));
            ok(as(admin, "cc-set", Map.of("id", WELCOME, "value", "Live hello")));
            ok(as(admin, "cc-set", Map.of("id", DAYS, "value", "40")));
            ok(as(admin, "cc-perm", Map.of("target", WELCOME, "side", "both", "node", "staff.secret")));
        }

        @Test
        void theAdminStillSeesAndRestoresEverySetting() {
            SnapshotsView.Preview preview = snapshotsFor(admin).preview("cfg").value();

            assertThat(preview.changes()).isEqualTo(2);
            assertThat(String.join("\n", preview.lines())).contains("Saved hello");
        }

        @Test
        void aSnapshotsPlayerSkipsSettingsTheyMayNotEditAndDoesNotSeeTheirValues() {
            SnapshotsView view = snapshotsFor(keeper);

            SnapshotsView.Preview preview = view.preview("cfg").value();
            String restored = view.restore("cfg", false).value();

            assertThat(preview.changes()).isEqualTo(1);
            assertThat(preview.skipped()).isEqualTo(1);
            assertThat(String.join("\n", preview.lines())).doesNotContain("hello");
            assertThat(restored).doesNotContain("hello");
            assertThat(ok(as(admin, "cc-get", Map.of("id", WELCOME)))).contains("Live hello");
            assertThat(ok(as(admin, "cc-get", Map.of("id", DAYS)))).contains("30");
        }

        private SnapshotsView snapshotsFor(PlayerInfo who) {
            ok(as(who, COMMAND, Map.of()));
            return platform.controlPanel.lastContent.snapshots().orElseThrow();
        }

        private Result<Reply> as(PlayerInfo who, String command, Map<String, Object> args) {
            return platform.commands.run(command, who, args);
        }

        private static String ok(Result<Reply> result) {
            assertThat(result.isSuccess()).as(String.valueOf(result)).isTrue();
            return result.value().text().orElse("");
        }
    }

    @Nested
    class ModeratorRestoreAndSwitch extends RuntimeTestBase {

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
            assertThat(as(moderator, "cc-snapshot-save", Map.of("name", "mine", "parts", "all"))
                            .error()
                            .code())
                    .as("asking for every part includes keys and permissions")
                    .isEqualTo("Snapshots.AdminParts");
            assertThat(ok(as(moderator, "cc-snapshot-save", Map.of("name", "mine"))))
                    .isNotBlank();
            assertThat(platform.snapshots.snapshots.get("mine").parts())
                    .as("without parts a moderator gets everything they may save")
                    .noneMatch(net.ofatech.controlcore.domain.model.snapshot.Snapshot.Part::adminOnly);
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
            assertThat(modDiff)
                    .contains("Changed -> Saved")
                    .doesNotContain("warp")
                    .doesNotContain("extra");
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

            assertThat(ok(as(admin, "cc-snapshot-restore", Map.of("name", "base"))))
                    .contains("0 failed");

            assertThat(value()).isEqualTo("Saved");
            assertThat(binds()).containsExactly("warp");
            assertThat(ruleChanged()).isFalse();
        }

        @Test
        void consoleRestoresEverything() {
            savedThenChanged("base");

            String diff = ok(console("cc-snapshot-diff", Map.of("name", "base")));
            assertThat(diff).contains("warp").contains("extra").doesNotContain("skipped");
            assertThat(ok(console("cc-snapshot-restore", Map.of("name", "base"))))
                    .contains("0 failed");

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
                    .contains("What you can restore still matches it");
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
            return platform.customKeyBinds.all().stream()
                    .map(bind -> bind.name())
                    .toList();
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

    @Nested
    class OwnershipClearingAndPanel extends RuntimeTestBase {

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

        @Test
        void clearingByAModeratorKeepsSnapshotsWithKeysOrPermissions() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "full")));
            ok(as(moderator, "cc-snapshot-save", Map.of("name", "mine", "parts", "config+settings")));

            ok(as(moderator, "cc-snapshots-clear", Map.of()));

            assertThat(platform.snapshots.snapshots).containsKey("full").doesNotContainKey("mine");

            ok(as(admin, "cc-snapshots-clear", Map.of()));
            assertThat(platform.snapshots.snapshots).doesNotContainKey("full");
        }

        @Test
        void clearingByAModeratorCountsOnlyWhatWasDeleted() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "full")));
            ok(as(admin, "cc-snapshot-save", Map.of("name", "adminconfig", "parts", "config")));

            String reply = ok(as(moderator, "cc-snapshots-clear", Map.of()));

            assertThat(reply).contains("1");
            assertThat(platform.snapshots.snapshots).containsKey("full").doesNotContainKey("adminconfig");
        }

        @Test
        void moderatorMayOverwriteTheirOwnSnapshotButNotAddKeysToIt() {
            ok(as(moderator, "cc-snapshot-save", Map.of("name", "mine", "parts", "config+settings")));
            ok(as(moderator, "cc-snapshot-save", Map.of("name", "mine", "parts", "settings")));
            assertThat(platform.snapshots.snapshots.get("mine").parts()).containsExactly(Snapshot.Part.SETTINGS);

            Result<Reply> withKeys =
                    as(moderator, "cc-snapshot-save", Map.of("name", "mine", "parts", "settings+keys"));
            Result<Reply> withPerms =
                    as(moderator, "cc-snapshot-save", Map.of("name", "other", "parts", "permissions"));

            assertThat(withKeys.error().code()).isEqualTo("Snapshots.AdminParts");
            assertThat(withPerms.error().code()).isEqualTo("Snapshots.AdminParts");
            assertThat(platform.snapshots.snapshots.get("mine").parts()).containsExactly(Snapshot.Part.SETTINGS);
            assertThat(platform.snapshots.snapshots).doesNotContainKey("other");
        }

        @Test
        void moderatorCannotOverwriteAnAdminSnapshotEvenWithAllowedParts() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "full")));

            Result<Reply> result = as(moderator, "cc-snapshot-save", Map.of("name", "full", "parts", "config"));

            assertThat(result.error().code()).isEqualTo("Snapshots.AdminParts");
            assertThat(platform.snapshots.snapshots.get("full").parts())
                    .contains(Snapshot.Part.KEYS, Snapshot.Part.PERMISSIONS);
        }

        @Test
        void adminMayOverwriteAndDeleteAModeratorSnapshot() {
            ok(as(moderator, "cc-snapshot-save", Map.of("name", "mine", "parts", "config+settings")));

            ok(as(admin, "cc-snapshot-save", Map.of("name", "mine")));
            assertThat(platform.snapshots.snapshots.get("mine").parts()).contains(Snapshot.Part.KEYS);
            assertThat(as(moderator, "cc-snapshot-delete", Map.of("name", "mine"))
                            .error()
                            .code())
                    .isEqualTo("Snapshots.AdminParts");
            ok(as(admin, "cc-snapshot-delete", Map.of("name", "mine")));
            assertThat(platform.snapshots.snapshots).doesNotContainKey("mine");
        }

        @Test
        void moderatorCannotMarkOrUnmarkAnAdminSnapshotAsAProfileButCanTheirOwn() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "full")));
            ok(as(moderator, "cc-snapshot-save", Map.of("name", "mine", "parts", "config+settings")));
            SnapshotsView view = snapshotsFor(moderator);

            assertThat(view.setProfile("full", true).error().code()).isEqualTo("Snapshots.AdminParts");
            assertThat(platform.snapshots.snapshots.get("full").profile()).isFalse();
            assertThat(view.setProfile("mine", true).isSuccess()).isTrue();
            assertThat(platform.snapshots.snapshots.get("mine").profile()).isTrue();

            assertThat(snapshotsFor(admin).setProfile("full", true).isSuccess()).isTrue();
            assertThat(profilesFor(moderator).unmark("full").error().code()).isEqualTo("Snapshots.ProfileReplace");
            assertThat(platform.snapshots.snapshots.get("full").profile()).isTrue();
            assertThat(profilesFor(moderator).unmark("mine").error().code())
                    .as("once it is a profile only admins unmark it")
                    .isEqualTo("Snapshots.ProfileReplace");
            assertThat(profilesFor(admin).unmark("mine").isSuccess()).isTrue();
        }

        @Test
        void panelDeleteAndDeleteAllFollowTheSameRules() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "full")));
            ok(as(moderator, "cc-snapshot-save", Map.of("name", "mine", "parts", "config")));
            SnapshotsView view = snapshotsFor(moderator);

            assertThat(view.delete("full").error().code()).isEqualTo("Snapshots.AdminParts");
            assertThat(view.deleteAll().isSuccess()).isTrue();
            assertThat(platform.snapshots.snapshots).containsKey("full").doesNotContainKey("mine");
        }

        @Test
        void panelSaveWithKeysIsRefusedForModeratorAndAllowedForAdmin() {
            Result<String> mod =
                    snapshotsFor(moderator).save("x", Set.of(Snapshot.Part.KEYS), java.util.Optional.empty());
            Result<String> adm = snapshotsFor(admin)
                    .save("x", Set.of(Snapshot.Part.KEYS, Snapshot.Part.PERMISSIONS), java.util.Optional.empty());

            assertThat(mod.error().code()).isEqualTo("Snapshots.AdminParts");
            assertThat(adm.isSuccess()).isTrue();
        }

        @Test
        void panelSaveBeforeDropsAdminPartsForModerator() {
            Result<String> saved = snapshotsFor(moderator)
                    .saveBefore(
                            "defaults", Set.of(Snapshot.Part.CONFIG, Snapshot.Part.KEYS, Snapshot.Part.PERMISSIONS));

            assertThat(saved.isSuccess()).as(String.valueOf(saved)).isTrue();
            assertThat(platform.snapshots.snapshots.get(saved.value()).parts()).containsExactly(Snapshot.Part.CONFIG);
        }

        @Test
        void moderatorSafeRestoreOfAKeysOnlySnapshotStillWorks() {
            ok(as(admin, "cc-keybind-add", Map.of("name", "warp", "keys", "ctrl+r", "command", "warp spawn")));
            ok(as(admin, "cc-snapshot-save", Map.of("name", "keysonly", "parts", "keys")));
            ok(as(admin, "cc-keybind-remove", Map.of("name", "warp")));

            Result<Reply> restored = as(moderator, "cc-snapshot-restore-safe", Map.of("name", "keysonly"));

            assertThat(restored.isSuccess()).as(String.valueOf(restored)).isTrue();
            assertThat(restored.value().text().orElse("")).contains("left out");
        }

        @Test
        void moderatorSwitchToAKeysAndPermissionsOnlyProfileStillWorks() {
            ok(as(admin, "cc-keybind-add", Map.of("name", "warp", "keys", "ctrl+r", "command", "warp spawn")));
            ok(as(admin, "cc-snapshot-save", Map.of("name", "keyprof", "parts", "keys+permissions")));
            platform.snapshots.save(platform.snapshots.snapshots.get("keyprof").asProfile(true));
            ok(as(admin, "cc-keybind-remove", Map.of("name", "warp")));

            Result<Reply> switched = as(moderator, "cc-profile", Map.of("profile", "keyprof"));

            assertThat(switched.isSuccess()).as(String.valueOf(switched)).isTrue();
            assertThat(binds()).doesNotContain("warp");
        }

        @Test
        void profilePreviewAndStatusPerViewer() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "staff")));
            platform.snapshots.save(platform.snapshots.snapshots.get("staff").asProfile(true));
            ok(as(admin, "cc-profile", Map.of("profile", "staff")));
            ok(as(admin, "cc-keybind-add", Map.of("name", "extra", "keys", "ctrl+e", "command", "say extra")));
            ok(as(admin, "cc-perm", Map.of("target", WELCOME, "side", "edit", "node", "staff.motd")));

            SnapshotsView.Preview modPreview =
                    profilesFor(moderator).preview("staff").value();
            SnapshotsView.Preview adminPreview =
                    profilesFor(admin).preview("staff").value();

            assertThat(modPreview.changes()).isZero();
            assertThat(modPreview.adminOnly()).isEqualTo(2);
            assertThat(modPreview.title(platformTr())).contains("only differs in keys, permissions or mods");
            assertThat(String.join("\n", modPreview.allLines(platformTr())))
                    .as("the title already says it")
                    .doesNotContain("left out");
            assertThat(adminPreview.changes()).isEqualTo(2);
            assertThat(adminPreview.adminOnly()).isZero();
            assertThat(adminPreview.title(platformTr())).contains("changes 2");

            assertThat(profilesFor(admin).status()).contains("2 value(s) changed");
            assertThat(profilesFor(moderator).status())
                    .contains("What you can restore still matches it")
                    .contains("2 key, permission or mod difference(s) only admins can restore");
            assertThat(ok(as(admin, "cc-profiles", Map.of()))).contains("2 value(s) changed");
        }

        @Test
        void moderatorDiffOfASnapshotThatMatchesEverythingSaysSame() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "base")));

            String diff = ok(as(moderator, "cc-snapshot-diff", Map.of("name", "base")));

            assertThat(diff).contains("nothing to restore").doesNotContain("left out");
        }

        @Test
        void consoleMaySaveDeleteAndMarkAnything() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "full")));
            var service = platform.snapshots;

            assertThat(service.snapshots.get("full").parts()).contains(Snapshot.Part.KEYS);
            ok(console("cc-snapshot-save", Map.of("name", "full", "parts", "keys+permissions")));
            assertThat(service.snapshots.get("full").parts())
                    .containsExactlyInAnyOrder(Snapshot.Part.KEYS, Snapshot.Part.PERMISSIONS);
            ok(console("cc-snapshots-clear", Map.of()));
            assertThat(service.snapshots).doesNotContainKey("full");
        }

        @Test
        void moderatorRestoreWithOnlyAdminChangesAppliesNothing() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "base")));
            ok(as(admin, "cc-perm", Map.of("target", WELCOME, "side", "edit", "node", "staff.motd")));

            String restored = ok(as(moderator, "cc-snapshot-restore", Map.of("name", "base")));

            assertThat(restored).contains("0 change(s), 0 failed").contains("1 change(s) to keys, permissions or mods");
            SettingPermission rule = platform.settingPermissions.all().get(RULE);
            assertThat(rule != null && !rule.isInherit()).isTrue();
        }

        private net.ofatech.controlcore.domain.model.text.Translator platformTr() {
            return platform.controlPanel.lastContent.translator();
        }

        private SnapshotsView snapshotsFor(PlayerInfo who) {
            ok(as(who, COMMAND, Map.of()));
            return platform.controlPanel.lastContent.snapshots().orElseThrow();
        }

        private ProfilesView profilesFor(PlayerInfo who) {
            ok(as(who, COMMAND, Map.of()));
            return platform.controlPanel.lastContent.profiles().orElseThrow();
        }

        private List<String> binds() {
            return platform.customKeyBinds.all().stream()
                    .map(bind -> bind.name())
                    .toList();
        }

        private Result<Reply> as(PlayerInfo who, String command, Map<String, Object> args) {
            return platform.commands.run(command, who, args);
        }

        private Result<Reply> console(String command, Map<String, Object> args) {
            var sender = net.ofatech.controlcore.domain.ports.command.CommandSender.console("Console");
            var context = new net.ofatech.controlcore.domain.ports.command.CommandContext() {
                @Override
                public net.ofatech.controlcore.domain.ports.command.CommandSender sender() {
                    return sender;
                }

                @Override
                public java.util.Optional<Object> arg(String name) {
                    return java.util.Optional.ofNullable(args.get(name));
                }
            };
            return platform.commands.commands.get(command).handler().handle(context);
        }

        private static String ok(Result<Reply> result) {
            assertThat(result.isSuccess()).as(String.valueOf(result)).isTrue();
            return result.value().text().orElse("");
        }
    }

    @Nested
    class ModsStatusAndLocking extends RuntimeTestBase {

        private static final String HOMES = "Acme:Homes";

        private PlayerInfo admin;
        private PlayerInfo moderator;

        @BeforeEach
        void startUp() {
            platform.modCatalog.add("OFATech:ControlCore", false);
            platform.modCatalog.add(HOMES, false);
            runtime.setup();
            runtime.start();
            admin = platform.players.join("Admin");
            moderator = platform.players.join("Mod");
            platform.deniedNodesFor.put(moderator.id(), new ArrayList<>(List.of("settings", "admin")));
        }

        @Test
        void onlyAdminsMaySaveMods() {
            assertThat(as(moderator, "cc-snapshot-save", Map.of("name", "m1", "parts", "mods"))
                            .error()
                            .code())
                    .isEqualTo("Snapshots.AdminParts");
            assertThat(as(moderator, "cc-snapshot-save", Map.of("name", "m2", "parts", "config+mods"))
                            .error()
                            .code())
                    .isEqualTo("Snapshots.AdminParts");
            assertThat(snapshotsFor(moderator)
                            .save("m3", Set.of(Snapshot.Part.MODS), Optional.empty())
                            .error()
                            .code())
                    .isEqualTo("Snapshots.AdminParts");
            assertThat(platform.snapshots.snapshots).doesNotContainKeys("m1", "m2", "m3");

            ok(as(admin, "cc-snapshot-save", Map.of("name", "modsonly", "parts", "mods")));
            assertThat(platform.snapshots.snapshots.get("modsonly").parts()).containsExactly(Snapshot.Part.MODS);
            assertThat(platform.snapshots.snapshots.get("modsonly").modsEnabled())
                    .containsEntry(HOMES, true);
        }

        @Test
        void moderatorRestoreLeavesModsAloneAndAdminRestoresThem() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "modsonly", "parts", "mods")));
            platform.modCatalog.setEnabled(HOMES, false);

            String byModerator = ok(as(moderator, "cc-snapshot-restore", Map.of("name", "modsonly")));

            assertThat(byModerator).contains("0 change(s)").contains("1 change(s) to keys, permissions or mods");
            assertThat(homesEnabled()).isFalse();

            String byAdmin = ok(as(admin, "cc-snapshot-restore", Map.of("name", "modsonly")));

            assertThat(byAdmin).contains("1 change(s)").doesNotContain("left out");
            assertThat(homesEnabled()).isTrue();
        }

        @Test
        void moderatorPreviewCountsModChangesAsAdminOnly() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "modsonly", "parts", "mods")));
            platform.modCatalog.setEnabled(HOMES, false);

            SnapshotsView.Preview mod =
                    snapshotsFor(moderator).preview("modsonly").value();
            SnapshotsView.Preview adm = snapshotsFor(admin).preview("modsonly").value();

            assertThat(mod.changes()).isZero();
            assertThat(mod.adminOnly()).isEqualTo(1);
            assertThat(adm.changes()).isEqualTo(1);
            assertThat(adm.adminOnly()).isZero();
        }

        @Test
        void moderatorSafeRestoreOfAModsOnlySnapshotMakesNoSafetyCopy() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "modsonly", "parts", "mods")));
            platform.modCatalog.setEnabled(HOMES, false);
            int before = platform.snapshots.snapshots.size();

            String reply = ok(as(moderator, "cc-snapshot-restore-safe", Map.of("name", "modsonly")));

            assertThat(platform.snapshots.snapshots).hasSize(before);
            assertThat(reply).contains("left out").doesNotContain("previous state is saved");
            assertThat(homesEnabled()).isFalse();
        }

        @Test
        void moderatorSafeRestoreOfAMixedSnapshotBacksUpOnlyTheAllowedParts() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "mixed", "parts", "config+keys+mods")));
            int before = platform.snapshots.snapshots.size();

            String reply = ok(as(moderator, "cc-snapshot-restore-safe", Map.of("name", "mixed")));

            assertThat(reply).contains("previous state is saved");
            assertThat(platform.snapshots.snapshots).hasSize(before + 1);
            Snapshot safety = platform.snapshots.snapshots.values().stream()
                    .filter(s -> !s.name().equals("mixed"))
                    .findFirst()
                    .orElseThrow();
            assertThat(safety.parts()).containsExactly(Snapshot.Part.CONFIG);
        }

        @Test
        void moderatorProfileSwitchToAnAdminOnlyProfileMakesNoSafetyCopy() {
            ok(as(admin, "cc-keybind-add", Map.of("name", "warp", "keys", "ctrl+r", "command", "warp spawn")));
            ok(as(admin, "cc-snapshot-save", Map.of("name", "keyprof", "parts", "keys+mods")));
            markProfile("keyprof");
            ok(as(admin, "cc-keybind-remove", Map.of("name", "warp")));
            int before = platform.snapshots.snapshots.size();

            String reply = ok(as(moderator, "cc-profile", Map.of("profile", "keyprof")));

            assertThat(platform.snapshots.snapshots).hasSize(before);
            assertThat(reply).contains("Switched to profile keyprof").contains("left out");
            assertThat(platform.activeProfile.active).contains("keyprof");
        }

        @Test
        void statusKeepsTheActiveProfileWhenAViewerLosesTheSnapshotsNode() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "staff")));
            markProfile("staff");
            ok(as(admin, "cc-profile", Map.of("profile", "staff")));
            ProfilesView view = profilesFor(moderator);
            platform.deniedNodesFor.get(moderator.id()).add("snapshots");

            view.status();

            assertThat(platform.activeProfile.active).contains("staff");
            assertThat(ok(as(admin, "cc-profiles", Map.of()))).contains("* staff");
        }

        @Test
        void statusStillClearsAProfileThatNoLongerExists() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "staff")));
            markProfile("staff");
            ok(as(admin, "cc-profile", Map.of("profile", "staff")));
            platform.snapshots.snapshots.remove("staff");

            assertThat(profilesFor(moderator).status()).isEqualTo("No profile applied yet.");
            assertThat(platform.activeProfile.active).isEmpty();
        }

        @Test
        void moderatorStatusListsAdminOnlyModChanges() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "staff")));
            markProfile("staff");
            ok(as(admin, "cc-profile", Map.of("profile", "staff")));
            platform.modCatalog.setEnabled(HOMES, false);

            assertThat(profilesFor(moderator).status())
                    .contains("What you can restore still matches it")
                    .contains("1 key, permission or mod difference(s) only admins can restore");
            assertThat(profilesFor(admin).status())
                    .contains("1 value(s) changed")
                    .doesNotContain("only admins");
            assertThat(ok(as(moderator, "cc-profiles", Map.of()))).contains("1 key, permission or mod difference(s)");
        }

        @Test
        void moderatorDeleteAllSaysHowManyWereKept() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "full")));
            ok(as(admin, "cc-snapshot-save", Map.of("name", "modsonly", "parts", "mods")));
            ok(as(admin, "cc-snapshot-save", Map.of("name", "fullprof")));
            markProfile("fullprof");
            ok(as(moderator, "cc-snapshot-save", Map.of("name", "mine", "parts", "config")));

            String panel = snapshotsFor(moderator).deleteAll().value();

            assertThat(panel)
                    .contains("Deleted 1 snapshot(s)")
                    .contains("2 snapshot(s) with keys, permissions or mods were kept");
            assertThat(platform.snapshots.snapshots).containsKeys("full", "modsonly", "fullprof");

            String command = ok(as(moderator, "cc-snapshots-clear", Map.of()));
            assertThat(command).contains("Deleted 0 snapshot(s)").contains("2 snapshot(s)");

            String byAdmin = ok(as(admin, "cc-snapshots-clear", Map.of()));
            assertThat(byAdmin).contains("Deleted 2 snapshot(s)").doesNotContain("with keys, permissions or mods");
            assertThat(platform.snapshots.snapshots).containsOnlyKeys("fullprof");
        }

        @Test
        void replacingAProfileKeepsTheMarkAndSaysReplaced() {
            SnapshotsView view = snapshotsFor(admin);
            String first = view.save("prof", Set.of(Snapshot.Part.CONFIG), Optional.empty())
                    .value();
            assertThat(first).startsWith("Saved snapshot prof");
            markProfile("prof");

            String second = view.save("prof", Set.of(Snapshot.Part.SETTINGS), Optional.empty())
                    .value();

            assertThat(second).startsWith("Replaced snapshot prof: ").endsWith("It is still a profile.");
            assertThat(platform.snapshots.snapshots.get("prof").profile()).isTrue();
            assertThat(platform.snapshots.snapshots.get("prof").parts()).containsExactly(Snapshot.Part.SETTINGS);

            ok(as(admin, "cc-snapshot-save", Map.of("name", "prof")));
            assertThat(platform.snapshots.snapshots.get("prof").profile()).isTrue();
            assertThat(platform.snapshots.snapshots.get("prof").parts()).contains(Snapshot.Part.KEYS);
        }

        @Test
        void replacingAProfileByCommandKeepsTheMark() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "prof", "parts", "config")));
            markProfile("prof");

            ok(as(admin, "cc-snapshot-save", Map.of("name", "prof", "parts", "settings")));

            assertThat(platform.snapshots.snapshots.get("prof").profile()).isTrue();
        }

        @Test
        void replacingAPlainSnapshotStaysPlain() {
            SnapshotsView view = snapshotsFor(admin);
            view.save("plain", Set.of(Snapshot.Part.CONFIG), Optional.empty());

            assertThat(view.save("plain", Set.of(Snapshot.Part.KEYS), Optional.empty())
                            .value())
                    .isEqualTo("Replaced snapshot plain: keys and binds.");
            assertThat(platform.snapshots.snapshots.get("plain").profile()).isFalse();
        }

        @Test
        void refusedReplaceSaysNothingAndKeepsTheSnapshot() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "full")));
            markProfile("full");

            Result<String> result =
                    snapshotsFor(moderator).save("full", Set.of(Snapshot.Part.CONFIG), Optional.empty());

            assertThat(result.error().code()).isIn("Snapshots.AdminParts", "Snapshots.ProfileReplace");
            assertThat(platform.snapshots.snapshots.get("full").profile()).isTrue();
            assertThat(platform.snapshots.snapshots.get("full").parts()).contains(Snapshot.Part.KEYS);
        }

        @Test
        void entriesAreLockedPerReader() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "full")));
            ok(as(admin, "cc-snapshot-save", Map.of("name", "modsonly", "parts", "mods")));
            ok(as(admin, "cc-snapshot-save", Map.of("name", "cfg", "parts", "config")));
            ok(as(moderator, "cc-snapshot-save", Map.of("name", "mine", "parts", "settings")));

            SnapshotsView modView = snapshotsFor(moderator);
            SnapshotsView adminView = snapshotsFor(admin);

            assertThat(modView.admin()).isFalse();
            assertThat(adminView.admin()).isTrue();
            assertThat(locked(modView))
                    .containsEntry("full", true)
                    .containsEntry("modsonly", true)
                    .containsEntry("cfg", false)
                    .containsEntry("mine", false);
            assertThat(locked(adminView))
                    .containsOnlyKeys("full", "modsonly", "cfg", "mine")
                    .doesNotContainValue(true);
        }

        @Test
        void lockedFollowsRightsChangesAtTheNextRead() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "full")));
            SnapshotsView view = snapshotsFor(moderator);
            assertThat(view.admin()).isFalse();

            platform.deniedNodesFor.get(moderator.id()).remove("admin");

            assertThat(view.admin()).isTrue();
            assertThat(locked(view)).containsEntry("full", false);
        }

        private Map<String, Boolean> locked(SnapshotsView view) {
            Map<String, Boolean> map = new java.util.HashMap<>();
            view.list().forEach(entry -> map.put(entry.name(), entry.locked()));
            return map;
        }

        private void markProfile(String name) {
            platform.snapshots.save(platform.snapshots.snapshots.get(name).asProfile(true));
        }

        private boolean homesEnabled() {
            return platform.modCatalog.mods().stream()
                    .filter(m -> m.id().equals(HOMES))
                    .findFirst()
                    .orElseThrow()
                    .enabledAfterRestart();
        }

        private SnapshotsView snapshotsFor(PlayerInfo who) {
            ok(as(who, COMMAND, Map.of()));
            return platform.controlPanel.lastContent.snapshots().orElseThrow();
        }

        private ProfilesView profilesFor(PlayerInfo who) {
            ok(as(who, COMMAND, Map.of()));
            return platform.controlPanel.lastContent.profiles().orElseThrow();
        }

        private Result<Reply> as(PlayerInfo who, String command, Map<String, Object> args) {
            return platform.commands.run(command, who, args);
        }

        private static String ok(Result<Reply> result) {
            assertThat(result.isSuccess()).as(String.valueOf(result)).isTrue();
            return result.value().text().orElse("");
        }
    }

    @Nested
    class ProfileProtection extends RuntimeTestBase {

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

        @Test
        void aModeratorCannotUnmarkDeleteOrReplaceAProfile() {
            assertThat(platform.commands
                            .run("cc-snapshot-save", admin, Map.of("name", "lobby", "parts", "config"))
                            .isSuccess())
                    .isTrue();
            platform.snapshots.save(platform.snapshots.snapshots.get("lobby").asProfile(true));

            assertThat(platform.commands
                            .run("cc-snapshot-delete", moderator, Map.of("name", "lobby"))
                            .error()
                            .code())
                    .isEqualTo("Snapshots.ProfileReplace");
            assertThat(platform.commands
                            .run("cc-snapshot-save", moderator, Map.of("name", "lobby", "parts", "config"))
                            .error()
                            .code())
                    .isEqualTo("Snapshots.ProfileReplace");
            assertThat(platform.snapshots.snapshots.get("lobby").profile()).isTrue();
            assertThat(platform.commands
                            .run("cc-snapshot-delete", admin, Map.of("name", "lobby"))
                            .isSuccess())
                    .isTrue();
        }
    }

    @Nested
    class ProfileReplaceAndSaveParts extends RuntimeTestBase {

        private static final String HOMES = "Acme:Homes";

        private PlayerInfo admin;
        private PlayerInfo moderator;

        @BeforeEach
        void startUp() {
            platform.modCatalog.add("OFATech:ControlCore", false);
            platform.modCatalog.add(HOMES, false);
            runtime.setup();
            runtime.start();
            admin = platform.players.join("Admin");
            moderator = platform.players.join("Mod");
            platform.deniedNodesFor.put(moderator.id(), new ArrayList<>(List.of("settings", "admin")));
        }

        @Test
        void moderatorCannotReplaceAProfileEvenWithOnlyAllowedParts() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "cfgprof", "parts", "config")));
            markProfile("cfgprof");

            Result<String> byPanel =
                    snapshotsFor(moderator).save("cfgprof", Set.of(Snapshot.Part.SETTINGS), Optional.empty());
            Result<Reply> byCommand = as(moderator, "cc-snapshot-save", Map.of("name", "cfgprof", "parts", "settings"));
            Result<Reply> byCommandNoParts = as(moderator, "cc-snapshot-save", Map.of("name", "cfgprof"));

            assertThat(byPanel.error().code()).isEqualTo("Snapshots.ProfileReplace");
            assertThat(byCommand.error().code()).isEqualTo("Snapshots.ProfileReplace");
            assertThat(byCommandNoParts.error().code()).isEqualTo("Snapshots.ProfileReplace");
            assertThat(platform.snapshots.snapshots.get("cfgprof").parts()).containsExactly(Snapshot.Part.CONFIG);
            assertThat(platform.snapshots.snapshots.get("cfgprof").profile()).isTrue();
        }

        @Test
        void moderatorMayStillReplaceAPlainSnapshotWithAllowedParts() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "plain", "parts", "config")));

            String reply = ok(as(moderator, "cc-snapshot-save", Map.of("name", "plain", "parts", "settings")));

            assertThat(reply).startsWith("Replaced snapshot plain").doesNotContain("still a profile");
            assertThat(platform.snapshots.snapshots.get("plain").parts()).containsExactly(Snapshot.Part.SETTINGS);
        }

        @Test
        void adminReplacingAProfileByCommandSaysReplacedAndKeepsTheMark() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "cfgprof", "parts", "config")));
            markProfile("cfgprof");

            String reply = ok(as(admin, "cc-snapshot-save", Map.of("name", "cfgprof", "parts", "settings")));

            assertThat(reply).startsWith("Replaced snapshot cfgprof").endsWith("It is still a profile.");
            assertThat(platform.snapshots.snapshots.get("cfgprof").profile()).isTrue();
            assertThat(platform.snapshots.snapshots.get("cfgprof").parts()).containsExactly(Snapshot.Part.SETTINGS);
        }

        @Test
        void firstSaveByCommandSaysSavedNotReplaced() {
            String reply = ok(as(admin, "cc-snapshot-save", Map.of("name", "fresh", "parts", "config")));

            assertThat(reply).startsWith("Saved snapshot fresh").doesNotContain("Replaced");
        }

        @Test
        void statusWithoutTheSnapshotsNodeShowsNoCountsAndClearsNothing() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "staff")));
            markProfile("staff");
            ok(as(admin, "cc-profile", Map.of("profile", "staff")));
            platform.modCatalog.setEnabled(HOMES, false);
            ProfilesView view = profilesFor(moderator);
            platform.deniedNodesFor.get(moderator.id()).add("snapshots");

            String status = view.status();

            assertThat(status).isEqualTo("No profile applied yet.");
            assertThat(status).doesNotContainPattern("\\d");
            assertThat(platform.activeProfile.active).contains("staff");
            assertThat(profilesFor(admin).status()).contains("staff").contains("1 value(s) changed");
        }

        @Test
        void moderatorSaveWithoutPartsKeepsOnlyAllowedPartsAndAllIsRefused() {
            String reply = ok(as(moderator, "cc-snapshot-save", Map.of("name", "mine")));
            Result<Reply> all = as(moderator, "cc-snapshot-save", Map.of("name", "mine2", "parts", "all"));

            assertThat(reply).startsWith("Saved snapshot mine");
            assertThat(platform.snapshots.snapshots.get("mine").parts())
                    .isNotEmpty()
                    .noneMatch(Snapshot.Part::adminOnly);
            assertThat(all.error().code()).isEqualTo("Snapshots.AdminParts");
            assertThat(platform.snapshots.snapshots).doesNotContainKey("mine2");
        }

        @Test
        void adminSaveWithoutPartsKeepsEveryPart() {
            ok(as(admin, "cc-snapshot-save", Map.of("name", "full")));

            assertThat(platform.snapshots.snapshots.get("full").parts())
                    .contains(Snapshot.Part.KEYS, Snapshot.Part.PERMISSIONS, Snapshot.Part.MODS);
        }

        @Test
        void adminOnlyPartsAreKeysPermissionsAndMods() {
            assertThat(Set.of(Snapshot.Part.values()).stream().filter(Snapshot.Part::adminOnly))
                    .containsExactlyInAnyOrder(Snapshot.Part.KEYS, Snapshot.Part.PERMISSIONS, Snapshot.Part.MODS);
        }

        private void markProfile(String name) {
            platform.snapshots.save(platform.snapshots.snapshots.get(name).asProfile(true));
        }

        private SnapshotsView snapshotsFor(PlayerInfo who) {
            ok(as(who, COMMAND, Map.of()));
            return platform.controlPanel.lastContent.snapshots().orElseThrow();
        }

        private ProfilesView profilesFor(PlayerInfo who) {
            ok(as(who, COMMAND, Map.of()));
            return platform.controlPanel.lastContent.profiles().orElseThrow();
        }

        private Result<Reply> as(PlayerInfo who, String command, Map<String, Object> args) {
            return platform.commands.run(command, who, args);
        }

        private static String ok(Result<Reply> result) {
            assertThat(result.isSuccess()).as(String.valueOf(result)).isTrue();
            return result.value().text().orElse("");
        }
    }
}
