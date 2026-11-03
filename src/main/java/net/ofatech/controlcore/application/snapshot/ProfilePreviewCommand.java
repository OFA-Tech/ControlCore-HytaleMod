package net.ofatech.controlcore.application.snapshot;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ProfilePreviewCommand implements ModCommand {

    private final ProfileService profiles;
    private final Texts texts;

    public ProfilePreviewCommand(ProfileService profiles, Texts texts) {
        this.profiles = profiles;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-profile-preview")
                .description("Show what switching to a profile would change, without changing anything")
                .arg("profile", ArgType.STRING, "@vanilla, @pvp, @creative, @event or a snapshot marked as profile");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Translator tr = texts.forSender(context.sender());
        return profiles.preview(context.string("profile"), SnapshotCommands.who(context), tr)
                .map(preview -> {
                    StringBuilder text = new StringBuilder(preview.profileTitle(tr));
                    preview.allLines(tr).forEach(line -> text.append('\n').append(line));
                    return Reply.of(text.toString());
                });
    }
}
