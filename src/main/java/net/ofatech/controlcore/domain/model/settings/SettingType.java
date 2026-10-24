package net.ofatech.controlcore.domain.model.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /**
     * A yes/no setting.
     *
     * @return a type written {@code true} / {@code false}; parsing also accepts on/off, yes/no and 1/0, ignoring
     *     case and surrounding spaces
     */
    static SettingType<Boolean> bool() {
        return new BooleanType();
    }

    /**
     * A whole-number setting with a range.
     *
     * @param min smallest allowed value
     * @param max largest allowed value; not smaller than {@code min}
     * @return a type for whole numbers between {@code min} and {@code max}, inclusive
     * @throws DomainException if {@code min} is greater than {@code max}
     */
    static SettingType<Integer> integer(int min, int max) {
        return new IntegerType(min, max);
    }

    /**
     * A decimal-number setting with a range.
     *
     * @param min smallest allowed value
     * @param max largest allowed value; not smaller than {@code min}
     * @return a type for finite decimal numbers between {@code min} and {@code max}, inclusive
     * @throws DomainException if {@code min} is greater than {@code max} or either is NaN
     */
    static SettingType<Double> decimal(double min, double max) {
        return new DecimalType(min, max);
    }

    /**
     * A free-text setting.
     *
     * @param maxLength longest allowed text in characters, at least 1
     * @return a type for text of at most {@code maxLength} characters; the empty text is allowed
     * @throws DomainException if {@code maxLength} is less than 1
     */
    static SettingType<String> text(int maxLength) {
        return new TextType(maxLength);
    }

    /**
     * A setting that holds one of a fixed list of options, for example {@code choice("north", "south")}.
     *
     * @param options the allowed values, in display order; at least one, none blank
     * @return a type for one of the options; parsing matches case-insensitively and stores the option as written
     *     here
     * @throws DomainException if there are no options or one is blank
     */
    static SettingType<String> choice(String... options) {
        return new ChoiceType(List.of(options));
    }

    /**
     * A colour setting.
     *
     * @return a type for a colour written {@code #rrggbb}; parsing also accepts {@code rrggbb} and {@code #rgb}
     *     and stores lower case, for example {@code #F80} becomes {@code #ff8800}
     * @since API 2.0.0
     */
    static SettingType<String> color() {
        return new ColorType();
    }

    /**
     * A setting that holds a list of short texts, for example player names.
     *
     * @param maxItems most items allowed, at least 1
     * @param maxItemLength longest item allowed in characters, at least 1
     * @return a type written comma-separated ({@code a, b, c}), so items cannot contain commas; parsing strips
     *     each item and drops empty ones
     * @throws DomainException if {@code maxItems} or {@code maxItemLength} is less than 1
     * @since API 2.0.0
     */
    static SettingType<List<String>> textList(int maxItems, int maxItemLength) {
        return new TextListType(maxItems, maxItemLength);
    }

    /**
     * Returns the Java class of the values.
     *
     * @return the value class, for example {@code Integer.class}; {@code List.class} for {@link #textList(int, int)}
     */
    Class<T> valueType();

    /**
     * Reads a value from text, for example what a player typed.
     *
     * @param text user input; may be {@code null}
     * @return the value, or the error {@link #INVALID_VALUE} if the text is not a valid value of this type
     */
    Result<T> parse(String text);

    /**
     * Checks that a value is allowed by this type, for example one passed by another mod.
     *
     * @param value a value to check; {@code null} is never valid
     * @return the value, or the error {@link #INVALID_VALUE}
     */
    Result<T> validate(T value);

    /**
     * Writes a value as text.
     *
     * @param value a valid value of this type
     * @return the value as text that {@link #parse(String)} reads back
     */
    default String format(T value) {
        return String.valueOf(value);
    }

    /**
     * Describes the type and its limits in English.
     *
     * @return a short description for players, for example {@code "integer 1..100"}
     */
    String describe();

    /**
     * Describes the type and its limits as translatable text.
     *
     * @return {@link #describe()} in the player's language
     * @since API 1.5.0
     */
    default Text describeText() {
        return Text.of(describe());
    }

    private static <T> Result<T> invalid(String text, SettingType<?> type) {
        Text described = type.describeText();
        String english = "'" + text + "' is not a " + type.describe() + ".";
        Text message = described
                .key()
                .map(key -> key.replace("controlcore.types.", "controlcore.errors.invalid_value."))
                .map(key -> new Text(Optional.of(key), english, described.params()).with("value", text))
                .orElse(Text.of(english));
        return Result.failure(Error.validation(INVALID_VALUE, message));
    }

    /** {@code true} or {@code false}; created by {@link SettingType#bool()}. */
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

        /**
         * Creates the type; use {@link SettingType#integer(int, int)} instead.
         *
         * @param min smallest allowed value
         * @param max largest allowed value
         * @throws DomainException if {@code min} is greater than {@code max}
         */
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
            return anyValue() ? "whole number" : "whole number from " + min + " to " + max;
        }

        @Override
        public Text describeText() {
            return Text.key(anyValue() ? "controlcore.types.integer_any" : "controlcore.types.integer", describe())
                    .with("min", min, "max", max);
        }

        private boolean anyValue() {
            return min == Integer.MIN_VALUE && max == Integer.MAX_VALUE;
        }
    }

    /**
     * A decimal number.
     *
     * @param min smallest allowed value
     * @param max largest allowed value
     */
    record DecimalType(double min, double max) implements SettingType<Double> {

        /**
         * Creates the type; use {@link SettingType#decimal(double, double)} instead.
         *
         * @param min smallest allowed value
         * @param max largest allowed value
         * @throws DomainException if {@code min} is greater than {@code max} or either is NaN
         */
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
            return "number from " + min + " to " + max;
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

        /**
         * Creates the type; use {@link SettingType#text(int)} instead.
         *
         * @param maxLength longest allowed text in characters, at least 1
         * @throws DomainException if {@code maxLength} is less than 1
         */
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

        /**
         * Creates the type from a copy of the options; use {@link SettingType#choice(String...)} instead.
         *
         * @param options allowed values, in display order; at least one, none blank
         * @throws DomainException if there are no options or one is blank
         */
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
     * A colour, {@code #rrggbb} in lower case; created by {@link SettingType#color()}.
     *
     * @since API 2.0.0
     */
    record ColorType() implements SettingType<String> {

        private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})");

        @Override
        public Class<String> valueType() {
            return String.class;
        }

        @Override
        public Result<String> parse(String text) {
            String wanted = text == null ? "" : text.strip();
            Matcher match = HEX.matcher(wanted);
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

        /**
         * Creates the type; use {@link SettingType#textList(int, int)} instead.
         *
         * @param maxItems most items allowed, at least 1
         * @param maxItemLength longest item allowed in characters, at least 1
         * @throws DomainException if {@code maxItems} or {@code maxItemLength} is less than 1
         */
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
            List<String> items = new ArrayList<>();
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

        /**
         * Writes the items separated by a comma and a space.
         *
         * @param value the items
         * @return the items as text, for example {@code a, b, c}
         */
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
