package net.ofatech.hytaletemplate.core.domain.extensions;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class StringExtensions {
    private static final Pattern BASE64_PATTERN = Pattern.compile("^[a-zA-Z0-9+/]*={0,3}$");

    private StringExtensions() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    public static String capitalize(String value) {
        Objects.requireNonNull(value, "value cannot be null");

        if (value.isEmpty()) {
            return value;
        }

        if (value.length() == 1) {
            return value.toUpperCase();
        }

        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    public static Optional<Integer> toInteger(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public static Optional<Long> toLong(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public static String limitStringLength(String value, int limit) {
        Objects.requireNonNull(value, "value cannot be null");

        if (limit < 0) {
            throw new IllegalArgumentException("limit cannot be negative");
        }

        if (limit >= value.length()) {
            return value;
        }

        return value.substring(0, limit);
    }

    public static byte[] toBytes(String value) {
        Objects.requireNonNull(value, "value cannot be null");

        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] hexToBytes(String hex) {
        Objects.requireNonNull(hex, "hex cannot be null");

        return HexFormat.of().parseHex(hex);
    }

    public static String toSha256(String value) {
        Objects.requireNonNull(value, "value cannot be null");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", exception);
        }
    }

    public static String toBase64(String value) {
        Objects.requireNonNull(value, "value cannot be null");

        return Base64.getEncoder()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String fromBase64(String value) {
        Objects.requireNonNull(value, "value cannot be null");

        byte[] decodedBytes = Base64.getDecoder().decode(value);
        return new String(decodedBytes, StandardCharsets.UTF_8);
    }

    public static boolean isBase64String(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String trimmedValue = value.trim();

        return trimmedValue.length() % 4 == 0
            && BASE64_PATTERN.matcher(trimmedValue).matches();
    }

    public static String toUriEscaped(String value) {
        Objects.requireNonNull(value, "value cannot be null");

        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
