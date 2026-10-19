package net.ofatech.controlcore.application.settings;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class GetSettingCommand implements ModCommand {

    private final SettingsRegistry settings;

    public GetSettingCommand(SettingsRegistry settings) {
        this.settings = settings;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-get")
                .description("Show a ControlCore setting")
                .arg("id", ArgType.STRING, "Setting id, namespace:key")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return SettingId.parse(context.string("id"))
                .flatMap(id -> settings.definition(id)
                        .filter(definition -> definition.scope() == SettingScope.PLAYER)
                        .map(definition ->
                                settings.read(id, context.sender().player().map(p -> p.id())))
                        .orElseGet(() -> settings.read(id, Optional.empty())))
                .map(snapshot -> Reply.of(SettingsText.details(snapshot)));
    }
}
