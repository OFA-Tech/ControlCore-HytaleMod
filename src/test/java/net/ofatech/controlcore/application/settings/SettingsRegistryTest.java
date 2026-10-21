package net.ofatech.controlcore.application.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.ofatech.controlcore.application.event.DomainEventBus;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.result.ErrorType;
import net.ofatech.controlcore.fakes.FakePlayerData;
import net.ofatech.controlcore.fakes.FakeSettingStore;
import net.ofatech.controlcore.fakes.RecordingLog;
import org.junit.jupiter.api.Test;

class SettingsRegistryTest {

    private static final SettingId MAX_HOMES = SettingId.of("homes:max-homes");
    private static final SettingId SHOW_HINTS = SettingId.of("homes:show-hints");

    private final RecordingLog log = new RecordingLog();
    private final DomainEventBus bus = new DomainEventBus(log);
    private final FakeSettingStore store = new FakeSettingStore();
    private final FakePlayerData playerData = new FakePlayerData();
    private final SettingsRegistry registry = new SettingsRegistry(store, playerData, bus, bus, log, new ModNames());
    private final PlayerId alice = PlayerId.of(UUID.randomUUID());
    private final PlayerId bob = PlayerId.of(UUID.randomUUID());

    private final SettingDefinition<Integer> maxHomes = SettingDefinition.server(
                    MAX_HOMES.namespace(), MAX_HOMES.key(), SettingType.integer(1, 50), 3)
            .named("Max homes");
    private final SettingDefinition<Boolean> showHints =
            SettingDefinition.player(SHOW_HINTS.namespace(), SHOW_HINTS.key(), SettingType.bool(), true);

    @Test
    void aNewSettingStartsAtItsDefault() {
        var setting = registry.register(maxHomes).value();

        assertThat(setting.get()).isEqualTo(3);
        assertThat(registry.read(MAX_HOMES, Optional.empty()).value().overridden())
                .isFalse();
        assertThat(registry.definitions()).extracting(SettingDefinition::id).containsExactly(MAX_HOMES);
    }

    @Test
    void serverValuesAreValidatedStoredAndFlushed() {
        var setting = registry.register(maxHomes).value();

        var written = registry.write(MAX_HOMES, Optional.empty(), "10");

        assertThat(written.value().value()).isEqualTo("10");
        assertThat(written.value().overridden()).isTrue();
        assertThat(setting.get()).isEqualTo(10);
        assertThat(store.values).containsEntry(MAX_HOMES, "10");
        assertThat(store.flushes).isEqualTo(1);
        assertThat(registry.write(MAX_HOMES, Optional.empty(), "99").error().code())
                .isEqualTo(SettingType.INVALID_VALUE);
        assertThat(setting.set(0).isFailure()).isTrue();
        assertThat(setting.get()).isEqualTo(10);
    }

    @Test
    void resetReturnsToTheDefault() {
        var setting = registry.register(maxHomes).value();
        setting.set(20);

        var reset = registry.reset(MAX_HOMES, Optional.empty());

        assertThat(reset.value().value()).isEqualTo("3");
        assertThat(store.values).doesNotContainKey(MAX_HOMES);
        assertThat(setting.get()).isEqualTo(3);
    }

    @Test
    void playerPreferencesAreKeptPerPlayer() {
        var setting = registry.register(showHints).value();

        setting.set(alice, false);

        assertThat(setting.get(alice)).isFalse();
        assertThat(setting.get(bob)).isTrue();
        assertThat(setting.get()).isTrue();
        assertThat(playerData.get(alice, "setting:homes:show-hints")).contains("false");
        assertThat(registry.snapshot(SettingScope.PLAYER, Optional.of(alice)))
                .singleElement()
                .satisfies(snapshot -> assertThat(snapshot.value()).isEqualTo("false"));
    }

    @Test
    void scopesCannotBeMixed() {
        registry.register(maxHomes);
        registry.register(showHints);

        assertThat(registry.write(MAX_HOMES, Optional.of(alice), "5").error().code())
                .isEqualTo("Settings.WrongScope");
        assertThat(registry.write(SHOW_HINTS, Optional.empty(), "false").error().code())
                .isEqualTo("Settings.WrongScope");
    }

    @Test
    void unknownSettingsAreReported() {
        var result = registry.write(SettingId.of("nope:nothing"), Optional.empty(), "1");

        assertThat(result.error().type()).isEqualTo(ErrorType.NOT_FOUND);
        assertThat(result.error().code()).isEqualTo("Settings.UnknownSetting");
    }

    @Test
    void reRegisteringWithTheSameValueTypeReplacesTheDefinition() {
        var first = registry.register(maxHomes).value();
        first.set(7);

        var second = registry.register(maxHomes.named("Homes limit").withDefault(5));

        assertThat(second.isSuccess()).isTrue();
        assertThat(first.definition().displayName()).isEqualTo("Homes limit");
        assertThat(second.value().get()).isEqualTo(7);
    }

    @Test
    void reRegisteringWithAnotherTypeOrScopeConflicts() {
        registry.register(maxHomes);

        var otherType = registry.register(
                SettingDefinition.server(MAX_HOMES.namespace(), MAX_HOMES.key(), SettingType.bool(), true));
        var otherScope = registry.register(
                SettingDefinition.player(MAX_HOMES.namespace(), MAX_HOMES.key(), SettingType.integer(1, 50), 3));

        assertThat(otherType.error().code()).isEqualTo("Settings.Conflict");
        assertThat(otherScope.error().type()).isEqualTo(ErrorType.CONFLICT);
    }

    @Test
    void changesArePublishedOnlyWhenTheValueChanges() {
        var setting = registry.register(maxHomes).value();
        List<SettingChanged> handle = new ArrayList<>();
        List<SettingChanged> published = new ArrayList<>();
        var subscription = setting.onChange(handle::add);
        bus.subscribe(SettingChanged.class, published::add);
        registry.register(showHints).value().set(alice, false);

        setting.set(4);
        setting.set(4);
        setting.reset();
        subscription.close();
        setting.set(9);

        assertThat(handle)
                .containsExactly(
                        new SettingChanged(MAX_HOMES, SettingScope.SERVER, Optional.empty(), "3", "4"),
                        new SettingChanged(MAX_HOMES, SettingScope.SERVER, Optional.empty(), "4", "3"));
        assertThat(published).hasSize(4);
        assertThat(published.getFirst().player()).contains(alice);
    }

    @Test
    void anInvalidStoredValueFallsBackToTheDefaultAndWarnsOnce() {
        store.values.put(MAX_HOMES, "lots");
        var setting = registry.register(maxHomes).value();

        assertThat(setting.get()).isEqualTo(3);
        assertThat(setting.get()).isEqualTo(3);
        assertThat(registry.read(MAX_HOMES, Optional.empty()).value().overridden())
                .isFalse();
        assertThat(log.lines.stream().filter(line -> line.startsWith("WARN"))).hasSize(1);
    }

    @Test
    void headingsUseTheModNameSetBeforeOrAfterRegistering() {
        registry.register(SettingDefinition.server("home_tp", "max", SettingType.integer(1, 9), 3)
                .inCategory("Limits"));
        assertThat(registry.definition(SettingId.of("home_tp:max"))
                        .orElseThrow()
                        .heading())
                .isEqualTo("Home Tp: Limits");

        registry.setModName("home_tp", "Home Teleports");
        registry.register(SettingDefinition.player("home_tp", "hints", SettingType.bool(), true));

        assertThat(registry.definitions())
                .extracting(SettingDefinition::heading)
                .contains("Home Teleports: Limits", "Home Teleports");
        assertThat(registry.modName("controlcore").fallbackText()).isEqualTo("ControlCore");
    }
}
