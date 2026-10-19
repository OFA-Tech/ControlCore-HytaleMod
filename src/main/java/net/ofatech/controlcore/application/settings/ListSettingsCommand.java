package net.ofatech.controlcore.application.settings;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ListSettingsCommand implements ModCommand {

    private final SettingsRegistry settings;

    public ListSettingsCommand(SettingsRegistry settings) {
        this.settings = settings;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-settings")
                .description("List the server settings registered with ControlCore")
                .permission(SettingsPermissions.ADMIN);
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        String server = SettingsText.list(
                "Server settings", settings.snapshot(SettingScope.SERVER, Optional.empty()), "No server settings.");
        String player = SettingsText.list(
                "Player preferences (defaults)",
                settings.snapshot(SettingScope.PLAYER, Optional.empty()),
                "No player preferences.");
        return Result.success(Reply.of(server + "\n" + player));
    }
}
