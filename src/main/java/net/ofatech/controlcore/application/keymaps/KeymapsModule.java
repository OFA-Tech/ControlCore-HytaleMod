package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.FeatureModule;
import net.ofatech.controlcore.application.component.ModuleContext;
import net.ofatech.controlcore.domain.model.keys.Key;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.game.PlayerInput;

/** Registers ControlCore's own key actions and connects player input to the {@link KeyDispatcher}. */
@AutoService(FeatureModule.class)
public final class KeymapsModule implements FeatureModule {

    static final String OPEN_PREFERENCES = "open-preferences";

    private final KeymapRegistry keymaps;
    private final KeyDispatcher dispatcher;
    private final PlayerInput input;

    public KeymapsModule(KeymapRegistry keymaps, KeyDispatcher dispatcher, PlayerInput input) {
        this.keymaps = keymaps;
        this.dispatcher = dispatcher;
        this.input = input;
    }

    @Override
    public void setup(ModuleContext context) {
        keymaps.loadCustom();
        keymaps.register(KeyAction.of("controlcore", OPEN_PREFERENCES)
                .displayName(Text.key("controlcore.keys.actions.open_preferences.name", "Open my preferences"))
                .describedAs(Text.key(
                        "controlcore.keys.actions.open_preferences.desc", "Opens your preferences and key bindings."))
                .command("prefs")
                .defaultKeys(Key.CTRL, Key.F));
    }

    @Override
    public void start(ModuleContext context) {
        input.listen(dispatcher);
    }

    @Override
    public void shutdown() {
        input.stop();
    }
}
