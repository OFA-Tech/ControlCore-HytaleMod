package net.ofatech.controlcore.application.settings;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ListSettingPermissionsCommand implements ModCommand {

    private final SettingPermissionPolicy permissions;
    private final Texts texts;

    public ListSettingPermissionsCommand(SettingPermissionPolicy permissions, Texts texts) {
        this.permissions = permissions;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-perms")
                .description("List the rules for who may see and change settings")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Translator tr = texts.forSender(context.sender());
        Map<String, SettingPermission> rules = permissions.rules();
        if (rules.isEmpty()) {
            return Result.success(Reply.of(tr.t("controlcore.permissions.none")));
        }
        List<String> lines = new ArrayList<>();
        lines.add(tr.t("controlcore.permissions.title"));
        rules.forEach((target, rule) -> lines.add("- " + SettingPermissionsText.rule(tr, target, rule)));
        return Result.success(Reply.of(String.join("\n", lines)));
    }
}
