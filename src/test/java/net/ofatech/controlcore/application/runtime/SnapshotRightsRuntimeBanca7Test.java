package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.model.snapshot.ProfilesView;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot;
import net.ofatech.controlcore.domain.model.snapshot.SnapshotsView;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SnapshotRightsRuntimeBanca7Test extends RuntimeTestBase {

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

        Result<Reply> withKeys = as(moderator, "cc-snapshot-save", Map.of("name", "mine", "parts", "settings+keys"));
        Result<Reply> withPerms = as(moderator, "cc-snapshot-save", Map.of("name", "other", "parts", "permissions"));

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
        Result<String> mod = snapshotsFor(moderator).save("x", Set.of(Snapshot.Part.KEYS), java.util.Optional.empty());
        Result<String> adm = snapshotsFor(admin)
                .save("x", Set.of(Snapshot.Part.KEYS, Snapshot.Part.PERMISSIONS), java.util.Optional.empty());

        assertThat(mod.error().code()).isEqualTo("Snapshots.AdminParts");
        assertThat(adm.isSuccess()).isTrue();
    }

    @Test
    void panelSaveBeforeDropsAdminPartsForModerator() {
        Result<String> saved = snapshotsFor(moderator)
                .saveBefore("defaults", Set.of(Snapshot.Part.CONFIG, Snapshot.Part.KEYS, Snapshot.Part.PERMISSIONS));

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
        SnapshotsView.Preview adminPreview = profilesFor(admin).preview("staff").value();

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
        return platform.customKeyBinds.all().stream().map(bind -> bind.name()).toList();
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
