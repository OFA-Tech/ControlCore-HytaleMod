package net.ofatech.controlcore.application.settings;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

/** {@code /cc-perms}: admins' setting permission rules. */
@AutoService(ModCommand.class)
public final class ListSettingPermissionsCommand implements ModCommand {

    private final SettingPermissionPolicy permissions;

    public ListSettingPermissionsCommand(SettingPermissionPolicy permissions) {
        this.permissions = permissions;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-perms")
                .description("List the rules for who may see and change settings")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Map<String, SettingPermission> rules = permissions.rules();
        if (rules.isEmpty()) {
            return Result.success(Reply.of("No setting permission rules yet. Add one with /cc-perm; mods' own rules"
                    + " and the defaults apply (server settings: admins; preferences: everyone)."));
        }
        List<String> lines = new ArrayList<>();
        lines.add("Setting permission rules (check one setting with /cc-perm-check <id>):");
        rules.forEach((target, rule) -> lines.add("- " + SettingPermissionsText.rule(target, rule)));
        return Result.success(Reply.of(String.join("\n", lines)));
    }
}
