package net.ofatech.controlcore.application.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.application.event.DomainEventBus;
import net.ofatech.controlcore.application.settings.ModNames;
import net.ofatech.controlcore.application.settings.SettingsRegistry;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSender;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.system.Translations;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;
import net.ofatech.controlcore.fakes.FakePlayerData;
import net.ofatech.controlcore.fakes.FakePlayers;
import net.ofatech.controlcore.fakes.FakeSettingStore;
import net.ofatech.controlcore.fakes.RecordingLog;
import net.ofatech.controlcore.infrastructure.i18n.BundledTranslations;
import org.junit.jupiter.api.Test;

class TextsTest {

    private final RecordingLog log = new RecordingLog();
    private final DomainEventBus bus = new DomainEventBus(log);
    private final SettingsRegistry registry =
            new SettingsRegistry(new FakeSettingStore(), new FakePlayerData(), bus, bus, log, new ModNames());
    private final FakePlayers players = new FakePlayers();
    private final BundledTranslations bundled = new BundledTranslations();
    /** ControlCore's files plus a mod that has only an English and a Spanish text. */
    private final Translations translations = (language, key) -> key.startsWith("homes.")
            ? Optional.ofNullable(Map.of(
                            "en-US", Map.of("homes.title", "Homes of {player}"),
                            "es-ES", Map.of("homes.title", "Casas de {player}"))
                    .getOrDefault(language, Map.of())
                    .get(key))
            : bundled.lookup(language, key);

    private final Texts texts = new Texts(translations, players);
    private final PlayerInfo ana = players.join("Ana");

    @Test
    void playersReadTheirGameLanguageEachTimeAndTheConsoleEnglish() {
        var forAna = texts.forPlayer(Optional.of(ana.id()));
        players.languages.put(ana.id(), "pt-BR");
        assertThat(forAna.t("controlcore.ui.close")).isEqualTo("Fechar");

        players.languages.put(ana.id(), "es-ES");
        assertThat(forAna.t("controlcore.ui.close"))
                .as("changed in the game's options")
                .isEqualTo("Cerrar");

        assertThat(texts.forServer().t("controlcore.ui.close")).isEqualTo("Close");
        assertThat(texts.forPlayer(Optional.of(players.join("Bo").id())).t("controlcore.ui.close"))
                .as("players whose game language is unknown read English")
                .isEqualTo("Close");
        players.languages.put(ana.id(), "fr-FR");
        assertThat(forAna.t("controlcore.ui.close"))
                .as("a language ControlCore lacks")
                .isEqualTo("Close");
    }

    @Test
    void missingTextsFallBackToEnglishThenTheFallback() {
        players.languages.put(ana.id(), "pt-BR");
        var forAna = texts.forPlayer(Optional.of(ana.id()));
        Text title = Text.key("homes.title", "Houses").with("player", "Ana");

        assertThat(forAna.translate(title)).as("English, the mod has no pt-BR").isEqualTo("Homes of Ana");
        players.languages.put(ana.id(), "es-ES");
        assertThat(forAna.translate(title)).isEqualTo("Casas de Ana");
        assertThat(forAna.translate(
                        Text.key("homes.nothing", "Nothing for {player}").with("player", "Ana")))
                .isEqualTo("Nothing for Ana");
        assertThat(forAna.translate(Text.of("Fixed {player}"))).isEqualTo("Fixed {player}");
    }

    @Test
    void settingsAndErrorsUseTheirKeys() {
        players.languages.put(ana.id(), "pt-BR");
        var forAna = texts.forPlayer(Optional.of(ana.id()));
        var definition = SettingDefinition.server(
                        "homes", "tab", SettingType.choice("Server Configs"), "Server Configs")
                .named(Text.key("controlcore.settings.panel_start_tab.name", "Start tab"))
                .optionsKey("controlcore.tabs");
        Error invalid = SettingType.integer(1, 9).parse("70").error();

        assertThat(forAna.translate(definition.nameText())).isEqualTo("Aba inicial do painel");
        assertThat(forAna.translate(definition.optionText("Server Configs"))).isEqualTo("Config. do servidor");
        assertThat(forAna.error(invalid)).isEqualTo("'70' não é um inteiro válido 1..9.");
        assertThat(invalid.description()).isEqualTo("'70' is not a valid integer 1..9.");
    }

    @Test
    void commandsGetDescriptionKeysAndTranslatedErrors() {
        players.languages.put(ana.id(), "es-ES");
        CommandSpec spec = CommandSpec.named("cc-set")
                .description("Change a setting")
                .arg("id", ArgType.STRING, "Setting id")
                .arg("other", ArgType.STRING, "Not in the files");
        CommandSpec unknown = CommandSpec.named("homes-go").description("Go home");

        CommandSpec localized = CommandTexts.spec(spec, texts);
        Result<Reply> failed = CommandTexts.handler(
                        spec,
                        context -> Result.failure(Error.notFound(
                                "Settings.UnknownSetting",
                                Text.key("controlcore.errors.settings.unknown_setting", "There is no setting {id}.")
                                        .with("id", "x"))),
                        texts)
                .handle(new FakeContext(CommandSender.of(ana)));
        Result<Reply> fromConsole = CommandTexts.handler(
                        CommandSpec.named("prefs").requirePlayer(), context -> Result.success(Reply.none()), texts)
                .handle(new FakeContext(CommandSender.console("Console")));

        assertThat(localized.description()).isEqualTo("controlcore.commands.cc-set.desc");
        assertThat(localized.args())
                .extracting(arg -> arg.description())
                .containsExactly("controlcore.commands.cc-set.arg.id", "Not in the files");
        assertThat(CommandTexts.spec(unknown, texts).description()).isEqualTo("Go home");
        assertThat(failed.error().description()).isEqualTo("No existe el ajuste x.");
        assertThat(failed.error().code()).isEqualTo("Settings.UnknownSetting");
        assertThat(fromConsole.error().description()).isEqualTo("Only players can use this command.");
    }

    private record FakeContext(CommandSender sender) implements CommandContext {
        @Override
        public Optional<Object> arg(String name) {
            return Optional.empty();
        }
    }
}
