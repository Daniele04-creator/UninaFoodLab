package it.uninafoodlab.application.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.application.course.CourseFilter;
import it.uninafoodlab.application.course.CoursePeriod;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollmentUseCasesTest {

    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID PARTICIPANT_SESSION_TOKEN = UUID.randomUUID();

    @Test
    void participantPagingAndAttendanceContextUseTheAuthenticatedToken() {
        var repository = new RecordingEnrollmentRepository();
        var browse = new BrowseEnrollments(repository);
        var filter = new CourseFilter(Optional.empty(), "", CoursePeriod.ACTIVE,
                LocalDateTime.of(2027, 5, 1, 12, 0));
        var page = new PageRequest(1, 12);

        assertThat(browse.enrollments(participant(), filter, page).totalCount()).isZero();
        assertThat(browse.enrollmentsForCourses(participant(), List.of(COURSE_ID))).isEmpty();
        assertThat(browse.commitments(participant())).isEmpty();
        assertThat(repository.browseToken).isEqualTo(PARTICIPANT_SESSION_TOKEN);
        assertThat(repository.filter).isEqualTo(filter);
        assertThat(repository.page).isEqualTo(page);
        assertThat(repository.courseIds).containsExactly(COURSE_ID);
    }

    @Test
    void participantCanEnrollAndWithdrawUsingTheOpaqueSessionToken() {
        var repository = new RecordingEnrollmentRepository();
        var useCase = new ManageEnrollment(repository);

        useCase.enroll(participant(), COURSE_ID);
        useCase.withdraw(participant(), COURSE_ID);

        assertThat(repository.commands)
                .containsExactly(
                        new Command("ENROLL", PARTICIPANT_SESSION_TOKEN, COURSE_ID),
                        new Command("WITHDRAW", PARTICIPANT_SESSION_TOKEN, COURSE_ID));
    }

    @Test
    void participantCanConfirmAndDeclinePracticalAttendance() {
        var repository = new RecordingEnrollmentRepository();
        var useCase = new ManagePracticalAttendance(repository);

        useCase.confirm(participant(), SESSION_ID);
        useCase.decline(participant(), SESSION_ID);

        assertThat(repository.attendanceCommands)
                .containsExactly(
                        new AttendanceCommand(
                                PARTICIPANT_SESSION_TOKEN,
                                SESSION_ID,
                                AttendanceStatus.CONFIRMED),
                        new AttendanceCommand(
                                PARTICIPANT_SESSION_TOKEN,
                                SESSION_ID,
                                AttendanceStatus.DECLINED));
    }

    @Test
    void chefsAreRejectedBeforeTheRepositoryIsCalled() {
        var repository = new RecordingEnrollmentRepository();

        var manageEnrollment = new ManageEnrollment(repository);
        var chefUser = chef();
        assertThatThrownBy(() -> manageEnrollment.enroll(chefUser, COURSE_ID))
                .isInstanceOf(EnrollmentAuthorizationException.class)
                .hasMessage("Operazione riservata ai partecipanti");
        var managePracticalAttendance = new ManagePracticalAttendance(repository);
        assertThatThrownBy(() -> managePracticalAttendance
                        .confirm(chefUser, SESSION_ID))
                .isInstanceOf(EnrollmentAuthorizationException.class);
        var browseEnrollments = new BrowseEnrollments(repository);
        var filter = new CourseFilter(Optional.empty(), "", CoursePeriod.ALL,
                LocalDateTime.of(2027, 5, 1, 12, 0));
        assertThatThrownBy(() -> browseEnrollments.enrollments(chefUser, filter, new PageRequest(0, 12)))
                .isInstanceOf(EnrollmentAuthorizationException.class);
        assertThatThrownBy(() -> browseEnrollments.enrollmentsForCourses(chefUser, List.of(COURSE_ID)))
                .isInstanceOf(EnrollmentAuthorizationException.class);
        assertThatThrownBy(() -> browseEnrollments.commitments(chefUser))
                .isInstanceOf(EnrollmentAuthorizationException.class);
        assertThat(repository.commands).isEmpty();
        assertThat(repository.attendanceCommands).isEmpty();
        assertThat(repository.browseToken).isNull();
    }

    @Test
    void browsingReturnsImmutableEnrollmentAndPracticalSessionSnapshots() {
        var repository = new RecordingEnrollmentRepository();
        var enrollment = new EnrollmentSummary(
                COURSE_ID,
                CourseTitle.of("Pizza contemporanea"),
                "Cucina campana",
                "Chef Sofia Esposito",
                LocalDate.of(2027, 5, 1),
                EnrollmentStatus.ACTIVE,
                Instant.parse("2027-04-01T10:15:30Z"));
        var attendance = new PracticalAttendanceSummary(
                SESSION_ID,
                2,
                LocalDateTime.of(2027, 5, 8, 18, 30),
                120,
                "Laboratorio Vesuvio",
                14,
                3,
                11,
                Optional.of(AttendanceStatus.CONFIRMED));
        repository.enrollments.add(enrollment);
        repository.practicalSessions.add(attendance);
        var browse = new BrowseEnrollments(repository);

        var enrollments = browse.enrollmentsForCourses(participant(), List.of(COURSE_ID));
        var sessions = browse.practicalSessions(participant(), COURSE_ID);

        assertThat(enrollments).containsExactly(enrollment);
        assertThat(sessions).containsExactly(attendance);
        assertThatThrownBy(() -> enrollments.add(enrollment))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> sessions.add(attendance))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static AuthenticatedUser participant() {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                PARTICIPANT_SESSION_TOKEN,
                Username.of("participant.test"),
                "Partecipante Test",
                Role.PARTICIPANT);
    }

    private static AuthenticatedUser chef() {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Username.of("chef.test"),
                "Chef Test",
                Role.CHEF);
    }

    private record Command(String action, UUID sessionToken, UUID targetId) {}

    private record AttendanceCommand(
            UUID sessionToken, UUID sessionId, AttendanceStatus status) {}

    private static final class RecordingEnrollmentRepository implements EnrollmentRepository {

        private final List<Command> commands = new ArrayList<>();
        private final List<AttendanceCommand> attendanceCommands = new ArrayList<>();
        private final List<EnrollmentSummary> enrollments = new ArrayList<>();
        private final List<PracticalAttendanceSummary> practicalSessions = new ArrayList<>();
        private UUID browseToken;
        private CourseFilter filter;
        private PageRequest page;
        private List<UUID> courseIds;

        @Override
        public void enroll(UUID sessionToken, UUID courseId) {
            commands.add(new Command("ENROLL", sessionToken, courseId));
        }

        @Override
        public void withdraw(UUID sessionToken, UUID courseId) {
            commands.add(new Command("WITHDRAW", sessionToken, courseId));
        }

        @Override
        public void setAttendance(
                UUID sessionToken, UUID sessionId, AttendanceStatus status) {
            attendanceCommands.add(new AttendanceCommand(sessionToken, sessionId, status));
        }

        @Override
        public Page<EnrollmentSummary> findByParticipant(
                UUID sessionToken, CourseFilter filter, PageRequest page) {
            browseToken = sessionToken;
            this.filter = filter;
            this.page = page;
            return new Page<>(enrollments, enrollments.size());
        }

        @Override
        public List<EnrollmentSummary> findByParticipant(UUID sessionToken, List<UUID> courseIds) {
            browseToken = sessionToken;
            this.courseIds = courseIds;
            return enrollments;
        }

        @Override
        public List<AttendanceCommitment> commitments(UUID sessionToken) {
            browseToken = sessionToken;
            return List.of();
        }

        @Override
        public List<PracticalAttendanceSummary> practicalSessions(
                UUID sessionToken, UUID courseId) {
            return practicalSessions;
        }
    }
}
