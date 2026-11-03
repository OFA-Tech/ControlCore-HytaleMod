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
public final class UnmarkProfileCommand implements ModCommand {

    private final ProfileService profiles;
    private final Texts texts;

    public UnmarkProfileCommand(ProfileService profiles, Texts texts) {
        this.profiles = profiles;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-profile-unmark")
                .description("Turn a profile back into a plain snapshot (only admins change existing profiles)")
                .arg("profile", ArgType.STRING, "The profile's name (see /cc-profiles)");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return profiles.unmark(
                        context.string("profile"), SnapshotCommands.who(context), texts.forSender(context.sender()))
                .map(Reply::of);
    }
}
