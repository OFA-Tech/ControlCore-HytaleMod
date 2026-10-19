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
public final class ResetPreferenceCommand implements ModCommand {

    private final SettingsRegistry settings;

    public ResetPreferenceCommand(SettingsRegistry settings) {
        this.settings = settings;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-pref-reset")
                .description("Return one of your ControlCore preferences to the default")
                .arg("id", ArgType.STRING, "Preference id, namespace:key")
                .requirePlayer();
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        var player = context.sender().player().map(info -> info.id());
        return SettingId.parse(context.string("id"))
                .flatMap(id -> settings.reset(id, player))
                .map(snapshot -> Reply.of(SettingsText.changed(snapshot)));
    }
}
