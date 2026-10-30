package net.ofatech.controlcore.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PanelRightsBanca7Test {

    private static final RecordComponent[] COMPONENTS = PanelRights.class.getRecordComponents();

    @Test
    void everyFieldIsABooleanSoNoneIsSkipped() {
        assertThat(Arrays.stream(COMPONENTS)
                        .map(RecordComponent::getType)
                        .distinct()
                        .toList())
                .containsExactly(boolean.class);
        assertThat(Arrays.stream(COMPONENTS).map(RecordComponent::getName).toList())
                .containsExactly(
                        "panelEnabled",
                        "admin",
                        "adminPanel",
                        "history",
                        "rollback",
                        "snapshots",
                        "inputProbe",
                        "players",
                        "diagnostics",
                        "automation");
    }

    @Test
    void sameRightsAndNoRightsAreKept() throws ReflectiveOperationException {
        PanelRights all = with(List.of());
        PanelRights none = make(List.of());

        assertThat(all.keepsEverythingOf(all)).isTrue();
        assertThat(none.keepsEverythingOf(none)).isTrue();
        assertThat(all.keepsEverythingOf(none)).isTrue();
        assertThat(none.keepsEverythingOf(all)).isFalse();
    }

    @Test
    void eachSingleRightLostFromASingleRightHolderIsNoticed() throws ReflectiveOperationException {
        for (RecordComponent component : COMPONENTS) {
            String name = component.getName();
            if (name.equals("panelEnabled")) {
                continue;
            }
            PanelRights only = onlyWith(name);
            assertThat(make(List.of()).keepsEverythingOf(only)).as(name).isFalse();
            assertThat(only.keepsEverythingOf(only)).as(name).isTrue();
        }
    }

    @Test
    void theMasterSwitchCountsForAnAdminPanelHolder() throws ReflectiveOperationException {
        PanelRights before = make(List.of("panelEnabled", "adminPanel"));

        assertThat(make(List.of("adminPanel")).keepsEverythingOf(before)).isFalse();
        assertThat(make(List.of("panelEnabled")).keepsEverythingOf(before)).isFalse();
        assertThat(make(List.of("panelEnabled", "adminPanel", "history")).keepsEverythingOf(before))
                .isTrue();
    }

    @Test
    void gainingTheAdminPanelWhileTheSwitchGoesOffIsNotALoss() throws ReflectiveOperationException {
        PanelRights before = make(List.of("panelEnabled"));

        assertThat(make(List.of("adminPanel")).keepsEverythingOf(before)).isTrue();
    }

    private static PanelRights onlyWith(String name) throws ReflectiveOperationException {
        return make(List.of(name));
    }

    private static PanelRights with(List<String> missing) throws ReflectiveOperationException {
        Object[] values = new Object[COMPONENTS.length];
        for (int i = 0; i < COMPONENTS.length; i++) {
            values[i] = !missing.contains(COMPONENTS[i].getName());
        }
        return build(values);
    }

    private static PanelRights make(List<String> present) throws ReflectiveOperationException {
        Object[] values = new Object[COMPONENTS.length];
        for (int i = 0; i < COMPONENTS.length; i++) {
            values[i] = present.contains(COMPONENTS[i].getName());
        }
        return build(values);
    }

    private static PanelRights build(Object[] values) throws ReflectiveOperationException {
        Class<?>[] types = new Class<?>[values.length];
        Arrays.fill(types, boolean.class);
        return PanelRights.class.getDeclaredConstructor(types).newInstance(values);
    }
}
