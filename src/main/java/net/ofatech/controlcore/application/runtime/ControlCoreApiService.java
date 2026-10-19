package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.ControlCoreEvents;
import net.ofatech.controlcore.api.ControlPanelApi;

@AutoService(ControlCoreApi.class)
public final class ControlCoreApiService implements ControlCoreApi {

    private final ControlPanelApi controlPanel;
    private final ControlCoreEvents events;

    public ControlCoreApiService(ControlPanelApi controlPanel, ControlCoreEvents events) {
        this.controlPanel = controlPanel;
        this.events = events;
    }

    @Override
    public ControlPanelApi controlPanel() {
        return controlPanel;
    }

    @Override
    public ControlCoreEvents events() {
        return events;
    }
}
