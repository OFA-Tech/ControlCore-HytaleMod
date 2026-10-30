package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.snapshot.ProfilesView;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot;
import net.ofatech.controlcore.domain.model.snapshot.SnapshotsView;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SnapshotRightsRuntimeBanca8Test extends RuntimeTestBase {

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
        assertThat(platform.snapshots.snapshots.get("modsonly").modsEnabled()).containsEntry(HOMES, true);
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

        SnapshotsView.Preview mod = snapshotsFor(moderator).preview("modsonly").value();
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
        assertThat(profilesFor(admin).status()).contains("1 value(s) changed").doesNotContain("only admins");
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

        Result<String> result = snapshotsFor(moderator).save("full", Set.of(Snapshot.Part.CONFIG), Optional.empty());

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
