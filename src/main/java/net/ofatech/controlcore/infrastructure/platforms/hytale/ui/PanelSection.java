package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

/** A list of settings in the control panel and the elements it is drawn in. */
enum PanelSection {
    SERVER_CONFIG("#ServerList", "#ServerStatus", "#ServerSearch"),
    SETTINGS("#SettingsList", "#SettingsStatus", "#SettingsSearch"),
    PREFS("#PlayerList", "#PlayerStatus", "#PlayerSearch"),
    KEYMAPS("#KeymapList", "#KeymapStatus", "#KeymapSearch"),
    SERVER_KEYMAPS("#ServerKeymapList", "#ServerKeymapStatus", "#ServerKeymapSearch");

    /** The list the rows are appended to. */
    final String list;
    /** The status line above the list. */
    final String status;
    /** The search field; its dropdowns are {@code <search>Filter} and {@code <search>Match}. */
    final String search;

    PanelSection(String list, String status, String search) {
        this.list = list;
        this.status = status;
        this.search = search;
    }

    /** @return the selector of the row (or heading) at {@code index} */
    String row(int index) {
        return list + "[" + index + "]";
    }
}
