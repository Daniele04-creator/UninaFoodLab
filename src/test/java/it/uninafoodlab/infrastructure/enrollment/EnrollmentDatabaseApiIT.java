package it.uninafoodlab.infrastructure.enrollment;

import it.uninafoodlab.infrastructure.support.DatabaseActors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class EnrollmentDatabaseApiIT {

    private static final String MIGRATOR_PASSWORD = "enrollment-migrator-only";
    private static final String APP_PASSWORD = "enrollment-app-only";
    private static final UUID CHEF_PROFILE_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CATEGORY_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");

    private static ApplicationRuntime runtime;
    private static UUID chefSessionToken;
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
        chefSessionToken = runtime.authenticateUser()
                .execute("marta.rossi", "MartaRossi!2026".toCharArray())
                .sessionToken();
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
    void cutoffOnlyAndNoOpCalendarEditsPreserveConfirmedAttendance() throws SQLException {
        var course = createPublishedPracticalCourse(2);
        var session = practicalSessionId(course);
        try (var connection = runtime.dataSource().getConnection()) {
            enroll(connection, firstParticipantToken, course);
            setAttendance(connection, firstParticipantToken, session, "CONFIRMED");
            connection.commit();
            editOperationalCalendar(connection, session, false);
            connection.commit();
            assertThat(attendanceStatusDirect(session, firstParticipantToken)).isEqualTo("CONFIRMED");
            editOperationalCalendar(connection, session, false);
            connection.commit();
            assertThat(attendanceStatusDirect(session, firstParticipantToken)).isEqualTo("CONFIRMED");
        }
    }

    @Test
    void actualCalendarMoveStillRequiresExplicitReconfirmation() throws SQLException {
        var course = createPublishedPracticalCourse(2);
        var session = practicalSessionId(course);
        try (var connection = runtime.dataSource().getConnection()) {
            enroll(connection, firstParticipantToken, course);
            setAttendance(connection, firstParticipantToken, session, "CONFIRMED");
            connection.commit();
            editOperationalCalendar(connection, session, true);
            connection.commit();
            assertThat(operationalSession(connection, firstParticipantToken, course).status()).isNull();
            assertThat(countAttendanceRows(session)).isZero();
            setAttendance(connection, firstParticipantToken, session, "CONFIRMED");
            connection.commit();
            assertThat(operationalSession(connection, firstParticipantToken, course).status()).isEqualTo("CONFIRMED");
        }
    }

    @Test
    void lateWithdrawalPreservesHistoryButReleasesOperationalCountsAndCapacity() throws SQLException {
        var course = createPublishedPracticalCourse(1);
        var session = practicalSessionId(course);
        try (var connection = runtime.dataSource().getConnection()) {
            enroll(connection, firstParticipantToken, course);
            enroll(connection, secondParticipantToken, course);
            setAttendance(connection, firstParticipantToken, session, "CONFIRMED");
            connection.commit();
            setOperationalFixtureWindow(session, false);
            withdraw(connection, firstParticipantToken, course);
            connection.commit();
            assertThat(attendanceStatusDirect(session, firstParticipantToken)).isEqualTo("CONFIRMED");
            assertThat(operationalSession(connection, secondParticipantToken, course).confirmedCount()).isZero();
            assertThat(operationalSession(connection, secondParticipantToken, course).remainingPlaces()).isEqualTo(1);
            assertOperationalPreparation(connection, session, 0);
            setOperationalFixtureWindow(session, true);
            setAttendance(connection, secondParticipantToken, session, "CONFIRMED");
            connection.commit();
            assertThat(operationalSession(connection, secondParticipantToken, course).confirmedCount()).isEqualTo(1);
            assertOperationalPreparation(connection, session, 1);
        }
    }

    @Test
    void reenrollmentDoesNotResurrectEarlierPracticalConfirmation() throws SQLException {
        var course = createPublishedPracticalCourse(1);
        var session = practicalSessionId(course);
        try (var connection = runtime.dataSource().getConnection()) {
            enroll(connection, firstParticipantToken, course);
            setAttendance(connection, firstParticipantToken, session, "CONFIRMED");
            connection.commit();
            setOperationalFixtureWindow(session, false);
            withdraw(connection, firstParticipantToken, course);
            connection.commit();
            enroll(connection, firstParticipantToken, course);
            connection.commit();
            assertThat(attendanceStatusDirect(session, firstParticipantToken)).isEqualTo("CONFIRMED");
            var current = operationalSession(connection, firstParticipantToken, course);
            assertThat(current.status()).isNull();
            assertThat(current.confirmedCount()).isZero();
            assertOperationalPreparation(connection, session, 0);
            setOperationalFixtureWindow(session, true);
            setAttendance(connection, firstParticipantToken, session, "CONFIRMED");
            connection.commit();
            assertThat(operationalSession(connection, firstParticipantToken, course).status()).isEqualTo("CONFIRMED");
            assertOperationalPreparation(connection, session, 1);
        }
    }

    private static void editOperationalCalendar(Connection connection, UUID session, boolean move) throws SQLException {
        LocalDateTime scheduled;
        try (var fixture = migratorConnection(); var query = fixture.prepareStatement(
                "select scheduled_at from app.course_session where id=?")) {
            query.setObject(1, session);
            try (var rows = query.executeQuery()) { rows.next(); scheduled = rows.getObject(1, LocalDateTime.class); }
        }
        if (move) scheduled = scheduled.plusMinutes(15);
        try (var update = connection.prepareStatement("select app.reschedule_course_session(?,?,?,?,NULL,?,?)")) {
            update.setObject(1, chefSessionToken);
            update.setObject(2, session);
            update.setObject(3, scheduled);
            update.setObject(4, scheduled.minusHours(2));
            update.setString(5, "Aggiornamento calendario");
            update.setString(6, "Nuova disponibilità per la sessione.");
            update.execute();
        }
    }

    private static void setOperationalFixtureWindow(UUID session, boolean open) throws SQLException {
        try (var fixture = migratorConnection(); var update = fixture.prepareStatement(
                "update app.course_session set attendance_closes_at="
                        + (open ? "scheduled_at-interval '1 minute'" : "(clock_timestamp() at time zone 'Europe/Rome')-interval '1 minute'")
                        + " where id=?")) {
            update.setObject(1, session);
            update.executeUpdate();
        }
    }

    private static SessionView operationalSession(Connection connection, UUID token, UUID course) throws SQLException {
        try (var query = connection.prepareStatement("select attendance_status,confirmed_count,remaining_places from app.list_enrollment_practical_sessions(?,?)")) {
            query.setObject(1, token); query.setObject(2, course);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return new SessionView(rows.getString(1), rows.getInt(2), rows.getInt(3));
            }
        }
    }

    private static void assertOperationalPreparation(Connection connection, UUID session, int expected) throws SQLException {
        try (var query = connection.prepareStatement("select confirmed_participants from app.session_preparation(?,?)")) {
            query.setObject(1, chefSessionToken); query.setObject(2, session);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(expected);
            }
        }
    }

    @Test
    void incompletePracticalProgramRejectsConfirmationButAllowsDecline() throws SQLException {
        var courseId = createPublishedPracticalCourse(3, false);
        var sessionId = practicalSessionId(courseId);
        try (var connection = runtime.dataSource().getConnection()) {
            enroll(connection, firstParticipantToken, courseId);
            connection.commit();
            var incomplete = new JdbcEnrollmentRepository(runtime.dataSource()).practicalSessions(firstParticipantToken, courseId);
            assertThat(incomplete).singleElement().satisfies(session -> {
                assertThat(session.recipeCount()).isZero();
                assertThat(session.programReady()).isFalse();
            });
            assertThatThrownBy(() -> setAttendance(connection, firstParticipantToken, sessionId, "CONFIRMED"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
            connection.rollback();
            setAttendance(connection, firstParticipantToken, sessionId, "DECLINED");
            connection.commit();
            addRecipes(courseId);
            assertThat(new JdbcEnrollmentRepository(runtime.dataSource()).practicalSessions(firstParticipantToken, courseId))
                    .singleElement().satisfies(session -> assertThat(session.programReady()).isTrue());
            setAttendance(connection, firstParticipantToken, sessionId, "CONFIRMED");
            connection.commit();
            assertThat(practicalSession(connection, firstParticipantToken, courseId).status())
                    .isEqualTo("CONFIRMED");
            try (var remove = connection.prepareStatement("select app.remove_session_recipe(?,?,?)")) {
                remove.setObject(1, chefSessionToken);
                remove.setObject(2, sessionId);
                remove.setObject(3, UUID.fromString("50000000-0000-0000-0000-000000000001"));
                assertThatThrownBy(remove::execute)
                        .isInstanceOf(SQLException.class)
                        .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
                connection.rollback();
            }
            assertThat(new JdbcEnrollmentRepository(runtime.dataSource())
                    .practicalSessions(firstParticipantToken, courseId))
                    .singleElement().satisfies(session -> assertThat(session.recipeCount()).isEqualTo(1));
        }
    }

    @Test
    void enrollmentLifecycleIsIdempotentAndBoundToParticipantSessions() throws SQLException {
        var courseId = createPublishedPracticalCourse(4);
        try (var connection = runtime.dataSource().getConnection()) {
            enroll(connection, firstParticipantToken, courseId);
            enroll(connection, firstParticipantToken, courseId);
            connection.commit();

            assertThat(enrollmentStatus(connection, firstParticipantToken, courseId))
                    .isEqualTo("ACTIVE");
            assertThatThrownBy(() -> enroll(connection, chefSessionToken, courseId))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("42501"));
            connection.rollback();

            withdraw(connection, firstParticipantToken, courseId);
            withdraw(connection, firstParticipantToken, courseId);
            connection.commit();
            assertThat(enrollmentStatus(connection, firstParticipantToken, courseId))
                    .isEqualTo("WITHDRAWN");

            enroll(connection, firstParticipantToken, courseId);
            connection.commit();
            assertThat(enrollmentStatus(connection, firstParticipantToken, courseId))
                    .isEqualTo("ACTIVE");

            try (var statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.executeQuery("select * from app.enrollment"))
                        .isInstanceOf(SQLException.class)
                        .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                                .isEqualTo("42501"));
            }
        }
        assertThat(countEnrollmentRows(courseId)).isEqualTo(1);
    }

    @Test
    void confirmationAndWithdrawalReleasePracticalCapacity() throws SQLException {
        var courseId = createPublishedPracticalCourse(2);
        var sessionId = practicalSessionId(courseId);
        try (var connection = runtime.dataSource().getConnection()) {
            enroll(connection, firstParticipantToken, courseId);
            setAttendance(connection, firstParticipantToken, sessionId, "CONFIRMED");
            connection.commit();

            assertThat(practicalSession(connection, firstParticipantToken, courseId))
                    .isEqualTo(new SessionView("CONFIRMED", 1, 1));

            setAttendance(connection, firstParticipantToken, sessionId, "DECLINED");
            connection.commit();
            assertThat(practicalSession(connection, firstParticipantToken, courseId))
                    .isEqualTo(new SessionView("DECLINED", 0, 2));

            setAttendance(connection, firstParticipantToken, sessionId, "CONFIRMED");
            withdraw(connection, firstParticipantToken, courseId);
            connection.commit();

            assertThat(attendanceStatus(sessionId, firstParticipantToken))
                    .isEqualTo("DECLINED");
        }
    }

    @Test
    void concurrentConfirmationsNeverExceedCapacity() throws Exception {
        var courseId = createPublishedPracticalCourse(1);
        var sessionId = practicalSessionId(courseId);
        try (var setup = runtime.dataSource().getConnection()) {
            enroll(setup, firstParticipantToken, courseId);
            enroll(setup, secondParticipantToken, courseId);
            setup.commit();
        }

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> confirmConcurrently(
                    firstParticipantToken, sessionId, ready, start));
            var second = executor.submit(() -> confirmConcurrently(
                    secondParticipantToken, sessionId, ready, start));
            ready.await();
            start.countDown();

            assertThat(new String[] {first.get(), second.get()})
                    .containsExactlyInAnyOrder("CONFIRMED", "23514");
        }
        assertThat(countConfirmedAttendance(sessionId)).isEqualTo(1);
    }

    @Test
    void concurrentWithdrawalAndFirstConfirmationPreserveTheEnrollmentInvariant()
            throws Exception {
        var courseId = createPublishedPracticalCourse(2);
        var sessionId = practicalSessionId(courseId);
        try (var setup = runtime.dataSource().getConnection()) {
            enroll(setup, firstParticipantToken, courseId);
            setup.commit();
        }

        try (var executor = Executors.newFixedThreadPool(2);
                var confirmation = runtime.dataSource().getConnection();
                var withdrawal = runtime.dataSource().getConnection();
                var sessionLock = migratorConnection()) {
            sessionLock.setAutoCommit(false);
            lockSession(sessionLock, sessionId);
            var confirmationPid = backendPid(confirmation);
            var withdrawalPid = backendPid(withdrawal);

            var confirmationResult = executor.submit(() -> {
                setAttendance(
                        confirmation, firstParticipantToken, sessionId, "CONFIRMED");
                confirmation.commit();
                return "CONFIRMED";
            });
            awaitDatabaseLock(confirmationPid);

            var withdrawalResult = executor.submit(() -> {
                withdraw(withdrawal, firstParticipantToken, courseId);
                withdrawal.commit();
                return "WITHDRAWN";
            });
            awaitCompletionOrDatabaseLock(withdrawalResult, withdrawalPid);

            sessionLock.commit();
            assertThat(confirmationResult.get(5, TimeUnit.SECONDS))
                    .isEqualTo("CONFIRMED");
            assertThat(withdrawalResult.get(5, TimeUnit.SECONDS))
                    .isEqualTo("WITHDRAWN");
        }

        assertThat(enrollmentStatusDirect(courseId, firstParticipantToken))
                .isEqualTo("WITHDRAWN");
        assertThat(attendanceStatusDirect(sessionId, firstParticipantToken))
                .isEqualTo("DECLINED");
        assertThat(countConfirmedAttendance(sessionId)).isZero();
    }

    @Test
    void concurrentWithdrawalsAcrossSharedSessionsDoNotDeadlock() throws Exception {
        var courseId = createPublishedPracticalCourse(2);
        addPracticalSession(courseId);
        var sessionIds = practicalSessionIds(courseId);
        var firstSession = sessionIds.get(0);
        var secondSession = sessionIds.get(1);

        try (var setup = runtime.dataSource().getConnection()) {
            enroll(setup, firstParticipantToken, courseId);
            enroll(setup, secondParticipantToken, courseId);
            setup.commit();

            setAttendance(setup, firstParticipantToken, secondSession, "CONFIRMED");
            setup.commit();
            setAttendance(setup, secondParticipantToken, firstSession, "CONFIRMED");
            setup.commit();
            setAttendance(setup, firstParticipantToken, firstSession, "CONFIRMED");
            setup.commit();
            setAttendance(setup, secondParticipantToken, secondSession, "CONFIRMED");
            setup.commit();
        }

        try (var executor = Executors.newFixedThreadPool(2);
                var indexWithdrawal = runtime.dataSource().getConnection();
                var sequentialWithdrawal = runtime.dataSource().getConnection();
                var sessionLock = migratorConnection()) {
            preferIndexScan(indexWithdrawal);
            preferSequentialScan(sequentialWithdrawal);
            sessionLock.setAutoCommit(false);
            lockSession(sessionLock, firstSession);
            var indexPid = backendPid(indexWithdrawal);
            var sequentialPid = backendPid(sequentialWithdrawal);

            var indexResult = executor.submit(() -> withdrawConcurrently(
                    indexWithdrawal, secondParticipantToken, courseId));
            awaitDatabaseLock(indexPid);
            var sequentialResult = executor.submit(() -> withdrawConcurrently(
                    sequentialWithdrawal, firstParticipantToken, courseId));
            awaitDatabaseLock(sequentialPid);

            sessionLock.commit();
            assertThat(indexResult.get(8, TimeUnit.SECONDS)).isEqualTo("WITHDRAWN");
            assertThat(sequentialResult.get(8, TimeUnit.SECONDS)).isEqualTo("WITHDRAWN");
        }

        assertThat(countConfirmedAttendance(firstSession)).isZero();
        assertThat(countConfirmedAttendance(secondSession)).isZero();
    }

    @Test
    void publicAttendanceChangesRequireAScheduledFutureSession() throws SQLException {
        var cancelledCourse = createPublishedPracticalCourse(2);
        var cancelledSession = practicalSessionId(cancelledCourse);
        var pastCourse = createPublishedPracticalCourse(2);
        var pastSession = practicalSessionId(pastCourse);
        try (var setup = runtime.dataSource().getConnection()) {
            enroll(setup, firstParticipantToken, cancelledCourse);
            enroll(setup, firstParticipantToken, pastCourse);
            setup.commit();
        }
        makeSessionUnavailable(cancelledSession, "CANCELLED", LocalDateTime.of(2027, 10, 1, 18, 0));
        makeSessionUnavailable(pastSession, "SCHEDULED", LocalDateTime.of(2020, 1, 1, 18, 0));

        try (var connection = runtime.dataSource().getConnection()) {
            assertThatThrownBy(() -> setAttendance(
                            connection,
                            firstParticipantToken,
                            cancelledSession,
                            "DECLINED"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("23514"));
            connection.rollback();

            assertThatThrownBy(() -> setAttendance(
                            connection, firstParticipantToken, pastSession, "DECLINED"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("23514"));
            connection.rollback();
        }
    }

    @Test
    void concurrentCancellationWinsBeforeAPublicDeclineIsPersisted() throws Exception {
        var courseId = createPublishedPracticalCourse(2);
        var sessionId = practicalSessionId(courseId);
        try (var setup = runtime.dataSource().getConnection()) {
            enroll(setup, firstParticipantToken, courseId);
            setup.commit();
        }

        try (var executor = Executors.newFixedThreadPool(2);
                var cancellation = migratorConnection();
                var decline = runtime.dataSource().getConnection();
                var sessionLock = migratorConnection()) {
            cancellation.setAutoCommit(false);
            setStatementTimeout(cancellation);
            setStatementTimeout(decline);
            sessionLock.setAutoCommit(false);
            lockSession(sessionLock, sessionId);
            var cancellationPid = backendPid(cancellation);
            var declinePid = backendPid(decline);

            var cancellationResult = executor.submit(() ->
                    cancelSessionConcurrently(cancellation, sessionId));
            awaitDatabaseLock(cancellationPid);
            var declineResult = executor.submit(() ->
                    declineConcurrently(decline, firstParticipantToken, sessionId));
            awaitDatabaseLock(declinePid);

            sessionLock.commit();
            assertThat(cancellationResult.get(8, TimeUnit.SECONDS))
                    .isEqualTo("CANCELLED");
            assertThat(declineResult.get(8, TimeUnit.SECONDS)).isEqualTo("23514");
        }

        assertThat(countAttendanceRows(sessionId)).isZero();
    }

    @Test
    void italianLocalSessionTimesDefineEnrollmentAttendanceAndVisibility()
            throws SQLException {
        var attendanceCourse = createPublishedPracticalCourse(2);
        var attendanceSession = practicalSessionId(attendanceCourse);
        try (var connection = runtime.dataSource().getConnection()) {
            enroll(connection, firstParticipantToken, attendanceCourse);
            connection.commit();
        }
        moveSessionRelativeToRomeNow(attendanceSession, -30);

        try (var connection = runtime.dataSource().getConnection()) {
            setSessionTimezone(connection, "UTC");
            assertThat(sessionStatus(connection, attendanceCourse, attendanceSession)).isEqualTo("SCHEDULED");
            assertThat(countPracticalSessions(
                            connection, firstParticipantToken, attendanceCourse))
                    .isOne();
            assertThatThrownBy(() -> setAttendance(
                            connection,
                            firstParticipantToken,
                            attendanceSession,
                            "DECLINED"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("23514"));
            connection.rollback();
        }

        moveSessionRelativeToRomeNow(attendanceSession, -180);
        try (var connection = runtime.dataSource().getConnection()) {
            setSessionTimezone(connection, "UTC");
            assertThat(sessionStatus(connection, attendanceCourse, attendanceSession)).isEqualTo("COMPLETED");
            assertThat(countPracticalSessions(
                            connection, firstParticipantToken, attendanceCourse))
                    .isZero();
        }

        var enrollmentCourse = createPublishedPracticalCourse(2);
        moveSessionRelativeToRomeNow(practicalSessionId(enrollmentCourse), -360);
        try (var connection = runtime.dataSource().getConnection()) {
            setSessionTimezone(connection, "UTC");
            assertThatThrownBy(() -> enroll(
                            connection, firstParticipantToken, enrollmentCourse))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("55000"));
            connection.rollback();
        }
    }

    private static String confirmConcurrently(
            UUID sessionToken,
            UUID sessionId,
            CountDownLatch ready,
            CountDownLatch start) {
        try (var connection = runtime.dataSource().getConnection()) {
            ready.countDown();
            start.await();
            setAttendance(connection, sessionToken, sessionId, "CONFIRMED");
            connection.commit();
            return "CONFIRMED";
        } catch (SQLException exception) {
            return exception.getSQLState();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static String withdrawConcurrently(
            Connection connection, UUID sessionToken, UUID courseId) {
        try {
            withdraw(connection, sessionToken, courseId);
            connection.commit();
            return "WITHDRAWN";
        } catch (SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            return exception.getSQLState();
        }
    }

    private static String cancelSessionConcurrently(
            Connection connection, UUID sessionId) {
        try (var statement = connection.prepareStatement(
                "update app.course_session set status = 'CANCELLED' where id = ?")) {
            statement.setObject(1, sessionId);
            statement.executeUpdate();
            connection.commit();
            return "CANCELLED";
        } catch (SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            return exception.getSQLState();
        }
    }

    private static String declineConcurrently(
            Connection connection, UUID sessionToken, UUID sessionId) {
        try {
            setAttendance(connection, sessionToken, sessionId, "DECLINED");
            connection.commit();
            return "DECLINED";
        } catch (SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            return exception.getSQLState();
        }
    }

    private static void preferIndexScan(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("set local enable_seqscan = off");
            statement.execute("set local enable_bitmapscan = off");
            statement.execute("set local statement_timeout = '8s'");
        }
    }

    private static void preferSequentialScan(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("set local enable_indexscan = off");
            statement.execute("set local enable_bitmapscan = off");
            statement.execute("set local statement_timeout = '8s'");
        }
    }

    private static void setStatementTimeout(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("set local statement_timeout = '8s'");
        }
    }

    private static void setSessionTimezone(Connection connection, String timezone)
            throws SQLException {
        assertThat(timezone).isEqualTo("UTC");
        try (var statement = connection.createStatement()) {
            statement.execute("set local time zone 'UTC'");
        }
    }

    private static int backendPid(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("select pg_backend_pid()")) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private static void lockSession(Connection connection, UUID sessionId)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "select id from app.course_session where id = ? for update")) {
            statement.setObject(1, sessionId);
            statement.executeQuery();
        }
    }

    private static void awaitDatabaseLock(int backendPid) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (isWaitingOnLock(backendPid)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Database backend did not wait on the expected lock");
    }

    private static void awaitCompletionOrDatabaseLock(
            java.util.concurrent.Future<String> result, int backendPid) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (result.isDone()) {
                return;
            }
            if (isWaitingOnLock(backendPid)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Withdrawal neither completed nor waited on a lock");
    }

    private static boolean isWaitingOnLock(int backendPid) throws SQLException {
        try (var connection = administratorConnection();
                var statement = connection.prepareStatement(
                        "select wait_event_type from pg_stat_activity where pid = ?")) {
            statement.setInt(1, backendPid);
            try (var result = statement.executeQuery()) {
                return result.next() && "Lock".equals(result.getString(1));
            }
        }
    }

    private static void enroll(Connection connection, UUID sessionToken, UUID courseId)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "select app.enroll_in_course(?, ?)")) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, courseId);
            statement.executeQuery();
        }
    }

    private static void withdraw(Connection connection, UUID sessionToken, UUID courseId)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "select app.withdraw_from_course(?, ?)")) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, courseId);
            statement.executeQuery();
        }
    }

    private static void setAttendance(
            Connection connection, UUID sessionToken, UUID sessionId, String status)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "select app.set_practical_attendance(?, ?, ?)")) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, sessionId);
            statement.setString(3, status);
            statement.executeQuery();
        }
    }

    private static String enrollmentStatus(
            Connection connection, UUID sessionToken, UUID courseId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "select enrollment_status from app.list_participant_enrollments(?) "
                        + "where course_id = ?")) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, courseId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static SessionView practicalSession(
            Connection connection, UUID sessionToken, UUID courseId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "select attendance_status, confirmed_count, remaining_places "
                        + "from app.list_enrollment_practical_sessions(?, ?)")) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, courseId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return new SessionView(
                        result.getString("attendance_status"),
                        result.getInt("confirmed_count"),
                        result.getInt("remaining_places"));
            }
        }
    }

    private static UUID createPublishedPracticalCourse(int capacity) throws SQLException {
        return createPublishedPracticalCourse(capacity, true);
    }

    private static UUID createPublishedPracticalCourse(int capacity, boolean ready) throws SQLException {
        var courseId = UUID.randomUUID();
        var fixtureSlot = FIXTURE_SEQUENCE.getAndIncrement();
        try (var connection = migratorConnection()) {
            connection.setAutoCommit(false);
            try (var course = connection.prepareStatement(
                            "insert into app.course(id, chef_profile_id, category_id, title, "
                                    + "description, start_date, start_time, recurrence_days, "
                                    + "session_count, status, ingredient_margin) "
                                    + "values (?, ?, ?, ?, 'Corso per iscrizioni', "
                                    + "((current_timestamp at time zone 'Europe/Rome') "
                                    + "+ interval '1 year')::date, time '18:00', "
                                    + "7, 1, 'PUBLISHED', 0)");
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
                course.setString(4, "Corso iscrizioni " + courseId.toString().substring(0, 8));
                course.executeUpdate();
                session.setObject(1, courseId);
                session.setInt(2, fixtureSlot);
                session.setInt(3, capacity);
                session.executeUpdate();
                connection.commit();
            }
        }
        if (ready) {
            addRecipes(courseId);
        }
        return courseId;
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

    private static List<UUID> practicalSessionIds(UUID courseId) throws SQLException {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "select id from app.course_session where course_id = ? order by id")) {
            statement.setObject(1, courseId);
            try (var result = statement.executeQuery()) {
                var identifiers = new java.util.ArrayList<UUID>();
                while (result.next()) {
                    identifiers.add(result.getObject(1, UUID.class));
                }
                return List.copyOf(identifiers);
            }
        }
    }

    private static void addPracticalSession(UUID courseId) throws SQLException {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "insert into app.course_session(course_id, sequence_number, "
                                + "scheduled_at, mode, status, duration_minutes, venue, capacity) "
                                + "values (?, 2, date_trunc('day', "
                                + "current_timestamp at time zone 'Europe/Rome') "
                                + "+ interval '1 year 7 days 18 hours', 'PRACTICAL', "
                                + "'SCHEDULED', 120, 'Laboratorio Vesuvio', 2)")) {
            statement.setObject(1, courseId);
            statement.executeUpdate();
        }
        addRecipes(courseId);
    }

    private static void addRecipes(UUID courseId) throws SQLException {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "insert into app.session_recipe(session_id, recipe_id) "
                                + "select id, '50000000-0000-0000-0000-000000000001'::uuid "
                                + "from app.course_session where course_id = ? on conflict do nothing")) {
            statement.setObject(1, courseId);
            statement.executeUpdate();
        }
    }

    private static void makeSessionUnavailable(
            UUID sessionId, String status, LocalDateTime scheduledAt) throws SQLException {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "update app.course_session set status = ?, scheduled_at = ?, "
                                + "attendance_closes_at = ? - interval '48 hours' where id = ?")) {
            statement.setString(1, status);
            statement.setObject(2, scheduledAt);
            statement.setObject(3, scheduledAt);
            statement.setObject(4, sessionId);
            statement.executeUpdate();
        }
    }

    private static void moveSessionRelativeToRomeNow(UUID sessionId, int minutes)
            throws SQLException {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "update app.course_session set status = 'SCHEDULED', "
                                + "scheduled_at = (current_timestamp at time zone 'Europe/Rome') + (? * interval '1 minute'), "
                                + "attendance_closes_at = (current_timestamp at time zone 'Europe/Rome') "
                                + "+ (? * interval '1 minute') - interval '48 hours' where id = ?")) {
            statement.setInt(1, minutes);
            statement.setInt(2, minutes);
            statement.setObject(3, sessionId);
            statement.executeUpdate();
        }
    }

    private static int countEnrollmentRows(UUID courseId) throws SQLException {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "select count(*) from app.enrollment where course_id = ?")) {
            statement.setObject(1, courseId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private static int countConfirmedAttendance(UUID sessionId) throws SQLException {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "select count(*) from app.practical_attendance "
                                + "where session_id = ? and status = 'CONFIRMED'")) {
            statement.setObject(1, sessionId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private static int countAttendanceRows(UUID sessionId) throws SQLException {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "select count(*) from app.practical_attendance where session_id = ?")) {
            statement.setObject(1, sessionId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private static int countPracticalSessions(
            Connection connection, UUID sessionToken, UUID courseId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "select count(*) from app.list_enrollment_practical_sessions(?, ?)")) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, courseId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private static String sessionStatus(Connection connection, UUID courseId, UUID sessionId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "select status from app.list_course_sessions(?, ?) where session_id = ?")) {
            statement.setObject(1, chefSessionToken);
            statement.setObject(2, courseId);
            statement.setObject(3, sessionId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static String attendanceStatus(UUID sessionId, UUID sessionToken)
            throws SQLException {
        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select attendance_status "
                                + "from app.list_enrollment_practical_sessions(?, "
                                + "(select course_id from app.course_session where id = ?))")) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, sessionId);
            assertThatThrownBy(statement::executeQuery)
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("42501"));
        }
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "select practical_attendance.status from app.practical_attendance "
                                + "join app.enrollment on enrollment.id = "
                                + "practical_attendance.enrollment_id "
                                + "join app.auth_session on auth_session.account_id = "
                                + "(select account_id from app.participant_profile "
                                + "where id = enrollment.participant_profile_id) "
                                + "where practical_attendance.session_id = ? and "
                                + "auth_session.token_hash = sha256(convert_to(?::text, 'UTF8'))")) {
            statement.setObject(1, sessionId);
            statement.setObject(2, sessionToken);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static String enrollmentStatusDirect(UUID courseId, UUID sessionToken)
            throws SQLException {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "select enrollment.status from app.enrollment "
                                + "join app.participant_profile on participant_profile.id = "
                                + "enrollment.participant_profile_id "
                                + "join app.auth_session on auth_session.account_id = "
                                + "participant_profile.account_id "
                                + "where enrollment.course_id = ? and auth_session.token_hash = "
                                + "sha256(convert_to(?::text, 'UTF8'))")) {
            statement.setObject(1, courseId);
            statement.setObject(2, sessionToken);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static String attendanceStatusDirect(UUID sessionId, UUID sessionToken)
            throws SQLException {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "select practical_attendance.status from app.practical_attendance "
                                + "join app.enrollment on enrollment.id = "
                                + "practical_attendance.enrollment_id "
                                + "join app.participant_profile on participant_profile.id = "
                                + "enrollment.participant_profile_id "
                                + "join app.auth_session on auth_session.account_id = "
                                + "participant_profile.account_id "
                                + "where practical_attendance.session_id = ? and "
                                + "auth_session.token_hash = sha256(convert_to(?::text, 'UTF8'))")) {
            statement.setObject(1, sessionId);
            statement.setObject(2, sessionToken);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static void createSecondParticipant() throws SQLException {
        var accountId = UUID.fromString("10000000-0000-0000-0000-000000000004");
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

    private static Connection migratorConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), "uninafoodlab_migrator", MIGRATOR_PASSWORD);
    }

    private static Connection administratorConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private record SessionView(String status, int confirmedCount, int remainingPlaces) {}
}
