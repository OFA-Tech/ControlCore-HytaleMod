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
 * A word starting with {@code -} excludes matches, for example {@code -mod:server}. A {@link Filter} picked next to
 * the search box limits words without a prefix to one field, or shows only changed or unchanged values.
 */
public final class SettingSearch {

    /** What the dropdown next to the search box limits the search to. */
    public enum Filter {
        ALL("All", Field.ANY),
        NAME("Name", Field.NAME),
        MOD("Mod id", Field.MOD),
        ID("Id", Field.ID),
        CATEGORY("Category", Field.CATEGORY),
        VALUE("Value / keys", Field.VALUE),
        DEFAULT("Default", Field.DEFAULT),
        DESCRIPTION("Description", Field.DESCRIPTION),
        CHANGED("Changed only", Field.ANY),
        UNCHANGED("Default only", Field.ANY);

        private final String label;
        private final Field field;

        Filter(String label, Field field) {
            this.label = label;
            this.field = field;
        }

        /** @return the text shown in the dropdown */
        public String label() {
            return label;
        }

        /** @param label a dropdown label or name; unknown or {@code null} gives {@link #ALL} */
        public static Filter of(String label) {
            if (label != null) {
                for (Filter filter : values()) {
                    if (filter.label.equalsIgnoreCase(label.strip())
                            || filter.name().equalsIgnoreCase(label.strip())) {
                        return filter;
                    }
                }
            }
            return ALL;
        }
    }

    /** How each word is compared with a field, picked in the second dropdown next to the search box. */
    public enum Match {
        CONTAINS("Contains", Operator.CONTAINS, false),
        EQUALS("Equals", Operator.EQUALS, false),
        STARTS_WITH("Starts with", Operator.STARTS_WITH, false),
        ENDS_WITH("Ends with", Operator.ENDS_WITH, false),
        NOT_CONTAINS("Doesn't contain", Operator.CONTAINS, true),
        NOT_EQUALS("Doesn't equal", Operator.EQUALS, true),
        NOT_STARTS_WITH("Doesn't start with", Operator.STARTS_WITH, true),
        NOT_ENDS_WITH("Doesn't end with", Operator.ENDS_WITH, true);

        private final String label;
        private final Operator operator;
        private final boolean negated;

        Match(String label, Operator operator, boolean negated) {
            this.label = label;
            this.operator = operator;
            this.negated = negated;
        }

        /** @return the text shown in the dropdown */
        public String label() {
            return label;
        }

        /** @param label a dropdown label or name; unknown or {@code null} gives {@link #CONTAINS} */
        public static Match of(String label) {
            if (label != null) {
                for (Match match : values()) {
                    if (match.label.equalsIgnoreCase(label.strip())
                            || match.name().equalsIgnoreCase(label.strip())) {
                        return match;
                    }
                }
            }
            return CONTAINS;
        }
    }

    private enum Operator {
        CONTAINS,
        EQUALS,
        STARTS_WITH,
        ENDS_WITH;

        boolean test(String text, String word) {
            return switch (this) {
                case CONTAINS -> text.contains(word);
                case EQUALS -> text.equals(word);
                case STARTS_WITH -> text.startsWith(word);
                case ENDS_WITH -> text.endsWith(word);
            };
        }
    }

    /** A search that matches everything. */
    public static final SettingSearch ALL = new SettingSearch("", Filter.ALL, Match.CONTAINS, List.of());

    private final String text;
    private final Filter filter;
    private final Match match;
    private final List<Term> terms;

    private SettingSearch(String text, Filter filter, Match match, List<Term> terms) {
        this.text = text;
        this.filter = filter;
        this.match = match;
        this.terms = terms;
    }

    /** @param text what the player typed; {@code null} or blank matches everything */
    public static SettingSearch parse(String text) {
        return parse(text, Filter.ALL);
    }

    /**
     * @param text what the player typed; {@code null} or blank matches everything the filter lets through
     * @param filter what the dropdown is set to
     */
    public static SettingSearch parse(String text, Filter filter) {
        return parse(text, filter, Match.CONTAINS);
    }

    /**
     * @param text what the player typed; {@code null} or blank matches everything the filter lets through
     * @param filter what the first dropdown is set to
     * @param match how words are compared (the second dropdown); a {@code "Doesn't …"} match excludes rows where
     *     the word matches, and a {@code -} in front of a word flips it again
     */
    public static SettingSearch parse(String text, Filter filter, Match match) {
        String query = text == null ? "" : text.strip();
        List<Term> terms = new ArrayList<>();
        if (filter == Filter.CHANGED || filter == Filter.UNCHANGED) {
            terms.add(new Term(Field.IS, filter == Filter.CHANGED ? "changed" : "default", false, Operator.EQUALS));
        }
        // Equals, starts with and ends with compare the whole text as one phrase; contains checks each word.
        String[] words = query.isEmpty()
                ? new String[0]
                : match.operator == Operator.CONTAINS ? query.split("\\s+") : new String[] {query};
        for (String word : words) {
            boolean negated = word.startsWith("-") && word.length() > 1;
            String body = (negated ? word.substring(1) : word).toLowerCase(Locale.ROOT);
            Field field = filter.field;
            int colon = body.indexOf(':');
            if (colon > 0) {
                Field prefixed = Field.of(body.substring(0, colon));
                if (prefixed != null) {
                    field = prefixed;
                    body = body.substring(colon + 1);
                }
            }
            if (!body.isEmpty()) {
                terms.add(new Term(field, compact(body), negated != match.negated, match.operator));
            }
        }
        return terms.isEmpty() && filter == Filter.ALL
                ? (match == Match.CONTAINS ? ALL : new SettingSearch("", filter, match, List.of()))
                : new SettingSearch(query, filter, match, List.copyOf(terms));
    }

    /** @return what the player typed */
    public String text() {
        return text;
    }

    /** @return what the first dropdown is set to */
    public Filter filter() {
        return filter;
    }

    /** @return how words are compared (the second dropdown) */
    public Match match() {
        return match;
    }

    public boolean isEmpty() {
        return terms.isEmpty();
    }

    /**
     * @param snapshot the row
     * @param description the description line the row shows
     */
    public boolean matches(SettingSnapshot snapshot, String description) {
        return matches(
                snapshot,
                snapshot.definition().displayName(),
                snapshot.definition().category(),
                description);
    }

    /**
     * @param name the row's name as shown (translated); the definition's own name matches too
     * @param category the row's category as shown (translated); the definition's own category matches too
     * @param description the description line the row shows
     */
    public boolean matches(SettingSnapshot snapshot, String name, String category, String description) {
        for (Term term : terms) {
            if (term.matches(snapshot, name, category, description) == term.negated) {
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

    private record Term(Field field, String word, boolean negated, Operator operator) {

        boolean matches(SettingSnapshot snapshot, String name, String category, String description) {
            SettingDefinition<?> definition = snapshot.definition();
            return switch (field) {
                case NAME -> has(definition.displayName()) || has(name);
                case ID -> has(definition.id().toString());
                case MOD -> has(definition.id().namespace());
                case CATEGORY -> has(definition.category()) || has(category);
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
                            || has(name)
                            || has(definition.id().toString())
                            || has(definition.category())
                            || has(category)
                            || has(snapshot.value())
                            || has(definition.formattedDefault())
                            || has(description);
            };
        }

        private boolean has(String text) {
            return text != null && operator.test(compact(text), word);
        }
    }
}
