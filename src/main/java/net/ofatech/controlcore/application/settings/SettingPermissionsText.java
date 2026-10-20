package net.ofatech.controlcore.application.settings;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.model.text.Translator;

/** Text for setting permission commands. */
final class SettingPermissionsText {

    private SettingPermissionsText() {}

    /** @return for example {@code setting:homes:max-homes: see everyone, change homes.moderator} */
    static String rule(Translator tr, String target, SettingPermission rule) {
        return rule.isInherit()
                ? tr.t("controlcore.permissions.rule_inherit", "target", target)
                : tr.t(
                        "controlcore.permissions.rule",
                        "target",
                        target,
                        "view",
                        side(tr, rule.view()),
                        "edit",
                        side(tr, rule.edit()));
    }

    private static String side(Translator tr, Optional<String> node) {
        return node.orElse(tr.t("controlcore.permissions.inherits"));
    }
}
