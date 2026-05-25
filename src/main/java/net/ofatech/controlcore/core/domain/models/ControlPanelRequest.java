package net.ofatech.controlcore.core.domain.models;

public record ControlPanelRequest(
    boolean includeServerSettings,
    boolean includePlayerPreferences,
    boolean includeRegisteredActions
) {}
