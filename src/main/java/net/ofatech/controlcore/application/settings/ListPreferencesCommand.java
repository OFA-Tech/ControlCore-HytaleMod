package net.ofatech.controlcore.application.settings;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ListPreferencesCommand implements ModCommand {

    private final SettingsRegistry settings;
    private final SettingPermissionPolicy permissions;
    private final Texts texts;

    public ListPreferencesCommand(SettingsRegistry settings, SettingPermissionPolicy permissions, Texts texts) {
        this.settings = settings;
        this.permissions = permissions;
        this.texts = texts;
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
                tr(context),
                tr(context).t("controlcore.settings.list.yours"),
                permitted(context).snapshot(SettingScope.PLAYER, player),
                tr(context).t("controlcore.settings.list.no_yours"))));
    }

    private SettingsEditor permitted(CommandContext context) {
        return permissions.editor(settings, context.sender().player().map(PlayerInfo::id));
    }

    private Translator tr(CommandContext context) {
        return texts.forSender(context.sender());
    }
}
