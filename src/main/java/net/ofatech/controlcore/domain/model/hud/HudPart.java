package net.ofatech.controlcore.domain.model.hud;

import java.util.Locale;
import net.ofatech.controlcore.domain.model.text.Text;

/** A part of the game's own HUD that can be shown or hidden for one player. */
public enum HudPart {
    HOTBAR("Hotbar"),
    HEALTH("Health"),
    STAMINA("Stamina"),
    MANA("Mana"),
    OXYGEN("Oxygen"),
    STATUS_ICONS("Status icons"),
    ABILITIES("Abilities"),
    AMMO_INDICATOR("Ammo indicator"),
    RETICLE("Crosshair"),
    COMPASS("Compass"),
    SPEEDOMETER("Speedometer"),
    CHAT("Chat"),
    NOTIFICATIONS("Notifications"),
    KILL_FEED("Kill feed"),
    EVENT_TITLE("Event titles"),
    BOSS_BAR("Boss bar"),
    OBJECTIVE_PANEL("Objectives"),
    PORTAL_PANEL("Portal panel"),
    SLEEP("Sleep"),
    INPUT_BINDINGS("Key hints"),
    UTILITY_SLOT_SELECTOR("Utility slot selector"),
    BLOCK_VARIANT_SELECTOR("Block variant selector"),
    BUILDER_TOOLS_LEGEND("Builder tools legend");

    private final String englishName;

    HudPart(String englishName) {
        this.englishName = englishName;
    }

    /** @return the part's id in setting ids, for example {@code kill-feed} */
    public String slug() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** @return the part's name as players read it */
    public Text nameText() {
        return Text.key("controlcore.hud.parts." + name().toLowerCase(Locale.ROOT), englishName);
    }
}
