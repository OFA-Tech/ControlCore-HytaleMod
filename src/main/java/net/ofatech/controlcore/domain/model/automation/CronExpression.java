package net.ofatech.controlcore.domain.model.automation;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class CronExpression {

    private static final List<String> MONTHS =
            List.of("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC");
    private static final List<String> DAYS = List.of("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT");
    private static final int SEARCH_MINUTES = 366 * 24 * 60;

    private final BitSet minutes;
    private final BitSet hours;
    private final BitSet daysOfMonth;
    private final BitSet months;
    private final BitSet daysOfWeek;
    private final boolean anyDayOfMonth;
    private final boolean anyDayOfWeek;

    private CronExpression(String[] fields) {
        this.minutes = field(fields[0], 0, 59, List.of(), 0);
        this.hours = field(fields[1], 0, 23, List.of(), 0);
        this.daysOfMonth = field(fields[2], 1, 31, List.of(), 0);
        this.months = field(fields[3], 1, 12, MONTHS, 1);
        BitSet week = field(fields[4], 0, 7, DAYS, 0);
        if (week != null && week.get(7)) {
            week.set(0);
        }
        this.daysOfWeek = week;
        this.anyDayOfMonth = fields[2].equals("*") || fields[2].equals("?");
        this.anyDayOfWeek = fields[4].equals("*") || fields[4].equals("?");
    }

    public static Optional<CronExpression> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String[] fields = text.strip().toUpperCase(Locale.ROOT).split("\\s+");
        if (fields.length != 5) {
            return Optional.empty();
        }
        CronExpression cron = new CronExpression(fields);
        return cron.minutes == null
                        || cron.hours == null
                        || cron.daysOfMonth == null
                        || cron.months == null
                        || cron.daysOfWeek == null
                ? Optional.empty()
                : Optional.of(cron);
    }

    public boolean matches(LocalDateTime time) {
        if (!minutes.get(time.getMinute()) || !hours.get(time.getHour()) || !months.get(time.getMonthValue())) {
            return false;
        }
        boolean dayOfMonth = daysOfMonth.get(time.getDayOfMonth());
        boolean dayOfWeek = daysOfWeek.get(time.getDayOfWeek().getValue() % 7);
        if (anyDayOfMonth || anyDayOfWeek) {
            return dayOfMonth && dayOfWeek;
        }
        return dayOfMonth || dayOfWeek;
    }

    public Optional<LocalDateTime> next(LocalDateTime after) {
        LocalDateTime time = after.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        for (int i = 0; i < SEARCH_MINUTES; i++, time = time.plusMinutes(1)) {
            if (matches(time)) {
                return Optional.of(time);
            }
        }
        return Optional.empty();
    }

    private static BitSet field(String text, int min, int max, List<String> names, int nameOffset) {
        BitSet bits = new BitSet(max + 1);
        for (String part : text.split(",", -1)) {
            String range = part;
            int step = 1;
            int slash = part.indexOf('/');
            if (slash >= 0) {
                Optional<Integer> parsed = number(part.substring(slash + 1), List.of(), 0);
                if (parsed.isEmpty() || parsed.get() < 1) {
                    return null;
                }
                step = parsed.get();
                range = part.substring(0, slash);
            }
            int from;
            int to;
            if (range.equals("*") || range.equals("?")) {
                from = min;
                to = max;
            } else {
                int dash = range.indexOf('-');
                Optional<Integer> start = number(dash < 0 ? range : range.substring(0, dash), names, nameOffset);
                Optional<Integer> end = dash < 0 ? start : number(range.substring(dash + 1), names, nameOffset);
                if (start.isEmpty() || end.isEmpty()) {
                    return null;
                }
                from = start.get();
                to = dash < 0 && slash >= 0 ? max : end.get();
            }
            if (from < min || to > max || from > to) {
                return null;
            }
            for (int value = from; value <= to; value += step) {
                bits.set(value);
            }
        }
        return bits;
    }

    private static Optional<Integer> number(String text, List<String> names, int nameOffset) {
        if (text.matches("\\d{1,2}")) {
            return Optional.of(Integer.parseInt(text));
        }
        int index = names.indexOf(text);
        return index < 0 ? Optional.empty() : Optional.of(index + nameOffset);
    }
}
