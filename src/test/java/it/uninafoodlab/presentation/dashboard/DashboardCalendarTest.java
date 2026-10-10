package it.uninafoodlab.presentation.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardCalendarTest {
    @Test
    void contextualNextSessionStartsAfterSelectedDayAndDoesNotReturnEarlierDates() {
        var day = LocalDate.of(2026, 9, 21);
        var calendar = new DashboardCalendar<LocalDateTime>(day, time -> time);
        var next = day.plusDays(2).atTime(18, 0);
        calendar.setSessions(List.of(day.minusWeeks(1).atTime(18, 0), next));
        assertThat(calendar.nextSessionAfterSelectedDay(item -> true)).contains(next);
        calendar.selectDay(next.toLocalDate());
        assertThat(calendar.nextSessionAfterSelectedDay(item -> true)).isEmpty();
    }

    @Test
    void selectedDayListsEverySessionInTimeOrderAndClearsOnAnEmptyDay() {
        var day = LocalDate.of(2026, 9, 9);
        var calendar = new DashboardCalendar<LocalDateTime>(day, time -> time);
        calendar.setSessions(List.of(day.atTime(18, 0), day.plusDays(1).atTime(9, 0), day.atTime(10, 0)));

        assertThat(calendar.sessionsOnSelectedDay()).containsExactly(day.atTime(10, 0), day.atTime(18, 0));
        calendar.selectDay(day.plusDays(1));
        assertThat(calendar.sessionsOnSelectedDay()).containsExactly(day.plusDays(1).atTime(9, 0));
        calendar.selectDay(day.plusDays(2));
        assertThat(calendar.sessionsOnSelectedDay()).isEmpty();
    }

    @Test
    void changingWeekMovesTheSelectionAndTodayReturnsToTheCurrentWeekAcrossYearBoundaries() {
        var today = LocalDate.of(2026, 12, 30);
        var calendar = new DashboardCalendar<LocalDateTime>(today, time -> time);

        calendar.moveWeeks(1);
        assertThat(calendar.selectedDay()).isEqualTo(LocalDate.of(2027, 1, 6));
        assertThat(calendar.monday()).isEqualTo(LocalDate.of(2027, 1, 4));
        calendar.moveWeeks(-2);
        assertThat(calendar.selectedDay()).isEqualTo(LocalDate.of(2026, 12, 23));
        calendar.selectDay(today);
        assertThat(calendar.monday()).isEqualTo(LocalDate.of(2026, 12, 28));
    }

    @Test
    void refreshPreservesTheSelectedDayAndReplacesTheSchedule() {
        var today = LocalDate.of(2026, 9, 9);
        var calendar = new DashboardCalendar<LocalDateTime>(today, time -> time);
        calendar.selectDay(today.plusDays(1));
        calendar.setSessions(List.of(today.plusDays(1).atTime(11, 0)));
        assertThat(calendar.selectedDay()).isEqualTo(today.plusDays(1));
        assertThat(calendar.sessionsOnSelectedDay()).containsExactly(today.plusDays(1).atTime(11, 0));
        calendar.setSessions(List.of());
        assertThat(calendar.sessionsOnSelectedDay()).isEmpty();
    }
    @Test
    void nextSessionSkipsPastAndCancelledCandidatesAndCountsSameDaySessions() {
        var day = LocalDate.of(2026, 9, 9);
        var calendar = new DashboardCalendar<LocalDateTime>(day, time -> time);
        var cancelled = day.plusDays(1).atTime(10, 0);
        var next = day.plusDays(2).atTime(9, 0);
        calendar.setSessions(List.of(day.minusDays(1).atTime(8, 0), cancelled, next,
                day.plusDays(2).atTime(18, 0)));
        assertThat(calendar.nextSession(day.atStartOfDay(), item -> !item.equals(cancelled))).contains(next);
        assertThat(calendar.sessionCounts()).containsEntry(day.plusDays(2), 2L);
        assertThat(calendar.nextSession(day.plusDays(3).atStartOfDay(), item -> true)).isEmpty();
    }
}
