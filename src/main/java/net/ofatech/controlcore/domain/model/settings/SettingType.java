package net.ofatech.controlcore.domain.model.settings;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.ofatech.controlcore.domain.guard.DomainException;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

/**
 * The kind of value a setting holds, how it is written as text and which values are allowed.
 *
 * @param <T> Java type of the value
 */
public sealed interface SettingType<T>
        permits SettingType.BooleanType,
                SettingType.IntegerType,
                SettingType.DecimalType,
                SettingType.TextType,
                SettingType.ChoiceType,
                SettingType.ColorType,
                SettingType.TextListType {

    /** Error code for a value that does not fit the type. */
    String INVALID_VALUE = "Settings.InvalidValue";

    /** @return {@code true} / {@code false}; also accepts on/off, yes/no, 1/0 */
    static SettingType<Boolean> bool() {
        return new BooleanType();
    }

    /** @return a whole number between {@code min} and {@code max}, inclusive */
    static SettingType<Integer> integer(int min, int max) {
        return new IntegerType(min, max);
    }

    /** @return a decimal number between {@code min} and {@code max}, inclusive */
    static SettingType<Double> decimal(double min, double max) {
        return new DecimalType(min, max);
    }

    /** @return free text of at most {@code maxLength} characters */
    static SettingType<String> text(int maxLength) {
        return new TextType(maxLength);
    }

    /** @return one of the given options (matched case-insensitively, stored as written here) */
    static SettingType<String> choice(String... options) {
        return new ChoiceType(List.of(options));
    }

    /**
     * @return a colour written {@code #rrggbb} (also accepts {@code rrggbb} and {@code #rgb}; stored lower case)
     * @since API 2.0.0
     */
    static SettingType<String> color() {
        return new ColorType();
    }

    /**
     * @return a list of short texts, for example player names; written comma-separated ({@code a, b, c}), so items
     *     cannot contain commas
     * @param maxItems most items allowed
     * @param maxItemLength longest item allowed
     * @since API 2.0.0
     */
    static SettingType<List<String>> textList(int maxItems, int maxItemLength) {
        return new TextListType(maxItems, maxItemLength);
    }

    /** @return the Java type of the values */
    Class<T> valueType();

    /**
     * @param text user input
     * @return the value, or {@link #INVALID_VALUE}
     */
    Result<T> parse(String text);

    /**
     * @param value a value to check, for example one passed by another mod
     * @return the value, or {@link #INVALID_VALUE}
     */
    Result<T> validate(T value);

    /** @return the value as text that {@link #parse(String)} reads back */
    default String format(T value) {
        return String.valueOf(value);
    }

    /** @return a short description for players, for example {@code "integer 1..100"} */
    String describe();

    /**
     * @return {@link #describe()} in the player's language
     * @since API 1.5.0
     */
    default Text describeText() {
        return Text.of(describe());
    }

    private static <T> Result<T> invalid(String text, SettingType<?> type) {
        Text described = type.describeText();
        String english = "'" + text + "' is not a valid " + type.describe() + ".";
        Text message = described
                .key()
                .map(key -> key.replace("controlcore.types.", "controlcore.errors.invalid_value."))
                .map(key -> new Text(Optional.of(key), english, described.params()).with("value", text))
                .orElse(Text.of(english));
        return Result.failure(Error.validation(INVALID_VALUE, message));
    }

    /** {@code true} or {@code false}. */
    record BooleanType() implements SettingType<Boolean> {

        @Override
        public Class<Boolean> valueType() {
            return Boolean.class;
        }

        @Override
        public Result<Boolean> parse(String text) {
            return switch (text == null ? "" : text.trim().toLowerCase(Locale.ROOT)) {
                case "true", "on", "yes", "1" -> Result.success(true);
                case "false", "off", "no", "0" -> Result.success(false);
                default -> invalid(text, this);
            };
        }

        @Override
        public Result<Boolean> validate(Boolean value) {
            return value == null ? invalid("null", this) : Result.success(value);
        }

        @Override
        public String describe() {
            return "boolean (true/false)";
        }

        @Override
        public Text describeText() {
            return Text.key("controlcore.types.boolean", describe());
        }
    }

    /**
     * A whole number.
     *
     * @param min smallest allowed value
     * @param max largest allowed value
     */
    record IntegerType(int min, int max) implements SettingType<Integer> {

        public IntegerType {
            if (min > max) {
                throw new DomainException("min " + min + " is greater than max " + max);
            }
        }

        @Override
        public Class<Integer> valueType() {
            return Integer.class;
        }

        @Override
        public Result<Integer> parse(String text) {
            try {
                return validate(Integer.parseInt(text == null ? "" : text.trim()));
            } catch (NumberFormatException e) {
                return invalid(text, this);
            }
        }

        @Override
        public Result<Integer> validate(Integer value) {
            return value != null && value >= min && value <= max
                    ? Result.success(value)
                    : invalid(String.valueOf(value), this);
        }

        @Override
        public String describe() {
            return "integer " + min + ".." + max;
        }

        @Override
        public Text describeText() {
            return Text.key("controlcore.types.integer", describe()).with("min", min, "max", max);
        }
    }

    /**
     * A decimal number.
     *
     * @param min smallest allowed value
     * @param max largest allowed value
     */
    record DecimalType(double min, double max) implements SettingType<Double> {

        public DecimalType {
            if (!(min <= max)) {
                throw new DomainException("min " + min + " is greater than max " + max);
            }
        }

        @Override
        public Class<Double> valueType() {
            return Double.class;
        }

        @Override
        public Result<Double> parse(String text) {
            try {
                return validate(Double.parseDouble(text == null ? "" : text.trim()));
            } catch (NumberFormatException e) {
                return invalid(text, this);
            }
        }

        @Override
        public Result<Double> validate(Double value) {
            return value != null && Double.isFinite(value) && value >= min && value <= max
                    ? Result.success(value)
                    : invalid(String.valueOf(value), this);
        }

        @Override
        public String describe() {
            return "decimal " + min + ".." + max;
        }

        @Override
        public Text describeText() {
            return Text.key("controlcore.types.decimal", describe()).with("min", min, "max", max);
        }
    }

    /**
     * Free text.
     *
     * @param maxLength longest allowed text
     */
    record TextType(int maxLength) implements SettingType<String> {

        public TextType {
            if (maxLength < 1) {
                throw new DomainException("maxLength must be at least 1");
            }
        }

        @Override
        public Class<String> valueType() {
            return String.class;
        }

        @Override
        public Result<String> parse(String text) {
            return validate(text);
        }

        @Override
        public Result<String> validate(String value) {
            return value != null && value.length() <= maxLength
                    ? Result.success(value)
                    : invalid(String.valueOf(value), this);
        }

        @Override
        public String describe() {
            return "text (up to " + maxLength + " characters)";
        }

        @Override
        public Text describeText() {
            return Text.key("controlcore.types.text", describe()).with("max", maxLength);
        }
    }

    /**
     * One of a fixed list of options.
     *
     * @param options allowed values, in display order
     */
    record ChoiceType(List<String> options) implements SettingType<String> {

        public ChoiceType {
            options = List.copyOf(options);
            if (options.isEmpty() || options.stream().anyMatch(String::isBlank)) {
                throw new DomainException("A choice needs at least one non-blank option");
            }
        }

        @Override
        public Class<String> valueType() {
            return String.class;
        }

        @Override
        public Result<String> parse(String text) {
            String wanted = text == null ? "" : text.trim();
            return options.stream()
                    .filter(option -> option.equalsIgnoreCase(wanted))
                    .findFirst()
                    .<Result<String>>map(Result::success)
                    .orElseGet(() -> invalid(text, this));
        }

        @Override
        public Result<String> validate(String value) {
            return value != null && options.contains(value) ? Result.success(value) : invalid(value, this);
        }

        @Override
        public String describe() {
            return "one of " + String.join(", ", options);
        }

        @Override
        public Text describeText() {
            return Text.key("controlcore.types.choice", describe()).with("options", String.join(", ", options));
        }
    }

    /**
     * A colour, {@code #rrggbb}.
     *
     * @since API 2.0.0
     */
    record ColorType() implements SettingType<String> {

        private static final java.util.regex.Pattern HEX =
                java.util.regex.Pattern.compile("#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})");

        @Override
        public Class<String> valueType() {
            return String.class;
        }

        @Override
        public Result<String> parse(String text) {
            String wanted = text == null ? "" : text.strip();
            java.util.regex.Matcher match = HEX.matcher(wanted);
            if (!match.matches()) {
                return invalid(text, this);
            }
            String digits = match.group(1).toLowerCase(Locale.ROOT);
            if (digits.length() == 3) {
                StringBuilder full = new StringBuilder();
                for (char digit : digits.toCharArray()) {
                    full.append(digit).append(digit);
                }
                digits = full.toString();
            }
            return Result.success("#" + digits);
        }

        @Override
        public Result<String> validate(String value) {
            return value != null && value.matches("#[0-9a-f]{6}") ? Result.success(value) : invalid(value, this);
        }

        @Override
        public String describe() {
            return "colour (#rrggbb)";
        }

        @Override
        public Text describeText() {
            return Text.key("controlcore.types.color", describe());
        }
    }

    /**
     * A list of short texts.
     *
     * @param maxItems most items allowed
     * @param maxItemLength longest item allowed
     * @since API 2.0.0
     */
    record TextListType(int maxItems, int maxItemLength) implements SettingType<List<String>> {

        /** Separates items when the list is written as text. */
        public static final String SEPARATOR = ",";

        public TextListType {
            if (maxItems < 1 || maxItemLength < 1) {
                throw new DomainException("maxItems and maxItemLength must be at least 1");
            }
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Class<List<String>> valueType() {
            return (Class) List.class;
        }

        @Override
        public Result<List<String>> parse(String text) {
            List<String> items = new java.util.ArrayList<>();
            for (String part : (text == null ? "" : text).split(SEPARATOR)) {
                String item = part.strip();
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }
            return validate(items);
        }

        @Override
        public Result<List<String>> validate(List<String> value) {
            if (value == null
                    || value.size() > maxItems
                    || value.stream()
                            .anyMatch(item -> item == null
                                    || item.isBlank()
                                    || item.length() > maxItemLength
                                    || item.contains(SEPARATOR)
                                    || !item.equals(item.strip()))) {
                return invalid(value == null ? "null" : String.join(SEPARATOR + " ", value), this);
            }
            return Result.success(List.copyOf(value));
        }

        @Override
        public String format(List<String> value) {
            return String.join(SEPARATOR + " ", value);
        }

        @Override
        public String describe() {
            return "list (up to " + maxItems + " items, separated by commas)";
        }

        @Override
        public Text describeText() {
            return Text.key("controlcore.types.text_list", describe()).with("max", maxItems);
        }
    }
}
