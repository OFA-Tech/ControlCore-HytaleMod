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

class SnapshotProfileBanca9Test extends RuntimeTestBase {

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
