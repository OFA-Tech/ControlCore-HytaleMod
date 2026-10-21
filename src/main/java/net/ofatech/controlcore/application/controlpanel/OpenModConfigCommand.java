package net.ofatech.controlcore.application.controlpanel;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class OpenModConfigCommand implements ModCommand {

    private final ControlPanelService controlPanel;

    public OpenModConfigCommand(ControlPanelService controlPanel) {
        this.controlPanel = controlPanel;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-config")
                .description("Open the settings of one mod")
                .arg("mod", ArgType.STRING, "Mod id (Group:Name), name or setting id prefix, for example controlcore")
                .requirePlayer();
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        PlayerInfo sender = context.sender().player().orElseThrow();
        return controlPanel.openForMod(sender.id(), context.string("mod")).map(opened -> Reply.none());
    }
}
