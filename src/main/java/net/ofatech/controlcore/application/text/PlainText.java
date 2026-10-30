package net.ofatech.controlcore.application.text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class PlainText {

    public static final int MAX_REASON = 200;
    public static final int MAX_SHOWN_INPUT = 64;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private PlainText() {}

    public static String oneLine(String text, int max) {
        String flat = text == null
                ? ""
                : text.replaceAll("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]", " ").strip();
        if (flat.length() <= max) {
            return flat;
        }
        int end = max - 1;
        if (end > 0 && Character.isHighSurrogate(flat.charAt(end - 1))) {
            end--;
        }
        return flat.substring(0, end) + "…";
    }

    public static String when(Instant instant) {
        return WHEN.format(instant.atZone(ZoneId.systemDefault()));
    }

    public static String clock(Instant instant) {
        return CLOCK.format(instant.atZone(ZoneId.systemDefault()));
    }
}
