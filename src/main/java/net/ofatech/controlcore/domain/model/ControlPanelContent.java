package net.ofatech.controlcore.domain.model;

import net.ofatech.controlcore.domain.guard.Guard;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;

/**
 * What a control panel view shows.
 *
 * @param server current server configuration, for the values that cannot be edited (password)
 * @param serverConfig the editable server configuration fields, as server settings ({@code server:*})
 * @param settings registered settings, listed and edited by the panel
 * @param keymaps the viewer's key bindings, as player settings (one per key action)
 * @param startTab the tab shown first
 */
public record ControlPanelContent(
        ServerSettings server,
        SettingsEditor serverConfig,
        SettingsEditor settings,
        SettingsEditor keymaps,
        ControlPanelTab startTab) {

    public ControlPanelContent {
        Guard.notNull(server, "server");
        Guard.notNull(serverConfig, "serverConfig");
        Guard.notNull(settings, "settings");
        Guard.notNull(keymaps, "keymaps");
        Guard.notNull(startTab, "startTab");
    }
}
