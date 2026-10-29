package net.ofatech.controlcore.domain.model;

import java.lang.reflect.RecordComponent;

public record PanelRights(
        boolean panelEnabled,
        boolean admin,
        boolean adminPanel,
        boolean history,
        boolean rollback,
        boolean snapshots,
        boolean inputProbe,
        boolean players,
        boolean diagnostics,
        boolean automation) {

    public boolean keepsEverythingOf(PanelRights before) {
        for (RecordComponent component : PanelRights.class.getRecordComponents()) {
            boolean had = flag(before, component);
            if (component.getName().equals("panelEnabled")) {
                had = had && before.adminPanel();
            }
            if (had && !flag(this, component)) {
                return false;
            }
        }
        return true;
    }

    private static boolean flag(PanelRights rights, RecordComponent component) {
        try {
            return (boolean) component.getAccessor().invoke(rights);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
