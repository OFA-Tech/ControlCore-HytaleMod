package net.ofatech.controlcore.application.settings;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ListSettingsCommand implements ModCommand {

    private final SettingsRegistry settings;
    private final SettingPermissionPolicy permissions;
    private final Texts texts;

    public ListSettingsCommand(SettingsRegistry settings, SettingPermissionPolicy permissions, Texts texts) {
        this.settings = settings;
        this.permissions = permissions;
        this.texts = texts;
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
                    "Settings.Forbidden",
                    Text.key(
                            "controlcore.errors.settings.none_visible",
                            "You may not see any server settings. Your own preferences: /cc-prefs")));
        }
        String server = SettingsText.list(
                tr(context),
                tr(context).t("controlcore.settings.list.server"),
                serverSettings,
                tr(context).t("controlcore.settings.list.no_server"));
        String player = SettingsText.list(
                tr(context),
                tr(context).t("controlcore.settings.list.player_defaults"),
                visible.snapshot(SettingScope.PLAYER, Optional.empty()),
                tr(context).t("controlcore.settings.list.no_player"));
        return Result.success(Reply.of(server + "\n" + player));
    }

    private SettingsEditor permitted(CommandContext context) {
        return permissions.editor(settings, context.sender().player().map(PlayerInfo::id));
    }

    private Translator tr(CommandContext context) {
        return texts.forSender(context.sender());
    }
}
