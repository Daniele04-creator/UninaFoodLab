package it.uninafoodlab.infrastructure.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class SessionLifecycleIT {
    @Container private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");
    private static ApplicationRuntime runtime;
    private static UUID token;
    private static final java.util.concurrent.atomic.AtomicInteger FIXTURE_DAY = new java.util.concurrent.atomic.AtomicInteger(200);

    @BeforeAll
    static void start() throws Exception {
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(
                Path.of("database/bootstrap/001__create_roles.sql")), "/tmp/roles.sql");
        var result = POSTGRES.execInContainer("psql", "--username=" + POSTGRES.getUsername(),
                "--dbname=" + POSTGRES.getDatabaseName(), "--set=ON_ERROR_STOP=1",
                "--set=migrator_password=lifecycle-migrator-only", "--set=app_password=lifecycle-app-only",
                "--file=/tmp/roles.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        runtime = it.uninafoodlab.infrastructure.support.DemoDatabaseFixture.start(new ApplicationConfig(POSTGRES.getJdbcUrl(), "uninafoodlab_app",
                "lifecycle-app-only", "uninafoodlab_migrator", "lifecycle-migrator-only", "test"));
        token = runtime.authenticateUser().execute("marta.rossi", "MartaRossi!2026".toCharArray()).sessionToken();
    }

    @AfterAll
    static void stop() { if (runtime != null) runtime.close(); }

    @Test
    void assignedDosesStayStableUntilExplicitRefreshAndQuantitiesFollowResponses() throws Exception {
        var lesson = fixture(false);
        try (var admin = admin(); var app = runtime.dataSource().getConnection()) {
            app.setAutoCommit(true);
            execute(admin, "UPDATE app.recipe_ingredient SET quantity=75 WHERE recipe_id=?", lesson.recipe());
            assertThat(amount(app, lesson.session())).isEqualByComparingTo("50");
            execute(app, "SELECT app.refresh_session_menu(?,?)", token, lesson.session());
            assertThat(amount(app, lesson.session())).isEqualByComparingTo("75");
            execute(admin, "UPDATE app.practical_attendance SET status='DECLINED' WHERE session_id=?", lesson.session());
            assertThat(amount(app, lesson.session())).isEqualByComparingTo("0");
        }
    }

    @Test
    void elapsedSessionIsCompletedAcrossChefParticipantLifecycleAndReportWithoutWrites() throws Exception {
        var lesson = fixture(true);
        try (var app = runtime.dataSource().getConnection(); var admin = admin()) {
            app.setAutoCommit(true);
            assertThat(state(app, lesson.session())).isEqualTo("COMPLETED");
            UUID course = courseOf(lesson.session());
            var participant = runtime.authenticateUser().execute("luca.bianchi", "LucaBianchi!2026".toCharArray()).sessionToken();
            for (String api : new String[]{"list_course_sessions", "list_published_course_sessions"}) {
                try (var statement = app.prepareStatement("SELECT status FROM app." + api + "(?,?) WHERE session_id=?")) {
                    statement.setObject(1, api.startsWith("list_published") ? participant : token);
                    statement.setObject(2, course); statement.setObject(3, lesson.session());
                    try (var rows = statement.executeQuery()) {
                        assertThat(rows.next()).as(api).isTrue();
                        assertThat(rows.getString(1)).as(api).isEqualTo("COMPLETED");
                    }
                }
            }
            try (var statement = app.prepareStatement("SELECT completed_at=scheduled_at+make_interval(mins=>duration_minutes) FROM app.list_course_sessions(?,?) WHERE session_id=?")) {
                statement.setObject(1, token); statement.setObject(2, course); statement.setObject(3, lesson.session());
                try (var rows = statement.executeQuery()) { assertThat(rows.next()).isTrue(); assertThat(rows.getBoolean(1)).isTrue(); }
            }
            assertReport(app, lesson.session(), "COMPLETED", 1);
            try (var statement = admin.prepareStatement("SELECT s.status,s.completed_at, o.session_id FROM app.course_session s LEFT JOIN app.session_outcome o ON o.session_id=s.id WHERE s.id=?")) {
                statement.setObject(1, lesson.session());
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("SCHEDULED");
                    assertThat(rows.getObject(2)).isNull(); assertThat(rows.getObject(3)).isNull();
                }
            }
        }
    }

    @Test
    void historicalOutcomesArePreservedButReportUsesAssignedMenu() throws Exception {
        var lesson = fixture(true);
        try (var app = runtime.dataSource().getConnection(); var admin = admin()) {
            app.setAutoCommit(true);
            execute(admin, "INSERT INTO app.session_outcome(session_id,recipe_ids,enrollment_ids,note) VALUES(?, '{}', '{}', 'Historical note')", lesson.session());
            assertReport(app, lesson.session(), "COMPLETED", 1);
            try (var statement=admin.prepareStatement("SELECT note,cardinality(recipe_ids) FROM app.session_outcome WHERE session_id=?")) {
                statement.setObject(1,lesson.session());
                try(var rows=statement.executeQuery()) { assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("Historical note"); assertThat(rows.getInt(2)).isZero(); }
            }
        }
    }

    @Test
    void onlineAndEmptyPracticalSessionsCompleteAutomaticallyButFutureAndCancelledDoNot() throws Exception {
        var future=fixture(false); var online=fixture(true); var empty=fixture(true); var cancelled=fixture(true);
        try (var app=runtime.dataSource().getConnection(); var admin=admin()) {
            app.setAutoCommit(true);
            execute(admin,"DELETE FROM app.session_recipe WHERE session_id=?",online.session());
            execute(admin,"UPDATE app.course_session SET mode='ONLINE',venue=NULL,capacity=NULL,online_url='https://example.org/lesson' WHERE id=?",online.session());
            execute(admin,"DELETE FROM app.session_recipe WHERE session_id=?",empty.session());
            execute(admin,"UPDATE app.course_session SET status='CANCELLED' WHERE id=?",cancelled.session());
            assertThat(state(app,future.session())).isEqualTo("PRELIMINARY");
            assertThat(state(app,cancelled.session())).isEqualTo("CANCELLED");
            assertThat(state(app,online.session())).isEqualTo("COMPLETED");
            assertThat(state(app,empty.session())).isEqualTo("COMPLETED");
            assertReport(app,online.session(),"COMPLETED",0);
            assertReport(app,empty.session(),"COMPLETED",0);
            assertReport(app,cancelled.session(),"CANCELLED",null);
            assertThatThrownBy(()->execute(app,"SELECT * FROM app.session_lifecycle(?,?)",UUID.randomUUID(),empty.session())).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->execute(app,"SELECT * FROM app.chef_monthly_report(?,2026,9)",UUID.randomUUID())).isInstanceOf(SQLException.class);
            var otherAccount=UUID.randomUUID();
            execute(admin,"INSERT INTO app.account(id,username,password_hash,role) SELECT ?,?,password_hash,'CHEF' FROM app.account WHERE username='marta.rossi'",otherAccount,"chef.automatic");
            execute(admin,"INSERT INTO app.chef_profile(id,account_id,display_name) VALUES(?,?,'Altro chef')",UUID.randomUUID(),otherAccount);
            var otherToken=runtime.authenticateUser().execute("chef.automatic","MartaRossi!2026".toCharArray()).sessionToken();
            assertThatThrownBy(()->execute(app,"SELECT * FROM app.session_lifecycle(?,?)",otherToken,empty.session())).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->execute(app,"SELECT * FROM app.session_menu_snapshot")).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void completionBoundaryIncludesExactEndAndCancellationAlwaysWins() throws Exception {
        try (var admin=admin(); var statement=admin.createStatement(); var rows=statement.executeQuery("""
                SELECT app.effective_session_status('SCHEDULED',timestamp '2026-09-25 10:00',60,timestamp '2026-09-25 10:59:59.999999'),
                       app.effective_session_status('SCHEDULED',timestamp '2026-09-25 10:00',60,timestamp '2026-09-25 11:00'),
                       app.effective_session_status('CANCELLED',timestamp '2026-09-25 10:00',60,timestamp '2026-09-25 12:00'),
                       app.effective_session_status('COMPLETED',timestamp '2026-09-25 10:00',60,timestamp '2026-09-25 09:00')
                """)) {
            assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("SCHEDULED");
            assertThat(rows.getString(2)).isEqualTo("COMPLETED"); assertThat(rows.getString(3)).isEqualTo("CANCELLED");
            assertThat(rows.getString(4)).isEqualTo("SCHEDULED");
        }
    }

    @Test
    void completedSessionsCannotBeCancelledButFutureSessionsCan() throws Exception {
        var past=fixture(true); var future=fixture(false);
        try(var app=runtime.dataSource().getConnection()) {
            app.setAutoCommit(true);
            assertThatThrownBy(()->execute(app,"SELECT app.cancel_course_session(?,?,?,?)",token,past.session(),"Annullamento","Sessione annullata"))
                    .isInstanceOfSatisfying(SQLException.class,error->assertThat(error.getSQLState()).isEqualTo("55000"));
            assertThat(state(app,past.session())).isEqualTo("COMPLETED");
            execute(app,"SELECT app.cancel_course_session(?,?,?,?)",token,future.session(),"Annullamento","Sessione annullata");
            assertThat(state(app,future.session())).isEqualTo("CANCELLED");
        }
    }

    private static void assertReport(Connection app, UUID session, String expectedState, Integer recipes) throws SQLException {
        var month=java.time.YearMonth.from(java.time.LocalDate.now(java.time.ZoneId.of("Europe/Rome")).minusDays(5));
        try(var statement=app.prepareStatement("SELECT state,recipes,attendees FROM app.chef_monthly_report_details(?,?,?) WHERE session_id=?")) {
            statement.setObject(1,token); statement.setInt(2,month.getYear()); statement.setInt(3,month.getMonthValue()); statement.setObject(4,session);
            try(var rows=statement.executeQuery()) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo(expectedState);
                assertThat(rows.getObject(2)).isEqualTo(recipes); assertThat(rows.getObject(3)).isNull();
            }
        }
    }

    @Test
    void removedWorkflowIsNotCallableByApplication() throws Exception {
        try (var app = runtime.dataSource().getConnection(); var statement=app.createStatement(); var rows=statement.executeQuery("""
                SELECT to_regprocedure('app.consolidate_session_preparation(uuid,uuid,text)'),
                       to_regprocedure('app.reopen_session_preparation(uuid,uuid,timestamp without time zone,text)'),
                       to_regprocedure('app.session_revision_history(uuid,uuid)'),
                       to_regprocedure('app.record_session_outcome(uuid,uuid,uuid[],uuid[],text)'),
                       to_regprocedure('app.record_session_outcome(uuid,uuid,uuid[],text)'),
                       to_regprocedure('app.session_outcome_choices(uuid,uuid)'),
                       to_regprocedure('app.session_recorded_outcome(uuid,uuid)')
                """)) {
            assertThat(rows.next()).isTrue();
            for (int i=1;i<=7;i++) assertThat(rows.getObject(i)).isNull();
        }
    }

    private static String state(Connection connection, UUID session) throws SQLException {
        try (var statement=connection.prepareStatement("SELECT state FROM app.session_lifecycle(?,?)")) {
            statement.setObject(1,token); statement.setObject(2,session);
            try(var rows=statement.executeQuery()){ assertThat(rows.next()).isTrue(); return rows.getString(1); }
        }
    }

    private static java.math.BigDecimal amount(Connection app, UUID session) throws SQLException {
        try (var statement = app.prepareStatement("SELECT sum(required_quantity) FROM app.list_session_ingredient_requirements(?,?)")) {
            statement.setObject(1, token); statement.setObject(2, session);
            try (var rows = statement.executeQuery()) { rows.next(); return rows.getBigDecimal(1); }
        }
    }

    private static UUID courseOf(UUID session) throws SQLException {
        try (var connection = admin(); var statement = connection.prepareStatement(
                "SELECT course_id FROM app.course_session WHERE id=?")) {
            statement.setObject(1, session);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getObject(1, UUID.class);
            }
        }
    }

    private static Lesson fixture(boolean past) throws SQLException {
        return fixture(past, true);
    }

    private static Lesson fixture(boolean past, boolean confirmed) throws SQLException {
        var course = UUID.randomUUID(); var session = UUID.randomUUID();
        var recipe = UUID.randomUUID(); var enrollment = UUID.randomUUID();
        int day = FIXTURE_DAY.addAndGet(3);
        try (var connection = admin()) {
            execute(connection, """
                    INSERT INTO app.recipe(id,category_id,name,description,base_servings)
                    SELECT ?,id,?,'Preparazione sintetica',1 FROM app.category ORDER BY id LIMIT 1
                    """, recipe, "Ricetta " + recipe);
            execute(connection, """
                    INSERT INTO app.recipe_ingredient(recipe_id,ingredient_id,quantity)
                    SELECT ?,id,50 FROM app.ingredient ORDER BY id LIMIT 1
                    """, recipe);
            execute(connection, """
                    INSERT INTO app.course(id,chef_profile_id,category_id,title,description,start_date,start_time,recurrence_days,session_count,status)
                    SELECT ?,cp.id,c.id,?,'Prova ciclo',current_date+20,'12:00',7,2,'PUBLISHED'
                    FROM app.chef_profile cp JOIN app.account a ON a.id=cp.account_id CROSS JOIN app.category c
                    WHERE a.username='marta.rossi' ORDER BY c.id LIMIT 1
                    """, course, "Ciclo " + course.toString().substring(0, 16));
            execute(connection, """
                    INSERT INTO app.course_session(id,course_id,sequence_number,scheduled_at,attendance_closes_at,mode,status,duration_minutes,venue,capacity)
                    VALUES(?,?,1,(current_timestamp AT TIME ZONE 'Europe/Rome') + (? * interval '1 day'),
                    (current_timestamp AT TIME ZONE 'Europe/Rome') + (? * interval '1 day'),'PRACTICAL','SCHEDULED',60,'Laboratorio prova',10)
                    """, session, course, day, day - 1);
            execute(connection, "INSERT INTO app.session_recipe(session_id,recipe_id) VALUES(?,?)", session, recipe);
            execute(connection, """
                    INSERT INTO app.enrollment(id,participant_profile_id,course_id,status)
                    SELECT ?,p.id,?,'ACTIVE' FROM app.participant_profile p JOIN app.account a ON a.id=p.account_id
                    WHERE a.username='luca.bianchi'
                    """, enrollment, course);
            if (confirmed) {

                execute(connection, "INSERT INTO app.practical_attendance(enrollment_id,session_id,status) VALUES(?,?,'CONFIRMED')",
                        enrollment, session);
            }
            if (past) {
                execute(connection, "UPDATE app.course_session SET scheduled_at=(current_timestamp AT TIME ZONE 'Europe/Rome')::date-interval '5 days'+(? * interval '25 minutes'), attendance_closes_at=(current_timestamp AT TIME ZONE 'Europe/Rome')::date-interval '6 days' WHERE id=?", day - 200, session);
            }
        }
        return new Lesson(session, recipe, enrollment);
    }

    private static Connection admin() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (var index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            statement.execute();
        }
    }

    private record Lesson(UUID session, UUID recipe, UUID enrollment) {}
}
