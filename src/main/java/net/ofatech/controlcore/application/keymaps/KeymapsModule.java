package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.FeatureModule;
import net.ofatech.controlcore.application.component.ModuleContext;
import net.ofatech.controlcore.domain.model.keys.Key;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.game.PlayerInput;

@AutoService(FeatureModule.class)
public final class KeymapsModule implements FeatureModule {

    static final String OPEN_PREFERENCES = "open-preferences";

    private final KeymapRegistry keymaps;
    private final KeyDetector detector;
    private final KeyDetectSettings detectSettings;
    private final PlayerInput input;

    public KeymapsModule(
            KeymapRegistry keymaps, KeyDetector detector, PlayerInput input, KeyDetectSettings detectSettings) {
        this.keymaps = keymaps;
        this.detector = detector;
        this.detectSettings = detectSettings;
        this.input = input;
    }

    @Override
    public void setup(ModuleContext context) {
        keymaps.loadCustom();
        detectSettings.register();
        keymaps.register(KeyAction.of("controlcore", OPEN_PREFERENCES)
                .displayName(Text.key("controlcore.keys.actions.open_preferences.name", "Open my preferences"))
                .describedAs(Text.key(
                        "controlcore.keys.actions.open_preferences.desc", "Opens your preferences and key bindings."))
                .command("prefs")
                .defaultKeys(Key.CTRL, Key.F));
    }

    @Override
    public void start(ModuleContext context) {
        input.listen(detector);
    }

    @Override
    public void shutdown() {
        input.stop();
    }
}
