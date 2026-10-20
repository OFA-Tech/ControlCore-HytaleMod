package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.game.InputProbe;
import net.ofatech.controlcore.domain.result.Result;

/** Opens the key test screen: shows what the client reports for each key while a ControlCore screen is open. */
@AutoService(ModCommand.class)
public final class KeyProbeCommand implements ModCommand {

    private final InputProbe probe;

    public KeyProbeCommand(InputProbe probe) {
        this.probe = probe;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-key-probe")
                .description("Open a test screen that shows what your client reports for each key")
                .arg("target", ArgType.STRING, "Where to listen: " + InputProbe.KEY_TEST_TARGETS)
                .permission(InputProbeCommand.PERMISSION)
                .requirePlayer();
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        PlayerInfo sender = context.sender().player().orElseThrow();
        return probe.openKeyTest(sender.id(), context.string("target")).map(ignored -> Reply.none());
    }
}
