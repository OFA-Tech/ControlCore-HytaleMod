package net.ofatech.controlcore.fakes;

import net.ofatech.controlcore.domain.model.server.MaintenanceState;
import net.ofatech.controlcore.domain.ports.config.MaintenanceStore;

public final class FakeMaintenanceStore implements MaintenanceStore {

    public MaintenanceState state = MaintenanceState.OFF;
    public boolean failWrites;
    public boolean failReads;

    @Override
    public MaintenanceState load() {
        if (failReads) {
            throw new IllegalStateException("MaintenanceState.json is unreadable");
        }
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
