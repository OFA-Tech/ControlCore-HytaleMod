package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import org.junit.jupiter.api.Test;

class SectionStateUndoTest {

    private static final SettingDefinition<Integer> MAX =
            SettingDefinition.server("homes", "max", SettingType.integer(1, 9), 3);
    private static final SettingId ID = MAX.id();

    private final SectionState state = new SectionState();

    private static SettingSnapshot snapshot(String value) {
        return new SettingSnapshot(MAX, value, false, SettingAccess.EDITABLE);
    }

    @Test
    void aListChangeAlwaysKeepsTheOldValueForUndoAndClearsTheError() {
        state.fail(ID, "bad");

        state.listChanged(snapshot("3"), snapshot("4"));

        assertThat(state.error(ID)).isNull();
        assertThat(state.shows(ID, "4")).isTrue();
        assertThat(state.takeUndo(ID)).isEqualTo("3");
    }

    @Test
    void undoingClearsTheRowAndAFailedUndoPutsTheValueBack() {
        state.saved(snapshot("3"), snapshot("5"), true);
        String previous = state.takeUndo(ID);

        state.undoFailed(ID, previous, "nope");

        assertThat(state.hasUndo(ID)).isTrue();
        assertThat(state.error(ID)).isEqualTo("nope");

        state.markTyped(ID, "8", false);
        state.undone(snapshot(state.takeUndo(ID)));

        assertThat(state.error(ID)).isNull();
        assertThat(state.isUnsaved(ID)).isFalse();
        assertThat(state.shows(ID, "3")).isTrue();
        assertThat(state.hasUndo(ID)).isFalse();
    }

    @Test
    void latestOrFallsBackUntilTheRowIsRemembered() {
        SettingSnapshot fallback = snapshot("3");
        assertThat(state.latestOr(fallback)).isSameAs(fallback);

        SettingSnapshot remembered = snapshot("6");
        state.remember(remembered);

        assertThat(state.latestOr(fallback)).isSameAs(remembered);
        assertThat(state.shows(ID, "3")).isFalse();
    }
}
