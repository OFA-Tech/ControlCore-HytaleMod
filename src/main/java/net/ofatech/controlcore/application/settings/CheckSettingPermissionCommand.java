package net.ofatech.controlcore.application.settings;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

/** {@code /cc-perm-check <id>}: who may see and change one setting, and which rule decides it. */
@AutoService(ModCommand.class)
public final class CheckSettingPermissionCommand implements ModCommand {

    private final SettingPermissionPolicy permissions;
    private final SettingsRegistry settings;
    private final ServerConfigEditor serverConfig;

    public CheckSettingPermissionCommand(
            SettingPermissionPolicy permissions, SettingsRegistry settings, ServerConfigEditor serverConfig) {
        this.permissions = permissions;
        this.settings = settings;
        this.serverConfig = serverConfig;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-perm-check")
                .description("Show who may see and change a setting, and which rule decides it")
                .arg("id", ArgType.STRING, "Setting id, for example homes:max-homes or server:motd")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return SettingId.parse(context.string("id")).flatMap(this::definition).map(definition -> {
            SettingPermissionPolicy.Resolved rule = permissions.resolve(definition);
            return Reply.of(definition.displayName() + " (" + definition.id() + ")\n"
                    + "- see: " + rule.view().describe(definition.scope()) + "\n"
                    + "- change: " + rule.edit().describe(definition.scope()) + "\n"
                    + "ControlCore admins (settings or admin permission) may always see and change it.");
        });
    }

    private Result<SettingDefinition<?>> definition(SettingId id) {
        Optional<SettingDefinition<?>> registered = settings.definition(id);
        if (registered.isPresent()) {
            return Result.success(registered.get());
        }
        Result<SettingSnapshot> config = serverConfig.read(id, Optional.empty());
        return config.isSuccess()
                ? Result.success(config.value().definition())
                : Result.failure(Error.notFound("Settings.UnknownSetting", "There is no setting " + id + "."));
    }
}
