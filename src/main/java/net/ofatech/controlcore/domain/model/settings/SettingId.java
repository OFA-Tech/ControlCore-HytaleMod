package net.ofatech.controlcore.domain.model.settings;

import java.util.Locale;
import java.util.regex.Pattern;
import net.ofatech.controlcore.domain.guard.DomainException;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Identifies a registered setting as {@code namespace:key}, for example {@code controlcore:control-panel-enabled}.
 * Both parts are lower case; the namespace is usually the owning mod's name.
 *
 * @param namespace owner of the setting: lower-case letters, digits, {@code -} and {@code _}
 * @param key name within the namespace: lower-case letters, digits, {@code -}, {@code _} and {@code .}
 */
public record SettingId(String namespace, String key) implements Comparable<SettingId> {

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,127}");

    /**
     * Creates an id from its two parts; use {@link #of(String)} or {@link #parse(String)} for {@code namespace:key}
     * text.
     *
     * @param namespace owner of the setting, 1 to 64 characters: lower-case letters, digits, {@code -} and
     *     {@code _}, starting with a letter or digit
     * @param key name within the namespace, 1 to 128 characters: lower-case letters, digits, {@code -}, {@code _}
     *     and {@code .}, starting with a letter or digit
     * @throws DomainException if either part is {@code null} or does not match its format
     */
    public SettingId {
        if (namespace == null || !NAMESPACE.matcher(namespace).matches()) {
            throw new DomainException("Invalid setting namespace: " + namespace);
        }
        if (key == null || !KEY.matcher(key).matches()) {
            throw new DomainException("Invalid setting key: " + key);
        }
    }

    /**
     * Parses user input; surrounding spaces are ignored.
     *
     * @param text {@code namespace:key}; upper case is accepted and folded to lower case; may be {@code null}
     * @return the id, or the error {@code Settings.InvalidId} if the text is {@code null} or not a valid id
     */
    public static Result<SettingId> parse(String text) {
        if (text != null) {
            String folded = text.trim().toLowerCase(Locale.ROOT);
            int colon = folded.indexOf(':');
            if (colon > 0) {
                String namespace = folded.substring(0, colon);
                String key = folded.substring(colon + 1);
                if (NAMESPACE.matcher(namespace).matches() && KEY.matcher(key).matches()) {
                    return Result.success(new SettingId(namespace, key));
                }
            }
        }
        return Result.failure(Error.validation(
                "Settings.InvalidId",
                Text.key(
                                "controlcore.errors.settings.invalid_id",
                                "'{text}' is not a setting id. Use namespace:key, for example controlcore:control-panel-enabled.")
                        .with("text", text)));
    }

    /**
     * Parses an id written in code, like {@link #parse(String)} but throwing on invalid text.
     *
     * @param text {@code namespace:key}, for example {@code homes:max-homes}
     * @return the id
     * @throws DomainException if the text is not a valid id
     */
    public static SettingId of(String text) {
        Result<SettingId> parsed = parse(text);
        if (parsed.isFailure()) {
            throw new DomainException(parsed.error().description());
        }
        return parsed.value();
    }

    /**
     * Orders ids alphabetically by their {@code namespace:key} text.
     *
     * @param other the id to compare with
     * @return a negative number, zero or a positive number as this id sorts before, equal to or after {@code other}
     */
    @Override
    public int compareTo(SettingId other) {
        return toString().compareTo(other.toString());
    }

    /**
     * Returns the id as text, the form {@link #of(String)} and {@link #parse(String)} read back.
     *
     * @return {@code namespace:key}, for example {@code homes:max-homes}
     */
    @Override
    public String toString() {
        return namespace + ":" + key;
    }
}
