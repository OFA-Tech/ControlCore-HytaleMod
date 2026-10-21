package net.ofatech.controlcore.domain.model;

/**
 * Which sections of the control panel to show, passed to
 * {@link net.ofatech.controlcore.api.ControlPanelApi#open(PlayerId, ControlPanelRequest)}.
 *
 * @param includeServerSettings the admin tabs: Server Configs and Mod Settings
 * @param includePlayerPreferences the viewer's own preferences
 * @param includeRegisteredActions the Keymaps tab: the viewer's own key bindings (since API 1.3.0 a player tab)
 */
public record ControlPanelRequest(
        boolean includeServerSettings, boolean includePlayerPreferences, boolean includeRegisteredActions) {

    /** Every tab, as shown by {@code /open-admin-config}. */
    public static final ControlPanelRequest ALL = new ControlPanelRequest(true, true, true);

    /**
     * Only the viewer's own preferences and key bindings, as shown by {@code /prefs}; safe for any player.
     *
     * @since API 1.2.0
     */
    public static final ControlPanelRequest PLAYER_PREFERENCES = new ControlPanelRequest(false, true, true);

    /**
     * Tells whether the request shows an admin section; those need the control panel to be enabled.
     *
     * @return whether an admin section is shown (Server Configs and Mod Settings)
     */
    public boolean includesAdminSections() {
        return includeServerSettings;
    }

    /**
     * Tells whether the request shows no section at all.
     *
     * @return whether nothing is shown at all
     */
    public boolean isEmpty() {
        return !includeServerSettings && !includePlayerPreferences && !includeRegisteredActions;
    }
}
