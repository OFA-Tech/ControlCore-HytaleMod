package net.ofatech.controlcore.domain.model;

import net.ofatech.controlcore.domain.guard.Guard;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;

/**
 * What a control panel view shows.
 *
 * @param server current server configuration (read-only)
 * @param settings registered settings, listed and edited by the panel
 * @param startTab the tab shown first
 */
public record ControlPanelContent(ServerSettings server, SettingsEditor settings, ControlPanelTab startTab) {

    public ControlPanelContent {
        Guard.notNull(server, "server");
        Guard.notNull(settings, "settings");
        Guard.notNull(startTab, "startTab");
    }
}
