package net.ofatech.controlcore.application.settings;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class GetSettingCommand implements ModCommand {

    private final SettingsRegistry settings;
    private final SettingPermissionPolicy permissions;
    private final Texts texts;

    public GetSettingCommand(SettingsRegistry settings, SettingPermissionPolicy permissions, Texts texts) {
        this.settings = settings;
        this.permissions = permissions;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-get")
                .description("Show a ControlCore setting")
                .arg("id", ArgType.STRING, "Setting id, namespace:key");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return SettingId.parse(context.string("id"))
                .flatMap(id -> settings.definition(id)
                        .filter(definition -> definition.scope() == SettingScope.PLAYER)
                        .map(definition -> permitted(context)
                                .read(id, context.sender().player().map(PlayerInfo::id)))
                        .orElseGet(() -> permitted(context).read(id, Optional.empty())))
                .map(snapshot -> Reply.of(SettingsText.details(tr(context), snapshot)));
    }

    private SettingsEditor permitted(CommandContext context) {
        return permissions.editor(settings, context.sender().player().map(PlayerInfo::id));
    }

    private Translator tr(CommandContext context) {
        return texts.forSender(context.sender());
    }
}
