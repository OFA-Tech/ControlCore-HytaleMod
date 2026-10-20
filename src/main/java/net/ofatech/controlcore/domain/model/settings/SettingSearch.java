package net.ofatech.controlcore.domain.model.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A search typed into the control panel. Words are separated by spaces and must all match (case does not matter;
 * spaces inside values are ignored, so {@code ctrl+f} finds {@code CTRL + F}). A plain word matches the name, id,
 * mod id, category, value, default or description. A prefix limits a word to one field:
 *
 * <ul>
 *   <li>{@code name:} the display name
 *   <li>{@code id:} the full id, for example {@code controlcore:welcome-message}
 *   <li>{@code mod:} the mod id (the id's namespace)
 *   <li>{@code category:} or {@code cat:} the heading
 *   <li>{@code key:} or {@code value:} the current value (for key bindings: the keys)
 *   <li>{@code default:} the default value
 *   <li>{@code desc:} the description
 *   <li>{@code is:changed} only values that differ from their default; {@code is:default} the others
 * </ul>
 *
 * A word starting with {@code -} excludes matches, for example {@code -mod:server}.
 */
public final class SettingSearch {

    /** A search that matches everything. */
    public static final SettingSearch ALL = new SettingSearch("", List.of());

    private final String text;
    private final List<Term> terms;

    private SettingSearch(String text, List<Term> terms) {
        this.text = text;
        this.terms = terms;
    }

    /** @param text what the player typed; {@code null} or blank matches everything */
    public static SettingSearch parse(String text) {
        String query = text == null ? "" : text.strip();
        if (query.isEmpty()) {
            return ALL;
        }
        List<Term> terms = new ArrayList<>();
        for (String word : query.split("\\s+")) {
            boolean negated = word.startsWith("-") && word.length() > 1;
            String body = (negated ? word.substring(1) : word).toLowerCase(Locale.ROOT);
            Field field = Field.ANY;
            int colon = body.indexOf(':');
            if (colon > 0) {
                Field prefixed = Field.of(body.substring(0, colon));
                if (prefixed != null) {
                    field = prefixed;
                    body = body.substring(colon + 1);
                }
            }
            if (!body.isEmpty()) {
                terms.add(new Term(field, compact(body), negated));
            }
        }
        return terms.isEmpty() ? ALL : new SettingSearch(query, List.copyOf(terms));
    }

    /** @return what the player typed */
    public String text() {
        return text;
    }

    public boolean isEmpty() {
        return terms.isEmpty();
    }

    /**
     * @param snapshot the row
     * @param description the description line the row shows
     */
    public boolean matches(SettingSnapshot snapshot, String description) {
        for (Term term : terms) {
            if (term.matches(snapshot, description) == term.negated) {
                return false;
            }
        }
        return true;
    }

    private static String compact(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private enum Field {
        ANY,
        NAME,
        ID,
        MOD,
        CATEGORY,
        VALUE,
        DEFAULT,
        DESCRIPTION,
        IS;

        static Field of(String prefix) {
            return switch (prefix) {
                case "name" -> NAME;
                case "id" -> ID;
                case "mod", "modid" -> MOD;
                case "category", "cat" -> CATEGORY;
                case "key", "keys", "value" -> VALUE;
                case "default" -> DEFAULT;
                case "desc", "description" -> DESCRIPTION;
                case "is" -> IS;
                default -> null;
            };
        }
    }

    private record Term(Field field, String word, boolean negated) {

        boolean matches(SettingSnapshot snapshot, String description) {
            SettingDefinition<?> definition = snapshot.definition();
            return switch (field) {
                case NAME -> has(definition.displayName());
                case ID -> has(definition.id().toString());
                case MOD -> has(definition.id().namespace());
                case CATEGORY -> has(definition.category());
                case VALUE -> has(snapshot.value());
                case DEFAULT -> has(definition.formattedDefault());
                case DESCRIPTION -> has(description);
                case IS ->
                    switch (word) {
                        case "changed", "modified", "overridden" -> snapshot.overridden();
                        case "default" -> !snapshot.overridden();
                        default -> false;
                    };
                case ANY ->
                    has(definition.displayName())
                            || has(definition.id().toString())
                            || has(definition.category())
                            || has(snapshot.value())
                            || has(definition.formattedDefault())
                            || has(description);
            };
        }

        private boolean has(String text) {
            return text != null && compact(text).contains(word);
        }
    }
}
