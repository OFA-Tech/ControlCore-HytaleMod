package net.ofatech.controlcore.application.settings;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class SetSettingPermissionCommand implements ModCommand {

    private final SettingPermissionPolicy permissions;
    private final Log log;
    private final Texts texts;

    public SetSettingPermissionCommand(SettingPermissionPolicy permissions, Log log, Texts texts) {
        this.permissions = permissions;
        this.log = log;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-perm")
                .description("Set who may see or change a setting, a category (category:<name>) or a mod (mod:<id>)")
                .arg("target", ArgType.STRING, "Setting id, category:<name> or mod:<mod id>")
                .arg("side", ArgType.STRING, "view, edit or both")
                .arg("node", ArgType.STRING, "Permission node, everyone, or default to remove the rule")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        String target = context.string("target");
        return permissions
                .setRule(target, context.string("side"), context.string("node"))
                .flatMap(rule -> SettingPermissionPolicy.target(target).map(key -> {
                    log.info(context.sender().name() + " set the setting permission "
                            + SettingPermissionsText.rule(texts.forServer(), key, rule));
                    Translator tr = texts.forSender(context.sender());
                    return Reply.of(
                            tr.t("controlcore.permissions.saved", "rule", SettingPermissionsText.rule(tr, key, rule)));
                }));
    }
}
