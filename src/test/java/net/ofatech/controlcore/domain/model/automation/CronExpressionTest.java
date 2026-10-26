package net.ofatech.controlcore.domain.model.automation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CronExpressionTest {

    private static final LocalDateTime SATURDAY_NOON = LocalDateTime.of(2026, 10, 24, 12, 0);

    @Test
    void parsesTheUsualForms() {
        assertThat(CronExpression.parse("* * * * *")).isPresent();
        assertThat(CronExpression.parse("*/15 8-18 * * MON-FRI")).isPresent();
        assertThat(CronExpression.parse("0 0 1,15 JAN,JUL ?")).isPresent();
        assertThat(CronExpression.parse("0 20 * * 7")).isPresent();
    }

    @Test
    void refusesBadExpressions() {
        assertThat(CronExpression.parse("")).isEmpty();
        assertThat(CronExpression.parse("0 20 * *")).isEmpty();
        assertThat(CronExpression.parse("60 * * * *")).isEmpty();
        assertThat(CronExpression.parse("0 24 * * *")).isEmpty();
        assertThat(CronExpression.parse("*/0 * * * *")).isEmpty();
        assertThat(CronExpression.parse("0 20 * * FUNDAY")).isEmpty();
        assertThat(CronExpression.parse("5-1 * * * *")).isEmpty();
    }

    @Test
    void findsTheNextRun() {
        assertThat(CronExpression.parse("0 20 * * FRI").orElseThrow().next(SATURDAY_NOON))
                .contains(LocalDateTime.of(2026, 10, 30, 20, 0));
        assertThat(CronExpression.parse("*/15 * * * *").orElseThrow().next(SATURDAY_NOON))
                .contains(LocalDateTime.of(2026, 10, 24, 12, 15));
        assertThat(CronExpression.parse("0 0 1 * *").orElseThrow().next(SATURDAY_NOON))
                .contains(LocalDateTime.of(2026, 11, 1, 0, 0));
    }

    @Test
    void dayOfMonthOrWeekWhenBothAreSet() {
        CronExpression cron = CronExpression.parse("0 12 1 * SAT").orElseThrow();

        assertThat(cron.matches(SATURDAY_NOON)).isTrue();
        assertThat(cron.matches(LocalDateTime.of(2026, 11, 1, 12, 0))).isTrue();
        assertThat(cron.matches(LocalDateTime.of(2026, 10, 26, 12, 0))).isFalse();
        assertThat(CronExpression.parse("0 12 * * 0").orElseThrow().matches(LocalDateTime.of(2026, 10, 25, 12, 0)))
                .isTrue();
    }
}
