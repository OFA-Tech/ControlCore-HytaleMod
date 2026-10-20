package net.ofatech.controlcore.domain.model.settings;

import java.util.Locale;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.text.Text;

/**
 * Translation keys of a setting's texts; each one that is empty shows the plain text of the definition.
 *
 * @param name key of the display name
 * @param description key of the description
 * @param category key of the category heading
 * @param options key prefix of a choice's options: option {@code Server Configs} under {@code homes.tabs} is
 *     {@code homes.tabs.server_configs}
 * @since API 1.5.0
 */
public record SettingLabels(
        Optional<String> name, Optional<String> description, Optional<String> category, Optional<String> options) {

    /** No translation keys. */
    public static final SettingLabels NONE =
            new SettingLabels(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public SettingLabels {
        name = clean(name);
        description = clean(description);
        category = clean(category);
        options = clean(options);
    }

    SettingLabels withName(Optional<String> key) {
        return new SettingLabels(key, description, category, options);
    }

    SettingLabels withDescription(Optional<String> key) {
        return new SettingLabels(name, key, category, options);
    }

    SettingLabels withCategory(Optional<String> key) {
        return new SettingLabels(name, description, key, options);
    }

    SettingLabels withOptions(Optional<String> prefix) {
        return new SettingLabels(name, description, category, prefix);
    }

    /** @return the text of {@code option}, a choice's option */
    public Text option(String option) {
        return options.map(prefix -> Text.key(prefix + "." + slug(option), option))
                .orElse(Text.of(option));
    }

    /** @return {@code Server Configs} as {@code server_configs} */
    public static String slug(String text) {
        return text.strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
    }

    private static Optional<String> clean(Optional<String> key) {
        return key == null ? Optional.empty() : key.map(String::strip).filter(text -> !text.isEmpty());
    }
}
