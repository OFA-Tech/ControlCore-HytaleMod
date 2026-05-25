package net.ofatech.controlcore.core.domain.extensions;

public final class NumericExtensions {
    private static final long KILOBYTE = 1024L;
    private static final String[] BYTE_UNITS = {
        "B",
        "KB",
        "MB",
        "GB",
        "TB",
        "PB",
        "EB"
    };

    private NumericExtensions() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    public static boolean isEven(int number) {
        return number % 2 == 0;
    }

    public static boolean isOdd(int number) {
        return number % 2 != 0;
    }

    public static boolean isPositive(int number) {
        return number > 0;
    }

    public static boolean isNegative(int number) {
        return number < 0;
    }

    public static boolean isZero(int number) {
        return number == 0;
    }

    public static String toFormattedBytes(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes cannot be negative.");
        }

        if (bytes == 0) {
            return "0 B";
        }

        int unitIndex = (int) Math.floor(Math.log(bytes) / Math.log(KILOBYTE));

        if (unitIndex >= BYTE_UNITS.length) {
            unitIndex = BYTE_UNITS.length - 1;
        }

        double formattedValue = bytes / Math.pow(KILOBYTE, unitIndex);

        return String.format(java.util.Locale.ROOT, "%.2f %s", formattedValue, BYTE_UNITS[unitIndex]);
    }

    public static String toFormattedBytes(Long bytes) {
        if (bytes == null) {
            return "0 B";
        }

        return toFormattedBytes(bytes.longValue());
    }
}
