package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import java.util.Optional;
import java.util.function.Predicate;
import net.ofatech.controlcore.api.DefaultsApi;
import net.ofatech.controlcore.application.players.PlayerDirectory;
import net.ofatech.controlcore.application.settings.DefaultsService;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.DefaultsView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(DefaultsApi.class)
public final class DefaultsApiService implements DefaultsApi {

    private final DefaultsService defaults;
    private final Texts texts;
    private final PlayerDirectory directory;
    private final ApiActor actor;

    public DefaultsApiService(DefaultsService defaults, Texts texts, PlayerDirectory directory) {
        this(defaults, texts, directory, ApiActor.MOD);
    }

    private DefaultsApiService(DefaultsService defaults, Texts texts, PlayerDirectory directory, ApiActor actor) {
        this.defaults = defaults;
        this.texts = texts;
        this.directory = directory;
        this.actor = actor;
    }

    @Override
    public DefaultsApi actingFor(PlayerId player) {
        return new DefaultsApiService(defaults, texts, directory, ApiActor.of(player, directory));
    }

    @Override
    public int count(Section section, Optional<String> world, String search) {
        DefaultsView.Section wanted = section(section);
        return defaults.count(wanted, matching(wanted, world, search), actor.who());
    }

    @Override
    public Result<String> restore(Section section, Optional<String> world, String search) {
        DefaultsView.Section wanted = section(section);
        Predicate<String> shown = matching(wanted, world, search);
        return actor.run(() -> defaults.restore(wanted, shown, actor.who(), actor.actor(), tr()));
    }

    private Predicate<String> matching(DefaultsView.Section section, Optional<String> world, String search) {
        return defaults.matching(section, world, search, actor.who(), tr());
    }

    private Translator tr() {
        return texts.forPlayer(actor.who());
    }

    private static DefaultsView.Section section(Section section) {
        return switch (section) {
            case MOD_SETTINGS -> DefaultsView.Section.MOD_SETTINGS;
            case PREFERENCES -> DefaultsView.Section.PREFS;
            case SERVER_CONFIGS -> DefaultsView.Section.SERVER_CONFIGS;
            case SERVER_KEYS -> DefaultsView.Section.KEYMAPS_SERVER;
            case MY_KEYS -> DefaultsView.Section.KEYMAPS_MINE;
            case PERMISSIONS -> DefaultsView.Section.PERMISSIONS;
        };
    }
}
