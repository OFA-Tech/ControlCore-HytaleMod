package net.ofatech.controlcore.application.text;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandSender;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.system.Translations;

/**
 * Shows text in each player's game language, read every time so a change in the game's options shows at once;
 * text missing in that language falls back to English, then to the text's own fallback. The console reads
 * English.
 */
public final class Texts {

    public static final String ENGLISH = "en-US";
    /** Languages ControlCore itself is translated to. */
    public static final String[] LANGUAGES = {ENGLISH, "pt-BR", "es-ES"};

    private final Translations translations;
    private final Players players;

    public Texts(Translations translations, Players players) {
        this.translations = translations;
        this.players = players;
    }

    /** @return text for {@code viewer}, or the console when empty */
    public Translator forPlayer(Optional<PlayerId> viewer) {
        return text -> translate(text, viewer);
    }

    /** @return text for whoever ran a command */
    public Translator forSender(CommandSender sender) {
        return forPlayer(sender.player().map(info -> info.id()));
    }

    /** @return text in English, for the console and the log */
    public Translator forServer() {
        return forPlayer(Optional.empty());
    }

    /** @return the language {@code viewer} reads: their game language, or English */
    public String language(Optional<PlayerId> viewer) {
        return viewer.flatMap(players::language).orElse(ENGLISH);
    }

    /** @return whether English has {@code key} */
    public boolean has(String key) {
        return translations.lookup(ENGLISH, key).isPresent();
    }

    private String translate(Text text, Optional<PlayerId> viewer) {
        if (text.key().isEmpty()) {
            return text.fallbackText();
        }
        String key = text.key().get();
        Set<String> languages = new LinkedHashSet<>();
        languages.add(language(viewer));
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
