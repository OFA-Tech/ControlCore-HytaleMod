package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import net.ofatech.controlcore.application.event.DomainEventBus;
import net.ofatech.controlcore.application.settings.ModNames;
import net.ofatech.controlcore.application.settings.SettingsRegistry;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.fakes.FakePlayerData;
import net.ofatech.controlcore.fakes.FakeSettingStore;
import net.ofatech.controlcore.fakes.RecordingLog;
import net.ofatech.controlcore.fakes.TestTexts;
import org.junit.jupiter.api.Test;

class SettingsSectionTest {

    private final RecordingLog log = new RecordingLog();
    private final DomainEventBus bus = new DomainEventBus(log);
    private final SettingsRegistry registry =
            new SettingsRegistry(new FakeSettingStore(), new FakePlayerData(), bus, bus, log, new ModNames());
    private final PlayerId viewer = PlayerId.of(UUID.randomUUID());

    private SettingsSection section(SettingScope scope) {
        return new SettingsSection(registry, scope, Optional.of(viewer), "Nothing here.", TestTexts.english());
    }

    @Test
    void rowsAreGroupedByCategoryWithTheRightEditor() {
        registry.register(SettingDefinition.server("homes", "max", SettingType.integer(1, 9), 3)
                .inCategory("Limits"));
        registry.register(SettingDefinition.server("homes", "pvp", SettingType.bool(), false)
                .inCategory("Limits"));
        registry.register(SettingDefinition.server("chat", "mode", SettingType.choice("Local", "Global"), "Local"));
        registry.register(SettingDefinition.player("chat", "sounds", SettingType.bool(), true));

        var entries = section(SettingScope.SERVER).entries();

        assertThat(entries)
                .extracting(entry -> switch (entry) {
                    case SettingsSection.Heading heading -> "# " + heading.text();
                    case SettingsSection.Row row -> row.kind() + " " + row.id();
                })
                .containsExactly(
                        "# Chat", "CHOICE chat:mode", "# Homes: Limits", "INTEGER homes:max", "TOGGLE homes:pvp");
    }

    @Test
    void anEmptySectionExplainsItself() {
        assertThat(section(SettingScope.PLAYER).entries())
                .containsExactly(new SettingsSection.Heading("Nothing here."));
    }

    @Test
    void actionsWriteTheViewersOwnPreferences() {
        var sounds = registry.register(SettingDefinition.player("chat", "sounds", SettingType.bool(), true))
                .value();
        var section = section(SettingScope.PLAYER);

        var unchecked = section.check("chat:sounds", false);
        var missing = section.check("chat:sounds", null);

        assertThat(unchecked.value().value()).isEqualTo("false");
        assertThat(sounds.get(viewer)).isFalse();
        assertThat(sounds.get(PlayerId.of(UUID.randomUUID()))).isTrue();
        assertThat(missing.error().code()).isEqualTo("ControlPanel.MissingValue");
        assertThat(section.reset("chat:sounds").value().overridden()).isFalse();
        assertThat(sounds.get(viewer)).isTrue();
    }

    @Test
    void serverSectionsIgnoreTheViewerAndReportBadInput() {
        var max = registry.register(SettingDefinition.server("homes", "max", SettingType.integer(1, 9), 3)
                        .named("Max homes")
                        .describedAs("Homes per player"))
                .value();
        var section = section(SettingScope.SERVER);

        var saved = section.save("homes:max", " 7 ");
        var invalid = section.save("homes:max", "70");
        var unknown = section.save("not an id", "1");

        assertThat(saved.value().value()).isEqualTo("7");
        assertThat(max.get()).isEqualTo(7);
        assertThat(section.saved(saved.value())).isEqualTo("Saved: Max homes = 7");
        assertThat(invalid.error().description()).contains("whole number from 1 to 9");
        assertThat(unknown.error().code()).isEqualTo("Settings.InvalidId");
        assertThat(section.description(saved.value())).isEqualTo("Homes per player · Default: 3");
    }

    @Test
    void sectionsOnlyCareAboutTheirOwnChanges() {
        registry.register(SettingDefinition.server("homes", "max", SettingType.integer(1, 9), 3));
        var sounds = registry.register(SettingDefinition.player("chat", "sounds", SettingType.bool(), true))
                .value();
        var changes = new ArrayList<SettingChanged>();
        registry.onAnyChange(changes::add);
        var server = section(SettingScope.SERVER);
        var prefs = section(SettingScope.PLAYER);

        sounds.set(PlayerId.of(UUID.randomUUID()), false);
        sounds.set(viewer, false);
        registry.write(SettingId.of("homes:max"), Optional.empty(), "5");

        assertThat(changes).hasSize(3);
        assertThat(changes).extracting(prefs::concerns).containsExactly(false, true, false);
        assertThat(changes).extracting(server::concerns).containsExactly(false, false, true);
        assertThat(prefs.current(changes.get(1).id()).value().value()).isEqualTo("false");
    }

    @Test
    void choiceOptionsAreOffered() {
        registry.register(SettingDefinition.server("chat", "mode", SettingType.choice("Local", "Global"), "Local"));
        var row = (SettingsSection.Row) section(SettingScope.SERVER).entries().get(1);

        assertThat(SettingsSection.options(row.snapshot())).containsExactly("Local", "Global");
        assertThat(section(SettingScope.SERVER)
                        .save("chat:mode", "global")
                        .value()
                        .value())
                .isEqualTo("Global");
    }

    @Test
    void numbersWithAUsableRangeGetASlider() {
        registry.register(SettingDefinition.server("homes", "max", SettingType.integer(1, 9), 3));
        registry.register(SettingDefinition.server("homes", "ratio", SettingType.decimal(0, 2.5), 1.0));
        registry.register(SettingDefinition.server("homes", "huge", SettingType.integer(0, Integer.MAX_VALUE), 0));
        var snapshots = registry.snapshot(SettingScope.SERVER, Optional.empty());
        var byId = new HashMap<String, SettingSnapshot>();
        snapshots.forEach(snapshot -> byId.put(snapshot.id().toString(), snapshot));

        assertThat(SettingsSection.kindOf(byId.get("homes:max"))).isEqualTo(SettingsSection.Kind.INTEGER);
        assertThat(SettingsSection.kindOf(byId.get("homes:ratio"))).isEqualTo(SettingsSection.Kind.DECIMAL);
        assertThat(SettingsSection.kindOf(byId.get("homes:huge")))
                .as("too wide")
                .isEqualTo(SettingsSection.Kind.TEXT);

        var slider = SettingsSection.Slider.of(byId.get("homes:ratio"));
        assertThat(slider.step()).isEqualTo(0.05);
        assertThat(slider.text(1.2312, false)).isEqualTo("1.25");
        assertThat(slider.text(9, false)).as("kept in range").isEqualTo("2.5");
        assertThat(SettingsSection.Slider.of(byId.get("homes:max")).text(4.6, true))
                .isEqualTo("5");
        assertThat(slider.position("oops")).isEqualTo(0);
    }

    @Test
    void aMessageAndItsColourShareOneRow() {
        registry.register(SettingDefinition.server("homes", "greeting", SettingType.text(64), "Hi")
                .coloredBy("homes:greeting-color"));
        registry.register(SettingDefinition.server("homes", "greeting-color", SettingType.color(), "#ffaa00"));
        registry.register(SettingDefinition.server("homes", "other-color", SettingType.color(), "#ffffff"));

        var rows = section(SettingScope.SERVER).entries().stream()
                .filter(entry -> entry instanceof SettingsSection.Row)
                .map(entry -> (SettingsSection.Row) entry)
                .toList();

        assertThat(rows).extracting(row -> row.id().toString()).containsExactly("homes:greeting", "homes:other-color");
        assertThat(rows.get(0).color()).map(color -> color.id().toString()).contains("homes:greeting-color");
        assertThat(rows.get(1).color()).isEmpty();
    }
}
