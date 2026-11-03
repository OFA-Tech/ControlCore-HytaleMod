package net.ofatech.controlcore.application.snapshot;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class MarkProfileCommand implements ModCommand {

    private final ProfileService profiles;
    private final Texts texts;

    public MarkProfileCommand(ProfileService profiles, Texts texts) {
        this.profiles = profiles;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-profile-mark")
                .description("Mark a snapshot as a profile so it shows in /cc-profiles")
                .arg("snapshot", ArgType.STRING, "The snapshot's name (see /cc-snapshots)");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return profiles.mark(
                        context.string("snapshot"), SnapshotCommands.who(context), texts.forSender(context.sender()))
                .map(Reply::of);
    }
}
