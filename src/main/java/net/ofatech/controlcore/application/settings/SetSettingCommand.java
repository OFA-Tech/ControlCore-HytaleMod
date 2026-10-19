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
public final class SetSettingCommand implements ModCommand {

    private final SettingsRegistry settings;
    private final Log log;

    public SetSettingCommand(SettingsRegistry settings, Log log) {
        this.settings = settings;
        this.log = log;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-set")
                .description("Change a ControlCore server setting")
                .arg("id", ArgType.STRING, "Setting id, namespace:key")
                .arg("value", ArgType.GREEDY_STRING, "New value")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return SettingId.parse(context.string("id"))
                .flatMap(id -> settings.write(id, Optional.empty(), context.string("value")))
                .onSuccess(snapshot -> log.info(context.sender().name() + " set " + SettingsText.line(snapshot)))
                .map(snapshot -> Reply.of(SettingsText.changed(snapshot)));
    }
}
