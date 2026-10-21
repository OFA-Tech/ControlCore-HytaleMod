package net.ofatech.controlcore.domain.model.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SettingSearch {

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

        public String label() {
            return label;
        }

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

        public String label() {
            return label;
        }

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

    public static SettingSearch parse(String text) {
        return parse(text, Filter.ALL);
    }

    public static SettingSearch parse(String text, Filter filter) {
        return parse(text, filter, Match.CONTAINS);
    }

    public static SettingSearch parse(String text, Filter filter, Match match) {
        String query = text == null ? "" : text.strip();
        List<Term> terms = new ArrayList<>();
        if (filter == Filter.CHANGED || filter == Filter.UNCHANGED) {
            terms.add(new Term(Field.IS, filter == Filter.CHANGED ? "changed" : "default", false, Operator.EQUALS));
        }
        boolean eachWordSeparately = match.operator == Operator.CONTAINS;
        String[] phrases =
                query.isEmpty() ? new String[0] : eachWordSeparately ? query.split("\\s+") : new String[] {query};
        for (String word : phrases) {
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

    public String text() {
        return text;
    }

    public Filter filter() {
        return filter;
    }

    public Match match() {
        return match;
    }

    public boolean isEmpty() {
        return terms.isEmpty();
    }

    public boolean matches(SettingSnapshot snapshot, String description) {
        return matches(
                snapshot,
                snapshot.definition().displayName(),
                snapshot.definition().heading(),
                description);
    }

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
                case MOD -> has(definition.id().namespace()) || has(definition.modName());
                case CATEGORY -> has(definition.heading()) || has(category);
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
                            || has(definition.heading())
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
