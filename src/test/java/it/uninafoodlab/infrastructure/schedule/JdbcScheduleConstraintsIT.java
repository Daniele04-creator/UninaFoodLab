package it.uninafoodlab.infrastructure.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.application.course.NewCourseInput;
import it.uninafoodlab.application.notification.NotificationDraft;
import it.uninafoodlab.application.schedule.NewSessionInput;
import it.uninafoodlab.application.schedule.ScheduleConflictException;
import it.uninafoodlab.domain.course.SessionDetails;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class JdbcScheduleConstraintsIT {

    private static final String MIGRATOR_PASSWORD = "schedule-constraints-migrator-only";
    private static final String APP_PASSWORD = "schedule-constraints-app-only";
    private static final UUID CATEGORY_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");

    private static ApplicationRuntime runtime;

    @BeforeAll
    static void startDatabase() throws Exception {
        POSTGRES.copyFileToContainer(
                MountableFile.forHostPath(Path.of("database", "bootstrap", "001__create_roles.sql")),
                "/tmp/001__create_roles.sql");
        var result = POSTGRES.execInContainer(
                "psql", "--username=" + POSTGRES.getUsername(),
                "--dbname=" + POSTGRES.getDatabaseName(), "--set=ON_ERROR_STOP=1",
                "--set=migrator_password=" + MIGRATOR_PASSWORD,
                "--set=app_password=" + APP_PASSWORD, "--file=/tmp/001__create_roles.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();

        runtime = it.uninafoodlab.infrastructure.support.DemoDatabaseFixture.start(new ApplicationConfig(
                POSTGRES.getJdbcUrl(), "uninafoodlab_app", APP_PASSWORD,
                "uninafoodlab_migrator", MIGRATOR_PASSWORD, "test"));
    }

    @AfterAll
    static void stopDatabase() {
        runtime.close();
    }

    @Test
    void databaseApiRejectsAnInitialCourseWithOnlyOneSession() throws SQLException {
        var chef = runtime.authenticateUser().execute("marta.rossi", "MartaRossi!2026".toCharArray());

        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select app.create_course(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, chef.sessionToken());
            statement.setObject(2, CATEGORY_ID);
            statement.setString(3, "API una sola sessione");
            statement.setString(4, "La regola deve essere applicata anche dal database.");
            statement.setObject(5, LocalDate.of(2035, 5, 6));
            statement.setObject(6, LocalTime.of(10, 0));
            statement.setInt(7, 7);
            statement.setBigDecimal(8, BigDecimal.ZERO);
            statement.setArray(9, connection.createArrayOf("text", new String[] {"ONLINE"}));
            statement.setArray(10, connection.createArrayOf("integer", new Integer[] {120}));
            statement.setArray(11, connection.createArrayOf("text", new String[] {"https://meet.example.test/one"}));
            statement.setArray(12, connection.createArrayOf("text", new String[] {null}));
            statement.setArray(13, connection.createArrayOf("integer", new Integer[] {null}));

            assertThatThrownBy(statement::executeQuery)
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("22023"));
            connection.rollback();
        }
    }

    @Test
    void preventsOverlappingChefSessionsButAllowsTouchingBoundaries() {
        var chef = runtime.authenticateUser().execute("marta.rossi", "MartaRossi!2026".toCharArray());
        var courseId = runtime.createCourse().execute(chef, new NewCourseInput(
                CATEGORY_ID, "Calendario senza conflitti", "Verifica degli intervalli del docente.",
                LocalDate.of(2036, 6, 2), LocalTime.of(10, 0), 7, BigDecimal.ZERO,
                List.of(online(), online())));
        var sessions = runtime.browseCourses().sessions(chef, courseId);
        var first = sessions.getFirst();
        var second = sessions.getLast();
        var schedule = runtime.manageCourseSchedule();
        var notification = NotificationDraft.of("Calendario aggiornato", "La sessione è stata aggiornata.");

        assertThatThrownBy(() -> schedule.reschedule(
                        chef, second.id(), first.scheduledAt().plusMinutes(30), notification))
                .isInstanceOf(ScheduleConflictException.class)
                .hasMessage("Lo Chef ha già una sessione programmata in questo intervallo.");

        assertThatThrownBy(() -> schedule.add(chef, courseId, new NewSessionInput(
                        first.scheduledAt().plusMinutes(30), SessionMode.ONLINE, first.durationMinutes(),
                        Optional.of(URI.create("https://meet.example.test/conflict")),
                        Optional.empty(), Optional.empty()), notification))
                .isInstanceOf(ScheduleConflictException.class);

        var boundarySession = schedule.add(chef, courseId, new NewSessionInput(
                first.scheduledAt().plusMinutes(first.durationMinutes()), SessionMode.ONLINE,
                first.durationMinutes(), Optional.of(URI.create("https://meet.example.test/boundary")),
                Optional.empty(), Optional.empty()), notification);
        assertThat(boundarySession).isNotNull();
    }

    @Test
    void tomorrowPracticalRemainsOpenAndCanBeMovedOrAddedWithoutAnExplicitDeadline() throws SQLException {
        var chef = runtime.authenticateUser().execute("marta.rossi", "MartaRossi!2026".toCharArray());
        var tomorrow = LocalDate.now(java.time.ZoneId.of("Europe/Rome")).plusDays(1).atTime(6, 0);
        var courseId = runtime.createCourse().execute(chef, new NewCourseInput(
                CATEGORY_ID, "Pratica organizzata per domani", "Adesioni senza anticipo obbligatorio.",
                tomorrow.toLocalDate(), tomorrow.toLocalTime(), 7, BigDecimal.ZERO,
                List.of(SessionDetails.practical(60, "Laboratorio vicino", 12), online())));
        var first = runtime.browseCourses().sessions(chef, courseId).getFirst();
        assertThat(first.attendanceClosesAt()).contains(tomorrow.minusMinutes(1));
        runtime.assignRecipeToSession().execute(chef, first.id(),
                UUID.fromString("50000000-0000-0000-0000-000000000001"));
        var participant = runtime.authenticateUser().execute("luca.bianchi", "LucaBianchi!2026".toCharArray());
        var enrollments = new it.uninafoodlab.infrastructure.enrollment.JdbcEnrollmentRepository(runtime.dataSource());
        enrollments.enroll(participant.sessionToken(), courseId);
        enrollments.setAttendance(participant.sessionToken(), first.id(),
                it.uninafoodlab.domain.enrollment.AttendanceStatus.CONFIRMED);

        var moved = tomorrow.plusHours(2);
        runtime.manageCourseSchedule().reschedule(chef, first.id(), moved,
                NotificationDraft.of("Orario aggiornato", "La pratica inizia due ore dopo."));
        assertThat(runtime.browseCourses().sessions(chef, courseId).getFirst().attendanceClosesAt())
                .contains(moved.minusMinutes(1));

        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select app.add_course_session(?, ?, ?, 'PRACTICAL', 60, null, ?, 12, null, ?, ?)")) {
            statement.setObject(1, chef.sessionToken());
            statement.setObject(2, courseId);
            statement.setObject(3, tomorrow.plusHours(4));
            statement.setString(4, "Laboratorio vicino");
            statement.setString(5, "Nuova pratica");
            statement.setString(6, "Aggiunta una pratica per domani.");
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
            }
            connection.commit();
        }
        assertThat(runtime.browseCourses().sessions(chef, courseId))
                .filteredOn(session -> session.scheduledAt().equals(tomorrow.plusHours(4)))
                .singleElement().satisfies(session -> assertThat(session.attendanceClosesAt())
                        .contains(tomorrow.plusHours(4).minusMinutes(1)));
    }

    @Test
    void correctsVenueWithoutClearingRepliesAndPublishesNotice() throws SQLException {
        var chef = runtime.authenticateUser().execute("marta.rossi", "MartaRossi!2026".toCharArray());
        var participant = runtime.authenticateUser().execute("luca.bianchi", "LucaBianchi!2026".toCharArray());
        var course = runtime.createCourse().execute(chef, new NewCourseInput(CATEGORY_ID,
                "Correzione sede", "Dati della sessione modificabili senza spostamento.",
                LocalDate.of(2037, 2, 4), LocalTime.of(9, 0), 7, BigDecimal.ZERO,
                List.of(SessionDetails.practical(60, "Sede originale", 12), online())));
        var session = runtime.browseCourses().sessions(chef, course).getFirst();
        runtime.assignRecipeToSession().execute(chef, session.id(),
                UUID.fromString("50000000-0000-0000-0000-000000000001"));
        var enrollment = new it.uninafoodlab.infrastructure.enrollment.JdbcEnrollmentRepository(runtime.dataSource());
        enrollment.enroll(participant.sessionToken(), course);
        enrollment.setAttendance(participant.sessionToken(), session.id(), it.uninafoodlab.domain.enrollment.AttendanceStatus.CONFIRMED);
        var closedCutoff = LocalDateTime.now(java.time.ZoneId.of("Europe/Rome")).minusMinutes(5).withNano(0);
        try (var admin = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = admin.prepareStatement("update app.course_session set attendance_closes_at=? where id=?")) {
            statement.setObject(1, closedCutoff); statement.setObject(2, session.id()); statement.executeUpdate();
        }
        assertThatThrownBy(() -> runtime.manageCourseSchedule().reschedule(
                chef, session.id(), session.scheduledAt(), Optional.of(closedCutoff),
                NotificationDraft.of("Scadenza chiusa", "La modifica non deve essere salvata.")))
                .isInstanceOf(ScheduleRepositoryException.class)
                .satisfies(error -> assertThat(((SQLException) error.getCause()).getSQLState()).isEqualTo("22023"));
        runtime.manageCourseSchedule().reschedule(chef, session.id(), session.scheduledAt(), Optional.of(closedCutoff), "Sede nuova",
                NotificationDraft.of("Luogo aggiornato", "Consulta i dettagli aggiornati della sessione."));
        assertThat(runtime.browseCourses().sessions(chef, course).getFirst().venue()).contains("Sede nuova");
        var people = new it.uninafoodlab.infrastructure.planning.JdbcChefPlanningRepository(runtime.dataSource())
                .participants(chef.sessionToken(), session.id());
        assertThat(people).singleElement().satisfies(person -> assertThat(person.attendanceStatus())
                .contains(it.uninafoodlab.domain.enrollment.AttendanceStatus.CONFIRMED));
        assertThat(runtime.browseNotifications().forChef(chef)).anySatisfy(notice ->
                assertThat(notice.title()).isEqualTo("Luogo aggiornato"));
    }

    @ParameterizedTest
    @CsvSource({"ONLINE,180,COMPLETED", "PRACTICAL,180,COMPLETED", "ONLINE,5,SCHEDULED", "PRACTICAL,5,SCHEDULED"})
    void rejectsMovingStartedSessionsWithoutALocationAndPreservesHistory(
            SessionMode mode, int elapsedMinutes, String expectedState) throws SQLException {
        var chef = runtime.accountLifecycle().registerChef("schedule." + UUID.randomUUID(),
                "Chef storico", "ScheduleHistory!2026".toCharArray());
        var planned = LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(500).atTime(10, 0);
        var details = mode == SessionMode.ONLINE ? online() : SessionDetails.practical(120, "Laboratorio", 12);
        var course = runtime.createCourse().execute(chef, new NewCourseInput(CATEGORY_ID,
                "Protezione storico", "Le sessioni iniziate conservano il loro programma.",
                planned.toLocalDate(), planned.toLocalTime(), 7, BigDecimal.ZERO, List.of(details, online())));
        var session = runtime.browseCourses().sessions(chef, course).getFirst();
        var startedAt = LocalDateTime.now(ZoneId.of("Europe/Rome")).minusMinutes(elapsedMinutes).withNano(0);
        try (var admin = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), "uninafoodlab_migrator", MIGRATOR_PASSWORD);
                var statement = admin.prepareStatement(
                        "update app.course_session set scheduled_at=?, attendance_closes_at=? where id=?")) {
            statement.setObject(1, startedAt);
            statement.setObject(2, mode == SessionMode.PRACTICAL ? startedAt.minusMinutes(1) : null,
                    java.sql.Types.TIMESTAMP);
            statement.setObject(3, session.id());
            assertThat(statement.executeUpdate()).isOne();
        }
        var before = runtime.browseCourses().sessions(chef, course).getFirst();
        assertThat(before.status().name()).isEqualTo(expectedState);
        var month = YearMonth.from(startedAt);
        var report = runtime.browseChefPlanning().monthlyReport(chef, month);
        assertThat(report.onlineSessions() + report.practicalSessions())
                .isEqualTo(expectedState.equals("COMPLETED") ? 1 : 0);
        var notices = runtime.browseNotifications().forChef(chef);

        assertThatThrownBy(() -> runtime.manageCourseSchedule().reschedule(chef, session.id(),
                planned.plusDays(1), NotificationDraft.of("Spostamento storico", "La sessione non deve cambiare.")))
                .isInstanceOf(ScheduleRepositoryException.class)
                .satisfies(error -> assertThat(((SQLException) error.getCause()).getSQLState()).isEqualTo("55000"));

        assertThat(runtime.browseCourses().sessions(chef, course).getFirst()).isEqualTo(before);
        assertThat(runtime.browseChefPlanning().monthlyReport(chef, month)).isEqualTo(report);
        assertThat(runtime.browseNotifications().forChef(chef)).isEqualTo(notices);
    }

    @Test
    void correctsOnlineLinkAndRejectsInvalidOrUnauthorizedCorrections() throws SQLException {
        var chef = runtime.authenticateUser().execute("marta.rossi", "MartaRossi!2026".toCharArray());
        var participant = runtime.authenticateUser().execute("luca.bianchi", "LucaBianchi!2026".toCharArray());
        var course = runtime.createCourse().execute(chef, new NewCourseInput(CATEGORY_ID,
                "Correzione link", "Il link deve restare HTTPS.", LocalDate.of(2037, 3, 4),
                LocalTime.of(9, 0), 7, BigDecimal.ZERO, List.of(online(), online())));
        var session = runtime.browseCourses().sessions(chef, course).getFirst();
        correctLocation(chef.sessionToken(), session.id(), session.scheduledAt(), Optional.empty(), "https://meet.example.test/new-room");
        assertThat(runtime.browseCourses().sessions(chef, course).getFirst().onlineUrl())
                .contains(URI.create("https://meet.example.test/new-room"));
        assertThatThrownBy(() -> correctLocation(chef.sessionToken(), session.id(), session.scheduledAt(), Optional.empty(), "http://unsafe.test"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> correctLocation(participant.sessionToken(), session.id(), session.scheduledAt(), Optional.empty(), "https://meet.example.test/other"))
                .isInstanceOf(SQLException.class);
        assertThat(runtime.browseCourses().sessions(chef, course).getFirst().onlineUrl())
                .contains(URI.create("https://meet.example.test/new-room"));
    }

    private static void correctLocation(UUID token, UUID session, LocalDateTime at,
            Optional<LocalDateTime> cutoff, String location) throws SQLException {
        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement("select app.reschedule_course_session(?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, token); statement.setObject(2, session); statement.setObject(3, at);
            statement.setObject(4, cutoff.orElse(null), java.sql.Types.TIMESTAMP);
            statement.setString(5, location); statement.setString(6, "Luogo aggiornato");
            statement.setString(7, "Consulta i dettagli aggiornati della sessione.");
            statement.execute(); connection.commit();
        }
    }

    private static SessionDetails online() {
        return SessionDetails.online(120, URI.create("https://meet.example.test/course"));
    }
}
