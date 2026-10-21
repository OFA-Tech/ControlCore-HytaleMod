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

    /**
     * Creates a rule. Nodes are stripped of surrounding spaces; a blank node counts as empty, and any spelling of
     * {@code everyone} becomes {@link #EVERYONE}.
     *
     * @param view who may see the setting; empty or {@code null} to inherit
     * @param edit who may change it; empty or {@code null} to inherit
     * @throws DomainException if a node contains spaces
     */
    public SettingPermission {
        view = normalized(view);
        edit = normalized(edit);
    }

    /**
     * Creates a rule where the same node decides who may see and who may change the setting.
     *
     * @param node a full permission node, for example {@code homes.moderator}, or {@link #EVERYONE}; not
     *     {@code null}
     * @return the rule
     * @throws DomainException if the node contains spaces
     */
    public static SettingPermission of(String node) {
        return new SettingPermission(Optional.of(node), Optional.of(node));
    }

    /**
     * Creates a rule with separate nodes for seeing and changing the setting.
     *
     * @param view who may see it, or {@code null} to inherit
     * @param edit who may change it, or {@code null} to inherit
     * @return the rule
     * @throws DomainException if a node contains spaces
     */
    public static SettingPermission of(String view, String edit) {
        return new SettingPermission(Optional.ofNullable(view), Optional.ofNullable(edit));
    }

    /**
     * Returns a copy with a different node for seeing the setting; who may change it stays the same.
     *
     * @param node who may see it, or {@code null} to inherit
     * @return the new rule
     * @throws DomainException if the node contains spaces
     */
    public SettingPermission withView(String node) {
        return new SettingPermission(Optional.ofNullable(node), edit);
    }

    /**
     * Returns a copy with a different node for changing the setting; who may see it stays the same.
     *
     * @param node who may change it, or {@code null} to inherit
     * @return the new rule
     * @throws DomainException if the node contains spaces
     */
    public SettingPermission withEdit(String node) {
        return new SettingPermission(view, Optional.ofNullable(node));
    }

    /**
     * Tells whether this rule leaves both sides to the next rule, like {@link #INHERIT}.
     *
     * @return {@code true} if neither side has a node
     */
    public boolean isInherit() {
        return view.isEmpty() && edit.isEmpty();
    }

    /**
     * Fills the sides this rule leaves empty from another rule.
     *
     * @param fallback the rule that decides the empty sides
     * @return a rule with this rule's nodes, and {@code fallback}'s nodes where this one has none
     */
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
