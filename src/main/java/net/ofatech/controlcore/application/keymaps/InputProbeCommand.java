package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.game.InputProbe;
import net.ofatech.controlcore.domain.result.Result;

/** Turns the input probe on or off for the sender: every key action their client sends is shown in chat. */
@AutoService(ModCommand.class)
public final class InputProbeCommand implements ModCommand {

    /**
     * Debug tools need their own node ({@code <base>.debug}): operators have it; grant it to anyone else who
     * debugs input.
     */
    static final String PERMISSION = "debug";

    private final InputProbe probe;

    public InputProbeCommand(InputProbe probe) {
        this.probe = probe;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-input-probe")
                .description("Show in chat what your client sends when you press keys (run again to stop)")
                .permission(PERMISSION)
                .requirePlayer();
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        PlayerInfo sender = context.sender().player().orElseThrow();
        return Result.success(Reply.of(
                probe.toggle(sender.id())
                        ? "Input probe on: press keys and buttons; each action your client sends appears here and in the"
                                + " server log. Run /cc-input-probe again to stop."
                        : "Input probe off."));
    }
}
