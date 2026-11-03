package net.ofatech.controlcore.application.players;

import com.google.auto.service.AutoService;
import java.util.List;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class GroupsCommand implements ModCommand {

    private final PlayerCommands commands;

    public GroupsCommand(PlayerCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-groups").description("List the permission groups and their permissions");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        PlayersView view = commands.view(context);
        if (!view.rights().groups()) {
            return Result.failure(PlayerErrors.adminsOnly());
        }
        Translator tr = commands.tr(context);
        List<PlayersView.Choice> groups = view.groups();
        if (groups.isEmpty()) {
            return Result.success(Reply.of(tr.t("controlcore.players.group_list.none")));
        }
        StringBuilder text = new StringBuilder(tr.t("controlcore.players.group_list.header", "count", groups.size()));
        for (PlayersView.Choice group : groups) {
            List<String> nodes = view.groupPermissions(group.id());
            text.append('\n')
                    .append(tr.t(
                            "controlcore.players.group_list.line",
                            "group",
                            group.label(),
                            "id",
                            group.id(),
                            "nodes",
                            nodes.isEmpty() ? "-" : String.join(", ", nodes)));
        }
        return Result.success(Reply.of(text.toString()));
    }
}
