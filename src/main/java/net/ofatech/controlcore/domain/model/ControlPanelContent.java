package net.ofatech.controlcore.domain.model;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.ofatech.controlcore.domain.guard.Guard;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBindEditor;
import net.ofatech.controlcore.domain.model.mods.ModsView;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;

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
 * @param translator shows text in the viewer's language (since API 1.5.0)
 * @param pendingRestart names of the server config keys changed since start that apply after a restart
 * @param mods the Mods tab as the viewer sees it
 * @param focus a mod id (the first part of setting ids) whose settings the panel opens on, if any
 */
public record ControlPanelContent(
        ServerSettings server,
        SettingsEditor serverConfig,
        SettingsEditor settings,
        SettingsEditor keymaps,
        CustomKeyBindEditor customKeyBinds,
        ControlPanelTab startTab,
        boolean manageKeys,
        Translator translator,
        Supplier<List<Text>> pendingRestart,
        ModsView mods,
        Optional<String> focus) {

    public ControlPanelContent {
        Guard.notNull(server, "server");
        Guard.notNull(serverConfig, "serverConfig");
        Guard.notNull(settings, "settings");
        Guard.notNull(keymaps, "keymaps");
        Guard.notNull(customKeyBinds, "customKeyBinds");
        Guard.notNull(startTab, "startTab");
        Guard.notNull(translator, "translator");
        Guard.notNull(pendingRestart, "pendingRestart");
        Guard.notNull(mods, "mods");
        Guard.notNull(focus, "focus");
    }
}
