package net.ofatech.controlcore.domain.model.text;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.domain.guard.Guard;

/**
 * Text shown to players: either fixed text, or a translation key looked up in the player's language with an
 * English fallback. Placeholders such as {@code {player}} are filled in from {@link #params()}.
 *
 * <pre>{@code
 * Text.key("homes.settings.max_homes", "Max homes")       // homes.lang: settings.max_homes = Max homes
 * Text.key("homes.messages.saved", "Saved {name}").with("name", home)
 * Text.of("Max homes")                                      // not translated
 * }</pre>
 *
 * <p>Translation keys come from {@code Server/Languages/<language>/<file>.lang} in a mod's assets: a line
 * {@code settings.max_homes = Max homes} in {@code homes.lang} is the key {@code homes.settings.max_homes}.
 *
 * @param key the translation key, or empty for fixed text
 * @param fallback the text used when no translation is found (English); may contain placeholders
 * @param params values for the placeholders
 * @since API 1.5.0
 */
public record Text(Optional<String> key, String fallback, Map<String, String> params) {

    public Text {
        Guard.notNull(key, "key");
        Guard.notNull(fallback, "fallback");
        Guard.notNull(params, "params");
        key = key.map(String::strip).filter(text -> !text.isEmpty());
        params = Map.copyOf(params);
    }

    /** @return fixed text, never translated */
    public static Text of(String text) {
        return new Text(Optional.empty(), Guard.notNull(text, "text"), Map.of());
    }

    /**
     * @param key the translation key, for example {@code homes.settings.max_homes}
     * @param fallback the English text, used when no language has the key
     */
    public static Text key(String key, String fallback) {
        return new Text(Optional.of(Guard.notBlank(key, "key")), Guard.notNull(fallback, "fallback"), Map.of());
    }

    /**
     * @param nameValuePairs placeholder names and values, alternating: {@code "id", id, "value", value}
     * @return a copy where each {@code {name}} is replaced by its value
     */
    public Text with(Object... nameValuePairs) {
        if (nameValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholders come in name, value pairs");
        }
        Map<String, String> next = new LinkedHashMap<>(params);
        for (int index = 0; index < nameValuePairs.length; index += 2) {
            next.put(
                    Guard.notBlank(String.valueOf(nameValuePairs[index]), "name"),
                    String.valueOf(nameValuePairs[index + 1]));
        }
        return new Text(key, fallback, next);
    }

    /** @return a key part for {@code text}: {@code Server Configs} becomes {@code server_configs} */
    public static String slug(String text) {
        return text.strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
    }

    /** @return the fallback with its placeholders filled in */
    public String fallbackText() {
        return format(fallback);
    }

    /** @return {@code template} with this text's placeholders filled in; unknown placeholders stay as they are */
    public String format(String template) {
        if (params.isEmpty() || template.indexOf('{') < 0) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> param : params.entrySet()) {
            result = result.replace("{" + param.getKey() + "}", param.getValue());
        }
        return result;
    }
}
