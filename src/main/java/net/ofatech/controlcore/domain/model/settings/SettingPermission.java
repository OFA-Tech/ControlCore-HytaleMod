package net.ofatech.controlcore.domain.model.settings;

import java.util.Optional;
import net.ofatech.controlcore.domain.guard.DomainException;

/**
 * Who may see and who may change a setting. Each side is a full permission node (for example
 * {@code homes.moderator}), {@link #EVERYONE}, or empty to fall back to the next rule: an admin's rule for the
 * setting, its category or its mod, then the mod's rule for the setting or its category, then ControlCore's
 * default (server settings: ControlCore's {@code settings} permission; player preferences: everyone).
 *
 * <p>Changing implies seeing. Players with ControlCore's {@code settings} or {@code admin} permission may always
 * see and change everything.
 *
 * @param view who may see the setting (players who may only see it get it read-only)
 * @param edit who may change it
 * @since API 1.4.0
 */
public record SettingPermission(Optional<String> view, Optional<String> edit) {

    /** Instead of a node: anyone may do it. */
    public static final String EVERYONE = "everyone";

    /** No rule of its own: the next rule decides. */
    public static final SettingPermission INHERIT = new SettingPermission(Optional.empty(), Optional.empty());

    public SettingPermission {
        view = normalized(view);
        edit = normalized(edit);
    }

    /** @param node who may see and change it */
    public static SettingPermission of(String node) {
        return new SettingPermission(Optional.of(node), Optional.of(node));
    }

    /**
     * @param view who may see it, or {@code null} to inherit
     * @param edit who may change it, or {@code null} to inherit
     */
    public static SettingPermission of(String view, String edit) {
        return new SettingPermission(Optional.ofNullable(view), Optional.ofNullable(edit));
    }

    /** @return a copy where who may see it is {@code node} */
    public SettingPermission withView(String node) {
        return new SettingPermission(Optional.ofNullable(node), edit);
    }

    /** @return a copy where who may change it is {@code node} */
    public SettingPermission withEdit(String node) {
        return new SettingPermission(view, Optional.ofNullable(node));
    }

    /** @return whether neither side has a rule */
    public boolean isInherit() {
        return view.isEmpty() && edit.isEmpty();
    }

    /** @return the fields this rule leaves empty taken from {@code fallback} */
    public SettingPermission orElse(SettingPermission fallback) {
        return new SettingPermission(view.or(fallback::view), edit.or(fallback::edit));
    }

    private static Optional<String> normalized(Optional<String> node) {
        if (node == null) {
            return Optional.empty();
        }
        return node.map(String::strip).filter(text -> !text.isEmpty()).map(text -> {
            if (text.equalsIgnoreCase(EVERYONE)) {
                return EVERYONE;
            }
            if (text.chars().anyMatch(Character::isWhitespace)) {
                throw new DomainException("A permission node has no spaces: '" + text + "'");
            }
            return text;
        });
    }
}
