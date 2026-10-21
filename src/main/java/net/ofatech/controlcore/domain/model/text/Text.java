package net.ofatech.controlcore.domain.model.text;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.domain.guard.Guard;

/**
 * Text shown to players: either fixed text, or a translation key looked up in the player's language with an
 * English fallback. Placeholders such as {@code {player}} are filled in from {@link #params()}. A
 * {@link Translator} turns it into words for one viewer.
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
 * @param params values for the placeholders, by placeholder name without braces
 * @since API 1.5.0
 */
public record Text(Optional<String> key, String fallback, Map<String, String> params) {

    /**
     * Creates a text. The key is stripped of surrounding whitespace, and a blank key counts as empty (fixed
     * text). {@code params} is copied.
     *
     * @param key the translation key, or empty for fixed text
     * @param fallback the English text, used when no translation is found
     * @param params values for the placeholders
     * @throws net.ofatech.controlcore.domain.guard.DomainException if any argument is null
     */
    public Text {
        Guard.notNull(key, "key");
        Guard.notNull(fallback, "fallback");
        Guard.notNull(params, "params");
        key = key.map(String::strip).filter(text -> !text.isEmpty());
        params = Map.copyOf(params);
    }

    /**
     * Creates fixed text that is never translated.
     *
     * @param text the text shown to every player; may contain placeholders for {@link #with(Object...)}
     * @return fixed text, never translated
     * @throws net.ofatech.controlcore.domain.guard.DomainException if {@code text} is null
     */
    public static Text of(String text) {
        return new Text(Optional.empty(), Guard.notNull(text, "text"), Map.of());
    }

    /**
     * Creates text that is looked up by translation key in the viewer's language.
     *
     * @param key the translation key, for example {@code homes.settings.max_homes}
     * @param fallback the English text, used when no language has the key
     * @return translatable text without placeholder values
     * @throws net.ofatech.controlcore.domain.guard.DomainException if {@code key} is null or blank, or
     *     {@code fallback} is null
     */
    public static Text key(String key, String fallback) {
        return new Text(Optional.of(Guard.notBlank(key, "key")), Guard.notNull(fallback, "fallback"), Map.of());
    }

    /**
     * Adds placeholder values. Values are turned into text with {@link String#valueOf(Object)}; a name that is
     * already set gets the new value.
     *
     * @param nameValuePairs placeholder names and values, alternating: {@code "id", id, "value", value}
     * @return a copy where each {@code {name}} is replaced by its value
     * @throws IllegalArgumentException if the number of arguments is odd
     * @throws net.ofatech.controlcore.domain.guard.DomainException if a name is blank
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

    /**
     * Turns a label into a translation key part: lower case, with each run of other characters than
     * {@code a-z} and {@code 0-9} replaced by one underscore, and no leading or trailing underscore.
     *
     * @param text the label, for example {@code Server Configs}
     * @return a key part for {@code text}: {@code Server Configs} becomes {@code server_configs}
     */
    public static String slug(String text) {
        return text.strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
    }

    /**
     * Returns the English fallback ready to show, without any translation.
     *
     * @return the fallback with its placeholders filled in
     */
    public String fallbackText() {
        return format(fallback);
    }

    /**
     * Fills this text's placeholder values into a template, such as a translated line.
     *
     * @param template text with {@code {name}} placeholders
     * @return {@code template} with this text's placeholders filled in; unknown placeholders stay as they are
     */
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
