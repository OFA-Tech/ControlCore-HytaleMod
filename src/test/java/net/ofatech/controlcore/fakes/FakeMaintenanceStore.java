package net.ofatech.controlcore.fakes;

import net.ofatech.controlcore.domain.model.server.MaintenanceState;
import net.ofatech.controlcore.domain.ports.config.MaintenanceStore;

public final class FakeMaintenanceStore implements MaintenanceStore {

    public MaintenanceState state = MaintenanceState.OFF;
    public boolean failWrites;

    @Override
    public MaintenanceState load() {
        return state;
    }

    @Override
    public boolean save(MaintenanceState changed) {
        if (failWrites) {
            return false;
        }
        state = changed;
        return true;
    }
}
