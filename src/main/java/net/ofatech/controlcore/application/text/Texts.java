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

public final class Texts {

    public static final String ENGLISH = "en-US";
    public static final String[] LANGUAGES = {ENGLISH, "pt-BR", "es-ES"};

    private final Translations translations;
    private final Players players;

    public Texts(Translations translations, Players players) {
        this.translations = translations;
        this.players = players;
    }

    public Translator forPlayer(Optional<PlayerId> viewer) {
        return text -> translate(text, viewer);
    }

    public Translator forSender(CommandSender sender) {
        return forPlayer(sender.player().map(info -> info.id()));
    }

    public Translator forServer() {
        return forPlayer(Optional.empty());
    }

    public String language(Optional<PlayerId> viewer) {
        return viewer.flatMap(players::language).orElse(ENGLISH);
    }

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
