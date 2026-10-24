package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.application.component.FeatureModule;
import net.ofatech.controlcore.application.component.ModuleContext;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.text.Text;

@AutoService(FeatureModule.class)
public final class KeepGameActions implements FeatureModule {

    static final String ID = "controlcore:keys-keep-game-action";

    private final Setting<Boolean> enabled;
    private final KeyDispatcher dispatcher;

    public KeepGameActions(SettingsApi settings, KeyDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        this.enabled = settings.register(
                        SettingDefinition.server("controlcore", "keys-keep-game-action", SettingType.bool(), true)
                                .named(Text.key(
                                        "controlcore.settings.keys_keep_game_action.name",
                                        "Bound keys keep their game action"))
                                .describedAs(
                                        Text.key(
                                                "controlcore.settings.keys_keep_game_action.desc",
                                                "When on, a bound key does both: F still opens doors and also runs its binds. Turn off"
                                                        + " to let binds replace the key's own action, unless the mod asks to keep it.")))
                .value();
    }

    @Override
    public void start(ModuleContext context) {
        dispatcher.keepGameActions(enabled.get());
        enabled.onChange(event -> dispatcher.keepGameActions(enabled.get()));
    }
}
