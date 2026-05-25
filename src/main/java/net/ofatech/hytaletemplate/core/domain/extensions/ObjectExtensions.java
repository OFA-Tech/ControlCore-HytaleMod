package net.ofatech.hytaletemplate.core.domain.extensions;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

import java.math.BigDecimal;
import java.math.BigInteger;


import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Predicate;

public final class ObjectExtensions {
    private static final Gson GSON = new Gson();

    private ObjectExtensions() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    public static byte[] toBytes(Object value) {
        Objects.requireNonNull(value, "value cannot be null");

        if (value instanceof byte[] bytes) {
            return bytes;
        }

        if (value instanceof ByteBuffer byteBuffer) {
            ByteBuffer duplicate = byteBuffer.asReadOnlyBuffer();
            byte[] bytes = new byte[duplicate.remaining()];
            duplicate.get(bytes);
            return bytes;
        }

        if (value instanceof Collection<?> collection && isByteCollection(collection)) {
            return byteCollectionToBytes(collection);
        }

        if (value instanceof String stringValue) {
            return stringValue.getBytes(StandardCharsets.UTF_8);
        }

        if (value instanceof Character character) {
            return character.toString().getBytes(StandardCharsets.UTF_8);
        }

        if (value instanceof char[] chars) {
            return new String(chars).getBytes(StandardCharsets.UTF_8);
        }

        if (value instanceof InputStream inputStream) {
            return readStreamBytes(inputStream);
        }

        if (isSimpleType(value.getClass())) {
            return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        }

        return GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readStreamBytes(InputStream inputStream) {
        Objects.requireNonNull(inputStream, "inputStream cannot be null");

        try {
            if (inputStream instanceof ByteArrayInputStream byteArrayInputStream) {
                return byteArrayInputStream.readAllBytes();
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            inputStream.transferTo(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read input stream bytes.", exception);
        }
    }

    private static boolean isByteCollection(Collection<?> collection) {
        return collection.stream().allMatch(item -> item instanceof Byte);
    }

    private static byte[] byteCollectionToBytes(Collection<?> collection) {
        byte[] bytes = new byte[collection.size()];
        int index = 0;

        for (Object item : collection) {
            bytes[index++] = (Byte) item;
        }

        return bytes;
    }

    private static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
            || type.isEnum()
            || type == String.class
            || type == Boolean.class
            || type == Character.class
            || Number.class.isAssignableFrom(type)
            || type == BigDecimal.class
            || type == BigInteger.class
            || type == UUID.class
            || type == Instant.class
            || type == LocalDate.class
            || type == LocalDateTime.class
            || type == OffsetDateTime.class
            || type == Duration.class;
    }

    public static <T> Optional<T> toObject(byte[] data, Class<T> type) {
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        if (data.length == 0) {
            return Optional.empty();
        }

        if (type == byte[].class) {
            return Optional.of(type.cast(data));
        }

        String raw = new String(data, StandardCharsets.UTF_8);

        if (type == String.class) {
            return Optional.of(type.cast(raw));
        }

        if (raw.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(GSON.fromJson(raw, type));
        } catch (JsonSyntaxException exception) {
            return convertSimpleValue(raw, type);
        }
    }

    public static <T> Optional<T> toObject(String json, Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");

        if (json == null || json.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(GSON.fromJson(json, type));
        } catch (JsonSyntaxException exception) {
            return Optional.empty();
        }
    }

    public static <T> Optional<T> toObject(String json, Type type) {
        Objects.requireNonNull(type, "type cannot be null");

        if (json == null || json.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(GSON.fromJson(json, type));
        } catch (JsonSyntaxException exception) {
            return Optional.empty();
        }
    }

    public static <T> Optional<T> toObject(byte[] data, Type type) {
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        if (data.length == 0) {
            return Optional.empty();
        }

        String raw = new String(data, StandardCharsets.UTF_8);

        if (raw.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(GSON.fromJson(raw, type));
        } catch (JsonSyntaxException exception) {
            return Optional.empty();
        }
    }

    public static <T> T toObjectOrThrow(byte[] data, Class<T> type) {
        return toObject(data, type)
            .orElseThrow(() -> new IllegalArgumentException("Failed to convert bytes to object."));
    }

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    public static String toJsonOrThrow(Object value) {
        Objects.requireNonNull(value, "value cannot be null");

        return GSON.toJson(value);
    }

    public static <T> T toObjectOrThrow(String json, Class<T> type) {
        Objects.requireNonNull(json, "json cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        try {
            return GSON.fromJson(json, type);
        } catch (JsonSyntaxException exception) {
            throw new IllegalArgumentException("Failed to deserialize JSON.", exception);
        }
    }

    public static <T> T toObjectOrThrow(String json, Type type) {
        Objects.requireNonNull(json, "json cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        try {
            return GSON.fromJson(json, type);
        } catch (JsonSyntaxException exception) {
            throw new IllegalArgumentException("Failed to deserialize JSON.", exception);
        }
    }

    public static <T> String toCsv(Collection<T> data) {
        return toCsv(data, ';');
    }

    public static <T> String toCsv(Collection<T> data, char separator) {
        Objects.requireNonNull(data, "data cannot be null");

        if (data.isEmpty()) {
            return "";
        }

        List<Field> fields = getSerializableFields(data.iterator().next().getClass());

        StringBuilder builder = new StringBuilder();

        builder.append(joinCsvValues(
            fields.stream()
                .map(Field::getName)
                .toList(),
            separator
        ));
        builder.append(System.lineSeparator());

        for (T item : data) {
            List<String> values = new ArrayList<>();

            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object fieldValue = item == null ? null : field.get(item);
                    values.add(fieldValue == null ? "" : fieldValue.toString());
                } catch (IllegalAccessException exception) {
                    values.add("");
                }
            }

            builder.append(joinCsvValues(values, separator));
            builder.append(System.lineSeparator());
        }

        return builder.toString();
    }

    public static String toCsvFromMaps(Collection<Map<String, Object>> data) {
        return toCsvFromMaps(data, ';');
    }

    public static String toCsvFromMaps(Collection<Map<String, Object>> data, char separator) {
        Objects.requireNonNull(data, "data cannot be null");

        if (data.isEmpty()) {
            return "";
        }

        List<Map<String, Object>> rows = new ArrayList<>(data);
        List<String> keys = new ArrayList<>(rows.getFirst().keySet());

        StringBuilder builder = new StringBuilder();

        builder.append(joinCsvValues(keys, separator));
        builder.append(System.lineSeparator());

        for (Map<String, Object> row : rows) {
            List<String> values = new ArrayList<>();

            for (String key : keys) {
                Object value = row.get(key);
                values.add(value == null ? "" : value.toString());
            }

            builder.append(joinCsvValues(values, separator));
            builder.append(System.lineSeparator());
        }

        return builder.toString();
    }

    public static <T> Predicate<T> toPredicateFilter(Map<String, Object> filters, Class<T> type) {
        Objects.requireNonNull(filters, "filters cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        Map<String, Field> fieldsByName = getFieldsByName(type);

        List<Predicate<T>> predicates = new ArrayList<>();

        for (Map.Entry<String, Object> filter : filters.entrySet()) {
            Field field = fieldsByName.get(filter.getKey());

            if (field == null) {
                continue;
            }

            Optional<Object> convertedValue = convertFilterValue(filter.getValue(), field.getType());

            if (convertedValue.isEmpty() && filter.getValue() != null) {
                continue;
            }

            field.setAccessible(true);

            predicates.add(item -> {
                try {
                    Object actualValue = field.get(item);
                    Object expectedValue = convertedValue.orElse(null);
                    return Objects.equals(actualValue, expectedValue);
                } catch (IllegalAccessException exception) {
                    return false;
                }
            });
        }

        if (predicates.isEmpty()) {
            throw new IllegalArgumentException("No valid filters were provided.");
        }

        return item -> predicates.stream().allMatch(predicate -> predicate.test(item));
    }

    public static String toQueryString(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }

        StringJoiner query = new StringJoiner("&");

        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            if (parameter.getValue() == null) {
                continue;
            }

            query.add(
                StringExtensions.toUriEscaped(parameter.getKey())
                    + "="
                    + StringExtensions.toUriEscaped(parameter.getValue().toString())
            );
        }

        return query.toString();
    }

    private static List<Field> getSerializableFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();

        Class<?> currentType = type;

        while (currentType != null && currentType != Object.class) {
            for (Field field : currentType.getDeclaredFields()) {
                if (!field.isSynthetic()) {
                    fields.add(field);
                }
            }

            currentType = currentType.getSuperclass();
        }

        return fields;
    }

    private static Map<String, Field> getFieldsByName(Class<?> type) {
        return getSerializableFields(type).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    Field::getName,
                    field -> field,
                    (existing, ignored) -> existing
                )
            );
    }

    private static String joinCsvValues(List<String> values, char separator) {
        return String.join(
            String.valueOf(separator),
            values.stream()
                .map(value -> escapeCsvValue(value, separator))
                .toList()
        );
    }

    private static String escapeCsvValue(String value, char separator) {
        if (value == null) {
            return "";
        }

        return value
            .replace(String.valueOf(separator), "\\" + separator)
            .replace("\r", "")
            .replace("\n", "\\n");
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> Optional<T> convertSimpleValue(String raw, Class<T> type) {
        try {
            Object converted;

            if (type == String.class) {
                converted = raw;
            } else if (type == Integer.class || type == int.class) {
                converted = Integer.parseInt(raw);
            } else if (type == Long.class || type == long.class) {
                converted = Long.parseLong(raw);
            } else if (type == Double.class || type == double.class) {
                converted = Double.parseDouble(raw);
            } else if (type == Float.class || type == float.class) {
                converted = Float.parseFloat(raw);
            } else if (type == Boolean.class || type == boolean.class) {
                converted = Boolean.parseBoolean(raw);
            } else if (type == Short.class || type == short.class) {
                converted = Short.parseShort(raw);
            } else if (type == Byte.class || type == byte.class) {
                converted = Byte.parseByte(raw);
            } else if (type == BigDecimal.class) {
                converted = new BigDecimal(raw);
            } else if (type == BigInteger.class) {
                converted = new BigInteger(raw);
            } else if (type == UUID.class) {
                converted = UUID.fromString(raw);
            } else if (type == Instant.class) {
                converted = Instant.parse(raw);
            } else if (type == LocalDate.class) {
                converted = LocalDate.parse(raw);
            } else if (type == LocalDateTime.class) {
                converted = LocalDateTime.parse(raw);
            } else if (type == OffsetDateTime.class) {
                converted = OffsetDateTime.parse(raw);
            } else if (type == Duration.class) {
                converted = Duration.parse(raw);
            } else if (type.isEnum()) {
                converted = Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), raw.toUpperCase());
            } else {
                return Optional.empty();
            }

            return Optional.of((T) converted);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Optional<Object> convertFilterValue(Object value, Class<?> targetType) {
        if (value == null) {
            if (targetType.isPrimitive()) {
                return Optional.empty();
            }

            return Optional.empty();
        }

        if (targetType.isInstance(value)) {
            return Optional.of(value);
        }

        String raw = value.toString();

        try {
            if (targetType == String.class) {
                return Optional.of(raw);
            }

            if (targetType == Integer.class || targetType == int.class) {
                return Optional.of(Integer.parseInt(raw));
            }

            if (targetType == Long.class || targetType == long.class) {
                return Optional.of(Long.parseLong(raw));
            }

            if (targetType == Double.class || targetType == double.class) {
                return Optional.of(Double.parseDouble(raw));
            }

            if (targetType == Float.class || targetType == float.class) {
                return Optional.of(Float.parseFloat(raw));
            }

            if (targetType == Boolean.class || targetType == boolean.class) {
                return Optional.of(Boolean.parseBoolean(raw));
            }

            if (targetType == UUID.class) {
                return Optional.of(UUID.fromString(raw));
            }

            if (targetType.isEnum()) {
                return Optional.of(Enum.valueOf((Class<? extends Enum>) targetType.asSubclass(Enum.class), raw.toUpperCase()));
            }

            return Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
