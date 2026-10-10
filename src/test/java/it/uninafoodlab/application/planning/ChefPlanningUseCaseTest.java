package it.uninafoodlab.application.planning;

import it.uninafoodlab.domain.catalog.MeasurementUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.course.CourseAuthorizationException;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChefPlanningUseCaseTest {

    private static final UUID TOKEN = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();

    @Test
    void chefReadsOnePreparationSnapshotAndParticipantIsRejected() {
        var repository = new StubRepository(null, null, null);
        var snapshot = new BrowseChefPlanning(repository).sessionPreparation(chef(), SESSION_ID);
        assertThat(snapshot.sessionId()).isEqualTo(SESSION_ID);
        assertThat(repository.token).isEqualTo(TOKEN);
        assertThat(repository.calls).isEqualTo(1);
        var browseChefPlanning = new BrowseChefPlanning(repository);
        var participantUser = participant();
        assertThatThrownBy(() -> browseChefPlanning
                .sessionPreparation(participantUser, SESSION_ID))
                .isInstanceOf(CourseAuthorizationException.class);
        assertThat(repository.calls).isEqualTo(1);
    }

    @Test
    void chefReadsCourseEnrollmentWithoutAPracticalSession() {
        var repository = new StubRepository(null, null, null);
        var courseId = UUID.randomUUID();
        assertThat(new BrowseChefPlanning(repository).courseParticipants(chef(), courseId))
                .containsExactly(new CourseParticipant("Luca Romano", EnrollmentStatus.ACTIVE));
        assertThat(repository.courseId).isEqualTo(courseId);
        assertThat(repository.token).isEqualTo(TOKEN);
    }

    @Test
    void participantCannotReadCourseEnrollment() {
        var repository = new StubRepository(null, null, null);
        var browseChefPlanning = new BrowseChefPlanning(repository);
        var participantUser = participant();
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> browseChefPlanning
                .courseParticipants(participantUser, id))
                .isInstanceOf(CourseAuthorizationException.class);
        assertThat(repository.calls).isZero();
    }

    @Test
    void chefBrowsesRequirementsRosterAndMonthlyReport() {
        var requirement = new IngredientRequirement(
                "Farina tipo 00", MeasurementUnit.GRAM, 3, new BigDecimal("393.750"));
        var participant = new SessionParticipant(
                "Luca Romano", EnrollmentStatus.ACTIVE,
                Optional.of(AttendanceStatus.CONFIRMED));
        var report = new MonthlyReport(
                YearMonth.of(2027, 5), 2, 3, 4,
                Optional.of(new BigDecimal("1.5")), Optional.of(1), Optional.of(2));
        var repository = new StubRepository(requirement, participant, report);
        var useCase = new BrowseChefPlanning(repository);

        assertThat(useCase.requirements(chef(), SESSION_ID)).containsExactly(requirement);
        assertThat(useCase.participants(chef(), SESSION_ID)).containsExactly(participant);
        assertThat(useCase.monthlyReport(chef(), YearMonth.of(2027, 5))).isEqualTo(report);
    }

    @Test
    void participantIsRejectedBeforePlanningDataIsRead() {
        var repository = new StubRepository(null, null, null);

        var browseChefPlanning = new BrowseChefPlanning(repository);
        var participantUser = participant();
        assertThatThrownBy(() -> browseChefPlanning
                        .requirements(participantUser, SESSION_ID))
                .isInstanceOf(CourseAuthorizationException.class);
        assertThat(repository.calls).isZero();
    }

    @Test
    void aggregatesIngredientRequirementsAcrossFutureCourseSessions() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var repository = new ChefPlanningRepository() {
            @Override
            public List<IngredientRequirement> requirements(UUID token, UUID sessionId) {
                return sessionId.equals(first)
                        ? List.of(new IngredientRequirement("Farina", MeasurementUnit.GRAM, 4,
                                new BigDecimal("500")))
                        : List.of(
                                new IngredientRequirement("Farina", MeasurementUnit.GRAM, 6,
                                        new BigDecimal("750")),
                                new IngredientRequirement("Olio", MeasurementUnit.LITER, 6,
                                        new BigDecimal("0.30")));
            }
            @Override public List<SessionParticipant> participants(UUID token, UUID sessionId) {
                return List.of();
            }
            @Override public MonthlyReport monthlyReport(UUID token, YearMonth month) {
                throw new UnsupportedOperationException();
            }
            @Override public List<CourseParticipant> courseParticipants(UUID token, UUID courseId) {
                throw new UnsupportedOperationException();
            }
        };

        assertThat(new BrowseChefPlanning(repository).requirements(chef(), List.of(first, second)))
                .containsExactly(
                        new IngredientRequirement("Farina", MeasurementUnit.GRAM, 10,
                                new BigDecimal("1250")),
                        new IngredientRequirement("Olio", MeasurementUnit.LITER, 6,
                                new BigDecimal("0.30")));
    }

    @Test
    void exportsStateAndQuantitiesFromTheRepositorySnapshotInsteadOfSeparateReads() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var repository = new ChefPlanningRepository() {
            @Override
            public List<SessionPlanningSnapshot> exportSnapshots(UUID token, List<UUID> sessionIds) {
                assertThat(token).isEqualTo(TOKEN);
                assertThat(sessionIds).containsExactly(first, second);
                return List.of(
                        new SessionPlanningSnapshot(first, java.time.LocalDateTime.of(2026, 10, 1, 18, 0), 4, "CONSOLIDATED", "CAPTURED",
                                List.of(new IngredientRequirement("Farina", MeasurementUnit.GRAM, 3,
                                        new BigDecimal("300")))),
                        new SessionPlanningSnapshot(second, java.time.LocalDateTime.of(2026, 10, 8, 18, 0), 1, "PRELIMINARY", "CAPTURED",
                                List.of(new IngredientRequirement("Farina", MeasurementUnit.GRAM, 2,
                                        new BigDecimal("200")))));
            }

            @Override public List<IngredientRequirement> requirements(UUID token, UUID sessionId) {
                throw new AssertionError("The export must not re-read requirements outside its snapshot");
            }
            @Override public List<SessionParticipant> participants(UUID token, UUID sessionId) { return List.of(); }
            @Override public MonthlyReport monthlyReport(UUID token, YearMonth month) { throw new UnsupportedOperationException(); }
            @Override public List<CourseParticipant> courseParticipants(UUID token, UUID courseId) { throw new UnsupportedOperationException(); }
        };

        var snapshot = new BrowseChefPlanning(repository).exportSnapshots(chef(), List.of(first, second));

        assertThat(snapshot).extracting(SessionPlanningSnapshot::revision).containsExactly(4, 1);
        assertThat(BrowseChefPlanning.aggregateRequirements(snapshot)).containsExactly(
                new IngredientRequirement("Farina", MeasurementUnit.GRAM, 5, new BigDecimal("500")));
        assertThatThrownBy(() -> new BrowseChefPlanning(repository).exportSnapshots(participant(), List.of(first)))
                .isInstanceOf(CourseAuthorizationException.class);
    }

    private static AuthenticatedUser chef() {
        return user(Role.CHEF, "chef.test");
    }

    private static AuthenticatedUser participant() {
        return user(Role.PARTICIPANT, "participant.test");
    }

    private static AuthenticatedUser user(Role role, String username) {
        return new AuthenticatedUser(
                UUID.randomUUID(), UUID.randomUUID(), TOKEN, Username.of(username),
                "Utente Test", role);
    }

    private static final class StubRepository implements ChefPlanningRepository {
        private final IngredientRequirement requirement;
        private final SessionParticipant participant;
        private final MonthlyReport report;
        private int calls;
        private UUID token;
        private UUID courseId;

        @Override
        public SessionPreparation sessionPreparation(UUID token, UUID sessionId) {
            calls++;
            this.token = token;
            return new SessionPreparation(sessionId, 0, BigDecimal.ZERO, List.of(), List.of());
        }

        public List<CourseParticipant> courseParticipants(UUID token, UUID courseId) {
            calls++;
            this.token = token;
            this.courseId = courseId;
            return List.of(new CourseParticipant("Luca Romano", EnrollmentStatus.ACTIVE));
        }

        private StubRepository(
                IngredientRequirement requirement,
                SessionParticipant participant,
                MonthlyReport report) {
            this.requirement = requirement;
            this.participant = participant;
            this.report = report;
        }

        @Override
        public List<IngredientRequirement> requirements(UUID token, UUID sessionId) {
            calls++;
            return List.of(requirement);
        }

        @Override
        public List<SessionParticipant> participants(UUID token, UUID sessionId) {
            calls++;
            return List.of(participant);
        }

        @Override
        public MonthlyReport monthlyReport(UUID token, YearMonth month) {
            calls++;
            return report;
        }
    }
}
