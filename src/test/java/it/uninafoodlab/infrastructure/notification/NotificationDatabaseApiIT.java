package it.uninafoodlab.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.application.notification.NotificationDraft;
import it.uninafoodlab.application.notification.NotificationReason;
import it.uninafoodlab.application.schedule.NewSessionInput;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import it.uninafoodlab.infrastructure.planning.JdbcChefPlanningRepository;
import it.uninafoodlab.infrastructure.schedule.JdbcScheduleRepository;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import java.net.URI;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class NotificationDatabaseApiIT {

    private static final String MIGRATOR_PASSWORD = "notification-migrator-only";
    private static final String APP_PASSWORD = "notification-app-only";
    private static final UUID CATEGORY_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID RECIPE_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");

    private static ApplicationRuntime runtime;
    private static UUID chefToken;
    private static UUID participantToken;
    private static UUID otherChefToken;
    private static final java.util.concurrent.atomic.AtomicInteger FUTURE_DAY = new java.util.concurrent.atomic.AtomicInteger(100);

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
        createOtherChef();
        chefToken = authenticate("marta.rossi", "MartaRossi!2026");
        participantToken = authenticate("luca.bianchi", "LucaBianchi!2026");
        otherChefToken = authenticate("chef.notifications", "ChefDemo!2026");
    }

    @AfterAll
    static void stopDatabase() {
        runtime.close();
    }

    @Test
    void chefHistoryCarriesIdentityForSameNamedCoursesAndRejectsOtherRoles() throws SQLException {
        var first = createPublishedCourse("Edizioni omonime");
        var second = createPublishedCourse("Edizioni omonime", futureTime().plusDays(7));
        enroll(first);
        enroll(second);
        var repository = new JdbcNotificationRepository(runtime.dataSource());
        var firstNotice = repository.send(chefToken, java.util.Optional.of(first),
                NotificationDraft.of("Prima edizione", "Avviso della prima edizione."));
        var secondNotice = repository.send(chefToken, java.util.Optional.of(second),
                NotificationDraft.of("Seconda edizione", "Avviso della seconda edizione."));
        var history = repository.findForChef(chefToken);
        var firstItem = history.stream().filter(item -> item.id().equals(firstNotice)).findFirst().orElseThrow();
        var secondItem = history.stream().filter(item -> item.id().equals(secondNotice)).findFirst().orElseThrow();
        assertThat(firstItem.courseId()).contains(first);
        assertThat(secondItem.courseId()).contains(second);
        assertThat(firstItem.courseTitle()).isEqualTo(secondItem.courseTitle());
        assertThat(firstItem.courseStartDate()).isPresent();
        assertThat(repository.findForChef(otherChefToken)).noneMatch(item -> item.id().equals(firstNotice)
                || item.id().equals(secondNotice));
        assertThatThrownBy(() -> repository.findForChef(participantToken))
                .isInstanceOf(NotificationRepositoryException.class);
        var invalidToken = UUID.randomUUID();
        assertThatThrownBy(() -> repository.findForChef(invalidToken))
                .isInstanceOf(NotificationRepositoryException.class);
    }

    @Test
    void participantHistoryCarriesCourseIdentityOnlyForItsRecipientScope() throws SQLException {
        var enrolledCourse = createPublishedCourse("Corso destinatario");
        var otherCourse = createPublishedCourse("Corso non destinatario", futureTime().plusDays(7));
        enroll(enrolledCourse);
        var repository = new JdbcNotificationRepository(runtime.dataSource());
        var enrolledNotice = repository.send(chefToken, java.util.Optional.of(enrolledCourse),
                NotificationDraft.of("Avviso corso", "Comunicazione per il corso iscritto."));
        var otherNotice = repository.send(chefToken, java.util.Optional.of(otherCourse),
                NotificationDraft.of("Avviso escluso", "Comunicazione per un altro corso."));

        var history = repository.findForParticipant(participantToken);
        assertThat(history)
                .filteredOn(notification -> notification.id().equals(enrolledNotice))
                .singleElement()
                .satisfies(notification -> assertThat(notification.courseId()).contains(enrolledCourse));
        assertThat(history).noneMatch(notification -> notification.id().equals(otherNotice));
        assertThatThrownBy(() -> repository.findForParticipant(otherChefToken))
                .isInstanceOf(NotificationRepositoryException.class);
    }

    @Test
    void recipientPreviewMatchesActualUniqueRecipientsAndChecksChefOwnership() throws SQLException {
        var courseId = createPublishedCourse("Anteprima destinatari");
        var secondCourseId = createPublishedCourse("Anteprima destinatari multipli");
        enroll(courseId);
        enroll(secondCourseId);
        var repository = new JdbcNotificationRepository(runtime.dataSource());
        assertThat(repository.countRecipients(chefToken, java.util.Optional.of(courseId))).isEqualTo(1);
        var expectedGlobal = repository.countRecipients(chefToken, java.util.Optional.empty());
        var notificationId = repository.send(chefToken, java.util.Optional.empty(),
                NotificationDraft.of("Anteprima verificata", "Messaggio per i partecipanti iscritti."));
        assertThat(repository.findForChef(chefToken).stream()
                .filter(notice -> notice.id().equals(notificationId)).findFirst().orElseThrow().recipientCount())
                .isEqualTo(expectedGlobal);
        var selectedCourse = java.util.Optional.of(courseId);
        assertThatThrownBy(() -> repository.countRecipients(otherChefToken, selectedCourse))
                .isInstanceOf(NotificationRepositoryException.class);
        java.util.Optional<UUID> allCourses = java.util.Optional.empty();
        assertThatThrownBy(() -> repository.countRecipients(participantToken, allCourses))
                .isInstanceOf(NotificationRepositoryException.class);
    }

    @Test
    void reschedulesSessionAndNotifiesCurrentCourseParticipantsAtomically() throws SQLException {
        var courseId = createPublishedCourse("Tecniche aggiornate");
        enroll(courseId);
        var sessionId = sessionId(courseId);
        var replacement = futureTime().plusDays(2);

        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select app.reschedule_course_session(?, ?, ?, NULL, NULL, ?, ?)")) {
            statement.setObject(1, chefToken);
            statement.setObject(2, sessionId);
            statement.setObject(3, replacement);
            statement.setString(4, "Orario aggiornato");
            statement.setString(5, "La sessione è stata spostata per esigenze organizzative.");
            statement.execute();
            connection.commit();
        }

        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select title, reason, course_title, read_at "
                                + "from app.list_participant_notifications(?) "
                                + "where course_title = ?")) {
            statement.setObject(1, participantToken);
            statement.setString(2, "Tecniche aggiornate");
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("title")).isEqualTo("Orario aggiornato");
                assertThat(result.getString("reason")).isEqualTo("SCHEDULE_CHANGED");
                assertThat(result.getString("course_title")).isEqualTo("Tecniche aggiornate");
                assertThat(result.getObject("read_at")).isNull();
                assertThat(result.next()).isFalse();
            }
        }
        assertThat(scheduledAt(sessionId)).isEqualTo(replacement);
    }

    @Test
    void addsAndCancelsSessionsWhileKeepingNotificationsAndAttendanceConsistent()
            throws SQLException {
        var courseId = createPublishedCourse("Laboratorio flessibile");
        enroll(courseId);
        var notificationsBeforeChanges = notificationCount(participantToken);
        var originalSession = sessionId(courseId);
        confirmAttendance(originalSession);

        UUID addedSession;
        var addedSessionTime = futureTime().plusDays(7);
        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select app.add_course_session(?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)")) {
            statement.setObject(1, chefToken);
            statement.setObject(2, courseId);
            statement.setObject(3, addedSessionTime);
            statement.setString(4, "PRACTICAL");
            statement.setInt(5, 90);
            statement.setNull(6, Types.VARCHAR);
            statement.setString(7, "Laboratorio Partenope");
            statement.setInt(8, 8);
            statement.setString(9, "Nuova sessione");
            statement.setString(10, "È disponibile una nuova attività pratica.");
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                addedSession = result.getObject(1, UUID.class);
            }
            connection.commit();
        }

        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select app.cancel_course_session(?, ?, ?, ?)")) {
            statement.setObject(1, chefToken);
            statement.setObject(2, originalSession);
            statement.setString(3, "Sessione annullata");
            statement.setString(4, "La sessione non potrà avere luogo.");
            statement.execute();
            connection.commit();
        }

        assertThat(sessionStatus(originalSession)).isEqualTo("CANCELLED");
        assertThat(attendanceStatus(originalSession)).isEqualTo("DECLINED");
        assertThat(sessionStatus(addedSession)).isEqualTo("SCHEDULED");
        assertThat(courseStartsAt(courseId)).isEqualTo(addedSessionTime);
        assertThat(courseSessionCount(courseId)).isEqualTo(3);
        assertThat(notificationCount(participantToken)).isEqualTo(notificationsBeforeChanges + 2);
    }

    @Test
    void sendsCourseOrGlobalNotificationsAndLetsOnlyTheRecipientMarkThemRead()
            throws SQLException {
        var courseId = createPublishedCourse("Comunicazioni mirate");
        enroll(courseId);
        UUID notificationId;

        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select app.send_course_notification(?, ?, ?, ?)")) {
            statement.setObject(1, chefToken);
            statement.setNull(2, Types.OTHER);
            statement.setString(3, "Avviso generale");
            statement.setString(4, "Controlla il calendario aggiornato.");
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                notificationId = result.getObject(1, UUID.class);
            }
            connection.commit();
        }

        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select app.mark_notification_read(?, ?)")) {
            statement.setObject(1, participantToken);
            statement.setObject(2, notificationId);
            statement.execute();
            connection.commit();
        }
        assertThat(notificationReadAt(participantToken, notificationId)).isNotNull();

        try (var connection = runtime.dataSource().getConnection()) {
            var session = sessionId(courseId);
            assertThatThrownBy(() -> rescheduleAs(connection, otherChefToken, session))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("42501"));
            connection.rollback();
        }
    }

    @Test
    void rejectedPublicCalendarOperationLeavesSessionNotificationAndAuditUntouched() throws SQLException {
        var courseId = createPublishedCourse("Rollback calendario pubblico");
        enroll(courseId);
        var session = sessionId(courseId);
        executeSchedule("select app.cancel_course_session(?, ?, ?, ?)", chefToken, session,
                "Annullata", "Messaggio valido");
        var notificationsBefore = notificationCount(participantToken);
        var timeBefore = scheduledAt(session);

        try (var connection = runtime.dataSource().getConnection()) {
            assertThatThrownBy(() -> executeSchedule(connection, session, timeBefore.plusDays(2)))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("55000"));
            connection.rollback();
        }

        assertThat(sessionStatus(session)).isEqualTo("CANCELLED");
        assertThat(scheduledAt(session)).isEqualTo(timeBefore);
        assertThat(notificationCount(participantToken)).isEqualTo(notificationsBefore);
    }

    @Test
    void applicationRoleCannotReadNotificationTablesDirectly() throws SQLException {
        try (var connection = runtime.dataSource().getConnection()) {
            assertThatThrownBy(() -> queryCount(connection, "select count(*) from app.notification"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("42501"));
            connection.rollback();
            assertThatThrownBy(() -> queryCount(
                            connection, "select count(*) from app.notification_recipient"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("42501"));
        }
    }

    @Test
    void cancellationAndAttendanceUpdateUseADeadlockFreeLockOrder() throws Exception {
        var courseId = createPublishedCourse("Concorrenza calendario");
        enroll(courseId);
        var sessionId = sessionId(courseId);
        confirmAttendance(sessionId);

        try (var executor = Executors.newSingleThreadExecutor();
                var attendance = migratorConnection();
                var cancellation = runtime.dataSource().getConnection()) {
            attendance.setAutoCommit(false);
            setStatementTimeout(attendance);
            setStatementTimeout(cancellation);
            lockEnrollmentForSession(attendance, sessionId);
            var cancellationPid = backendPid(cancellation);

            var cancellationResult = executor.submit(
                    () -> cancelConcurrently(cancellation, sessionId));
            awaitDatabaseLock(cancellationPid);
            var attendanceResult = declineAttendance(attendance, sessionId);

            assertThat(attendanceResult).isEqualTo("DECLINED");
            assertThat(cancellationResult.get(8, TimeUnit.SECONDS)).isEqualTo("CANCELLED");
        }
    }

    @Test
    void jdbcAdaptersMapScheduleMutationsAndNotificationArchive() throws SQLException {
        var courseId = createPublishedCourse("Adapter notifiche");
        enroll(courseId);
        var originalSession = sessionId(courseId);
        var scheduleRepository = new JdbcScheduleRepository(runtime.dataSource());
        var notificationRepository = new JdbcNotificationRepository(runtime.dataSource());
        var draft = NotificationDraft.of(
                "Programma aggiornato", "Controlla la nuova sessione disponibile.");

        scheduleRepository.reschedule(
                chefToken, originalSession, futureTime().plusDays(4), draft);
        var added = scheduleRepository.add(
                chefToken,
                courseId,
                new NewSessionInput(
                        futureTime().plusDays(8), SessionMode.ONLINE, 60,
                        java.util.Optional.of(URI.create("https://meet.example.test/lab")),
                        java.util.Optional.empty(), java.util.Optional.empty()),
                draft);
        var sent = notificationRepository.send(
                chefToken, java.util.Optional.of(courseId),
                NotificationDraft.of("Materiali", "Porta il quaderno di laboratorio."));

        var participantHistory = notificationRepository.findForParticipant(participantToken);
        var chefHistory = notificationRepository.findForChef(chefToken);
        notificationRepository.markRead(participantToken, sent);

        assertThat(added).isNotNull();
        assertThat(participantHistory)
                .extracting(it.uninafoodlab.application.notification.ParticipantNotification::reason)
                .contains(NotificationReason.SCHEDULE_CHANGED, NotificationReason.SESSION_ADDED,
                        NotificationReason.GENERAL);
        assertThat(chefHistory).anySatisfy(notification -> {
            assertThat(notification.id()).isEqualTo(sent);
            assertThat(notification.recipientCount()).isEqualTo(1);
        });
        assertThat(notificationRepository.findForParticipant(participantToken))
                .filteredOn(notification -> notification.id().equals(sent))
                .singleElement()
                .satisfies(notification -> assertThat(notification.readAt()).isPresent());
    }

    @Test
    void computesIngredientRequirementsParticipantRosterAndMonthlyReport() throws SQLException {
        var isolatedMonth = LocalDateTime.now(ROME).minusYears(2).withNano(0);
        var reportBefore = monthlyCounts(isolatedMonth);
        var courseId = createPublishedCourse("Analisi mensile");
        enroll(courseId);
        var sessionId = sessionId(courseId);
        confirmAttendance(sessionId);
        setSessionTimeForRealizedReport(sessionId, isolatedMonth);
        var month = scheduledAt(sessionId);

        try (var connection = runtime.dataSource().getConnection();
                var requirements = connection.prepareStatement("""
                        select ingredient_name, unit, confirmed_participants, required_quantity
                          from app.list_session_ingredient_requirements(?, ?)
                         where ingredient_name = 'Farina tipo 00'
                        """);
                var roster = connection.prepareStatement("""
                        select participant_name, attendance_status
                          from app.list_session_participants(?, ?)
                        """);
                var report = connection.prepareStatement(
                        "select * from app.chef_monthly_report(?, ?, ?)")) {
            requirements.setObject(1, chefToken);
            requirements.setObject(2, sessionId);
            try (var result = requirements.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("unit")).isEqualTo("GRAM");
                assertThat(result.getInt("confirmed_participants")).isEqualTo(1);
                assertThat(result.getBigDecimal("required_quantity"))
                        .isEqualByComparingTo("125");
            }

            roster.setObject(1, chefToken);
            roster.setObject(2, sessionId);
            try (var result = roster.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("participant_name")).isEqualTo("Luca Bianchi");
                assertThat(result.getString("attendance_status")).isEqualTo("CONFIRMED");
            }

            report.setObject(1, chefToken);
            report.setInt(2, month.getYear());
            report.setInt(3, month.getMonthValue());
            try (var result = report.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong("total_courses"))
                        .isEqualTo(reportBefore.totalCourses() + 1);
                assertThat(result.getLong("online_sessions"))
                        .isEqualTo(reportBefore.onlineSessions());
                assertThat(result.getLong("practical_sessions"))
                        .isEqualTo(reportBefore.practicalSessions() + 1);
                assertThat(result.getBigDecimal("average_recipes"))
                        .isEqualByComparingTo("1.00000000000000000000");
                assertThat(result.getInt("minimum_recipes")).isEqualTo(1);
                assertThat(result.getInt("maximum_recipes")).isEqualTo(1);
            }
        }

        var repository = new JdbcChefPlanningRepository(runtime.dataSource());
        assertThat(repository.requirements(chefToken, sessionId))
                .filteredOn(item -> item.ingredientName().equals("Farina tipo 00"))
                .singleElement()
                        .satisfies(item -> assertThat(item.requiredQuantity())
                        .isEqualByComparingTo("125"));
        assertThat(repository.participants(chefToken, sessionId))
                .singleElement()
                .satisfies(item -> assertThat(item.displayName()).isEqualTo("Luca Bianchi"));
        assertThat(repository.monthlyReport(
                        chefToken, java.time.YearMonth.of(month.getYear(), month.getMonthValue())))
                .satisfies(value -> assertThat(value.practicalSessions())
                        .isEqualTo(reportBefore.practicalSessions() + 1));
    }

    @Test
    void planningRequirementsAndRosterUseTheSameConfirmedParticipantCount() {
        var sessionId = UUID.fromString("b3160000-0000-0000-0000-000000000002");
        var repository = new JdbcChefPlanningRepository(runtime.dataSource());
        var requirements = repository.requirements(chefToken, sessionId);
        var participants = repository.participants(chefToken, sessionId);

        var confirmedParticipants = participants.stream()
                .filter(participant -> participant.enrollmentStatus() == EnrollmentStatus.ACTIVE)
                .filter(participant -> participant.attendanceStatus()
                        .filter(status -> status == AttendanceStatus.CONFIRMED)
                        .isPresent())
                .count();

        assertThat(confirmedParticipants).isEqualTo(12);
        assertThat(requirements)
                .isNotEmpty()
                .allSatisfy(requirement -> assertThat(requirement.confirmedParticipants())
                        .isEqualTo(confirmedParticipants));
    }

    @Test
    void keepsWholePersonQuantitiesExactForEveryUnitAndReturnsZeroWithoutConfirmations() throws SQLException {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var prefix = "Quantità esatte " + suffix;
        addWholeQuantityIngredients(prefix);
        var courseId = createPublishedCourse("Unità acquisto " + suffix);
        var sessionId = sessionId(courseId);
        enroll(courseId);
        confirmAttendance(sessionId);

        assertThat(requirementQuantities(sessionId, prefix)).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "GRAM", "381", "MILLILITER", "381",
                "PIECE", "3", "TEASPOON", "7", "EXACT", "80"));

        var zeroCourse = createPublishedCourse("Zero acquisto " + suffix);
        assertThat(requirementQuantities(sessionId(zeroCourse), prefix).values())
                .allMatch(quantity -> new java.math.BigDecimal(quantity).signum() == 0);
    }

    @Test
    void rejectsAttendanceAndPreservesConfirmedAttendanceAfterADeadlineCrossedByAnOpenTransaction()
            throws Exception {
        var attendanceCourse = createPublishedCourse("Cutoff transazione presenza");
        enroll(attendanceCourse);
        var attendanceSession = sessionId(attendanceCourse);
        var attendanceCutoff = databaseNow().plusSeconds(2);
        setCustomCutoff(attendanceSession, attendanceCutoff);
        try (var connection = runtime.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            assertThat(transactionNow(connection)).isBefore(attendanceCutoff);
            waitUntilClosed(connection, attendanceCutoff);
            assertThat(clockNow(connection)).isAfterOrEqualTo(attendanceCutoff);
            assertThatThrownBy(() -> confirmAttendance(connection, attendanceSession))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
            connection.rollback();
        }

        var withdrawalCourse = createPublishedCourse("Cutoff transazione ritiro");
        enroll(withdrawalCourse);
        var withdrawalSession = sessionId(withdrawalCourse);
        setCustomCutoff(withdrawalSession, databaseNow().plusSeconds(3));
        confirmAttendance(withdrawalSession);
        var withdrawalCutoff = databaseNow().plusSeconds(2);
        setCustomCutoff(withdrawalSession, withdrawalCutoff);
        try (var connection = runtime.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            assertThat(transactionNow(connection)).isBefore(withdrawalCutoff);
            waitUntilClosed(connection, withdrawalCutoff);
            assertThat(clockNow(connection)).isAfterOrEqualTo(withdrawalCutoff);
            try (var withdrawal = connection.prepareStatement("select app.withdraw_from_course(?, ?)")) {
                withdrawal.setObject(1, participantToken);
                withdrawal.setObject(2, withdrawalCourse);
                withdrawal.execute();
            }
            connection.commit();
        }
        assertThat(attendanceStatus(withdrawalSession)).isEqualTo("CONFIRMED");
    }

    @Test
    void calendarOperationsUseTheDatabaseClockAfterLocksRatherThanTransactionStart()
            throws Exception {
        var rescheduleCourse = createPublishedCourse("Orologio spostamento");
        var rescheduleSession = sessionId(rescheduleCourse);
        var rescheduleThreshold = databaseNow().plusSeconds(4);
        try (var connection = runtime.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            assertThat(transactionNow(connection)).isBefore(rescheduleThreshold);
            waitUntilClosed(connection, rescheduleThreshold);
            assertThat(transactionNow(connection)).isBefore(rescheduleThreshold);
            assertThat(clockNow(connection)).isAfterOrEqualTo(rescheduleThreshold);
            assertThatThrownBy(() -> executeSchedule(connection, rescheduleSession, rescheduleThreshold))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("22023"));
            connection.rollback();
        }
        assertThat(scheduledAt(rescheduleSession)).isNotEqualTo(rescheduleThreshold);

        var addCourse = createPublishedCourse("Orologio aggiunta");
        var sessionsBefore = courseSessionCount(addCourse);
        var addThreshold = databaseNow().plusSeconds(4);
        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select app.add_course_session(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            connection.setAutoCommit(false);
            assertThat(transactionNow(connection)).isBefore(addThreshold);
            waitUntilClosed(connection, addThreshold);
            assertThat(transactionNow(connection)).isBefore(addThreshold);
            assertThat(clockNow(connection)).isAfterOrEqualTo(addThreshold);
            statement.setObject(1, chefToken); statement.setObject(2, addCourse);
            statement.setObject(3, addThreshold); statement.setString(4, "ONLINE");
            statement.setInt(5, 90); statement.setString(6, "https://example.test/aula");
            statement.setNull(7, Types.VARCHAR); statement.setNull(8, Types.INTEGER);
            statement.setNull(9, Types.TIMESTAMP); statement.setString(10, "Aggiunta scaduta");
            statement.setString(11, "Non deve produrre notifiche.");
            assertThatThrownBy(statement::executeQuery).isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("22023"));
            connection.rollback();
        }
        assertThat(courseSessionCount(addCourse)).isEqualTo(sessionsBefore);
    }

    private static void addWholeQuantityIngredients(String prefix) throws SQLException {
        var inputs = java.util.List.of(
                new WholeQuantityIngredient("GRAM", "381"), new WholeQuantityIngredient("MILLILITER", "381"),
                new WholeQuantityIngredient("PIECE", "3"), new WholeQuantityIngredient("TEASPOON", "7"),
                new WholeQuantityIngredient("EXACT", "80"));
        try (var connection = migratorConnection();
                var ingredient = connection.prepareStatement(
                        "insert into app.ingredient(id, name, unit) values (?, ?, ?)");
                var amount = connection.prepareStatement(
                        "insert into app.recipe_ingredient(recipe_id, ingredient_id, quantity) values (?, ?, ?)")) {
            for (var input : inputs) {
                var id = UUID.randomUUID();
                ingredient.setObject(1, id);
                ingredient.setString(2, prefix + " " + input.label());
                ingredient.setString(3, "EXACT".equals(input.label()) ? "GRAM" : input.label());
                ingredient.addBatch();
                amount.setObject(1, RECIPE_ID);
                amount.setObject(2, id);
                amount.setBigDecimal(3, new java.math.BigDecimal(input.quantity()));
                amount.addBatch();
            }
            ingredient.executeBatch();
            amount.executeBatch();
        }
    }

    private static java.util.Map<String, String> requirementQuantities(UUID sessionId, String prefix)
            throws SQLException {
        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement("""
                        select ingredient_name, required_quantity from app.list_session_ingredient_requirements(?, ?)
                         where ingredient_name like ? order by ingredient_name
                        """)) {
            statement.setObject(1, chefToken);
            statement.setObject(2, sessionId);
            statement.setString(3, prefix + "%");
            try (var result = statement.executeQuery()) {
                var values = new java.util.LinkedHashMap<String, String>();
                while (result.next()) {
                    values.put(result.getString(1).substring(prefix.length() + 1),
                            result.getBigDecimal(2).stripTrailingZeros().toPlainString());
                }
                return values;
            }
        }
    }

    private record WholeQuantityIngredient(String label, String quantity) {}

    @Test
    void monthlyReportAutomaticallyIncludesElapsedSessionsAndExcludesFutureAndCancelledOnes()
            throws SQLException {
        var elapsed = LocalDateTime.now(ROME).minusYears(3).withNano(0);
        var reportBefore = monthlyCounts(elapsed);

        var heldCourse = createPublishedCourse("Sessioni tenute", futureTime());
        var heldPractical = sessionId(heldCourse);
        var heldOnline = addOnlineSession(heldCourse, futureTime().plusDays(2));
        setSessionTimeForRealizedReport(heldPractical, elapsed);
        setOnlineSessionTime(heldOnline, elapsed.plusDays(1));

        var cancelledCourse = createPublishedCourse("Sessione annullata", futureTime().plusDays(4));
        var cancelledSession = sessionId(cancelledCourse);
        executeSchedule("select app.cancel_course_session(?, ?, ?, ?)", chefToken,
                cancelledSession, "Annullata", "Questa sessione non si terrà.");
        setSessionTimeForRealizedReport(cancelledSession, elapsed.plusDays(2));

        var futureCourse = createPublishedCourse("Sessione futura", futureTime().plusDays(6));
        var futureSession = sessionId(futureCourse);

        assertThat(sessionStatus(heldPractical)).isEqualTo("SCHEDULED");
        assertThat(sessionStatus(heldOnline)).isEqualTo("SCHEDULED");
        assertThat(sessionStatus(cancelledSession)).isEqualTo("CANCELLED");
        assertThat(sessionStatus(futureSession)).isEqualTo("SCHEDULED");
        assertThat(monthlyCounts(elapsed)).isEqualTo(new MonthlyCounts(
                reportBefore.totalCourses() + 1,
                reportBefore.onlineSessions() + 1,
                reportBefore.practicalSessions() + 1));
    }

    private static UUID authenticate(String username, String password) {

        return new it.uninafoodlab.infrastructure.auth.JdbcAccountRepository(
                runtime.dataSource(), new it.uninafoodlab.infrastructure.security.Pbkdf2PasswordHasher())
                .authenticate(it.uninafoodlab.domain.identity.Username.of(username), password.toCharArray())
                .orElseThrow().sessionToken();
    }

    private static void createOtherChef() throws SQLException {
        try (var connection = migratorConnection();
                var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into app.account(id, username, password_hash, role)
                    values ('10000000-0000-0000-0000-000000000099', 'chef.notifications',
                            '$pbkdf2-sha256$600000$jbDTsbcntZ7JDhcGHbwZ5Q$caxX34l2hBbPZM9Km+VzQVpA52LuXw9f1Mj0LFjbxEc',
                            'CHEF');
                    insert into app.chef_profile(id, account_id, display_name)
                    values ('20000000-0000-0000-0000-000000000099',
                            '10000000-0000-0000-0000-000000000099', 'Chef Notifiche')
                    """);
        }
    }

    private static UUID createPublishedCourse(String title) throws SQLException {
        return createPublishedCourse(title, futureTime());
    }

    private static UUID createPublishedCourse(String title, LocalDateTime start)
            throws SQLException {
        UUID courseId;
        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select app.create_course(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, chefToken);
            statement.setObject(2, CATEGORY_ID);
            statement.setString(3, title);
            statement.setString(4, "Corso creato per verificare le notifiche.");
            statement.setObject(5, start.toLocalDate());
            statement.setObject(6, start.toLocalTime());
            statement.setInt(7, 7);
            statement.setBigDecimal(8, java.math.BigDecimal.ZERO);
            statement.setArray(9, connection.createArrayOf("text", new String[] {"PRACTICAL", "ONLINE"}));
            statement.setArray(10, connection.createArrayOf("integer", new Integer[] {120,60}));
            statement.setArray(11, connection.createArrayOf("text", new String[] {null,"https://example.org/seconda"}));
            statement.setArray(12, connection.createArrayOf("text", new String[] {"Laboratorio Vesuvio",null}));
            statement.setArray(13, connection.createArrayOf("integer", new Integer[] {12,null}));
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                courseId = result.getObject(1, UUID.class);
            }
            try (var assign = connection.prepareStatement(
                    "select app.assign_recipe_to_session(?, ?, ?)")) {
                assign.setObject(1, chefToken);
                assign.setObject(2, sessionId(connection, courseId));
                assign.setObject(3, RECIPE_ID);
                assign.execute();
            }
            try (var publish = connection.prepareStatement("select app.publish_course(?, ?)")) {
                publish.setObject(1, chefToken);
                publish.setObject(2, courseId);
                publish.execute();
            }
            connection.commit();
        }

        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement("select session_id from app.list_course_sessions(?,?) where sequence_number=2")) {
            statement.setObject(1,chefToken); statement.setObject(2,courseId);
            try (var rows=statement.executeQuery()) {
                rows.next();
                executeSchedule("select app.cancel_course_session(?,?,?,?)",chefToken,rows.getObject(1,UUID.class),
                        "Programma aggiornato","Seconda sessione annullata prima delle iscrizioni.");
            }
        }
        return courseId;
    }

    private static void enroll(UUID courseId) throws SQLException {
        executeFunction("select app.enroll_in_course(?, ?)", participantToken, courseId);
    }

    private static void confirmAttendance(UUID sessionId) throws SQLException {
        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select app.set_practical_attendance(?, ?, 'CONFIRMED')")) {
            statement.setObject(1, participantToken);
            statement.setObject(2, sessionId);
            statement.execute();
            connection.commit();
        }
    }

    private static void confirmAttendance(Connection connection, UUID sessionId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "select app.set_practical_attendance(?, ?, 'CONFIRMED')")) {
            statement.setObject(1, participantToken);
            statement.setObject(2, sessionId);
            statement.execute();
        }
    }

    private static void setCustomCutoff(UUID sessionId, LocalDateTime cutoff) throws SQLException {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "update app.course_session set attendance_closes_at = ? where id = ?")) {
            statement.setObject(1, cutoff);
            statement.setObject(2, sessionId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private static LocalDateTime databaseNow() throws SQLException {
        try (var connection = migratorConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("select clock_timestamp() at time zone 'Europe/Rome'")) {
            rows.next(); return rows.getObject(1, LocalDateTime.class);
        }
    }

    private static LocalDateTime transactionNow(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("select transaction_timestamp() at time zone 'Europe/Rome'")) {
            rows.next(); return rows.getObject(1, LocalDateTime.class);
        }
    }

    private static LocalDateTime clockNow(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("select clock_timestamp() at time zone 'Europe/Rome'")) {
            rows.next(); return rows.getObject(1, LocalDateTime.class);
        }
    }

    private static void waitUntilClosed(Connection connection, LocalDateTime cutoff) throws SQLException {
        try (var statement = connection.prepareStatement("select pg_sleep(greatest(0, extract(epoch from (?::timestamp - (clock_timestamp() at time zone 'Europe/Rome'))) + 0.2))")) {
            statement.setObject(1, cutoff); statement.execute();
        }
    }

    private static void executeFunction(String sql, UUID first, UUID second) throws SQLException {
        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, first);
            statement.setObject(2, second);
            statement.execute();
            connection.commit();
        }
    }

    private static UUID executeSchedule(String sql, UUID token, UUID session, String title, String message)
            throws SQLException {
        try (var connection = runtime.dataSource().getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, token); statement.setObject(2, session); statement.setString(3, title);
            statement.setString(4, message);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                var notificationId = rows.getObject(1, UUID.class);
                connection.commit();
                return notificationId;
            }
        }
    }

    private static void executeSchedule(Connection connection, UUID session, LocalDateTime time)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "select app.reschedule_course_session(?, ?, ?, NULL, NULL, ?, ?)")) {
            statement.setObject(1, chefToken);
            statement.setObject(2, session);
            statement.setObject(3, time);
            statement.setString(4, "Spostamento non valido");
            statement.setString(5, "Non deve produrre effetti.");
            statement.execute();
        }
    }

    private static UUID addOnlineSession(UUID courseId, LocalDateTime time) throws SQLException {
        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select app.add_course_session(?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)")) {
            statement.setObject(1, chefToken);
            statement.setObject(2, courseId);
            statement.setObject(3, time);
            statement.setString(4, "ONLINE");
            statement.setInt(5, 60);
            statement.setString(6, "https://meet.example.test/report");
            statement.setNull(7, Types.VARCHAR);
            statement.setNull(8, Types.INTEGER);
            statement.setString(9, "Aggiunta online");
            statement.setString(10, "È disponibile una nuova sessione online.");
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                var id = rows.getObject(1, UUID.class);
                connection.commit();
                return id;
            }
        }
    }

    private static UUID sessionId(UUID courseId) throws SQLException {
        try (var connection = runtime.dataSource().getConnection()) {
            return sessionId(connection, courseId);
        }
    }

    private static UUID sessionId(Connection connection, UUID courseId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "select session_id from app.list_course_sessions(?, ?) order by sequence_number limit 1")) {
            statement.setObject(1, chefToken);
            statement.setObject(2, courseId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getObject(1, UUID.class);
            }
        }
    }

    private static void rescheduleAs(Connection connection, UUID token, UUID sessionId)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "select app.reschedule_course_session(?, ?, ?, NULL, NULL, ?, ?)")) {
            statement.setObject(1, token);
            statement.setObject(2, sessionId);
            statement.setObject(3, futureTime().plusDays(3));
            statement.setString(4, "Tentativo non autorizzato");
            statement.setString(5, "Questo messaggio non deve essere inviato.");
            statement.execute();
        }
    }

    private static LocalDateTime futureTime() {
        return LocalDateTime.now(ROME).plusDays(FUTURE_DAY.addAndGet(40)).withNano(0);
    }

    private static LocalDateTime scheduledAt(UUID sessionId) throws SQLException {
        return queryValue("select scheduled_at from app.course_session where id = ?", sessionId,
                LocalDateTime.class);
    }

    private static String sessionStatus(UUID sessionId) throws SQLException {
        return queryValue("select status from app.course_session where id = ?", sessionId, String.class);
    }

    private static void setSessionTimeForRealizedReport(UUID sessionId, LocalDateTime scheduledAt)
            throws SQLException {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement("""
                        update app.course_session
                           set scheduled_at = ?, attendance_closes_at = ?
                         where id = ?
                        """)) {

            connection.setAutoCommit(false);
            try (var guard=connection.createStatement()) { guard.execute("ALTER TABLE app.course_session DISABLE TRIGGER preserve_session_change"); }
            statement.setObject(1, scheduledAt);
            statement.setObject(2, scheduledAt.minusHours(48));
            statement.setObject(3, sessionId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
            try (var guard=connection.createStatement()) { guard.execute("ALTER TABLE app.course_session ENABLE TRIGGER preserve_session_change"); }
            connection.commit();
        }
    }

    private static void setOnlineSessionTime(UUID sessionId, LocalDateTime scheduledAt)
            throws SQLException {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement("""
                        update app.course_session
                           set scheduled_at = ?
                         where id = ?
                        """)) {
            statement.setObject(1, scheduledAt);
            statement.setObject(2, sessionId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private static String attendanceStatus(UUID sessionId) throws SQLException {
        return queryValue(
                "select status from app.practical_attendance where session_id = ?",
                sessionId,
                String.class);
    }

    private static int courseSessionCount(UUID courseId) throws SQLException {
        return queryValue("select session_count from app.course where id = ?", courseId, Integer.class);
    }

    private static LocalDateTime courseStartsAt(UUID courseId) throws SQLException {
        return queryValue(
                "select start_date + start_time from app.course where id = ?",
                courseId,
                LocalDateTime.class);
    }

    private static int notificationCount(UUID token) throws SQLException {
        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select count(*) from app.list_participant_notifications(?)")) {
            statement.setObject(1, token);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private static java.time.OffsetDateTime notificationReadAt(UUID token, UUID id)
            throws SQLException {
        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select read_at from app.list_participant_notifications(?) "
                                + "where notification_id = ?")) {
            statement.setObject(1, token);
            statement.setObject(2, id);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getObject(1, java.time.OffsetDateTime.class);
            }
        }
    }

    private static <T> T queryValue(String sql, UUID id, Class<T> type) throws SQLException {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getObject(1, type);
            }
        }
    }

    private static int queryCount(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private static MonthlyCounts monthlyCounts(LocalDateTime dateTime) throws SQLException {
        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select total_courses, online_sessions, practical_sessions "
                                + "from app.chef_monthly_report(?, ?, ?)")) {
            statement.setObject(1, chefToken);
            statement.setInt(2, dateTime.getYear());
            statement.setInt(3, dateTime.getMonthValue());
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return new MonthlyCounts(result.getLong(1), result.getLong(2), result.getLong(3));
            }
        }
    }

    private record MonthlyCounts(long totalCourses, long onlineSessions, long practicalSessions) {}
    private static String cancelConcurrently(Connection connection, UUID sessionId) {
        try (var statement = connection.prepareStatement(
                "select app.cancel_course_session(?, ?, ?, ?)")) {
            statement.setObject(1, chefToken);
            statement.setObject(2, sessionId);
            statement.setString(3, "Annullamento concorrente");
            statement.setString(4, "La sessione è stata annullata.");
            statement.execute();
            connection.commit();
            return "CANCELLED";
        } catch (SQLException exception) {
            rollback(connection, exception);
            return exception.getSQLState();
        }
    }

    private static String declineAttendance(Connection connection, UUID sessionId) {
        try (var statement = connection.prepareStatement(
                "select app.set_practical_attendance(?, ?, 'DECLINED')")) {
            statement.setObject(1, participantToken);
            statement.setObject(2, sessionId);
            statement.execute();
            connection.commit();
            return "DECLINED";
        } catch (SQLException exception) {
            rollback(connection, exception);
            return exception.getSQLState();
        }
    }

    private static void rollback(Connection connection, SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static void setStatementTimeout(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("set local statement_timeout = '8s'");
        }
    }

    private static void lockEnrollmentForSession(Connection connection, UUID sessionId)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                select enrollment.id
                  from app.enrollment
                  join app.course_session on course_session.course_id = enrollment.course_id
                 where course_session.id = ?
                 order by enrollment.id
                 for update of enrollment
                """)) {
            statement.setObject(1, sessionId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
            }
        }
    }

    private static int backendPid(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("select pg_backend_pid()")) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
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

    private static Connection migratorConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), "uninafoodlab_migrator", MIGRATOR_PASSWORD);
    }

    private static Connection administratorConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
