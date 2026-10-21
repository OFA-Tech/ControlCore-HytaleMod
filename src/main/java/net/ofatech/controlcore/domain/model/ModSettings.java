package net.ofatech.controlcore.domain.model;

public record ModSettings(boolean controlPanelEnabled) {

    public static final ModSettings DEFAULTS = new ModSettings(true);
}
