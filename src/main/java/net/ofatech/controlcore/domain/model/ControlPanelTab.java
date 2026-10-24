package net.ofatech.controlcore.domain.model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum ControlPanelTab {
    SERVER_CONFIGS("Server Configs"),
    MOD_SETTINGS("Mod Settings"),
    PLAYER_PREFS("Player Prefs"),
    KEYMAPS("Keymaps"),
    MODS("Mods"),
    PERMISSIONS("Permissions"),
    HISTORY("History");

    private final String label;

    ControlPanelTab(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static List<String> labels() {
        return Arrays.stream(values()).map(ControlPanelTab::label).toList();
    }

    public static Optional<ControlPanelTab> fromLabel(String label) {
        return Arrays.stream(values())
                .filter(tab -> tab.label.equalsIgnoreCase(label))
                .findFirst();
    }
}
