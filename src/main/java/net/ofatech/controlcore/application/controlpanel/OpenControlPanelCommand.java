package net.ofatech.controlcore.application.controlpanel;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class OpenControlPanelCommand implements ModCommand {

    private final ControlPanelService controlPanel;

    public OpenControlPanelCommand(ControlPanelService controlPanel) {
        this.controlPanel = controlPanel;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("open-admin-config")
                .description("Open the ControlCore admin configuration panel")
                .requirePlayer();
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        PlayerInfo sender = context.sender().player().orElseThrow();
        if (!controlPanel.mayOpenAdminPanel(sender.id())) {
            return Result.failure(Error.forbidden(
                    "ControlPanel.Forbidden",
                    Text.key(
                            "controlcore.errors.controlpanel.forbidden",
                            "You do not have access to the admin panel. Your preferences: /prefs")));
        }
        return controlPanel.open(sender.id(), ControlPanelRequest.ALL).map(opened -> Reply.none());
    }
}
