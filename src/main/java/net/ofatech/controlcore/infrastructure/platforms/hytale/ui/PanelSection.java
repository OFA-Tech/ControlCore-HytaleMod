package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

enum PanelSection {
    SERVER_CONFIG("#ServerList", "#ServerStatus", "#ServerSearch"),
    SETTINGS("#SettingsList", "#SettingsStatus", "#SettingsSearch"),
    PREFS("#PlayerList", "#PlayerStatus", "#PlayerSearch"),
    KEYMAPS("#KeymapList", "#KeymapStatus", "#KeymapSearch"),
    SERVER_KEYMAPS("#ServerKeymapList", "#ServerKeymapStatus", "#ServerKeymapSearch");

    final String list;
    final String status;
    final String search;

    PanelSection(String list, String status, String search) {
        this.list = list;
        this.status = status;
        this.search = search;
    }

    String row(int index) {
        return list + "[" + index + "]";
    }
}
