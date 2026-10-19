package net.ofatech.controlcore.domain.model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The tabs of the control panel, in display order. */
public enum ControlPanelTab {
    SERVER_CONFIGS("Server Configs"),
    MOD_SETTINGS("Mod Settings"),
    PLAYER_PREFS("Player Prefs"),
    KEYMAPS("Keymaps");

    private final String label;

    ControlPanelTab(String label) {
        this.label = label;
    }

    /** @return the name shown on the tab button */
    public String label() {
        return label;
    }

    /** @return every label, in display order */
    public static List<String> labels() {
        return Arrays.stream(values()).map(ControlPanelTab::label).toList();
    }

    /** @return the tab with this label (case-insensitive) */
    public static Optional<ControlPanelTab> fromLabel(String label) {
        return Arrays.stream(values())
                .filter(tab -> tab.label.equalsIgnoreCase(label))
                .findFirst();
    }
}
