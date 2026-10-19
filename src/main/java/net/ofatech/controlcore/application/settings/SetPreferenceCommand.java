package net.ofatech.controlcore.application.settings;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class SetPreferenceCommand implements ModCommand {

    private final SettingsRegistry settings;

    public SetPreferenceCommand(SettingsRegistry settings) {
        this.settings = settings;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-pref")
                .description("Change one of your ControlCore preferences")
                .arg("id", ArgType.STRING, "Preference id, namespace:key")
                .arg("value", ArgType.GREEDY_STRING, "New value")
                .requirePlayer();
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        var player = context.sender().player().map(info -> info.id());
        return SettingId.parse(context.string("id"))
                .flatMap(id -> settings.write(id, player, context.string("value")))
                .map(snapshot -> Reply.of(SettingsText.changed(snapshot)));
    }
}
