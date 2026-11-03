package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import org.junit.jupiter.api.Test;

class SectionStateTest {

    private static final SettingDefinition<Integer> MAX =
            SettingDefinition.server("homes", "max", SettingType.integer(1, 9), 3);
    private static final SettingId ID = MAX.id();

    private final SectionState state = new SectionState();

    private static SettingSnapshot snapshot(String value, SettingAccess access) {
        return new SettingSnapshot(MAX, value, false, access);
    }

    @Test
    void typingFlipsUnsavedOnlyWhenTheShownValueStartsOrStopsDiffering() {
        assertThat(state.markTyped(ID, "5", false)).isTrue();
        assertThat(state.markTyped(ID, "6", false)).isFalse();
        assertThat(state.typed(ID)).isEqualTo("6");
        assertThat(state.markTyped(ID, "3", true)).isTrue();
        assertThat(state.isUnsaved(ID)).isFalse();
        assertThat(state.markTyped(ID, "3", true)).isFalse();
    }

    @Test
    void savingClearsTheRowAndKeepsThePreviousValueForUndoOnce() {
        SettingSnapshot before = snapshot("3", SettingAccess.EDITABLE);
        SettingSnapshot after = snapshot("5", SettingAccess.EDITABLE);
        state.markTyped(ID, "5", false);
        state.fail(ID, "bad");

        state.saved(before, after, true);

        assertThat(state.isUnsaved(ID)).isFalse();
        assertThat(state.error(ID)).isNull();
        assertThat(state.latest(ID)).isEqualTo(after);
        assertThat(state.shows(ID, "5")).isTrue();
        assertThat(state.hasUndo(ID)).isTrue();
        assertThat(state.takeUndo(ID)).isEqualTo("3");
        assertThat(state.takeUndo(ID)).isNull();
    }

    @Test
    void savingTheSameValueOrWithoutUndoLeavesNothingToUndo() {
        state.saved(snapshot("3", SettingAccess.EDITABLE), snapshot("3", SettingAccess.EDITABLE), true);
        state.saved(snapshot("3", SettingAccess.EDITABLE), snapshot("5", SettingAccess.EDITABLE), false);
        state.saved(null, snapshot("7", SettingAccess.EDITABLE), true);

        assertThat(state.hasUndo(ID)).isFalse();
    }

    @Test
    void theRowStateFollowsLockRestartErrorUnsavedThenValue() {
        assertThat(state.state(snapshot("3", SettingAccess.EDITABLE), s -> false))
                .isEqualTo(SettingRows.State.DEFAULT);
        assertThat(state.state(snapshot("5", SettingAccess.EDITABLE), s -> false))
                .isEqualTo(SettingRows.State.CHANGED);
        state.markTyped(ID, "6", false);
        assertThat(state.state(snapshot("5", SettingAccess.EDITABLE), s -> false))
                .isEqualTo(SettingRows.State.UNSAVED);
        state.fail(ID, "bad");
        assertThat(state.state(snapshot("5", SettingAccess.EDITABLE), s -> true))
                .isEqualTo(SettingRows.State.ERROR);
        assertThat(state.state(snapshot("5", SettingAccess.APPLIES_AFTER_RESTART), s -> true))
                .isEqualTo(SettingRows.State.RESTART);
        assertThat(state.state(snapshot("5", SettingAccess.READ_ONLY), s -> true))
                .isEqualTo(SettingRows.State.LOCKED);
    }

    @Test
    void rebuildingForgetsRowPositionsButKeepsEdits() {
        state.index(ID, 4);
        state.inlineColor(ID);
        state.markTyped(ID, "6", false);

        state.startBuild();

        assertThat(state.index(ID)).isNull();
        assertThat(state.isInlineColor(ID)).isFalse();
        assertThat(state.typed(ID)).isEqualTo("6");
    }
}
