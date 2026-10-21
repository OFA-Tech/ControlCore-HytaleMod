package net.ofatech.controlcore.infrastructure.platforms.hytale.i18n;

import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import java.util.Optional;
import net.ofatech.controlcore.domain.ports.system.Translations;
import net.ofatech.controlcore.infrastructure.i18n.BundledTranslations;

public final class HytaleTranslations implements Translations {

    private final BundledTranslations bundled;

    public HytaleTranslations(BundledTranslations bundled) {
        this.bundled = bundled;
    }

    @Override
    public Optional<String> lookup(String language, String key) {
        Optional<String> loaded;
        try {
            loaded = Optional.ofNullable(I18nModule.get()).map(module -> module.getMessage(language, key));
        } catch (RuntimeException e) {
            loaded = Optional.empty();
        }
        return loaded.filter(text -> !text.isBlank()).or(() -> bundled.lookup(language, key));
    }
}
