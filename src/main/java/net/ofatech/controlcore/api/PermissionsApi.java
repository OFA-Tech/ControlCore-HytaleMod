package net.ofatech.controlcore.api;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Admins' rules for who may see and change settings, as in the control panel's Permissions tab and
 * {@code /cc-perm}. A rule targets one setting ({@code setting:<id>} or just the id), a category
 * ({@code category:<name>}) or a whole mod ({@code mod:<mod id>}), and has a permission node for seeing and one
 * for changing. Get it with {@link ControlCoreApi#permissions()}.
 *
 * <p>The API acts with full rights, like the console, and the change history shows the change as made by a mod.
 * {@link #actingFor(PlayerId)} gives the same API with a player's rights: only admins may see and change rules.
 *
 * @since API 2.4.0
 */
public interface PermissionsApi {

    /**
     * Returns this API acting for a player. Only admins may use it: for anyone else {@link #rules()} is empty and
     * every other method fails with {@code Permissions.Forbidden}. The change history records the player as the
     * one who made the change.
     *
     * @param player the player whose rights apply
     * @return the permissions API restricted to {@code player}'s rights
     * @throws NullPointerException if {@code player} is {@code null}
     */
    PermissionsApi actingFor(PlayerId player);

    /**
     * Lists every target a rule can be set on, as the Permissions tab does: each mod, then its categories and
     * settings, with the rule an admin set on each.
     *
     * @return the targets in display order; empty if this API may not see rules
     */
    List<Entry> rules();

    /**
     * Sets who may see, change, or both, for a target, like {@code /cc-perm set}.
     *
     * @param target {@code setting:<id>}, a setting id, {@code category:<name>} or {@code mod:<mod id>}
     * @param side {@code view}, {@code edit} or {@code both}
     * @param node a permission node, {@code everyone}, or {@code default} to remove that side of the rule
     * @return the target's rule after the change, or an error such as {@code Settings.InvalidPermissionTarget},
     *     {@code Settings.InvalidPermissionSide}, {@code Settings.InvalidPermission} or {@code Permissions.Forbidden}
     */
    Result<SettingPermission> set(String target, String side, String node);

    /**
     * Removes the admin rule of a target, so the mod's rule or the default applies again.
     *
     * @param target {@code setting:<id>}, a setting id, {@code category:<name>} or {@code mod:<mod id>}
     * @return {@link SettingPermission#INHERIT}, or an error such as {@code Settings.InvalidPermissionTarget} or
     *     {@code Permissions.Forbidden}
     */
    Result<SettingPermission> reset(String target);

    /**
     * Tells who may see and change one setting and which rule decides it, like {@code /cc-perm-check}. Admins
     * may always see and change every setting.
     *
     * @param id a registered setting or a {@code config.json} key ({@code server:<key>})
     * @return who may see and change it, or an error such as {@code Settings.UnknownSetting} or
     *     {@code Permissions.Forbidden}
     */
    Result<Check> check(SettingId id);

    /** What a rule targets. */
    enum Level {
        /** Every setting of a mod: {@code mod:<mod id>}. */
        MOD,
        /** Every setting in a category: {@code category:<name>}. */
        CATEGORY,
        /** One setting: {@code setting:<id>}. */
        SETTING
    }

    /**
     * A target a rule can be set on.
     *
     * @param level what the rule targets
     * @param target the target, for example {@code mod:homes}, {@code category:limits} or
     *     {@code setting:homes:max-homes}
     * @param heading the mod and category the target is shown under, in the reader's language
     * @param name the target's name, in the reader's language
     * @param rule the rule an admin set; {@link SettingPermission#INHERIT} if none
     */
    record Entry(Level level, String target, String heading, String name, SettingPermission rule) {}

    /**
     * Who may see and change one setting.
     *
     * @param setting the setting
     * @param viewNode the permission node needed to see it ({@code everyone} for anyone); empty for the default:
     *     admins for server settings, everyone for player preferences
     * @param viewRule which rule decides who may see it, for example "admin rule for mod homes"
     * @param editNode the permission node needed to change it, as {@code viewNode}
     * @param editRule which rule decides who may change it
     */
    record Check(
            SettingId setting, Optional<String> viewNode, Text viewRule, Optional<String> editNode, Text editRule) {}
}
