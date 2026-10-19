package net.ofatech.controlcore.domain.model;

/** Which sections of the control panel to show. */
public record ControlPanelRequest(
        boolean includeServerSettings, boolean includePlayerPreferences, boolean includeRegisteredActions) {

    public static final ControlPanelRequest ALL = new ControlPanelRequest(true, true, true);
}
