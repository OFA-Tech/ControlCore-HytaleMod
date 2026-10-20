package net.ofatech.controlcore.domain.model;

import net.ofatech.controlcore.domain.guard.Guard;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBindEditor;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;

/**
 * What a control panel view shows.
 *
 * @param server current server configuration, for the values that cannot be edited (password)
 * @param serverConfig the editable server configuration fields, as server settings ({@code server:*})
 * @param settings registered settings, listed and edited by the panel
 * @param keymaps the viewer's key bindings, as player settings (one per key action)
 * @param customKeyBinds creates admins' own key actions (shown in the admin panel's Keymaps tab)
 * @param startTab the tab shown first
 * @param manageKeys whether the viewer may change the server's keys and admins' own binds
 */
public record ControlPanelContent(
        ServerSettings server,
        SettingsEditor serverConfig,
        SettingsEditor settings,
        SettingsEditor keymaps,
        CustomKeyBindEditor customKeyBinds,
        ControlPanelTab startTab,
        boolean manageKeys) {

    public ControlPanelContent {
        Guard.notNull(server, "server");
        Guard.notNull(serverConfig, "serverConfig");
        Guard.notNull(settings, "settings");
        Guard.notNull(keymaps, "keymaps");
        Guard.notNull(customKeyBinds, "customKeyBinds");
        Guard.notNull(startTab, "startTab");
    }
}
