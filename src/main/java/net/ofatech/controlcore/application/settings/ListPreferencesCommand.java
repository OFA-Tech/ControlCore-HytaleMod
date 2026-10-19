package net.ofatech.controlcore.application.settings;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ListPreferencesCommand implements ModCommand {

    private final SettingsRegistry settings;

    public ListPreferencesCommand(SettingsRegistry settings) {
        this.settings = settings;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-prefs")
                .description("List your preferences registered with ControlCore")
                .requirePlayer();
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        var player = context.sender().player().map(info -> info.id());
        return Result.success(Reply.of(SettingsText.list(
                "Your preferences",
                settings.snapshot(SettingScope.PLAYER, player),
                "There are no preferences to set yet.")));
    }
}
