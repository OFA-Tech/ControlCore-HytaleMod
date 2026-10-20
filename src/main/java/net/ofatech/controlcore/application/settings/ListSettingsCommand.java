package net.ofatech.controlcore.application.settings;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ListSettingsCommand implements ModCommand {

    private final SettingsRegistry settings;
    private final SettingPermissionPolicy permissions;

    public ListSettingsCommand(SettingsRegistry settings, SettingPermissionPolicy permissions) {
        this.settings = settings;
        this.permissions = permissions;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-settings").description("List the server settings registered with ControlCore");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        SettingsEditor visible = permitted(context);
        var serverSettings = visible.snapshot(SettingScope.SERVER, Optional.empty());
        if (serverSettings.isEmpty()
                && context.sender().player().isPresent()
                && !permissions.isAdmin(context.sender().player().get().id())) {
            return Result.failure(Error.forbidden(
                    "Settings.Forbidden", "You may not see any server settings. Your own preferences: /cc-prefs"));
        }
        String server = SettingsText.list("Server settings", serverSettings, "No server settings.");
        String player = SettingsText.list(
                "Player preferences (defaults)",
                visible.snapshot(SettingScope.PLAYER, Optional.empty()),
                "No player preferences.");
        return Result.success(Reply.of(server + "\n" + player));
    }

    private SettingsEditor permitted(CommandContext context) {
        return permissions.editor(settings, context.sender().player().map(PlayerInfo::id));
    }
}
