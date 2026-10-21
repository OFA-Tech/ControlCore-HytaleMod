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
 * the whole HUD. The mod creates and removes its HUD in those callbacks and does not show it on its own.
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

    public HudElement {
        Guard.notNull(id, "id");
        Guard.notNull(nameText, "nameText");
        Guard.notNull(descriptionText, "descriptionText");
        Guard.notNull(onShow, "onShow");
        Guard.notNull(onHide, "onHide");
    }

    /**
     * @param modId your mod's id, lower case
     * @param key the HUD within your mod, lower case
     * @return a HUD that is visible by default and named after its key
     */
    public static HudElement of(String modId, String key) {
        Guard.notBlank(modId, "modId");
        Guard.notBlank(key, "key");
        SettingId id = new SettingId(
                modId.strip().toLowerCase(Locale.ROOT), key.strip().toLowerCase(Locale.ROOT));
        return new HudElement(id, Text.of(id.key()), Text.of(""), true, NOTHING, NOTHING);
    }

    public HudElement displayName(String name) {
        return displayName(Text.of(name));
    }

    public HudElement displayName(Text name) {
        return new HudElement(id, name, descriptionText, visibleByDefault, onShow, onHide);
    }

    public HudElement describedAs(String text) {
        return describedAs(Text.of(text));
    }

    public HudElement describedAs(Text text) {
        return new HudElement(id, nameText, text, visibleByDefault, onShow, onHide);
    }

    /** @return a copy that players only see once they turn it on */
    public HudElement hiddenByDefault() {
        return new HudElement(id, nameText, descriptionText, false, onShow, onHide);
    }

    public HudElement onShow(Consumer<PlayerId> action) {
        return new HudElement(id, nameText, descriptionText, visibleByDefault, action, onHide);
    }

    public HudElement onHide(Consumer<PlayerId> action) {
        return new HudElement(id, nameText, descriptionText, visibleByDefault, onShow, action);
    }

    /** @return the id of the player preference that turns it on and off */
    public SettingId preferenceId() {
        return new SettingId(id.namespace(), "hud." + id.key());
    }
}
