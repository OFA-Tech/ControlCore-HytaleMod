package net.ofatech.controlcore.application.settings;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class SetSettingCommand implements ModCommand {

    private final SettingsRegistry settings;
    private final ServerConfigEditor serverConfig;
    private final SettingPermissionPolicy permissions;
    private final Texts texts;
    private final Log log;

    public SetSettingCommand(
            SettingsRegistry settings,
            ServerConfigEditor serverConfig,
            SettingPermissionPolicy permissions,
            Texts texts,
            Log log) {
        this.settings = settings;
        this.serverConfig = serverConfig;
        this.permissions = permissions;
        this.texts = texts;
        this.log = log;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-set")
                .description("Change a ControlCore server setting")
                .arg("id", ArgType.STRING, "Setting id, for example homes:max-homes or server:motd")
                .arg("value", ArgType.GREEDY_STRING, "New value");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return SettingId.parse(context.string("id"))
                .flatMap(id -> permitted(context, id).write(id, Optional.empty(), context.string("value")))
                .onSuccess(snapshot ->
                        log.info(context.sender().name() + " set " + SettingsText.line(texts.forServer(), snapshot)))
                .map(snapshot -> Reply.of(SettingsText.changed(tr(context), snapshot)));
    }

    private SettingsEditor permitted(CommandContext context, SettingId id) {
        return permissions.editor(
                ServerConfigEditor.owns(id) ? serverConfig : settings,
                context.sender().player().map(PlayerInfo::id));
    }

    private Translator tr(CommandContext context) {
        return texts.forSender(context.sender());
    }
}
