package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.application.component.FeatureModule;
import net.ofatech.controlcore.application.component.ModuleContext;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.game.EmptyHandInput;

/**
 * The server setting {@value #ID}: whether keys that do nothing with an empty hand (right click, abilities 1, 2 and
 * 4, dodge) still reach the server, so they can be bound. Applied when the server starts and when it changes.
 */
@AutoService(FeatureModule.class)
public final class EmptyHandKeys implements FeatureModule {

    static final String ID = "controlcore:empty-hand-keys";

    private final Setting<Boolean> enabled;
    private final EmptyHandInput input;

    public EmptyHandKeys(SettingsApi settings, EmptyHandInput input) {
        this.input = input;
        this.enabled = settings.register(SettingDefinition.server(ID, SettingType.bool(), true)
                        .named(Text.key(
                                "controlcore.settings.empty_hand_keys.name", "Report every key with an empty hand"))
                        .describedAs(Text.key(
                                "controlcore.settings.empty_hand_keys.desc",
                                "Right click, abilities 1, 2 and 4 and dodge reach the server with an empty hand (or a"
                                        + " plain block or item), so they can be bound. They still do nothing unless"
                                        + " bound. Turn off if another mod changes the empty hand."))
                        .inCategory(Text.key("controlcore.settings.category", "ControlCore")))
                .value();
    }

    @Override
    public void start(ModuleContext context) {
        input.reportAllKeys(enabled.get());
        enabled.onChange(event -> input.reportAllKeys(enabled.get()));
    }
}
