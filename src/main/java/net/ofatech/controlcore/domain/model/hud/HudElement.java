package net.ofatech.controlcore.domain.model.hud;

import java.util.Locale;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.guard.Guard;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.text.Text;

/**
 * A mod's own HUD that players can turn on and off in their preferences. ControlCore remembers each player's choice
 * and calls {@code onShow} or {@code onHide}: when the player joins, when they change it, and when they hide or show
 * the whole HUD. The mod creates and removes its HUD in those callbacks and does not show it on its own. Instances
 * are immutable: every builder method returns a changed copy. Register it with
 * {@link net.ofatech.controlcore.api.HudApi#register(HudElement)}.
 *
 * <pre>{@code
 * api.huds().register(HudElement.of("minimap", "map")
 *         .displayName("Minimap")
 *         .onShow(player -> showMinimap(player))
 *         .onHide(player -> removeMinimap(player)));
 * }</pre>
 *
 * @param id {@code modId:key}; the player preference is {@code modId:hud.key}
 * @param nameText shown in the preferences
 * @param descriptionText one sentence; may be empty
 * @param visibleByDefault whether players see it until they turn it off
 * @param onShow called with the player when it should be shown (on the server thread that delivered the change)
 * @param onHide called with the player when it should be hidden
 * @since API 2.0.0
 */
public record HudElement(
        SettingId id,
        Text nameText,
        Text descriptionText,
        boolean visibleByDefault,
        Consumer<PlayerId> onShow,
        Consumer<PlayerId> onHide) {

    private static final Consumer<PlayerId> NOTHING = player -> {};

    /**
     * Creates a HUD; prefer {@link #of(String, String)} and the builder methods.
     *
     * @param id {@code modId:key}
     * @param nameText shown in the preferences
     * @param descriptionText one sentence; may be empty
     * @param visibleByDefault whether players see it until they turn it off
     * @param onShow called with the player when it should be shown
     * @param onHide called with the player when it should be hidden
     * @throws net.ofatech.controlcore.domain.guard.DomainException if an argument is {@code null}
     */
    public HudElement {
        Guard.notNull(id, "id");
        Guard.notNull(nameText, "nameText");
        Guard.notNull(descriptionText, "descriptionText");
        Guard.notNull(onShow, "onShow");
        Guard.notNull(onHide, "onHide");
    }

    /**
     * Starts a HUD with the id {@code modId:key}; its callbacks do nothing until {@link #onShow(Consumer)} and
     * {@link #onHide(Consumer)} are set.
     *
     * @param modId your mod's id, lower case (letters, digits, {@code -}, {@code _}); upper case is lowered
     * @param key the HUD within your mod, lower case (letters, digits, {@code -}, {@code _}, {@code .}); upper case
     *     is lowered
     * @return a HUD that is visible by default and named after its key
     * @throws net.ofatech.controlcore.domain.guard.DomainException if {@code modId} or {@code key} is blank or not
     *     a valid id part
     */
    public static HudElement of(String modId, String key) {
        Guard.notBlank(modId, "modId");
        Guard.notBlank(key, "key");
        SettingId id = new SettingId(
                modId.strip().toLowerCase(Locale.ROOT), key.strip().toLowerCase(Locale.ROOT));
        return new HudElement(id, Text.of(id.key()), Text.of(""), true, NOTHING, NOTHING);
    }

    /**
     * Sets the name shown in the preferences, in one language for everyone.
     *
     * @param name the display name, for example {@code "Minimap"}
     * @return a copy with the new name
     * @throws net.ofatech.controlcore.domain.guard.DomainException if {@code name} is {@code null}
     */
    public HudElement displayName(String name) {
        return displayName(Text.of(name));
    }

    /**
     * Sets the name shown in the preferences, translated into each player's language.
     *
     * @param name the display name, for example {@code Text.key("minimap.hud.map", "Minimap")}
     * @return a copy with the new name
     * @throws net.ofatech.controlcore.domain.guard.DomainException if {@code name} is {@code null}
     */
    public HudElement displayName(Text name) {
        return new HudElement(id, name, descriptionText, visibleByDefault, onShow, onHide);
    }

    /**
     * Sets the description shown in the preferences, in one language for everyone.
     *
     * @param text one sentence explaining the HUD
     * @return a copy with the new description
     * @throws net.ofatech.controlcore.domain.guard.DomainException if {@code text} is {@code null}
     */
    public HudElement describedAs(String text) {
        return describedAs(Text.of(text));
    }

    /**
     * Sets the description shown in the preferences, translated into each player's language.
     *
     * @param text one sentence explaining the HUD
     * @return a copy with the new description
     * @throws net.ofatech.controlcore.domain.guard.DomainException if {@code text} is {@code null}
     */
    public HudElement describedAs(Text text) {
        return new HudElement(id, nameText, text, visibleByDefault, onShow, onHide);
    }

    /**
     * Makes the HUD hidden until a player turns it on.
     *
     * @return a copy that players only see once they turn it on
     */
    public HudElement hiddenByDefault() {
        return new HudElement(id, nameText, descriptionText, false, onShow, onHide);
    }

    /**
     * Sets the code that shows the HUD to a player.
     *
     * @param action called with the player when the HUD should be shown; create the HUD here
     * @return a copy with the new callback
     * @throws net.ofatech.controlcore.domain.guard.DomainException if {@code action} is {@code null}
     */
    public HudElement onShow(Consumer<PlayerId> action) {
        return new HudElement(id, nameText, descriptionText, visibleByDefault, action, onHide);
    }

    /**
     * Sets the code that hides the HUD from a player.
     *
     * @param action called with the player when the HUD should be hidden; remove the HUD here
     * @return a copy with the new callback
     * @throws net.ofatech.controlcore.domain.guard.DomainException if {@code action} is {@code null}
     */
    public HudElement onHide(Consumer<PlayerId> action) {
        return new HudElement(id, nameText, descriptionText, visibleByDefault, onShow, action);
    }

    /**
     * Returns the id of the player preference that turns this HUD on and off.
     *
     * @return the preference id, {@code modId:hud.key}, for example {@code minimap:hud.map}
     */
    public SettingId preferenceId() {
        return new SettingId(id.namespace(), "hud." + id.key());
    }
}
