package it.uninafoodlab.presentation.participant;

import static org.assertj.core.api.Assertions.assertThat;

import it.uninafoodlab.application.enrollment.AttendanceCommitment;
import it.uninafoodlab.application.enrollment.PracticalAttendanceSummary;
import it.uninafoodlab.domain.course.CourseTitle;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantWorkspaceAttendanceTest {
    @Test
    void globalCommitmentsDetectOverlapWithCoursesOutsideTheVisiblePage() {
        var currentCourse = UUID.randomUUID();
        var start = LocalDateTime.of(2026, 10, 5, 18, 0);
        var candidate = candidate(start);
        var other = new AttendanceCommitment(UUID.randomUUID(), CourseTitle.of("Pasta fresca"), start.plusMinutes(30), 60);
        var own = new AttendanceCommitment(currentCourse, CourseTitle.of("Pane di casa"), start, 60);
        assertThat(ParticipantWorkspaceAttendance.conflict(candidate, currentCourse, List.of(own, other)))
                .hasValueSatisfying(message -> assertThat(message).contains("Pasta fresca"));
        assertThat(ParticipantWorkspaceAttendance.warning(currentCourse, List.of(candidate), List.of(other)))
                .contains("Pasta fresca");
    }

    @Test
    void adjacentAppointmentsAndTheCurrentCourseDoNotRequireAConflictWarning() {
        var currentCourse = UUID.randomUUID();
        var start = LocalDateTime.of(2026, 10, 5, 18, 0);
        var own = new AttendanceCommitment(currentCourse, CourseTitle.of("Pane di casa"), start, 60);
        var later = new AttendanceCommitment(UUID.randomUUID(), CourseTitle.of("Pasta fresca"), start.plusMinutes(60), 60);
        assertThat(ParticipantWorkspaceAttendance.conflict(candidate(start), currentCourse, List.of(own, later))).isEmpty();
    }

    private static PracticalAttendanceSummary candidate(LocalDateTime start) {
        return new PracticalAttendanceSummary(UUID.randomUUID(), 1, start, 60, "Laboratorio", 8, 0, 8,
                start.minusMinutes(1), Optional.empty(), 1);
    }
}
