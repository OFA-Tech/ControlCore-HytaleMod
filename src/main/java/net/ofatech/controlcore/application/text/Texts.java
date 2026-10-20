package net.ofatech.controlcore.application.text;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandSender;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.system.Translations;

/**
 * Shows text in each player's language: their game language first, then the server's language, then English;
 * text no language has falls back to its English fallback. The console reads the server's language.
 */
public final class Texts {

    public static final String ENGLISH = "en-US";
    /** Languages ControlCore itself is translated to. */
    public static final String[] LANGUAGES = {ENGLISH, "pt-BR", "es-ES"};

    static final String SERVER_LANGUAGE_ID = "controlcore:server-language";

    private final Translations translations;
    private final Players players;
    private final Setting<String> serverLanguage;

    public Texts(Translations translations, Players players, SettingsApi settings) {
        this.translations = translations;
        this.players = players;
        this.serverLanguage = settings.register(
                        SettingDefinition.server(SERVER_LANGUAGE_ID, SettingType.choice(LANGUAGES), ENGLISH)
                                .named(Text.key("controlcore.settings.server_language.name", "Server language"))
                                .describedAs(Text.key(
                                        "controlcore.settings.server_language.desc",
                                        "Used for the console and when a text is missing in a player's game language. Players"
                                                + " see ControlCore in their own game language."))
                                .inCategory(Text.key("controlcore.settings.category", "ControlCore")))
                .value();
    }

    /** @return text for {@code viewer}, or the console when empty */
    public Translator forPlayer(Optional<PlayerId> viewer) {
        return text -> translate(text, viewer);
    }

    /** @return text for whoever ran a command */
    public Translator forSender(CommandSender sender) {
        return forPlayer(sender.player().map(info -> info.id()));
    }

    /** @return text in the server's language */
    public Translator forServer() {
        return forPlayer(Optional.empty());
    }

    /** @return the language {@code viewer} reads: their game language, or the server's */
    public String language(Optional<PlayerId> viewer) {
        return viewer.flatMap(players::language).orElseGet(serverLanguage::get);
    }

    /** @return whether some language has {@code key} */
    public boolean has(String key) {
        return translations.lookup(ENGLISH, key).isPresent()
                || translations.lookup(serverLanguage.get(), key).isPresent();
    }

    private String translate(Text text, Optional<PlayerId> viewer) {
        if (text.key().isEmpty()) {
            return text.fallbackText();
        }
        String key = text.key().get();
        Set<String> languages = new LinkedHashSet<>();
        languages.add(language(viewer));
        languages.add(serverLanguage.get());
        languages.add(ENGLISH);
        for (String language : languages) {
            Optional<String> template = translations.lookup(language, key);
            if (template.isPresent()) {
                return text.format(template.get());
            }
        }
        return text.fallbackText();
    }
}
