package it.uninafoodlab.infrastructure.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.domain.catalog.MeasurementUnit;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import it.uninafoodlab.infrastructure.support.DatabaseActors;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class JdbcSessionPreparationIT {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");
    private static ApplicationRuntime runtime;
    private static JdbcChefPlanningRepository repository;
    private static UUID sessionToken;
    private static UUID participantSessionToken;
    private static String adminUrl;
    private static String adminUser;
    private static String adminPassword;
    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();

    @BeforeAll
    static void startDatabase() throws Exception {
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(
                Path.of("database", "bootstrap", "001__create_roles.sql")), "/tmp/roles.sql");
        var result = POSTGRES.execInContainer("psql", "--username=" + POSTGRES.getUsername(),
                "--dbname=" + POSTGRES.getDatabaseName(), "--set=ON_ERROR_STOP=1",
                "--set=migrator_password=preparation-migrator-only",
                "--set=app_password=preparation-app-only", "--file=/tmp/roles.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        adminUrl = POSTGRES.getJdbcUrl();
        adminUser = POSTGRES.getUsername();
        adminPassword = POSTGRES.getPassword();
        runtime = it.uninafoodlab.infrastructure.support.DemoDatabaseFixture.start(new ApplicationConfig(adminUrl, "uninafoodlab_app",
                "preparation-app-only", "uninafoodlab_migrator", "preparation-migrator-only", "test"));
        repository = new JdbcChefPlanningRepository(runtime.dataSource());
        sessionToken = runtime.authenticateUser().execute("marta.rossi", "MartaRossi!2026".toCharArray())
                .sessionToken();
        participantSessionToken = DatabaseActors.authenticate(runtime, "luca.bianchi",
                "LucaBianchi!2026".toCharArray()).sessionToken();
    }

    @AfterAll
    static void closeDatabase() {
        if (runtime != null) runtime.close();
    }

    @Test
    void sharedIngredientsSumExactSinglePersonDosesWithoutMarginOrRounding() throws Exception {
        var session = fixture(0, true, true);
        var snapshot = repository.sessionPreparation(sessionToken, session);
        assertThat(snapshot.confirmedParticipants()).isEqualTo(1);
        assertThat(snapshot.ingredientMargin()).isEqualByComparingTo("0");
        assertThat(snapshot.recipes()).hasSize(2);
        assertThat(snapshot.recipes()).extracting(r -> r.baseServings()).containsExactly(1, 1);
        assertThat(snapshot.recipes()).extracting(r -> r.portionsPerParticipant().intValueExact())
                .containsExactly(1, 1);
        var gramContributions = snapshot.recipes().stream().flatMap(r -> r.ingredients().stream())
                .filter(i -> i.unit() == MeasurementUnit.GRAM).map(i -> i.requiredQuantity()).toList();
        assertThat(gramContributions.get(0)).isEqualByComparingTo("25");
        assertThat(gramContributions.get(1)).isEqualByComparingTo("25");
        assertThat(gramContributions.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("50");
        assertThat(snapshot.totals().stream().filter(i -> i.unit() == MeasurementUnit.GRAM)
                .findFirst().orElseThrow().requiredQuantity()).isEqualByComparingTo("50");
        assertThat(snapshot.totals().stream().filter(i -> i.unit() == MeasurementUnit.MILLILITER)
                .findFirst().orElseThrow().requiredQuantity()).isEqualByComparingTo("250");
        assertThat(snapshot.totals().stream().filter(i -> i.unit() == MeasurementUnit.TEASPOON)
                .findFirst().orElseThrow().requiredQuantity()).isEqualByComparingTo("2");
        assertThat(snapshot.totals()).isEqualTo(repository.requirements(sessionToken, session));
    }

    @Test
    void fractionalDosesSurviveSnapshotsAndSumWithoutRounding() throws Exception {
        var session = fixture(0, true, true, "0.125");
        var snapshot = repository.sessionPreparation(sessionToken, session);
        assertThat(snapshot.confirmedParticipants()).isEqualTo(1);
        assertThat(snapshot.recipes().stream().flatMap(r -> r.ingredients().stream())
                .filter(i -> i.unit() == MeasurementUnit.TEASPOON))
                .allSatisfy(i -> assertThat(i.requiredQuantity()).isEqualByComparingTo("0.125"));
        assertThat(snapshot.totals().stream().filter(i -> i.unit() == MeasurementUnit.TEASPOON)
                .findFirst().orElseThrow().requiredQuantity()).isEqualByComparingTo("0.25");
        assertThat(snapshot.totals()).isEqualTo(repository.requirements(sessionToken, session));
    }

    @Test
    void zeroMarginAndZeroConfirmedDoNotInventOperationalQuantities() throws Exception {
        var noMargin = repository.sessionPreparation(sessionToken, fixture(0, true, true));
        assertThat(noMargin.recipes().getFirst().ingredients().stream()
                .filter(i -> i.unit() == MeasurementUnit.GRAM).findFirst().orElseThrow()
                .requiredQuantity()).isEqualByComparingTo("25");
        var noPeople = repository.sessionPreparation(sessionToken, fixture(0, false, true));
        assertThat(noPeople.confirmedParticipants()).isZero();
        assertThat(noPeople.recipes()).hasSize(2);
        assertThat(noPeople.recipes().stream().flatMap(r -> r.ingredients().stream()))
                .allSatisfy(i -> assertThat(i.requiredQuantity()).isZero());
        assertThat(noPeople.totals()).allSatisfy(i -> assertThat(i.requiredQuantity()).isZero());
    }

    @Test
    void multipleSessionsSumTheirExactPreparationTotals() throws Exception {
        var first = fixture(0, true, true);
        var second = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(adminUrl, adminUser, adminPassword);
                var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app.course_session(id,course_id,sequence_number,scheduled_at,mode,status,
                        duration_minutes,venue,capacity,attendance_closes_at)
                    SELECT '%s',course_id,2,scheduled_at+INTERVAL '7 days',mode,status,
                        duration_minutes,venue,capacity,attendance_closes_at+INTERVAL '7 days'
                    FROM app.course_session WHERE id='%s'
                    """.formatted(second, first));
            statement.execute("""
                    INSERT INTO app.session_recipe(session_id,recipe_id,portions_per_participant)
                    SELECT '%s',recipe_id,portions_per_participant FROM app.session_recipe WHERE session_id='%s'
                    """.formatted(second, first));
            statement.execute("""
                    INSERT INTO app.practical_attendance(enrollment_id,session_id,status)
                    SELECT enrollment_id,'%s',status FROM app.practical_attendance WHERE session_id='%s'
                    """.formatted(second, first));
        }
        var actor = runtime.authenticateUser().execute("marta.rossi", "MartaRossi!2026".toCharArray());
        var total = new it.uninafoodlab.application.planning.BrowseChefPlanning(repository)
                .requirements(actor, java.util.List.of(first, second));
        assertThat(total.stream().filter(i -> i.unit() == MeasurementUnit.GRAM)
                .findFirst().orElseThrow().requiredQuantity()).isEqualByComparingTo("100");
        assertThat(total.stream().filter(i -> i.unit() == MeasurementUnit.MILLILITER)
                .findFirst().orElseThrow().requiredQuantity()).isEqualByComparingTo("500");
    }

    @Test
    void emptyMenuStillReturnsContextAndRejectsUnauthorizedReads() throws Exception {
        var session = fixture(0, false, false);
        var snapshot = repository.sessionPreparation(sessionToken, session);
        assertThat(snapshot.sessionId()).isEqualTo(session);
        assertThat(snapshot.ingredientMargin()).isEqualByComparingTo("0");
        assertThat(snapshot.recipes()).isEmpty();
        assertThat(snapshot.totals()).isEmpty();
        assertThatThrownBy(() -> repository.sessionPreparation(participantSessionToken, session))
                .isInstanceOf(ChefPlanningRepositoryException.class);
        var unknownSession = UUID.randomUUID();
        assertThatThrownBy(() -> repository.sessionPreparation(sessionToken, unknownSession))
                .isInstanceOf(ChefPlanningRepositoryException.class);
    }

    @Test
    void responseTransitionsKeepCountsAndQuantitiesInTheSameSnapshot() throws Exception {
        var session = fixture(0, true, true);
        var confirmed = repository.sessionPreparation(sessionToken, session);
        assertThat(confirmed.confirmedParticipants()).isEqualTo(1);
        assertThat(confirmed.pendingParticipants()).isZero();
        assertThat(confirmed.declinedParticipants()).isZero();
        try (var connection = DriverManager.getConnection(adminUrl, adminUser, adminPassword);
                var statement = connection.createStatement()) {
            statement.execute("UPDATE app.practical_attendance SET status='DECLINED' WHERE session_id='%s'"
                    .formatted(session));
            var declined = repository.sessionPreparation(sessionToken, session);
            assertThat(declined.confirmedParticipants()).isZero();
            assertThat(declined.pendingParticipants()).isZero();
            assertThat(declined.declinedParticipants()).isEqualTo(1);
            assertThat(declined.totals()).allSatisfy(i -> assertThat(i.requiredQuantity()).isZero());
            statement.execute("DELETE FROM app.practical_attendance WHERE session_id='%s'".formatted(session));
            var pending = repository.sessionPreparation(sessionToken, session);
            assertThat(pending.confirmedParticipants()).isZero();
            assertThat(pending.pendingParticipants()).isEqualTo(1);
            assertThat(pending.declinedParticipants()).isZero();
            assertThat(pending.totals()).allSatisfy(i -> assertThat(i.requiredQuantity()).isZero());
            statement.execute("""
                    UPDATE app.enrollment SET status='WITHDRAWN', withdrawn_at=current_timestamp
                    WHERE course_id=(SELECT course_id FROM app.course_session WHERE id='%s')
                    """.formatted(session));
            var withdrawn = repository.sessionPreparation(sessionToken, session);
            assertThat(withdrawn.pendingParticipants()).isZero();
            assertThat(withdrawn.confirmedParticipants()).isZero();
            assertThat(withdrawn.declinedParticipants()).isZero();
        }
    }

    @Test
    void exportSnapshotKeepsLifecycleStateAndRequirementsAtTheSameRepeatableReadVersion() throws Exception {
        var session = fixture(0, true, true);
        var originalScheduledAt = scheduledAt(session);
        var snapshotRepository = new JdbcChefPlanningRepository(afterLifecycleRead(runtime.dataSource(), () -> {
            try (var connection = DriverManager.getConnection(adminUrl, adminUser, adminPassword);
                    var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE app.practical_attendance SET status='DECLINED' WHERE session_id='%s'"
                        .formatted(session));
                statement.executeUpdate("UPDATE app.course_session SET scheduled_at=scheduled_at+INTERVAL '1 day' WHERE id='%s'"
                        .formatted(session));
            } catch (SQLException error) {
                throw new AssertionError(error);
            }
        }));

        var snapshot = snapshotRepository.exportSnapshots(sessionToken, java.util.List.of(session)).getFirst();

        assertThat(snapshot.state()).isEqualTo("PRELIMINARY");
        assertThat(snapshot.revision()).isZero();
        assertThat(snapshot.scheduledAt()).isEqualTo(originalScheduledAt);
        assertThat(snapshot.requirements()).allSatisfy(requirement -> {
            assertThat(requirement.confirmedParticipants()).isEqualTo(1);
            assertThat(requirement.requiredQuantity()).isPositive();
        });
        assertThat(repository.requirements(sessionToken, session)).allSatisfy(requirement -> {
            assertThat(requirement.confirmedParticipants()).isZero();
            assertThat(requirement.requiredQuantity()).isZero();
        });
        assertThat(scheduledAt(session)).isEqualTo(originalScheduledAt.plusDays(1));
    }

    @Test
    void courseExportUsesTheCurrentAuthorizedSessionsInsteadOfAPreviouslyLoadedCourseList() throws Exception {
        var cancelled = fixture(0, true, true);
        var added = UUID.randomUUID();
        var course = courseId(cancelled);
        try (var connection = DriverManager.getConnection(adminUrl, adminUser, adminPassword);
                var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE app.course_session SET status='CANCELLED' WHERE id='%s'".formatted(cancelled));
            statement.executeUpdate("""
                    INSERT INTO app.course_session(id,course_id,sequence_number,scheduled_at,mode,status,
                        duration_minutes,venue,capacity,attendance_closes_at)
                    SELECT '%s',course_id,2,scheduled_at+INTERVAL '7 days','PRACTICAL','SCHEDULED',
                        duration_minutes,venue,capacity,attendance_closes_at+INTERVAL '7 days'
                    FROM app.course_session WHERE id='%s'
                    """.formatted(added, cancelled));
        }

        var export = repository.courseExport(sessionToken, course);

        assertThat(export.sessions()).extracting(value -> value.id()).contains(added).doesNotContain(cancelled);
        assertThat(export.snapshots()).extracting(value -> value.sessionId()).containsExactly(added);
        assertThat(export.snapshots().getFirst().requirements()).isEmpty();
    }

    @Test
    void closedAttendanceRetainsPendingAndDeclinedCountsWithoutChangingConfirmedRoster() throws Exception {
        var session = fixture(0, true, true);
        try (var connection = DriverManager.getConnection(adminUrl, adminUser, adminPassword);
                var statement = connection.createStatement()) {
            assertThat(statement.executeUpdate("""
                    INSERT INTO app.enrollment(participant_profile_id,course_id,status)
                    SELECT p.id,s.course_id,'ACTIVE'
                    FROM app.participant_profile p CROSS JOIN app.course_session s
                    WHERE s.id='%s' AND p.id<>'20000000-0000-0000-0000-000000000002'
                    ORDER BY p.id LIMIT 5
                    """.formatted(session))).isEqualTo(5);
            assertThat(statement.executeUpdate("""
                    INSERT INTO app.practical_attendance(enrollment_id,session_id,status)
                    SELECT e.id,s.id,'DECLINED'
                    FROM app.enrollment e JOIN app.course_session s ON s.course_id=e.course_id
                    WHERE s.id='%s' AND e.participant_profile_id<>'20000000-0000-0000-0000-000000000002'
                    ORDER BY e.id LIMIT 3
                    """.formatted(session))).isEqualTo(3);
            statement.executeUpdate("""
                    UPDATE app.course_session
                    SET attendance_closes_at=(current_timestamp AT TIME ZONE 'Europe/Rome')-INTERVAL '1 hour'
                    WHERE id='%s'
                    """.formatted(session));
        }
        var snapshot = repository.sessionPreparation(sessionToken, session);
        assertThat(snapshot.confirmedParticipants()).isEqualTo(1);
        assertThat(snapshot.pendingParticipants()).isEqualTo(2);
        assertThat(snapshot.declinedParticipants()).isEqualTo(3);
        assertThat(repository.participants(sessionToken, session)).hasSize(1);
        assertThat(snapshot.totals()).isEqualTo(repository.requirements(sessionToken, session));
        assertThat(snapshot.totals().stream().filter(i -> i.unit() == MeasurementUnit.GRAM)
                .findFirst().orElseThrow().requiredQuantity()).isEqualByComparingTo("50");
    }

    @Test
    void exportsUseOneQueryAndPreserveRequestedOrderIncludingDuplicatesAndEmptyMenus() throws Exception {
        var withMenu = fixture(0, true, true);
        var withoutMenu = fixture(0, false, false);
        var queryCount = new AtomicInteger();
        var measured = new JdbcChefPlanningRepository(countQueries(runtime.dataSource(), queryCount));

        var snapshots = measured.exportSnapshots(sessionToken, java.util.List.of(withoutMenu, withMenu, withoutMenu));

        assertThat(snapshots).extracting(value -> value.sessionId())
                .containsExactly(withoutMenu, withMenu, withoutMenu);
        assertThat(snapshots.getFirst().requirements()).isEmpty();
        assertThat(snapshots.get(1).requirements()).hasSize(3);
        assertThat(queryCount).hasValue(1);
        assertThatThrownBy(() -> measured.exportSnapshots(participantSessionToken, java.util.List.of(withMenu)))
                .isInstanceOf(ChefPlanningRepositoryException.class);
    }

    private static DataSource countQueries(DataSource delegate, AtomicInteger count) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class},
                (proxy, method, arguments) -> {
                    var result = call(method, delegate, arguments);
                    if (!method.getName().equals("getConnection")) return result;
                    return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                            (connection, operation, parameters) -> {
                                if (operation.getName().equals("prepareStatement")) count.incrementAndGet();
                                return call(operation, result, parameters);
                            });
                });
    }

    @Test
    void exportLatencyAndQueryCountStayBoundedAcrossTenTimesMoreSessions() throws Exception {
        var sessions = new java.util.ArrayList<UUID>();
        for (int index = 0; index < 10; index++) sessions.add(fixture(0, true, true));
        var queryCount = new AtomicInteger();
        var measured = new JdbcChefPlanningRepository(countQueries(runtime.dataSource(), queryCount));
        var normal = measureExports(measured, sessions.subList(0, 1), queryCount);
        var larger = measureExports(measured, sessions, queryCount);
        var report = String.format(java.util.Locale.ROOT, """
                {
                  "operation": "authorized session planning export",
                  "database": "PostgreSQL 18.4 Testcontainers",
                  "java": "%s",
                  "os": "%s",
                  "samplesPerDataset": 100,
                  "warmupPerDataset": 20,
                  "memory": "Used heap before and after sampling; affected by GC, not retained-memory evidence",
                  "normal": {"sessions": 1, "queriesPerOperation": 1, "measurement": %s},
                  "tenTimesLarger": {"sessions": 10, "queriesPerOperation": 1, "measurement": %s}
                }
                """, System.getProperty("java.version"), System.getProperty("os.name"), normal.json(), larger.json());
        java.nio.file.Files.writeString(Path.of("target", "planning-performance.json"), report);
    }

    private static Measurement measureExports(JdbcChefPlanningRepository measured, java.util.List<UUID> sessions,
            AtomicInteger queryCount) {
        for (int index = 0; index < 20; index++) measured.exportSnapshots(sessionToken, sessions);
        var times = new double[100];
        queryCount.set(0);
        long heapBefore = usedHeap();
        for (int index = 0; index < times.length; index++) {
            long startedAt = System.nanoTime();
            var snapshots = measured.exportSnapshots(sessionToken, sessions);
            times[index] = (System.nanoTime() - startedAt) / 1_000_000.0;
            assertThat(snapshots).hasSize(sessions.size());
            assertThat(snapshots).allSatisfy(snapshot -> assertThat(snapshot.requirements()).hasSize(3));
        }
        assertThat(queryCount).hasValue(times.length);
        java.util.Arrays.sort(times);
        return new Measurement(times[49], times[94], heapBefore, usedHeap());
    }

    @Test
    void monthlyReportLatencyUsesOneQueryAcrossTenTimesMoreCompletedSessions() throws Exception {
        var month = java.time.YearMonth.now(java.time.ZoneId.of("Europe/Rome")).minusMonths(1);
        var chef = runtime.accountLifecycle().registerChef("report.benchmark", "Report benchmark",
                "ReportBenchmark!2026".toCharArray());
        addCompletedSessions(month, chef.profileId(), 0, 10);
        var queryCount = new AtomicInteger();
        var measured = new JdbcChefPlanningRepository(countQueries(runtime.dataSource(), queryCount));
        var normal = measureReports(measured, chef.sessionToken(), month, queryCount, 10);
        addCompletedSessions(month, chef.profileId(), 10, 100);
        var larger = measureReports(measured, chef.sessionToken(), month, queryCount, 100);
        var report = String.format(java.util.Locale.ROOT, """
                {
                  "operation": "authorized monthly report",
                  "database": "PostgreSQL 18.4 Testcontainers",
                  "java": "%s",
                  "os": "%s",
                  "samplesPerDataset": 100,
                  "warmupPerDataset": 20,
                  "memory": "Used heap before and after sampling; affected by GC, not retained-memory evidence",
                  "normal": {"completedSessions": 10, "queriesPerOperation": 1, "measurement": %s},
                  "tenTimesLarger": {"completedSessions": 100, "queriesPerOperation": 1, "measurement": %s}
                }
                """, System.getProperty("java.version"), System.getProperty("os.name"), normal.json(), larger.json());
        java.nio.file.Files.writeString(Path.of("target", "monthly-report-performance.json"), report);
    }

    private static void addCompletedSessions(java.time.YearMonth month, UUID chefId, int first, int end) throws Exception {
        for (int index = first; index < end; index++) {
            var session = fixture(0, false, true, "1", chefId);
            var scheduledAt = month.atDay(1).atStartOfDay().plusHours(index * 3L);
            try (var connection = DriverManager.getConnection(adminUrl, adminUser, adminPassword);
                    var statement = connection.prepareStatement("""
                            WITH rescheduled AS (
                                UPDATE app.course_session SET scheduled_at=?, attendance_closes_at=? WHERE id=?
                                RETURNING course_id
                            )
                            UPDATE app.course SET start_date=?, start_time=?
                              FROM rescheduled WHERE app.course.id=rescheduled.course_id
                            """)) {
                statement.setObject(1, scheduledAt);
                statement.setObject(2, scheduledAt.minusMinutes(1));
                statement.setObject(3, session);
                statement.setObject(4, scheduledAt.toLocalDate());
                statement.setObject(5, scheduledAt.toLocalTime());
                statement.executeUpdate();
            }
        }
    }

    private static Measurement measureReports(JdbcChefPlanningRepository measured, UUID reportSessionToken, java.time.YearMonth month,
            AtomicInteger queryCount, int addedSessions) {
        for (int index = 0; index < 20; index++) measured.monthlyReport(reportSessionToken, month);
        var times = new double[100];
        queryCount.set(0);
        long heapBefore = usedHeap();
        for (int index = 0; index < times.length; index++) {
            long startedAt = System.nanoTime();
            var report = measured.monthlyReport(reportSessionToken, month);
            times[index] = (System.nanoTime() - startedAt) / 1_000_000.0;
            assertThat(report.practicalSessions()).isEqualTo(addedSessions);
            assertThat(report.maximumRecipes()).contains(2);
        }
        assertThat(queryCount).hasValue(times.length);
        java.util.Arrays.sort(times);
        return new Measurement(times[49], times[94], heapBefore, usedHeap());
    }

    private static long usedHeap() {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    private record Measurement(double p50Ms, double p95Ms, long heapBefore, long heapAfter) {
        String json() {
            return String.format(java.util.Locale.ROOT,
                    "{\"p50Ms\": %.3f, \"p95Ms\": %.3f, \"heapBefore\": %d, \"heapAfter\": %d}",
                    p50Ms, p95Ms, heapBefore, heapAfter);
        }
    }

    private static UUID fixture(int margin, boolean confirmed, boolean menu) throws Exception {
        return fixture(margin, confirmed, menu, "1");
    }

    private static UUID fixture(int margin, boolean confirmed, boolean menu, String teaspoonDose) throws Exception {
        return fixture(margin, confirmed, menu, teaspoonDose,
                UUID.fromString("20000000-0000-0000-0000-000000000001"));
    }

    private static UUID fixture(int margin, boolean confirmed, boolean menu, String teaspoonDose, UUID chefId) throws Exception {
        var course = UUID.randomUUID();
        var session = UUID.randomUUID();
        var enrollment = UUID.randomUUID();
        var fixtureSlot = FIXTURE_SEQUENCE.getAndIncrement();
        try (var connection = DriverManager.getConnection(adminUrl, adminUser, adminPassword);
                var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app.course(id,chef_profile_id,category_id,title,description,start_date,
                        start_time,recurrence_days,session_count,status,ingredient_margin)
                    VALUES ('%s','%s',
                        '30000000-0000-0000-0000-000000000001','Snapshot %s','Test',
                        current_date+60,'18:00',7,1,'PUBLISHED',%s)
                    """.formatted(course, chefId, course.toString().substring(0, 16), margin));
            statement.execute("""
                    INSERT INTO app.course_session(id,course_id,sequence_number,scheduled_at,mode,
                        status,duration_minutes,venue,capacity,attendance_closes_at)
                    VALUES ('%s','%s',1,current_date+60+TIME '18:00'
                        + (%d * INTERVAL '3 hours'),'PRACTICAL','SCHEDULED',
                        90,'Test laboratory',12,current_date+58+TIME '18:00'
                        + (%d * INTERVAL '3 hours'))
                    """.formatted(session, course, fixtureSlot, fixtureSlot));
            if (menu) {
                var firstRecipe = UUID.randomUUID();
                var secondRecipe = UUID.randomUUID();
                statement.execute("""
                        INSERT INTO app.recipe(id,category_id,name,description,base_servings) VALUES
                        ('%s','30000000-0000-0000-0000-000000000001','A %s','Test',1),
                        ('%s','30000000-0000-0000-0000-000000000001','B %s','Test',1)
                        """.formatted(firstRecipe, firstRecipe, secondRecipe, secondRecipe));
                for (var unit : java.util.List.of("GRAM", "MILLILITER", "TEASPOON")) {
                    var ingredient = UUID.randomUUID();
                    var quantity = unit.equals("GRAM") ? "25" : unit.equals("MILLILITER") ? "125" : teaspoonDose;
                    statement.execute("INSERT INTO app.ingredient(id,name,unit) VALUES ('%s','%s %s','%s')"
                            .formatted(ingredient, unit, ingredient, unit));
                    statement.execute("""
                            INSERT INTO app.recipe_ingredient(recipe_id,ingredient_id,quantity) VALUES
                            ('%s','%s',%s),('%s','%s',%s)
                            """.formatted(firstRecipe, ingredient, quantity, secondRecipe, ingredient, quantity));
                }
                statement.execute("""
                        INSERT INTO app.session_recipe(session_id,recipe_id,portions_per_participant)
                        VALUES ('%s','%s',1),('%s','%s',1)
                        """.formatted(session, firstRecipe, session, secondRecipe));
            }
            if (confirmed) {
                statement.execute("""
                        INSERT INTO app.enrollment(id,participant_profile_id,course_id,status)
                        VALUES ('%s','20000000-0000-0000-0000-000000000002','%s','ACTIVE')
                        """.formatted(enrollment, course));
                statement.execute("INSERT INTO app.practical_attendance(enrollment_id,session_id,status)"
                        + " VALUES ('%s','%s','CONFIRMED')".formatted(enrollment, session));
            }
        }
        return session;
    }

    private static java.time.LocalDateTime scheduledAt(UUID session) throws SQLException {
        try (var connection = DriverManager.getConnection(adminUrl, adminUser, adminPassword);
                var statement = connection.prepareStatement("SELECT scheduled_at FROM app.course_session WHERE id=?")) {
            statement.setObject(1, session);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getObject(1, java.time.LocalDateTime.class);
            }
        }
    }

    private static UUID courseId(UUID session) throws SQLException {
        try (var connection = DriverManager.getConnection(adminUrl, adminUser, adminPassword);
                var statement = connection.prepareStatement("SELECT course_id FROM app.course_session WHERE id=?")) {
            statement.setObject(1, session);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getObject(1, UUID.class);
            }
        }
    }

    private static DataSource afterLifecycleRead(DataSource delegate, Runnable action) {
        var fired = new java.util.concurrent.atomic.AtomicBoolean();
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getConnection") && (arguments == null || arguments.length == 0)) {
                        return lifecycleAwareConnection((Connection) call(method, delegate, arguments), action, fired);
                    }
                    return call(method, delegate, arguments);
                });
    }

    private static Connection lifecycleAwareConnection(Connection delegate, Runnable action,
            java.util.concurrent.atomic.AtomicBoolean fired) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> {
                    var result = call(method, delegate, arguments);
                    if (method.getName().equals("prepareStatement") && arguments != null && arguments.length > 0
                            && arguments[0] instanceof String sql && sql.contains("session_planning_export_context")) {
                        return lifecycleAwareStatement((PreparedStatement) result, action, fired);
                    }
                    return result;
                });
    }

    private static PreparedStatement lifecycleAwareStatement(PreparedStatement delegate, Runnable action,
            java.util.concurrent.atomic.AtomicBoolean fired) {
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (proxy, method, arguments) -> {
                    var result = call(method, delegate, arguments);
                    if (method.getName().equals("executeQuery") && fired.compareAndSet(false, true)) action.run();
                    return result;
                });
    }

    private static Object call(java.lang.reflect.Method method, Object target, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }
}
