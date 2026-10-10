package it.uninafoodlab.presentation.dashboard;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

final class DashboardCalendar<T> {
    private final Function<T, LocalDateTime> scheduledAt;
    private LocalDate selectedDay;
    private List<T> sessions = List.of();

    DashboardCalendar(LocalDate selectedDay, Function<T, LocalDateTime> scheduledAt) {
        this.selectedDay = Objects.requireNonNull(selectedDay, "selectedDay");
        this.scheduledAt = Objects.requireNonNull(scheduledAt, "scheduledAt");
    }

    void setSessions(List<T> sessions) { this.sessions = List.copyOf(sessions); }
    void selectDay(LocalDate day) { selectedDay = Objects.requireNonNull(day, "day"); }
    void moveWeeks(int weeks) { selectedDay = selectedDay.plusWeeks(weeks); }
    LocalDate selectedDay() { return selectedDay; }
    LocalDate monday() { return selectedDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }

    List<T> sessionsOnSelectedDay() {
        return sessions.stream().filter(session -> scheduledAt.apply(session).toLocalDate().equals(selectedDay))
                .sorted(Comparator.comparing(scheduledAt)).toList();
    }

    java.util.Optional<T> nextSession(LocalDateTime after, java.util.function.Predicate<T> eligible) {
        return sessions.stream().filter(eligible).filter(item -> !scheduledAt.apply(item).isBefore(after))
                .min(Comparator.comparing(scheduledAt));
    }

    java.util.Optional<T> nextSessionAfterSelectedDay(java.util.function.Predicate<T> eligible) {
        return nextSession(selectedDay.plusDays(1).atStartOfDay(), eligible);
    }

    java.util.Map<LocalDate, Long> sessionCounts() {
        return sessions.stream().collect(Collectors.groupingBy(item -> scheduledAt.apply(item).toLocalDate(), Collectors.counting()));
    }

}
