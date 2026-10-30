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

    private static final String MASTER_SWITCH = "panelEnabled";

    public boolean opensAdminPanel() {
        return adminPanel && panelEnabled;
    }

    public boolean keepsEverythingOf(PanelRights before) {
        if (before.opensAdminPanel() && !opensAdminPanel()) {
            return false;
        }
        for (RecordComponent component : PanelRights.class.getRecordComponents()) {
            boolean had = flag(before, component) && !component.getName().equals(MASTER_SWITCH);
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
