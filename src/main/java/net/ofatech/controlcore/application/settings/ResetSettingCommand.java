package net.ofatech.controlcore.application.settings;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ResetSettingCommand implements ModCommand {

    private final SettingsRegistry settings;
    private final Log log;

    public ResetSettingCommand(SettingsRegistry settings, Log log) {
        this.settings = settings;
        this.log = log;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-reset")
                .description("Return a ControlCore server setting to its default")
                .arg("id", ArgType.STRING, "Setting id, namespace:key")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return SettingId.parse(context.string("id"))
                .flatMap(id -> settings.reset(id, Optional.empty()))
                .onSuccess(snapshot -> log.info(context.sender().name() + " reset " + SettingsText.line(snapshot)))
                .map(snapshot -> Reply.of(SettingsText.changed(snapshot)));
    }
}
