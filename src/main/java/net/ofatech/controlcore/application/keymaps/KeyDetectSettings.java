package net.ofatech.controlcore.application.keymaps;

import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.text.Text;

public final class KeyDetectSettings {

    public enum DetectMode {
        GAME,
        SCREEN
    }

    static final String GAME = "game";
    static final String SCREEN = "screen";
    private static final Text CATEGORY = Text.key("controlcore.settings.category.keymaps", "Keymaps");

    private final SettingsApi settings;
    private volatile Setting<String> mode;
    private volatile Setting<Boolean> allKeys;

    public KeyDetectSettings(SettingsApi settings) {
        this.settings = settings;
    }

    public synchronized void register() {
        if (mode != null) {
            return;
        }
        this.mode = settings.register(SettingDefinition.server(
                                "controlcore", "key-detect-mode", SettingType.choice(GAME, SCREEN), GAME)
                        .named(Text.key("controlcore.settings.key_detect_mode.name", "How Detect key works"))
                        .describedAs(
                                Text.key(
                                        "controlcore.settings.key_detect_mode.desc",
                                        "From the game: press the key while playing. Capture screen: any key, even ones the server can't hear yet."))
                        .inCategory(CATEGORY)
                        .optionsKey("controlcore.keys.detect_modes"))
                .value();
        this.allKeys = settings.register(SettingDefinition.server(
                                "controlcore", "all-keys-in-lists", SettingType.bool(), false)
                        .named(Text.key(
                                "controlcore.settings.all_keys_in_lists.name", "Show every keyboard key in key lists"))
                        .describedAs(Text.key(
                                "controlcore.settings.all_keys_in_lists.desc",
                                "Adds numpad, F-keys and the rest. Keys the server can't hear yet are marked."))
                        .inCategory(CATEGORY))
                .value();
    }

    public DetectMode mode() {
        Setting<String> current = mode;
        return current != null && SCREEN.equals(current.get()) ? DetectMode.SCREEN : DetectMode.GAME;
    }

    public boolean allKeys() {
        Setting<Boolean> current = allKeys;
        return current != null && current.get();
    }
}
