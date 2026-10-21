package net.ofatech.controlcore.application.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.ofatech.controlcore.application.event.DomainEventBus;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.model.settings.SettingRights;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.fakes.FakePlayerData;
import net.ofatech.controlcore.fakes.FakeSettingPermissionStore;
import net.ofatech.controlcore.fakes.FakeSettingStore;
import net.ofatech.controlcore.fakes.RecordingLog;
import org.junit.jupiter.api.Test;

class SettingPermissionPolicyTest {

    private final RecordingLog log = new RecordingLog();
    private final DomainEventBus bus = new DomainEventBus(log);
    private final SettingsRegistry registry = new SettingsRegistry(
            new FakeSettingStore(),
            new FakePlayerData(),
            bus,
            bus,
            log,
            new net.ofatech.controlcore.application.settings.ModNames());
    private final FakeSettingPermissionStore store = new FakeSettingPermissionStore();
    private final Set<String> granted = new HashSet<>();
    private final SettingPermissionPolicy policy =
            new SettingPermissionPolicy(store, (player, node) -> granted.contains(node), registry);
    private final Optional<PlayerId> player = Optional.of(PlayerId.of(UUID.randomUUID()));

    private final SettingDefinition<Integer> maxHomes = SettingDefinition.server(
                    "homes", "max-homes", SettingType.integer(1, 50), 3)
            .inCategory("Homes");

    @Test
    void serverSettingsAreForAdminsAndPreferencesForEveryoneByDefault() {
        SettingDefinition<Boolean> pref = SettingDefinition.player("homes", "show", SettingType.bool(), true);

        assertThat(policy.rights(player, maxHomes)).isEqualTo(SettingRights.NONE);
        assertThat(policy.rights(player, pref)).isEqualTo(SettingRights.EDIT);
        assertThat(policy.rights(Optional.empty(), maxHomes)).as("the console").isEqualTo(SettingRights.EDIT);

        granted.add("settings");
        assertThat(policy.rights(player, maxHomes)).isEqualTo(SettingRights.EDIT);
    }

    @Test
    void theFirstRuleWithASideWinsFromAdminSettingDownToTheMod() {
        registry.setCategoryPermission("homes", SettingPermission.of("cat.mod"));
        SettingDefinition<Integer> ruled = maxHomes.editPermission("setting.mod");
        granted.add("cat.mod");

        assertThat(policy.rights(player, maxHomes)).as("mod's category rule").isEqualTo(SettingRights.EDIT);
        assertThat(policy.rights(player, ruled))
                .as("the setting's edit rule wins, seeing still comes from the category")
                .isEqualTo(SettingRights.VIEW);

        policy.setRule("mod:homes", "edit", "mod.admin");
        policy.setRule("category:Homes", "edit", "category.admin");
        policy.setRule("homes:max-homes", "edit", "setting.admin");
        assertThat(policy.resolve(ruled).edit().node()).contains("setting.admin");
        policy.setRule("homes:max-homes", "edit", "default");
        assertThat(policy.resolve(ruled).edit().node()).contains("category.admin");
        policy.setRule("category:homes", "both", "default");
        assertThat(policy.resolve(ruled).edit().node()).contains("mod.admin");
        policy.setRule("mod:homes", "edit", "everyone");
        assertThat(policy.rights(player, ruled)).isEqualTo(SettingRights.EDIT);
        assertThat(store.rules).containsOnlyKeys("mod:homes");
    }

    @Test
    void theEditorHidesSettingsAndMakesViewOnlyOnesReadOnly() {
        registry.register(maxHomes.viewPermission(SettingPermission.EVERYONE));
        registry.register(SettingDefinition.server("homes", "secret", SettingType.bool(), false));
        var editor = policy.editor(registry, player);

        var rows = editor.snapshot(SettingScope.SERVER, Optional.empty());
        assertThat(rows).extracting(row -> row.id().toString()).containsExactly("homes:max-homes");
        assertThat(rows.getFirst().access()).isEqualTo(SettingAccess.READ_ONLY);
        assertThat(editor.write(SettingId.of("homes:max-homes"), Optional.empty(), "5")
                        .error()
                        .code())
                .isEqualTo("Settings.Forbidden");
        assertThat(editor.read(SettingId.of("homes:secret"), Optional.empty())
                        .error()
                        .code())
                .isEqualTo("Settings.UnknownSetting");
        assertThat(registry.read(SettingId.of("homes:max-homes"), Optional.empty())
                        .value()
                        .value())
                .isEqualTo("3");
    }

    @Test
    void targetsNeedAPrefixUnlessTheyAreSettingIds() {
        assertThat(SettingPermissionPolicy.target("Homes:Max-Homes").value()).isEqualTo("setting:homes:max-homes");
        assertThat(SettingPermissionPolicy.target("category:Backup").value()).isEqualTo("category:backup");
        assertThat(SettingPermissionPolicy.target("mod:server").value()).isEqualTo("mod:server");
        assertThat(SettingPermissionPolicy.target("homes").isFailure()).isTrue();
        assertThat(policy.setRule("homes:x", "sideways", "a").isFailure()).isTrue();
        assertThat(policy.setRule("homes:x", "view", "two words").isFailure()).isTrue();
    }
}
