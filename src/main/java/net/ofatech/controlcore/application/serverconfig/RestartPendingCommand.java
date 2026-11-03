package net.ofatech.controlcore.application.serverconfig;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.mods.ModsEditor;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class RestartPendingCommand implements ModCommand {

    private final ServerConfigEditor serverConfig;
    private final ModsEditor mods;
    private final SettingPermissionPolicy permissions;
    private final Texts texts;

    public RestartPendingCommand(
            ServerConfigEditor serverConfig, ModsEditor mods, SettingPermissionPolicy permissions, Texts texts) {
        this.serverConfig = serverConfig;
        this.mods = mods;
        this.permissions = permissions;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-restart-pending")
                .description("List what was changed and only applies after a restart");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Optional<PlayerId> who = context.sender().player().map(PlayerInfo::id);
        if (who.isPresent() && !permissions.isAdmin(who.get())) {
            return Result.failure(Error.forbidden(
                    "ServerConfig.Forbidden",
                    Text.key("controlcore.errors.restart.forbidden", "Only admins can see what waits for a restart.")));
        }
        Translator tr = texts.forSender(context.sender());
        List<Text> names = new ArrayList<>(serverConfig.pendingRestart());
        names.addAll(mods.pendingRestart());
        if (names.isEmpty()) {
            return Result.success(Reply.of(tr.t("controlcore.restart.none")));
        }
        return Result.success(Reply.of(tr.t(
                "controlcore.panel.restart_pending",
                "keys",
                names.stream().map(tr::translate).collect(Collectors.joining(", ")))));
    }
}
