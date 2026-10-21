package net.ofatech.controlcore.domain.model.hud;

import java.util.Locale;
import net.ofatech.controlcore.domain.model.text.Text;

/** A part of the game's own HUD that can be shown or hidden for one player. */
public enum HudPart {
    /** The hotbar of item slots. */
    HOTBAR("Hotbar"),
    /** The health bar. */
    HEALTH("Health"),
    /** The stamina bar. */
    STAMINA("Stamina"),
    /** The mana bar. */
    MANA("Mana"),
    /** The oxygen (breath) bar. */
    OXYGEN("Oxygen"),
    /** The status effect icons. */
    STATUS_ICONS("Status icons"),
    /** The ability slots. */
    ABILITIES("Abilities"),
    /** The ammo indicator. */
    AMMO_INDICATOR("Ammo indicator"),
    /** The crosshair (reticle). */
    RETICLE("Crosshair"),
    /** The compass. */
    COMPASS("Compass"),
    /** The speedometer. */
    SPEEDOMETER("Speedometer"),
    /** The chat window. */
    CHAT("Chat"),
    /** The notification popups. */
    NOTIFICATIONS("Notifications"),
    /** The kill feed. */
    KILL_FEED("Kill feed"),
    /** The large event titles shown on screen. */
    EVENT_TITLE("Event titles"),
    /** The boss health bar. */
    BOSS_BAR("Boss bar"),
    /** The objectives panel. */
    OBJECTIVE_PANEL("Objectives"),
    /** The portal panel. */
    PORTAL_PANEL("Portal panel"),
    /** The sleep overlay. */
    SLEEP("Sleep"),
    /** The key hints that show which inputs do what. */
    INPUT_BINDINGS("Key hints"),
    /** The utility slot selector. */
    UTILITY_SLOT_SELECTOR("Utility slot selector"),
    /** The block variant selector. */
    BLOCK_VARIANT_SELECTOR("Block variant selector"),
    /** The builder tools legend. */
    BUILDER_TOOLS_LEGEND("Builder tools legend");

    private final String englishName;

    HudPart(String englishName) {
        this.englishName = englishName;
    }

    /**
     * Returns the part's id as used in setting ids: the constant name in lower case with dashes.
     *
     * @return the slug, for example {@code kill-feed}
     */
    public String slug() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * Returns the part's translatable name as players read it.
     *
     * @return the name, with key {@code controlcore.hud.parts.<name>} (for example
     *     {@code controlcore.hud.parts.kill_feed}) and an English fallback such as {@code Kill feed}
     */
    public Text nameText() {
        return Text.key("controlcore.hud.parts." + name().toLowerCase(Locale.ROOT), englishName);
    }
}
