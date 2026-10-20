package net.ofatech.controlcore.application.settings;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;

/** Text for setting permission commands. */
final class SettingPermissionsText {

    private SettingPermissionsText() {}

    /** @return for example {@code setting:homes:max-homes: see everyone, change homes.moderator} */
    static String rule(String target, SettingPermission rule) {
        return rule.isInherit() ? target + ": no rule (inherits)" : target + ": " + sides(rule);
    }

    static String sides(SettingPermission rule) {
        return "see " + side(rule.view()) + ", change " + side(rule.edit());
    }

    private static String side(Optional<String> node) {
        return node.orElse("(inherits)");
    }
}
