package net.ofatech.controlcore.application.snapshot;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.snapshot.ProfilesView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ProfilesCommand implements ModCommand {

    private final ProfileService profiles;
    private final SnapshotService snapshots;
    private final Texts texts;

    public ProfilesCommand(ProfileService profiles, SnapshotService snapshots, Texts texts) {
        this.profiles = profiles;
        this.snapshots = snapshots;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-profiles").description("List the profiles and show which one is active");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Translator tr = texts.forSender(context.sender());
        if (!snapshots.mayUse(SnapshotCommands.who(context))) {
            return Result.success(Reply.of(tr.t("controlcore.errors.snapshots.forbidden")));
        }
        List<String> lines = new ArrayList<>();
        lines.add(profiles.status(tr));
        for (ProfilesView.Entry entry : profiles.list(tr)) {
            lines.add((entry.active() ? "* " : "  ") + entry.id() + " - " + entry.name() + ": " + entry.details());
        }
        lines.add(tr.t("controlcore.profiles.command_hint"));
        return Result.success(Reply.of(String.join("\n", lines)));
    }
}
