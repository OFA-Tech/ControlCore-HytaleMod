package net.ofatech.controlcore.application.history;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.application.component.FeatureModule;
import net.ofatech.controlcore.application.component.ModuleContext;
import net.ofatech.controlcore.application.keymaps.KeymapRegistry;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Kind;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.text.Text;

@AutoService(FeatureModule.class)
public final class HistoryModule implements FeatureModule {

    static final String DAYS_ID = "controlcore:history-days";
    static final int DEFAULT_DAYS = 90;

    private final SettingsApi settings;
    private final ServerConfigEditor serverConfig;
    private final KeymapRegistry keymaps;
    private final ChangeHistory history;
    private final Setting<Integer> days;

    public HistoryModule(
            SettingsApi settings, ServerConfigEditor serverConfig, KeymapRegistry keymaps, ChangeHistory history) {
        this.settings = settings;
        this.serverConfig = serverConfig;
        this.keymaps = keymaps;
        this.history = history;
        this.days = settings.register(SettingDefinition.server(
                                "controlcore", "history-days", SettingType.integer(1, 3650), DEFAULT_DAYS)
                        .named(Text.key("controlcore.settings.history_days.name", "Keep change history (days)"))
                        .describedAs(Text.key(
                                "controlcore.settings.history_days.desc",
                                "How long the History tab and /cc-history keep changes. Older months are deleted.")))
                .value();
    }

    @Override
    public void start(ModuleContext context) {
        history.load(days.get());
        days.onChange(event -> history.keepDays(days.get()));
        settings.onAnyChange(change -> {
            if (change.player().isEmpty()) {
                history.record(Kind.SETTING, change.id().toString(), change.oldValue(), change.newValue(), false);
            }
        });
        serverConfig.onAnyChange(change -> history.record(
                Kind.SERVER_CONFIG,
                change.id().toString(),
                change.oldValue(),
                change.newValue(),
                serverConfig
                        .read(change.id(), Optional.empty())
                        .map(snapshot -> snapshot.access() == SettingAccess.APPLIES_AFTER_RESTART)
                        .orElse(false)));
        keymaps.onChange(change -> {
            if (change.player().isEmpty()) {
                history.record(Kind.SERVER_KEYS, change.id().toString(), change.oldValue(), change.newValue(), false);
            }
        });
    }
}
