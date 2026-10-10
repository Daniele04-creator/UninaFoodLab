package it.uninafoodlab.infrastructure.enrollment;

import it.uninafoodlab.infrastructure.support.DatabaseActors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.application.enrollment.AttendanceUnavailableException;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.application.course.CourseFilter;
import it.uninafoodlab.application.course.CoursePeriod;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class JdbcEnrollmentRepositoryIT {

    private static final String MIGRATOR_PASSWORD = "jdbc-enrollment-migrator-only";
    private static final String APP_PASSWORD = "jdbc-enrollment-app-only";
    private static final UUID CHEF_PROFILE_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CATEGORY_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");

    private static ApplicationRuntime runtime;
    private static JdbcEnrollmentRepository repository;
    private static UUID firstParticipantToken;
    private static UUID secondParticipantToken;
    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();

    @BeforeAll
    static void startDatabase() throws Exception {
        POSTGRES.copyFileToContainer(
                MountableFile.forHostPath(
                        Path.of("database", "bootstrap", "001__create_roles.sql")),
                "/tmp/001__create_roles.sql");
        var result = POSTGRES.execInContainer(
                "psql",
                "--username=" + POSTGRES.getUsername(),
                "--dbname=" + POSTGRES.getDatabaseName(),
                "--set=ON_ERROR_STOP=1",
                "--set=migrator_password=" + MIGRATOR_PASSWORD,
                "--set=app_password=" + APP_PASSWORD,
                "--file=/tmp/001__create_roles.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();

        runtime = it.uninafoodlab.infrastructure.support.DemoDatabaseFixture.start(new ApplicationConfig(
                POSTGRES.getJdbcUrl(),
                "uninafoodlab_app",
                APP_PASSWORD,
                "uninafoodlab_migrator",
                MIGRATOR_PASSWORD,
                "test"));
        createSecondParticipant();
        repository = new JdbcEnrollmentRepository(runtime.dataSource());
        firstParticipantToken = DatabaseActors.authenticate(runtime, "luca.bianchi", "LucaBianchi!2026".toCharArray())
                .sessionToken();
        secondParticipantToken = DatabaseActors.authenticate(runtime, "participant.second", "ParticipantDemo!2026".toCharArray())
                .sessionToken();
    }

    @AfterAll
    static void stopDatabase() {
        runtime.close();
    }

    @Test
    void pagesOwnHistoryAndLoadsOnlyCurrentAttendanceCommitments() throws SQLException {
        var fixture = createPublishedPracticalCourse(2);
        var courseId = fixture.courseId();
        var sessionId = practicalSessionId(courseId);
        var search = courseId.toString().substring(0, 8);
        var active = new CourseFilter(Optional.of(CATEGORY_ID), search, CoursePeriod.ACTIVE,
                LocalDateTime.now());
        repository.enroll(firstParticipantToken, courseId);
        repository.setAttendance(firstParticipantToken, sessionId, AttendanceStatus.CONFIRMED);

        var page = repository.findByParticipant(firstParticipantToken, active, new PageRequest(0, 1));
        assertThat(page.items()).extracting(enrollment -> enrollment.courseId()).containsExactly(courseId);
        assertThat(page.totalCount()).isEqualTo(1);
        var beyondEnd = repository.findByParticipant(firstParticipantToken, active, new PageRequest(1, 1));
        assertThat(beyondEnd.items()).isEmpty();
        assertThat(beyondEnd.totalCount()).isEqualTo(1);
        assertThat(repository.findByParticipant(secondParticipantToken, List.of(courseId))).isEmpty();
        assertThat(repository.commitments(firstParticipantToken))
                .filteredOn(commitment -> commitment.courseId().equals(courseId))
                .singleElement().satisfies(commitment -> {
                    assertThat(commitment.courseTitle().value()).contains(search);
                    assertThat(commitment.durationMinutes()).isEqualTo(120);
                });

        repository.withdraw(firstParticipantToken, courseId);
        assertThat(repository.findByParticipant(firstParticipantToken, active, new PageRequest(0, 1)).items())
                .isEmpty();
        var past = new CourseFilter(active.categoryId(), search, CoursePeriod.PAST, active.now());
        assertThat(repository.findByParticipant(firstParticipantToken, past, new PageRequest(0, 1)).items())
                .extracting(enrollment -> enrollment.courseId()).containsExactly(courseId);
        assertThat(repository.commitments(firstParticipantToken))
                .noneMatch(commitment -> commitment.courseId().equals(courseId));
        assertThat(repository.findByParticipant(firstParticipantToken, List.of(courseId)))
                .singleElement().satisfies(enrollment -> assertThat(enrollment.status())
                        .isEqualTo(EnrollmentStatus.WITHDRAWN));
        repository.enroll(firstParticipantToken, courseId);
        assertThat(repository.commitments(firstParticipantToken))
                .noneMatch(commitment -> commitment.courseId().equals(courseId));
        var elapsed = new CourseFilter(active.categoryId(), search, CoursePeriod.PAST,
                active.now().plusYears(3));
        assertThat(repository.findByParticipant(firstParticipantToken, elapsed, new PageRequest(0, 1)).items())
                .extracting(enrollment -> enrollment.courseId()).containsExactly(courseId);
        var unknownToken = UUID.randomUUID();
        assertThatThrownBy(() -> repository.findByParticipant(unknownToken, active, new PageRequest(0, 1)))
                .isInstanceOf(EnrollmentRepositoryException.class);
        assertThatThrownBy(() -> repository.commitments(unknownToken))
                .isInstanceOf(EnrollmentRepositoryException.class);
    }

    @Test
    void mapsEnrollmentAndPracticalAttendanceSnapshots() throws SQLException {
        var fixture = createPublishedPracticalCourse(2);
        var courseId = fixture.courseId();
        var sessionId = practicalSessionId(courseId);

        repository.enroll(firstParticipantToken, courseId);
        repository.setAttendance(
                firstParticipantToken, sessionId, AttendanceStatus.CONFIRMED);

        assertThat(repository.findByParticipant(firstParticipantToken, List.of(courseId)))
                .filteredOn(enrollment -> enrollment.courseId().equals(courseId))
                .singleElement()
                .satisfies(enrollment -> {
                    assertThat(enrollment.courseId()).isEqualTo(courseId);
                    assertThat(enrollment.title().value()).startsWith("Corso JDBC iscrizioni");
                    assertThat(enrollment.categoryName()).isEqualTo("Cucina italiana");
                    assertThat(enrollment.chefName()).isEqualTo("Marta Rossi");
                    assertThat(enrollment.startDate()).isEqualTo(fixture.startDate());
                    assertThat(enrollment.status()).isEqualTo(EnrollmentStatus.ACTIVE);
                    assertThat(enrollment.enrolledAt()).isNotNull();
                });
        assertThat(repository.practicalSessions(firstParticipantToken, courseId))
                .singleElement()
                .satisfies(session -> {
                    assertThat(session.sessionId()).isEqualTo(sessionId);
                    assertThat(session.capacity()).isEqualTo(2);
                    assertThat(session.recipeCount()).isEqualTo(1);
                    assertThat(session.programReady()).isTrue();
                    assertThat(session.confirmedCount()).isEqualTo(1);
                    assertThat(session.remainingPlaces()).isEqualTo(1);
                    assertThat(session.attendanceStatus())
                            .isEqualTo(Optional.of(AttendanceStatus.CONFIRMED));
                });

        repository.withdraw(firstParticipantToken, courseId);
        assertThat(repository.findByParticipant(firstParticipantToken, List.of(courseId)))
                .filteredOn(enrollment -> enrollment.courseId().equals(courseId))
                .singleElement()
                .satisfies(enrollment -> assertThat(enrollment.status())
                        .isEqualTo(EnrollmentStatus.WITHDRAWN));
    }

    @Test
    void translatesUnavailableAttendanceAndRollsBackTheFailedMutation()
            throws SQLException {
        var courseId = createPublishedPracticalCourse(1).courseId();
        var sessionId = practicalSessionId(courseId);
        repository.enroll(firstParticipantToken, courseId);
        repository.enroll(secondParticipantToken, courseId);
        repository.setAttendance(
                firstParticipantToken, sessionId, AttendanceStatus.CONFIRMED);

        assertThatThrownBy(() -> repository.setAttendance(
                        secondParticipantToken, sessionId, AttendanceStatus.CONFIRMED))
                .isInstanceOf(AttendanceUnavailableException.class)
                .hasMessage("La sessione pratica non è disponibile per questa adesione");

        assertThat(repository.practicalSessions(secondParticipantToken, courseId))
                .singleElement()
                .satisfies(session -> {
                    assertThat(session.confirmedCount()).isEqualTo(1);
                    assertThat(session.remainingPlaces()).isZero();
                    assertThat(session.attendanceStatus()).isEmpty();
                });
    }

    @Test
    void rejectsUnknownTokensAndRuntimeExposesTheEnrollmentUseCases()
            throws SQLException {
        var courseId = createPublishedPracticalCourse(3).courseId();

        var invalidToken = UUID.randomUUID();
        assertThatThrownBy(() -> repository.findByParticipant(invalidToken, List.of(courseId)))
                .isInstanceOf(EnrollmentRepositoryException.class)
                .hasCauseInstanceOf(SQLException.class);
        assertThat(runtime.manageEnrollment()).isNotNull();
        assertThat(runtime.managePracticalAttendance()).isNotNull();
        assertThat(runtime.browseEnrollments()).isNotNull();

        runtime.manageEnrollment().enroll(
                DatabaseActors.authenticate(runtime, "luca.bianchi", "LucaBianchi!2026".toCharArray()),
                courseId);
        assertThat(repository.findByParticipant(firstParticipantToken, List.of(courseId)))
                .extracting(it.uninafoodlab.application.enrollment.EnrollmentSummary::courseId)
                .contains(courseId);
    }

    private static CourseFixture createPublishedPracticalCourse(int capacity)
            throws SQLException {
        var courseId = UUID.randomUUID();
        var fixtureSlot = FIXTURE_SEQUENCE.getAndIncrement();
        LocalDate startDate;
        try (var connection = migratorConnection()) {
            connection.setAutoCommit(false);
            try (var course = connection.prepareStatement(
                            "insert into app.course(id, chef_profile_id, category_id, title, "
                                    + "description, start_date, start_time, recurrence_days, "
                                    + "session_count, status, ingredient_margin) "
                                    + "values (?, ?, ?, ?, 'Corso JDBC', "
                                    + "((current_timestamp at time zone 'Europe/Rome') "
                                    + "+ interval '1 year')::date, time '18:00', "
                                    + "7, 1, 'PUBLISHED', 0) returning start_date");
                    var session = connection.prepareStatement(
                            "insert into app.course_session(course_id, sequence_number, "
                                    + "scheduled_at, mode, status, duration_minutes, venue, capacity) "
                                    + "values (?, 1, date_trunc('day', "
                                    + "current_timestamp at time zone 'Europe/Rome') "
                                    + "+ interval '1 year 18 hours' "
                                    + "+ (? * interval '3 hours'), 'PRACTICAL', "
                                    + "'SCHEDULED', 120, 'Laboratorio Vesuvio', ?)")) {
                course.setObject(1, courseId);
                course.setObject(2, CHEF_PROFILE_ID);
                course.setObject(3, CATEGORY_ID);
                course.setString(
                        4, "Corso JDBC iscrizioni " + courseId.toString().substring(0, 8));
                try (var result = course.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    startDate = result.getObject(1, LocalDate.class);
                }
                session.setObject(1, courseId);
                session.setInt(2, fixtureSlot);
                session.setInt(3, capacity);
                session.executeUpdate();
                try (var recipe = connection.prepareStatement(
                        "insert into app.session_recipe(session_id, recipe_id) "
                                + "select id, '50000000-0000-0000-0000-000000000001'::uuid "
                                + "from app.course_session where course_id = ?")) {
                    recipe.setObject(1, courseId);
                    recipe.executeUpdate();
                }
                connection.commit();
            }
        }
        return new CourseFixture(courseId, startDate);
    }

    private static UUID practicalSessionId(UUID courseId) throws SQLException {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "select id from app.course_session where course_id = ?")) {
            statement.setObject(1, courseId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getObject(1, UUID.class);
            }
        }
    }

    private static void createSecondParticipant() throws SQLException {
        var accountId = UUID.fromString("10000000-0000-0000-0000-000000000005");
        try (var connection = migratorConnection()) {
            connection.setAutoCommit(false);
            try (var account = connection.prepareStatement(
                            "insert into app.account(id, username, password_hash, role) "
                                    + "values (?, 'participant.second', ?, 'PARTICIPANT')");
                    var profile = connection.prepareStatement(
                            "insert into app.participant_profile(account_id, display_name) "
                                    + "values (?, 'Partecipante Secondo')")) {
                account.setObject(1, accountId);
                account.setString(
                        2,
                        "$pbkdf2-sha256$600000$bKWdQKkig4RrI5Bk+sXkxA$"
                                + "gp4k3uJpvLuxD5c2oia3mupqE/14da2J5YQLKYmDJ9A");
                account.executeUpdate();
                profile.setObject(1, accountId);
                profile.executeUpdate();
                connection.commit();
            }
        }
    }

    private static java.sql.Connection migratorConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), "uninafoodlab_migrator", MIGRATOR_PASSWORD);
    }

    private record CourseFixture(UUID courseId, LocalDate startDate) {}
}
