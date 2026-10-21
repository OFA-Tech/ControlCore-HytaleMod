package net.ofatech.controlcore.fakes;

import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.application.event.DomainEventBus;
import net.ofatech.controlcore.application.settings.ModNames;
import net.ofatech.controlcore.application.settings.SettingsRegistry;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.infrastructure.i18n.BundledTranslations;

/** ControlCore's texts as the game would show them, read from the language files in the jar. */
public final class TestTexts {

    private static final BundledTranslations BUNDLED = new BundledTranslations();

    private TestTexts() {}

    /** @return texts for {@code players} */
    public static Texts of(SettingsApi settings, FakePlayers players) {
        return new Texts(BUNDLED, players);
    }

    /** @return texts with a registry of their own */
    public static Texts standalone() {
        RecordingLog log = new RecordingLog();
        DomainEventBus bus = new DomainEventBus(log);
        return of(
                new SettingsRegistry(new FakeSettingStore(), new FakePlayerData(), bus, bus, log, new ModNames()),
                new FakePlayers());
    }

    /** @return text in English */
    public static Translator english() {
        return standalone().forServer();
    }
}
