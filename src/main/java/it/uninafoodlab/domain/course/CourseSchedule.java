package it.uninafoodlab.domain.course;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CourseSchedule {

    private static final int MIN_FREQUENCY_DAYS = 1;
    private static final int MAX_FREQUENCY_DAYS = 31;
    private static final int MIN_INITIAL_SESSIONS = 2;
    private static final int MAX_SESSIONS = 30;

    private final LocalDate startDate;
    private final LocalTime startTime;
    private final int recurrenceDays;
    private final List<SessionPlan> sessions;

    private CourseSchedule(
            LocalDate startDate,
            LocalTime startTime,
            int recurrenceDays,
            List<SessionDetails> sessionDetails) {
        this.startDate = Objects.requireNonNull(startDate, "startDate");
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        if (recurrenceDays < MIN_FREQUENCY_DAYS || recurrenceDays > MAX_FREQUENCY_DAYS) {
            throw new IllegalArgumentException("frequency must contain 1 to 31 days");
        }
        this.recurrenceDays = recurrenceDays;
        this.sessions = generateSessions(Objects.requireNonNull(sessionDetails, "sessionDetails"));
    }

    public static CourseSchedule of(
            LocalDate startDate,
            LocalTime startTime,
            int recurrenceDays,
            List<SessionDetails> sessionDetails) {
        return new CourseSchedule(startDate, startTime, recurrenceDays, sessionDetails);
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalTime startTime() {
        return startTime;
    }

    public int recurrenceDays() {
        return recurrenceDays;
    }

    public List<SessionPlan> sessions() {
        return sessions;
    }

    private List<SessionPlan> generateSessions(List<SessionDetails> sessionDetails) {
        if (sessionDetails.size() < MIN_INITIAL_SESSIONS || sessionDetails.size() > MAX_SESSIONS) {
            throw new IllegalArgumentException("un corso deve prevedere da 2 a 30 sessioni iniziali");
        }
        var start = startDate.atTime(startTime);
        var generated = new ArrayList<SessionPlan>(sessionDetails.size());
        for (var index = 0; index < sessionDetails.size(); index++) {
            generated.add(new SessionPlan(
                    index + 1,
                    start.plusDays((long) index * recurrenceDays),
                    Objects.requireNonNull(sessionDetails.get(index), "session details")));
        }
        return List.copyOf(generated);
    }
}
