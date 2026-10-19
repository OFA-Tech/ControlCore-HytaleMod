package net.ofatech.controlcore.domain.model;

/**
 * User-editable mod settings ({@code Settings.json}).
 *
 * @param controlPanelEnabled whether admins can open the control panel
 */
public record ModSettings(boolean controlPanelEnabled) {

    public static final ModSettings DEFAULTS = new ModSettings(true);
}
