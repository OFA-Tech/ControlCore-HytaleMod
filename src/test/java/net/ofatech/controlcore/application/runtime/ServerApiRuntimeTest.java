package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.DefaultsApi;
import net.ofatech.controlcore.api.HistoryApi;
import net.ofatech.controlcore.api.PlayersApi;
import net.ofatech.controlcore.api.RulesApi;
import net.ofatech.controlcore.api.SnapshotsApi;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.keys.GameActionMode;
import net.ofatech.controlcore.domain.model.mods.ModDependency;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigEntry;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigValueKind;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin;
import net.ofatech.controlcore.domain.ports.mods.ModCatalog;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServerApiRuntimeTest extends RuntimeTestBase {

    private static final List<String> EVERY_NODE = List.of(
            "settings",
            "admin",
            "history",
            "history.rollback",
            "snapshots",
            "players",
            "players.kick",
            "players.gamemode",
            "players.teleport",
            "players.whitelist",
            "players.mute",
            "players.freeze",
            "players.inventory",
            "bans",
            "keys.server");
    private static final SettingId REQUIRE = SettingId.of("server:require-join-permission");

    private ControlCoreApi api;
    private PlayerInfo admin;
    private PlayerInfo guest;
    private PlayerInfo snapper;

    @BeforeEach
    void startUp() {
        platform.serverConfig.entries.add(new ConfigEntry(
                "RequireJoinPermission",
                "General",
                "Require join permission",
                "",
                ConfigValueKind.BOOLEAN,
                List.of(),
                0,
                0,
                "false",
                Optional.of("false"),
                SettingAccess.EDITABLE));
        platform.modCatalog.add("Acme:Base", false);
        platform.modCatalog.add(
                "Acme:Addon", false, new ModDependency("Acme:Base", "", false, ModDependency.Status.OK));
        runtime.setup();
        runtime.start();
        api = ControlCoreApi.get();
        admin = platform.players.join("Admin");
        guest = platform.players.join("Guest");
        snapper = platform.players.join("Snapper");
        platform.deniedNodesFor.put(guest.id(), EVERY_NODE);
        List<String> allButSnapshots = new ArrayList<>(EVERY_NODE);
        allButSnapshots.remove("snapshots");
        platform.deniedNodesFor.put(snapper.id(), allButSnapshots);
    }

    private ChangeRecord last() {
        return platform.changeLog.records.getLast();
    }

    private static String code(Result<?> result) {
        assertThat(result.isFailure()).as("%s", result).isTrue();
        return result.error().code();
    }

    private static <T> T ok(Result<T> result) {
        assertThat(result.isSuccess()).as("%s", result).isTrue();
        return result.value();
    }

    private void assertMadeBy(PlayerInfo player) {
        assertThat(last().actor().player()).contains(player.id());
        assertThat(last().source()).isEqualTo(ChangeRecord.Source.API);
    }

    private void assertMadeByMod() {
        assertThat(last().actor().isMod()).isTrue();
        assertThat(last().source()).isEqualTo(ChangeRecord.Source.API);
    }

    @Test
    void settingsReachServerConfigKeysWithTheRightsOfTheActingPlayer() {
        assertThat(api.settings().serverConfigs())
                .anyMatch(snapshot -> snapshot.id().equals(REQUIRE));
        assertThat(api.settings().worlds()).contains("default");

        ok(api.settings().write(REQUIRE, Optional.empty(), "true"));
        assertThat(platform.serverConfig
                        .entry("RequireJoinPermission")
                        .orElseThrow()
                        .value())
                .isEqualTo("true");
        assertMadeByMod();

        assertThat(code(api.settings().actingFor(guest.id()).write(REQUIRE, Optional.empty(), "false")))
                .isEqualTo("Settings.UnknownSetting");
        assertThat(api.settings().actingFor(guest.id()).serverConfigs()).isEmpty();
        assertThat(code(api.settings().actingFor(guest.id()).read(REQUIRE, Optional.of(admin.id()))))
                .isEqualTo("Settings.Forbidden");

        ok(api.settings().actingFor(admin.id()).reset(REQUIRE, Optional.empty()));
        assertThat(platform.serverConfig
                        .entry("RequireJoinPermission")
                        .orElseThrow()
                        .value())
                .isEqualTo("false");
        assertMadeBy(admin);
    }

    @Test
    void keymapsCustomBindsAndGameActionsAreForAdmins() {
        ok(api.keymaps().addCustom("warp", "ctrl+f", "warp spawn", false));
        assertThat(api.keymaps().customBinds()).extracting(bind -> bind.name()).containsExactly("warp");
        assertMadeByMod();

        assertThat(code(api.keymaps().actingFor(guest.id()).removeCustom("warp")))
                .isEqualTo("Keys.ServerOnlyAdmins");
        SettingId warp = SettingId.of("custom:warp");
        assertThat(code(api.keymaps().actingFor(guest.id()).setGameActionMode(warp, GameActionMode.KEEP)))
                .isEqualTo("Keys.ServerOnlyAdmins");
        assertThat(code(api.keymaps().actingFor(guest.id()).bind(warp, admin.id(), "ctrl+g")))
                .isEqualTo("Keys.Forbidden");

        ok(api.keymaps().actingFor(admin.id()).setGameActionMode(warp, GameActionMode.KEEP));
        assertThat(api.keymaps().gameActionMode(warp)).isEqualTo(GameActionMode.KEEP);
        assertMadeBy(admin);
        ok(api.keymaps().actingFor(admin.id()).removeCustom("custom:warp"));
        assertThat(api.keymaps().findCustom("warp")).isEmpty();
    }

    @Test
    void customBindsAPlayerCannotUseAreHiddenFromThem() {
        ok(api.keymaps().addCustom("announce", "ALT + 3", "say hi", true));

        assertThat(api.keymaps().customBinds()).extracting(bind -> bind.name()).contains("announce");
        assertThat(api.keymaps().actingFor(guest.id()).customBinds())
                .extracting(bind -> bind.name())
                .doesNotContain("announce");
        assertThat(api.keymaps().actingFor(guest.id()).findCustom("announce")).isEmpty();
        assertThat(api.keymaps().actingFor(admin.id()).findCustom("announce")).isPresent();
    }

    @Test
    void permissionRulesAreForAdmins() {
        ok(api.permissions().set("server:motd", "view", "everyone"));
        assertMadeByMod();
        assertThat(api.permissions().rules()).anyMatch(entry -> entry.target().equals("mod:server"));

        assertThat(api.permissions().actingFor(guest.id()).rules()).isEmpty();
        assertThat(code(api.permissions().actingFor(guest.id()).reset("server:motd")))
                .isEqualTo("Permissions.Forbidden");

        var check = ok(api.permissions().actingFor(admin.id()).check(REQUIRE));
        assertThat(check.setting()).isEqualTo(REQUIRE);
        ok(api.permissions().actingFor(admin.id()).set("server:require-join-permission", "both", "staff.join"));
        assertMadeBy(admin);
        assertThat(ok(api.permissions().check(REQUIRE)).editNode()).contains("staff.join");
    }

    @Test
    void modsShowDependenciesAndTurnOffWithTheModsThatNeedThem() {
        var base = api.mods().list().stream()
                .filter(mod -> mod.id().equals("Acme:Base"))
                .findFirst()
                .orElseThrow();
        assertThat(base.neededBy()).containsExactly("Addon");
        assertThat(api.mods().alsoChanged("Acme:Base", false))
                .extracting(mod -> mod.id())
                .containsExactly("Acme:Addon");

        assertThat(code(api.mods().actingFor(guest.id()).setEnabled("Acme:Base", false)))
                .isEqualTo("Mods.Forbidden");

        List<String> changed = ok(api.mods().actingFor(admin.id()).setEnabled("Acme:Base", false)).stream()
                .map(mod -> mod.id())
                .toList();
        assertThat(changed).containsExactly("Acme:Base", "Acme:Addon");
        assertMadeBy(admin);
        assertThat(api.mods().pendingRestart()).hasSize(2);
        assertThat(api.mods().actingFor(guest.id()).pendingRestart()).isEmpty();
    }

    @Test
    void modConfigFilesAreOnlyForAdmins() {
        platform.modCatalog.files.put("Acme:Base", List.of(new ModCatalog.ConfigFile("config.json", List.of("{}"))));

        assertThat(api.mods().actingFor(admin.id()).configFiles("Acme:Base")).hasSize(1);
        assertThat(api.mods().actingFor(guest.id()).configFiles("Acme:Base")).isEmpty();
    }

    @Test
    void historyListsRollsBackAndClearsWithTheRightsOfThePlayer() {
        ok(api.settings().write(REQUIRE, Optional.empty(), "true"));
        long id = last().id();
        List<HistoryApi.Change> changes = api.history().list(Optional.of(HistoryApi.Kind.SERVER_CONFIG), "", 10);
        assertThat(changes).extracting(HistoryApi.Change::id).contains(id);
        assertThat(changes.getFirst().source()).isEqualTo(HistoryApi.Source.API);

        assertThat(api.history().actingFor(guest.id()).list(Optional.empty(), "", 0))
                .isEmpty();
        assertThat(code(api.history().actingFor(guest.id()).rollback(id))).isEqualTo("History.Forbidden");
        assertThat(code(api.history().actingFor(guest.id()).clearAll())).isEqualTo("History.ClearForbidden");

        ok(api.history().actingFor(admin.id()).rollback(id));
        assertThat(last().actor().player()).contains(admin.id());
        assertThat(last().undoes()).isEqualTo(id);
        ok(api.history().clearAll());
        assertThat(api.history().list(Optional.empty(), null, 0)).isEmpty();
    }

    @Test
    void snapshotsKeepAdminOnlyPartsForAdmins() {
        assertThat(api.snapshots().actingFor(guest.id()).list()).isEmpty();
        assertThat(code(api.snapshots()
                        .actingFor(guest.id())
                        .save("x", Set.of(SnapshotsApi.Part.CONFIG), Optional.empty())))
                .isEqualTo("Snapshots.Forbidden");

        SnapshotsApi snapper = api.snapshots().actingFor(this.snapper.id());
        assertThat(code(snapper.save("keys", Set.of(SnapshotsApi.Part.KEYS), Optional.empty())))
                .isEqualTo("Snapshots.AdminParts");
        ok(api.snapshots()
                .save("full", Set.of(SnapshotsApi.Part.CONFIG, SnapshotsApi.Part.PERMISSIONS), Optional.empty()));
        assertThat(code(snapper.delete("full"))).isEqualTo("Snapshots.AdminParts");
        assertThat(snapper.list())
                .filteredOn(SnapshotsApi.Snapshot::locked)
                .extracting(SnapshotsApi.Snapshot::name)
                .contains("full");

        ok(snapper.save("mine", Set.of(SnapshotsApi.Part.CONFIG), Optional.empty()));
        ok(api.settings().write(REQUIRE, Optional.empty(), "true"));
        assertThat(ok(api.snapshots().actingFor(admin.id()).preview("mine")).changes())
                .isEqualTo(1);
        ok(api.snapshots().actingFor(admin.id()).restore("mine"));
        assertThat(last().actor().player()).contains(admin.id());
        assertThat(last().source()).isEqualTo(ChangeRecord.Source.SNAPSHOT);
        ok(snapper.delete("mine"));
    }

    @Test
    void profilesNeedTheSnapshotsRight() {
        assertThat(api.profiles().list()).isNotEmpty().anyMatch(profile -> profile.builtIn());
        assertThat(api.profiles().actingFor(guest.id()).list()).isEmpty();
        ok(api.snapshots().save("night", Set.of(SnapshotsApi.Part.CONFIG), Optional.empty()));
        assertThat(code(api.profiles().actingFor(guest.id()).mark("night"))).isEqualTo("Snapshots.Forbidden");
        ok(api.profiles().actingFor(admin.id()).mark("night"));
        assertThat(api.profiles().list()).anyMatch(profile -> profile.id().equals("night"));
        ok(api.profiles().unmark("night"));
    }

    @Test
    void healthIsForAdmins() {
        assertThat(api.health().sections()).hasSize(4);
        assertThat(api.health().actingFor(guest.id()).sections()).isEmpty();
        assertThat(code(api.health().actingFor(guest.id()).writeReport())).isEqualTo("Diagnostics.Forbidden");
        String path = ok(api.health().actingFor(admin.id()).writeReport());
        assertThat(platform.reports.files).isNotEmpty();
        assertThat(path).isNotBlank();
    }

    @Test
    void rulesAreSavedWithThePanelsValidationAndOnlyByAdmins() {
        RulesApi.Form form = new RulesApi.Form(
                "Hello", "join", "", "none", "", List.of(new RulesApi.Step("message-player", "Hi {player}")), "");
        String id = ok(api.rules().save(Optional.empty(), form));
        assertThat(api.rules().get(id))
                .hasValueSatisfying(saved -> assertThat(saved.name()).isEqualTo("Hello"));
        assertThat(api.rules().triggers()).contains("join", "daily-at");
        assertThat(api.rules().conditions()).startsWith("none");

        RulesApi.Form broken = new RulesApi.Form("Broken", "nope", "", "none", "", List.of(), "");
        assertThat(code(api.rules().save(Optional.empty(), broken))).startsWith("Automation.");

        assertThat(api.rules().actingFor(guest.id()).list()).isEmpty();
        assertThat(code(api.rules().actingFor(guest.id()).remove(id))).isEqualTo("Automation.Forbidden");
        ok(api.rules().actingFor(admin.id()).toggle(id));
        assertThat(api.rules().list())
                .singleElement()
                .satisfies(rule -> assertThat(rule.enabled()).isFalse());
        ok(api.rules().remove(id));
    }

    @Test
    void playersActionsFollowThePanelsRights() {
        assertThat(api.players().list(PlayersApi.Filter.ONLINE, "gu"))
                .extracting(PlayersApi.Player::id)
                .containsExactly(guest.id());
        assertThat(api.players().rights().kick()).isTrue();
        assertThat(code(api.players().teleportTo("Guest").join())).isEqualTo("Players.PlayersOnly");

        ok(api.players().whitelist("Guest", true).join());
        assertThat(platform.access.whitelisted()).contains(guest.id());
        assertMadeByMod();

        PlayersApi asGuest = api.players().actingFor(guest.id());
        assertThat(asGuest.rights().kick()).isFalse();
        assertThat(asGuest.list(PlayersApi.Filter.ALL, null)).isEmpty();
        assertThat(code(asGuest.kick("Admin", "").join())).isEqualTo("Players.Forbidden");
        assertThat(code(asGuest.whitelist("Admin", true).join())).isEqualTo("Players.Forbidden");

        ok(api.players().actingFor(admin.id()).freeze("Guest").join());
        assertMadeBy(admin);
        ok(api.players().actingFor(admin.id()).unfreeze("Guest").join());
        ok(api.players().actingFor(admin.id()).teleportTo("Guest").join());
        assertThat(code(api.players().details("Nobody").join())).isEqualTo("Players.Unknown");
    }

    @Test
    void inventoryAndChatAreDataNotPanelLines() {
        platform.admin.inventories.put(
                guest.id(), List.of(new PlayerAdmin.Item("Weapon_Sword_Iron", 1), new PlayerAdmin.Item("Rock", 32)));
        platform.chat.say(guest.id(), "Guest", "hello there");

        PlayersApi.Inventory inventory = ok(api.players().inventory("Guest").join());
        assertThat(inventory.online()).isTrue();
        assertThat(inventory.items())
                .containsExactly(
                        new PlayersApi.InventoryItem("Weapon_Sword_Iron", 1), new PlayersApi.InventoryItem("Rock", 32));
        assertThat(api.players().recentChat()).singleElement().satisfies(line -> {
            assertThat(line.player()).isEqualTo(guest.id());
            assertThat(line.playerName()).isEqualTo("Guest");
            assertThat(line.text()).isEqualTo("hello there");
        });

        assertThat(code(api.players().actingFor(guest.id()).inventory("Admin").join()))
                .isEqualTo("Players.Forbidden");
        assertThat(api.players().actingFor(guest.id()).recentChat()).isEmpty();
    }

    @Test
    void defaultsRestoreASectionWithTheRightsOfThePlayer() {
        ok(api.settings().write(REQUIRE, Optional.empty(), "true"));
        assertThat(api.defaults().count(DefaultsApi.Section.SERVER_CONFIGS, Optional.empty(), "require"))
                .isEqualTo(1);
        assertThat(api.defaults().actingFor(guest.id()).count(DefaultsApi.Section.SERVER_CONFIGS, Optional.empty(), ""))
                .isZero();
        assertThat(code(api.defaults()
                        .actingFor(guest.id())
                        .restore(DefaultsApi.Section.PERMISSIONS, Optional.empty(), "")))
                .isEqualTo("Defaults.Forbidden");
        assertThat(code(api.defaults().restore(DefaultsApi.Section.PREFERENCES, Optional.empty(), "")))
                .isEqualTo("Commands.PlayersOnly");

        ok(api.defaults().actingFor(admin.id()).restore(DefaultsApi.Section.SERVER_CONFIGS, Optional.empty(), ""));
        assertThat(platform.serverConfig
                        .entry("RequireJoinPermission")
                        .orElseThrow()
                        .value())
                .isEqualTo("false");
        assertMadeBy(admin);
    }

    @Test
    void openForModFindsTheModOrSaysWhy() {
        assertThat(code(api.controlPanel().openForMod(admin.id(), "nothing-here")))
                .isEqualTo("Mods.Unknown");
        assertThat(code(api.controlPanel().openForMod(admin.id(), "Acme:Base"))).isEqualTo("Mods.NoSettings");
        assertThat(api.controlPanel()
                        .openForMod(PlayerId.of(java.util.UUID.randomUUID()), "Acme:Base")
                        .isFailure())
                .isTrue();
    }
}
