package net.ofatech.controlcore.application.settings;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.text.Text;

/** The names mods are shown under in headings of settings and keys, by mod id. */
public final class ModNames {

    private final Map<String, Text> names = new ConcurrentHashMap<>();

    public ModNames() {
        names.put("controlcore", Text.key("controlcore.settings.category", "ControlCore"));
        names.put("custom", Text.key("controlcore.keys.custom_mod", "Custom binds"));
    }

    /** @return the name {@code modId} is shown under: the one set, else the id capitalised */
    public Text of(String modId) {
        Text name = names.get(key(modId));
        return name != null ? name : Text.of(SettingDefinition.defaultModName(modId));
    }

    /** @param name the name to show; {@code null} goes back to the id capitalised */
    public void set(String modId, Text name) {
        if (name == null || name.fallbackText().isBlank()) {
            names.remove(key(modId));
        } else {
            names.put(key(modId), name);
        }
    }

    private static String key(String modId) {
        return modId.strip().toLowerCase(Locale.ROOT);
    }
}
