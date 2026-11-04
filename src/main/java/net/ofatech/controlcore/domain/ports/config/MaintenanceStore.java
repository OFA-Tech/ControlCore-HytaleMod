package net.ofatech.controlcore.domain.ports.config;

import net.ofatech.controlcore.domain.model.server.MaintenanceState;

public interface MaintenanceStore {

    MaintenanceState load();

    boolean save(MaintenanceState state);
}
