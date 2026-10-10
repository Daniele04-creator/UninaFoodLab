package it.uninafoodlab.infrastructure.database;

import it.uninafoodlab.infrastructure.support.DatabaseActors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class DatabaseFoundationIT {

    private static final String MIGRATOR_USER = "uninafoodlab_migrator";
    private static final String MIGRATOR_PASSWORD = "integration-migrator-only";
    private static final String APP_USER = "uninafoodlab_app";
    private static final String APP_PASSWORD = "integration-app-only";
    private static ApplicationRuntime applicationRuntime;

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4");

    @BeforeAll
    static void bootstrapAndMigrate() throws Exception {
        var bootstrapScript = Path.of("database", "bootstrap", "001__create_roles.sql");
        POSTGRES.copyFileToContainer(
                MountableFile.forHostPath(bootstrapScript), "/tmp/001__create_roles.sql");

        var result = POSTGRES.execInContainer(
                "psql",
                "--username=" + POSTGRES.getUsername(),
                "--dbname=" + POSTGRES.getDatabaseName(),
                "--set=ON_ERROR_STOP=1",
                "--set=migrator_password=" + MIGRATOR_PASSWORD,
                "--set=app_password=" + APP_PASSWORD,
                "--file=/tmp/001__create_roles.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();

        applicationRuntime = it.uninafoodlab.infrastructure.support.DemoDatabaseFixture.start(new ApplicationConfig(
                POSTGRES.getJdbcUrl(),
                APP_USER,
                APP_PASSWORD,
                MIGRATOR_USER,
                MIGRATOR_PASSWORD,
                "test"));
    }

    @AfterAll
    static void stopApplicationRuntime() {
        applicationRuntime.close();
    }

    @Test
    void courseTitlesRejectEveryLineSeparatorAndAcceptASingleLine() throws SQLException {
        try (var connection = migratorConnection(); var statement = connection.prepareStatement("""
                insert into app.course(chef_profile_id,category_id,title,description,
                    start_date,start_time,recurrence_days,session_count)
                values ('20000000-0000-0000-0000-000000000001',
                    '30000000-0000-0000-0000-000000000001',?,'Constraint boundary',
                    current_date + 500,time '08:00',7,1)
                """)) {
            connection.setAutoCommit(false);
            try {
                for (int separator : new int[]{13, 10, 11, 12, 133, 8232, 8233}) {
                    var savepoint = connection.setSavepoint();
                    statement.setString(1, "Valid" + Character.toString(separator) + "title");
                    assertThatThrownBy(statement::executeUpdate)
                            .as("Line separator U+%04X must be rejected", separator)
                            .isInstanceOf(SQLException.class)
                            .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
                    connection.rollback(savepoint);
                }
                statement.setString(1, "Valid single line");
                assertThat(statement.executeUpdate()).isOne();
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void applicationRoleCanReadFoundationMetadata() throws SQLException {
        try (var connection = applicationRuntime.dataSource().getConnection()) {
            assertThat(queryForString(
                            connection,
                            "select component from app.schema_metadata "
                                    + "where component = 'foundation'"))
                    .isEqualTo("foundation");
        }
    }

    @Test
    void applicationRoleCannotCreateTables() throws SQLException {
        try (var connection = applicationRuntime.dataSource().getConnection()) {
            assertThatThrownBy(() -> execute(connection, "create table app.forbidden_ddl(id integer)"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("42501"));
        }
    }

    @Test
    void applicationRoleCannotCreateTemporaryTables() throws SQLException {
        try (var connection = applicationRuntime.dataSource().getConnection()) {
            assertThatThrownBy(() -> execute(connection, "create temporary table forbidden_temp(id integer)"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("42501"));
        }
    }

    @Test
    void securityDefinerFunctionsSearchTemporarySchemaLast() throws SQLException {
        try (var connection = migratorConnection()) {
            assertThat(queryForInt(
                            connection,
                            "select count(*) from pg_catalog.pg_proc function "
                                    + "join pg_catalog.pg_namespace namespace "
                                    + "on namespace.oid = function.pronamespace "
                                    + "where namespace.nspname = 'app' "
                                    + "and function.prosecdef "
                                    + "and not coalesce(function.proconfig, '{}'::text[]) "
                                    + "@> array['search_path=pg_catalog, app, pg_temp']"))
                    .isZero();
        }
    }

    @Test
    void seedsRealisticSyntheticLoginIdentityAndCulinaryCatalog() throws SQLException {
        assertThat(applicationRuntime.authenticateUser()
                        .execute("marta.rossi", "MartaRossi!2026".toCharArray())
                        .username()
                        .value())
                .isEqualTo("marta.rossi");
        assertThat(DatabaseActors.authenticate(applicationRuntime, "luca.bianchi", "LucaBianchi!2026".toCharArray())
                        .username()
                        .value())
                .isEqualTo("luca.bianchi");

        try (var connection = migratorConnection()) {
            assertThat(queryForInt(connection, "select count(*) from app.category"))
                    .isEqualTo(6);
            assertThat(queryForInt(connection, "select count(*) from app.recipe"))
                    .isEqualTo(9);
            assertThat(queryForInt(connection, "select count(*) from app.ingredient"))
                    .isEqualTo(21);
            assertThat(queryForInt(connection, "select count(*) from app.account where active"))
                    .isEqualTo(2);
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.account "
                                    + "where password_hash not like '$pbkdf2-sha256$600000$%' "
                                    + "or password_hash like '%Demo!2026%'"))
                    .isZero();
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.course "
                                    + "where title = 'Fermentazioni contemporanee' "
                                    + "and status = 'PUBLISHED'"))
                    .isEqualTo(1);
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.notification_recipient"))
                    .isEqualTo(16);
        }
    }

    @Test
    void closedPracticalSessionExposesOnlyConfirmedParticipantsAndRequirements() throws SQLException {
        var chef = applicationRuntime.authenticateUser()
                .execute("marta.rossi", "MartaRossi!2026".toCharArray());
        try (var connection = applicationRuntime.dataSource().getConnection();
                var roster = connection.prepareStatement(
                        "select attendance_status from app.list_session_participants(?, ?)");
                var requirements = connection.prepareStatement(
                        "select confirmed_participants from app.list_session_ingredient_requirements(?, ?)")) {
            var sessionId = java.util.UUID.fromString("b3160000-0000-0000-0000-000000000002");
            roster.setObject(1, chef.sessionToken());
            roster.setObject(2, sessionId);
            try (var result = roster.executeQuery()) {
                var count = 0;
                while (result.next()) {
                    assertThat(result.getString("attendance_status")).isEqualTo("CONFIRMED");
                    count++;
                }
                assertThat(count).isEqualTo(12);
            }
            requirements.setObject(1, chef.sessionToken());
            requirements.setObject(2, sessionId);
            try (var result = requirements.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("confirmed_participants")).isEqualTo(12);
            }
        }
    }

    @Test
    void seedsPortfolioCatalogAndIdentity() throws SQLException {
        try (var connection = migratorConnection()) {
            assertThat(queryForInt(connection, "select count(*) from app.category"))
                    .isEqualTo(6);
            assertThat(queryForInt(connection, "select count(*) from app.ingredient"))
                    .isEqualTo(21);
            assertThat(queryForInt(connection, "select count(*) from app.recipe"))
                    .isEqualTo(9);
            assertThat(queryForInt(connection, "select count(*) from app.account"))
                    .isEqualTo(15);
            assertThat(queryForInt(connection, "select count(*) from app.account where active"))
                    .isEqualTo(2);
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.account "
                                    + "where username in ('portfolio.guest.one', 'portfolio.guest.two', 'portfolio.guest.three') "
                                    + "and not active"))
                    .isEqualTo(3);
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.account "
                                    + "where username like 'planning.guest.%' and not active"))
                    .isEqualTo(10);
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.participant_profile profile "
                                    + "join app.account account on account.id = profile.account_id "
                                    + "where account.username in ('portfolio.guest.one', 'portfolio.guest.two', 'portfolio.guest.three') "
                                    + "and not account.active and profile.display_name is not null"))
                    .isEqualTo(3);

        }
    }

    @Test
    void seedsPortfolioCourseStateAndParticipationMatrix() throws SQLException {
        try (var connection = migratorConnection()) {
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.course where status = 'DRAFT'"))
                    .isZero();
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.course where status = 'PUBLISHED'"))
                    .isEqualTo(3);
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.course where status = 'COMPLETED'"))
                    .isZero();
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.course where status = 'CANCELLED'"))
                    .isZero();

            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.course_session session "
                                    + "join app.course course on course.id = session.course_id "
                                    + "where course.title = 'Fermentazioni contemporanee' "
                                    + "and session.mode = 'PRACTICAL' "
                                    + "and session.capacity = 14"))
                    .isEqualTo(2);
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.enrollment enrollment "
                                    + "join app.course course on course.id = enrollment.course_id "
                                    + "join app.participant_profile profile on profile.id = enrollment.participant_profile_id "
                                    + "join app.account account on account.id = profile.account_id "
                                    + "where account.username = 'luca.bianchi' "
                                    + "and course.title = 'Fermentazioni contemporanee'"))
                    .isEqualTo(1);
            assertThat(queryForInt(connection, "select count(*) from app.enrollment"))
                    .isEqualTo(14);
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.practical_attendance where status = 'CONFIRMED'"))
                    .isEqualTo(12);
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.practical_attendance where status = 'DECLINED'"))
                    .isZero();
        }
    }

    @Test
    void seedsApprovedChefDesktopScenario() throws SQLException {
        try (var connection = migratorConnection()) {
            assertThat(queryForString(
                            connection,
                            "select display_name from app.chef_profile "
                                    + "where id = '20000000-0000-0000-0000-000000000001'"))
                    .isEqualTo("Marta Rossi");
            assertThat(queryForString(
                            connection,
                            "select string_agg(title || ':' || status || ':' || session_count, "
                                    + "'|' order by start_date, title) from app.course "
                                    + "where chef_profile_id = '20000000-0000-0000-0000-000000000001'"))
                    .isEqualTo("Fermentazioni contemporanee:PUBLISHED:4|"
                            + "Pasta fresca regionale:PUBLISHED:3|"
                            + "Cucina vegetale creativa:PUBLISHED:5");
            assertThat(queryForString(
                            connection,
                            "select string_agg(profile.display_name, '|' order by profile.display_name) "
                                    + "from app.enrollment enrollment "
                                    + "join app.participant_profile profile "
                                    + "on profile.id = enrollment.participant_profile_id "
                                    + "join app.course course on course.id = enrollment.course_id "
                                    + "where course.title = 'Fermentazioni contemporanee' "
                                    + "and enrollment.status = 'ACTIVE'"))
                    .isEqualTo("Alessandro Greco|Beatrice Rinaldi|Chiara Conti|Davide Moretti|"
                            + "Elena Ferri|Federica Gallo|Giulia Romano|Lorenzo Marino|Luca Bianchi|"
                            + "Marco De Luca|Matteo Ricci|Sara Esposito|Simone Caruso|Valentina Serra");
            assertThat(queryForString(
                            connection,
                            "select count(*) filter (where attendance.status = 'CONFIRMED') || '|' || "
                                    + "count(*) filter (where attendance.status is null) "
                                    + "from app.enrollment enrollment "
                                    + "join app.course course on course.id = enrollment.course_id "
                                    + "left join app.practical_attendance attendance "
                                    + "on attendance.enrollment_id = enrollment.id "
                                    + "and attendance.session_id = "
                                    + "'b3160000-0000-0000-0000-000000000002' "
                                    + "where course.title = 'Fermentazioni contemporanee' "
                                    + "and enrollment.status = 'ACTIVE'"))
                    .isEqualTo("12|2");
            assertThat(queryForString(
                            connection,
                            "select string_agg(title, '|' order by created_at desc) "
                                    + "from app.notification "
                                    + "where chef_profile_id = '20000000-0000-0000-0000-000000000001'"))
                    .isEqualTo("Materiali per la sessione pratica|Cambio aula — Pasta fresca regionale|"
                            + "Promemoria adesioni|Ricetta disponibile");
        }
    }

    @Test
    void keepsVisibleCourseAndOnlineSessionCopyCommercial() throws SQLException {
        try (var connection = migratorConnection()) {
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.course "
                                    + "where title ~* 'portfolio|dimostrativ|esame|universit|accademic|stack' "
                                    + "or description ~* 'portfolio|dimostrativ|esame|universit|accademic|stack'"))
                    .isZero();
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.course_session "
                                    + "where online_url ~* 'portfolio|dimostrativ|esame|universit|accademic|stack'"))
                    .isZero();
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.course_session "
                                    + "where online_url is not null and online_url !~ '^https://'"))
                    .isZero();
        }
    }

    @Test
    void seedsApprovedCommunicationArchiveAndCleanAuditHistory() throws SQLException {
        try (var connection = migratorConnection()) {
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.notification "
                                    + "where chef_profile_id = '20000000-0000-0000-0000-000000000001'"))
                    .isEqualTo(4);
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.notification "
                                    + "where created_at >= timestamptz '2026-08-12 00:00:00+02' "
                                    + "and created_at < timestamptz '2026-08-18 00:00:00+02'"))
                    .isEqualTo(4);
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.notification_recipient where read_at is null"))
                    .isEqualTo(16);
            assertThat(queryForInt(
                            connection,
                            "select count(*) from app.notification_recipient where read_at is not null"))
                    .isZero();
        }
    }

    @Test
    void applicationRoleCannotInsertAccountsDirectly() throws SQLException {
        try (var connection = applicationRuntime.dataSource().getConnection()) {
            assertThatThrownBy(() -> execute(
                            connection,
                            "insert into app.account(username, password_hash, role) "
                                    + "values ('forbidden.user', 'not-a-hash', 'CHEF')"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("42501"));
        }
    }

    @Test
    void databaseApiHasStableNamesAndOnePublicSignaturePerOperation() throws SQLException {
        try (var connection = migratorConnection()) {
            assertThat(queryForInt(connection, """
                    SELECT count(*) FROM pg_catalog.pg_proc routine
                    JOIN pg_catalog.pg_namespace namespace ON namespace.oid = routine.pronamespace
                    WHERE namespace.nspname = 'app' AND routine.proname ~ '_v[0-9]+($|_)'
                    """))
                    .as("Database operations must use stable names without version suffixes")
                    .isZero();
            assertThat(queryForInt(connection, """
                    SELECT count(*) FROM (
                        SELECT routine.proname FROM pg_catalog.pg_proc routine
                        JOIN pg_catalog.pg_namespace namespace ON namespace.oid = routine.pronamespace
                        WHERE namespace.nspname = 'app'
                          AND has_function_privilege('uninafoodlab_app', routine.oid, 'EXECUTE')
                        GROUP BY routine.proname HAVING count(*) > 1
                    ) overloaded_operations
                    """))
                    .as("Each application database operation must have one public signature")
                    .isZero();
        }
    }

    @Test
    void installsTheCourseManagementDatabaseApi() throws SQLException {
        try (var connection = applicationRuntime.dataSource().getConnection()) {
            assertThat(queryForInt(
                            connection,
                            "select count(*) from information_schema.routines "
                                    + "where routine_schema = 'app' and routine_name in ("
                                    + "'get_password_parameters', 'authenticate_user', "
                                    + "'create_course', 'list_chef_courses', "
                                    + "'list_course_sessions', 'assign_recipe_to_session', "
                                    + "'publish_course', 'list_published_courses', "
                                    + "'list_published_course_sessions', 'enroll_in_course', "
                                    + "'withdraw_from_course', 'set_practical_attendance', "
                                    + "'list_participant_enrollments', "
                                    + "'list_enrollment_practical_sessions')"))
                    .isEqualTo(14);
        }
    }

    private static Connection migratorConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), MIGRATOR_USER, MIGRATOR_PASSWORD);
    }

    private static String queryForString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static int queryForInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
