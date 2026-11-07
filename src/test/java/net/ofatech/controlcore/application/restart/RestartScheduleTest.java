package net.ofatech.controlcore.application.restart;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RestartScheduleTest {

    @Test
    void dailyTimesAndCronSchedulesAreReadAndBadEntriesListed() {
        RestartService.Schedule schedule = RestartService.schedule("04:00; 0 16 * * SAT;  ; 25:00; soon");

        assertThat(schedule.valid()).hasSize(2);
        assertThat(schedule.invalid()).containsExactly("25:00", "soon");
    }

    @Test
    void theCountdownEndsOnTheScheduledMinute() {
        var entries = RestartService.schedule("04:00").valid();
        LocalDateTime early = LocalDateTime.of(2026, 11, 4, 3, 58, 59);
        LocalDateTime start = LocalDateTime.of(2026, 11, 4, 3, 59, 0);

        assertThat(RestartService.due(entries, at(early), Duration.ofSeconds(60), Set.of()))
                .isEmpty();
        assertThat(RestartService.due(entries, at(start), Duration.ofSeconds(60), Set.of()))
                .contains(LocalDateTime.of(2026, 11, 4, 4, 0));
    }

    @Test
    void aStartInsideTheCountdownWindowStillFiresOnceForTwoEntriesOnTheSameMinute() {
        var entries = RestartService.schedule("04:00; 0 4 * * *").valid();
        LocalDateTime target = LocalDateTime.of(2026, 11, 4, 4, 0);
        Instant late = at(LocalDateTime.of(2026, 11, 4, 3, 59, 45));

        assertThat(RestartService.due(entries, late, Duration.ofSeconds(60), Set.of()))
                .contains(target);
        assertThat(RestartService.due(entries, late, Duration.ofSeconds(60), Set.of(target)))
                .isEmpty();
        assertThat(RestartService.due(entries, at(target), Duration.ofSeconds(60), Set.of()))
                .isEmpty();
    }

    @Test
    void delaysAreSecondsMinutesOrHoursFromTenSecondsToADay() {
        assertThat(RestartService.delay("")).matches(result -> result.value().isEmpty());
        assertThat(RestartService.delay("default").value()).isEmpty();
        assertThat(RestartService.delay("30s").value()).contains(Duration.ofSeconds(30));
        assertThat(RestartService.delay("90").value()).contains(Duration.ofSeconds(90));
        assertThat(RestartService.delay("5m").value()).contains(Duration.ofMinutes(5));
        assertThat(RestartService.delay("2h").value()).contains(Duration.ofHours(2));
        for (String bad : new String[] {"5s", "25h", "soon", "-1m", "1d"}) {
            assertThat(RestartService.delay(bad).error().code()).isEqualTo("Restart.BadDelay");
        }
    }

    @Test
    void theClockTextRoundsUpToTheSecond() {
        assertThat(RestartService.clockText(Duration.ofSeconds(42))).isEqualTo("0:42");
        assertThat(RestartService.clockText(Duration.ofMillis(59_100))).isEqualTo("1:00");
        assertThat(RestartService.clockText(Duration.ofMinutes(10))).isEqualTo("10:00");
    }

    private static Instant at(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant();
    }
}
