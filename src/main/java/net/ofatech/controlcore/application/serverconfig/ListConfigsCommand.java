package net.ofatech.controlcore.application.serverconfig;

import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.settings.SettingsText;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSearch;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ListConfigsCommand implements ModCommand {

    private final ServerConfigEditor serverConfig;
    private final SettingPermissionPolicy permissions;
    private final Texts texts;

    public ListConfigsCommand(ServerConfigEditor serverConfig, SettingPermissionPolicy permissions, Texts texts) {
        this.serverConfig = serverConfig;
        this.permissions = permissions;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-configs")
                .description("List the keys of the server's config.json or of a world, with value and default")
                .optionalArg("world", ArgType.STRING, "A world's name; - or nothing for config.json")
                .optionalArg("search", ArgType.GREEDY_STRING, "Only what the panel's search shows for this text");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Translator tr = texts.forSender(context.sender());
        Optional<PlayerId> who = context.sender().player().map(PlayerInfo::id);
        Optional<String> world = context.arg("world")
                .map(Object::toString)
                .map(String::strip)
                .filter(text -> !text.isEmpty() && !text.equals("-"));
        if (world.isPresent()) {
            Optional<String> found = serverConfig.worldNames().stream()
                    .filter(world.get()::equalsIgnoreCase)
                    .findFirst();
            if (found.isEmpty()) {
                return Result.failure(unknownWorld(world.get(), serverConfig.worldNames()));
            }
            world = found;
        }
        SettingSearch search =
                SettingSearch.parse(context.arg("search").map(Object::toString).orElse(""));
        Optional<String> shownWorld = world;
        List<SettingSnapshot> visible =
                permissions.editor(serverConfig, who).snapshot(SettingScope.SERVER, Optional.empty());
        if (visible.isEmpty() && who.isPresent() && !permissions.isAdmin(who.get())) {
            return Result.failure(Error.forbidden(
                    "ServerConfig.NoneVisible",
                    Text.key("controlcore.errors.configs.none_visible", "You may not see any server config keys.")));
        }
        List<SettingSnapshot> rows = visible.stream()
                .filter(snapshot -> ServerConfigEditor.inWorld(snapshot.id(), shownWorld))
                .filter(snapshot -> search.matches(snapshot, tr))
                .toList();
        String title = shownWorld
                .map(name -> tr.t("controlcore.configs.title_world", "world", name))
                .orElseGet(() -> tr.t("controlcore.configs.title"));
        if (rows.isEmpty()) {
            return Result.success(Reply.of(title + "\n" + tr.t("controlcore.configs.none")));
        }
        StringBuilder text =
                new StringBuilder(tr.t("controlcore.settings.list.title", "title", title, "count", rows.size()));
        String heading = null;
        for (SettingSnapshot snapshot : rows) {
            if (!snapshot.definition().heading().equals(heading)) {
                heading = snapshot.definition().heading();
                text.append("\n[").append(snapshot.definition().heading(tr)).append(']');
            }
            text.append("\n  ")
                    .append(tr.t(
                            "controlcore.configs.line",
                            "id",
                            snapshot.id(),
                            "value",
                            SettingsText.value(tr, snapshot.definition(), snapshot.value()),
                            "default",
                            SettingsText.value(
                                    tr,
                                    snapshot.definition(),
                                    snapshot.definition().formattedDefault()),
                            "access",
                            tr.t("controlcore.configs.access." + access(snapshot.access()))));
        }
        return Result.success(Reply.of(text.toString()));
    }

    public static Error unknownWorld(String world, List<String> worlds) {
        return Error.notFound(
                "ServerConfig.UnknownWorld",
                Text.key("controlcore.errors.configs.unknown_world", "There is no world {world}. Worlds: {worlds}.")
                        .with("world", world, "worlds", String.join(", ", worlds)));
    }

    private static String access(SettingAccess access) {
        return switch (access) {
            case EDITABLE -> "live";
            case APPLIES_AFTER_RESTART -> "restart";
            case READ_ONLY -> "read_only";
        };
    }
}
