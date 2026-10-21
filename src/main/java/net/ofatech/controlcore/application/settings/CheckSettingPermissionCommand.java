package net.ofatech.controlcore.application.settings;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class CheckSettingPermissionCommand implements ModCommand {

    private final SettingPermissionPolicy permissions;
    private final SettingsRegistry settings;
    private final ServerConfigEditor serverConfig;
    private final Texts texts;

    public CheckSettingPermissionCommand(
            SettingPermissionPolicy permissions,
            SettingsRegistry settings,
            ServerConfigEditor serverConfig,
            Texts texts) {
        this.permissions = permissions;
        this.settings = settings;
        this.serverConfig = serverConfig;
        this.texts = texts;
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
            Translator tr = texts.forSender(context.sender());
            return Reply.of(tr.translate(definition.nameText()) + " (" + definition.id() + ")\n"
                    + "- "
                    + tr.t("controlcore.permissions.see", "rule", rule.view().describe(tr, definition.scope()))
                    + "\n- "
                    + tr.t("controlcore.permissions.change", "rule", rule.edit().describe(tr, definition.scope()))
                    + "\n" + tr.t("controlcore.permissions.admins_always"));
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
                : Result.failure(Error.notFound(
                        "Settings.UnknownSetting",
                        Text.key("controlcore.errors.settings.unknown_setting", "There is no setting {id}.")
                                .with("id", id)));
    }
}
