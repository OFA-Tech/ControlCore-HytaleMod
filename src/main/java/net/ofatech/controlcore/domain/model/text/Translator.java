package net.ofatech.controlcore.domain.model.text;

import net.ofatech.controlcore.domain.result.Error;

/**
 * Turns {@link Text} into words in one viewer's language.
 *
 * @since API 1.5.0
 */
@FunctionalInterface
public interface Translator {

    /**
     * Translates a text for this translator's viewer, with placeholders filled in.
     *
     * @param text the text to show
     * @return {@code text} in the viewer's language
     */
    String translate(Text text);

    /**
     * Translates a key with placeholder values, a shortcut for
     * {@code translate(Text.key(key, key).with(nameValuePairs))}.
     *
     * @param key a translation key; it is also the fallback when no language has it
     * @param nameValuePairs placeholder names and values, alternating
     * @return the key's text in the viewer's language
     * @throws net.ofatech.controlcore.domain.guard.DomainException if {@code key} is null or blank, or a
     *     placeholder name is blank
     * @throws IllegalArgumentException if the number of placeholder arguments is odd
     */
    default String t(String key, Object... nameValuePairs) {
        return translate(Text.key(key, key).with(nameValuePairs));
    }

    /**
     * Translates the message of an error.
     *
     * @param error the error whose {@link Error#message()} is shown
     * @return the error's message in the viewer's language
     */
    default String error(Error error) {
        return translate(error.message());
    }
}
