package it.uninafoodlab.domain.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CourseScheduleTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 9, 1);
    private static final LocalTime START_TIME = LocalTime.of(18, 30);

    @Test
    void scheduleCannotBeChangedThroughInputOrReturnedSessions() {
        var details = new ArrayList<>(List.of(online(), practical()));
        var schedule = CourseSchedule.of(START_DATE, START_TIME, 7, details);

        details.clear();

        assertThat(schedule.sessions()).hasSize(2);
        var sessions = schedule.sessions();
        assertThatThrownBy(sessions::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void generatesNumberedSessionsAtTheRequestedFrequency() {
        var schedule = CourseSchedule.of(
                START_DATE,
                START_TIME,
                7,
                List.of(online(), practical(), online()));

        assertThat(schedule.sessions()).extracting(SessionPlan::sequenceNumber)
                .containsExactly(1, 2, 3);
        assertThat(schedule.sessions()).extracting(SessionPlan::scheduledAt)
                .containsExactly(
                        LocalDateTime.of(2026, 9, 1, 18, 30),
                        LocalDateTime.of(2026, 9, 8, 18, 30),
                        LocalDateTime.of(2026, 9, 15, 18, 30));
        assertThat(schedule.sessions()).extracting(SessionPlan::mode)
                .containsExactly(SessionMode.ONLINE, SessionMode.PRACTICAL, SessionMode.ONLINE);
        assertThat(schedule.sessions()).extracting(SessionPlan::durationMinutes)
                .containsExactly(90, 120, 90);
    }

    @Test
    void copiesInputAndReturnsAnImmutableSessionList() {
        var details = new ArrayList<>(List.of(online(), practical()));
        var schedule = CourseSchedule.of(START_DATE, START_TIME, 1, details);

        details.set(0, practical());

        assertThat(schedule.sessions()).extracting(SessionPlan::mode)
                .containsExactly(SessionMode.ONLINE, SessionMode.PRACTICAL);
        var sessions = schedule.sessions();
        var extraSession = new SessionPlan(2, START_DATE.atTime(START_TIME), online());
        assertThatThrownBy(() -> sessions.add(extraSession))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsFrequencyOutsideOneToThirtyOneDays() {
        var details = List.of(online(), practical());
        assertThatThrownBy(() ->
                        CourseSchedule.of(START_DATE, START_TIME, 0, details))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("frequency");
        assertThatThrownBy(() ->
                        CourseSchedule.of(START_DATE, START_TIME, 32, details))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("frequency");
    }

    @Test
    void rejectsEmptyOrOversizedSessionPlans() {
        var oversized = java.util.Collections.nCopies(31, online());
        assertThatThrownBy(() -> CourseSchedule.of(START_DATE, START_TIME, 7, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessioni");
        assertThatThrownBy(() -> CourseSchedule.of(
                        START_DATE,
                        START_TIME,
                        7,
                        oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessioni");
    }

    @Test
    void requiresAtLeastTwoSessionsForInitialCourseCreation() {
        assertThatThrownBy(() -> CourseSchedule.of(START_DATE, START_TIME, 7, List.of(online())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2");
    }

    @Test
    void rejectsNullSchedulePartsAndSessionModes() {
        var details = List.of(online(), practical());
        List<SessionDetails> nullDetails = java.util.Arrays.asList(null, practical());
        assertThatThrownBy(() ->
                        CourseSchedule.of(null, START_TIME, 7, details))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        CourseSchedule.of(START_DATE, null, 7, details))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CourseSchedule.of(
                        START_DATE,
                        START_TIME,
                        7,
                        nullDetails))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void usesAndValidatesPracticalAttendanceCutoffs() {
        var scheduledAt = LocalDateTime.of(2026, 9, 8, 18, 30);
        var cutoff = scheduledAt.minusHours(48);

        var practical = new SessionPlan(1, scheduledAt, practical(), Optional.of(cutoff));

        assertThat(practical.attendanceClosesAt()).contains(cutoff);
        assertThat(new SessionPlan(1, scheduledAt, practical()).attendanceClosesAt()).contains(scheduledAt.minusMinutes(1));
        assertThat(new SessionPlan(1, scheduledAt, online()).attendanceClosesAt()).isEmpty();
        var practicalDetails = practical();
        var invalidCutoff = Optional.of(scheduledAt);
        assertThatThrownBy(() -> new SessionPlan(
                        1, scheduledAt, practicalDetails, invalidCutoff))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cutoff");
    }

    private static SessionDetails online() {
        return SessionDetails.online(
                90, URI.create("https://meet.uninafoodlab.local/session"));
    }

    private static SessionDetails practical() {
        return SessionDetails.practical(120, "Laboratorio Vesuvio", 14);
    }
}
