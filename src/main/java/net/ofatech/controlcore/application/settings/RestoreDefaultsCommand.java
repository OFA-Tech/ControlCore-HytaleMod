package net.ofatech.controlcore.application.settings;

import com.google.auto.service.AutoService;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.serverconfig.ListConfigsCommand;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Actor;
import net.ofatech.controlcore.domain.model.settings.DefaultsView.Section;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class RestoreDefaultsCommand implements ModCommand {

    static final String SECTIONS =
            "mod-settings, prefs, server-configs, world:<name>, keymaps-server, keymaps-mine, permissions";

    private final DefaultsService defaults;
    private final ServerConfigEditor serverConfig;
    private final Texts texts;

    public RestoreDefaultsCommand(DefaultsService defaults, ServerConfigEditor serverConfig, Texts texts) {
        this.defaults = defaults;
        this.serverConfig = serverConfig;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-restore-defaults")
                .description("Put every changed row of a section back to its default, like the panel's button")
                .arg("section", ArgType.STRING, SECTIONS)
                .arg("step", ArgType.STRING, "check to see how many rows change, confirm to do it")
                .optionalArg("search", ArgType.GREEDY_STRING, "Only rows whose id, name or heading has this text");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Translator tr = texts.forSender(context.sender());
        Optional<PlayerId> who = context.sender().player().map(PlayerInfo::id);
        String asked = context.string("section").strip();
        String lower = asked.toLowerCase(Locale.ROOT);
        Optional<String> world = Optional.empty();
        Section section;
        if (lower.startsWith("world:")) {
            String name = asked.substring("world:".length()).strip();
            world = serverConfig.worldNames().stream()
                    .filter(name::equalsIgnoreCase)
                    .findFirst();
            if (world.isEmpty()) {
                return Result.failure(ListConfigsCommand.unknownWorld(name, serverConfig.worldNames()));
            }
            section = Section.SERVER_CONFIGS;
        } else {
            Optional<Section> found = switch (lower) {
                case "mod-settings" -> Optional.of(Section.MOD_SETTINGS);
                case "prefs" -> Optional.of(Section.PREFS);
                case "server-configs" -> Optional.of(Section.SERVER_CONFIGS);
                case "keymaps-server" -> Optional.of(Section.KEYMAPS_SERVER);
                case "keymaps-mine" -> Optional.of(Section.KEYMAPS_MINE);
                case "permissions" -> Optional.of(Section.PERMISSIONS);
                default -> Optional.empty();
            };
            if (found.isEmpty()) {
                return Result.failure(Error.validation(
                        "Defaults.UnknownSection",
                        Text.key(
                                        "controlcore.errors.defaults.unknown_section",
                                        "'{section}' is not a section. Use {sections}.")
                                .with("section", asked, "sections", SECTIONS)));
            }
            section = found.get();
        }
        String step = context.string("step").strip().toLowerCase(Locale.ROOT);
        if (!step.equals("check") && !step.equals("confirm")) {
            return Result.failure(Error.validation(
                    "Defaults.Confirm",
                    Text.key("controlcore.errors.players.bad_choice", "Use {choices}.")
                            .with("choices", "check, confirm")));
        }
        Predicate<String> shown = defaults.matching(
                section, world, context.arg("search").map(Object::toString).orElse(""), who, tr);
        if (step.equals("check")) {
            return defaults.check(section, shown, who)
                    .map(count -> Reply.of(
                            count == 0
                                    ? tr.t("controlcore.panel.defaults.nothing")
                                    : tr.t(
                                            defaults.savesFirst(section, who)
                                                    ? "controlcore.defaults.check_safe"
                                                    : "controlcore.defaults.check",
                                            "count",
                                            count)));
        }
        Actor actor = context.sender()
                .player()
                .map(player -> Actor.player(player.id(), player.name()))
                .orElseGet(() -> Actor.console(context.sender().name()));
        return defaults.restore(section, shown, who, actor, tr).map(Reply::of);
    }
}
