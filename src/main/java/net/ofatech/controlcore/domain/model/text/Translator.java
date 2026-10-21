package net.ofatech.controlcore.domain.model.text;

import net.ofatech.controlcore.domain.result.Error;

/**
 * Turns {@link Text} into words in one viewer's language.
 *
 * @since API 1.5.0
 */
@FunctionalInterface
public interface Translator {

    /** @return {@code text} in the viewer's language */
    String translate(Text text);

    /**
     * @param key a translation key; it is also the fallback when no language has it
     * @param nameValuePairs placeholder names and values, alternating
     * @return the key's text in the viewer's language
     */
    default String t(String key, Object... nameValuePairs) {
        return translate(Text.key(key, key).with(nameValuePairs));
    }

    /** @return the error's message in the viewer's language */
    default String error(Error error) {
        return translate(error.message());
    }
}
