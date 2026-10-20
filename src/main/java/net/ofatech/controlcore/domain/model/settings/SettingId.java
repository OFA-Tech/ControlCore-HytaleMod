package net.ofatech.controlcore.domain.model.settings;

import java.util.Locale;
import java.util.regex.Pattern;
import net.ofatech.controlcore.domain.guard.DomainException;
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

    public SettingId {
        if (namespace == null || !NAMESPACE.matcher(namespace).matches()) {
            throw new DomainException("Invalid setting namespace: " + namespace);
        }
        if (key == null || !KEY.matcher(key).matches()) {
            throw new DomainException("Invalid setting key: " + key);
        }
    }

    /**
     * Parses user input.
     *
     * @param text {@code namespace:key}; upper case is accepted and folded to lower case
     * @return the id, or the error {@code Settings.InvalidId}
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
                        "'" + text + "' is not a setting id. Use namespace:key, for example "
                                + "controlcore:control-panel-enabled.")
                .localized("controlcore.errors.settings.invalidid", "text", text));
    }

    /**
     * Parses an id written in code.
     *
     * @param text {@code namespace:key}
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

    @Override
    public int compareTo(SettingId other) {
        return toString().compareTo(other.toString());
    }

    @Override
    public String toString() {
        return namespace + ":" + key;
    }
}
