package net.ofatech.controlcore.application.keymaps;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.ofatech.controlcore.application.settings.ModNames;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.Key;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.fakes.FakeCustomKeyBindStore;
import net.ofatech.controlcore.fakes.FakeKeyBindStore;
import net.ofatech.controlcore.fakes.FakePlayerData;
import net.ofatech.controlcore.fakes.RecordingLog;
import net.ofatech.controlcore.fakes.TestTexts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeymapsEditorTest {

    private static final SettingId HOME = SettingId.of("homes:home");

    private final KeymapRegistry keymaps = new KeymapRegistry(
            new FakeKeyBindStore(),
            new FakeCustomKeyBindStore(),
            new FakePlayerData(),
            (player, node) -> true,
            new RecordingLog());
    private final KeymapsEditor editor = new KeymapsEditor(keymaps, TestTexts.standalone(), new ModNames());
    private final PlayerId alice = PlayerId.of(UUID.randomUUID());
    private final PlayerId bob = PlayerId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        keymaps.register(KeyAction.of("homes", "home")
                .displayName("Go home")
                .command("home")
                .defaultKeys(Key.F));
    }

    @Test
    void keysGoFromTheModToTheServerToThePlayer() {
        assertThat(value(Optional.of(alice))).isEqualTo("F");

        editor.write(HOME, Optional.empty(), "ctrl+f");
        editor.write(HOME, Optional.of(alice), "alt+3");

        assertThat(value(Optional.empty())).isEqualTo("CTRL + F");
        assertThat(value(Optional.of(alice))).as("her own keys win").isEqualTo("ALT + 3");
        assertThat(value(Optional.of(bob)))
                .as("everyone else gets the server's")
                .isEqualTo("CTRL + F");

        editor.reset(HOME, Optional.of(alice));
        assertThat(value(Optional.of(alice))).isEqualTo("CTRL + F");

        editor.reset(HOME, Optional.empty());
        assertThat(value(Optional.of(bob))).as("back to the mod's keys").isEqualTo("F");
    }

    @Test
    void serverRowsShowTheModKeysAsDefaultAndReportChanges() {
        List<SettingChanged> changes = new ArrayList<>();
        editor.onAnyChange(changes::add);

        editor.write(HOME, Optional.empty(), "r");
        SettingSnapshot row =
                editor.snapshot(SettingScope.SERVER, Optional.empty()).getFirst();

        assertThat(row.value()).isEqualTo("R");
        assertThat(row.definition().formattedDefault()).isEqualTo("F");
        assertThat(row.overridden()).isTrue();
        assertThat(changes).singleElement().satisfies(change -> {
            assertThat(change.scope()).isEqualTo(SettingScope.SERVER);
            assertThat(change.newValue()).isEqualTo("R");
        });
    }

    private String value(Optional<PlayerId> player) {
        return editor.read(HOME, player).value().value();
    }
}
