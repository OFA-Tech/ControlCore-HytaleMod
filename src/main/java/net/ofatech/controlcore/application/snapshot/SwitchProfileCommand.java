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
public final class SwitchProfileCommand implements ModCommand {

    private final ProfileService profiles;
    private final Texts texts;

    public SwitchProfileCommand(ProfileService profiles, Texts texts) {
        this.profiles = profiles;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-profile")
                .description("Switch to a profile (saves the current state as before-<name> first)")
                .arg("profile", ArgType.STRING, "@vanilla, @pvp, @creative, @event or a snapshot marked as profile");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return profiles.switchTo(
                        context.string("profile"),
                        SnapshotCommands.who(context),
                        SnapshotCommands.actor(context),
                        texts.forSender(context.sender()))
                .map(Reply::of);
    }
}
