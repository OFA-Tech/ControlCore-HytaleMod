package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.ControlCoreEvents;
import net.ofatech.controlcore.api.ControlPanelApi;
import net.ofatech.controlcore.api.HudApi;
import net.ofatech.controlcore.api.KeymapsApi;
import net.ofatech.controlcore.api.SettingsApi;

@AutoService(ControlCoreApi.class)
public final class ControlCoreApiService implements ControlCoreApi {

    private final ControlPanelApi controlPanel;
    private final ControlCoreEvents events;
    private final SettingsApi settings;
    private final KeymapsApi keymaps;
    private final HudApi huds;

    public ControlCoreApiService(
            ControlPanelApi controlPanel,
            ControlCoreEvents events,
            SettingsApi settings,
            KeymapsApi keymaps,
            HudApi huds) {
        this.controlPanel = controlPanel;
        this.events = events;
        this.settings = settings;
        this.keymaps = keymaps;
        this.huds = huds;
    }

    @Override
    public ControlPanelApi controlPanel() {
        return controlPanel;
    }

    @Override
    public ControlCoreEvents events() {
        return events;
    }

    @Override
    public SettingsApi settings() {
        return settings;
    }

    @Override
    public KeymapsApi keymaps() {
        return keymaps;
    }

    @Override
    public HudApi huds() {
        return huds;
    }
}
