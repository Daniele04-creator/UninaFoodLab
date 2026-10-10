package it.uninafoodlab.infrastructure.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class SessionHistorySchemaIT {

    private static final String MIGRATOR_USER = "uninafoodlab_migrator";
    private static final String MIGRATOR_PASSWORD = "history-removal-migrator-only";
    private static final String APP_PASSWORD = "history-removal-app-only";

    @Container
    private final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @BeforeEach
    void setUpRoles() throws Exception {
        postgres.copyFileToContainer(MountableFile.forHostPath(
                Path.of("database", "bootstrap", "001__create_roles.sql")),
                "/tmp/001__create_roles.sql");
        var result = postgres.execInContainer("psql", "--username=" + postgres.getUsername(),
                "--dbname=" + postgres.getDatabaseName(), "--set=ON_ERROR_STOP=1",
                "--set=migrator_password=" + MIGRATOR_PASSWORD,
                "--set=app_password=" + APP_PASSWORD, "--file=/tmp/001__create_roles.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }

    @Test
    void initialSchemaOmitsSessionChangeHistoryAndManualCompletion()
            throws Exception {
        migrateLatest();

        try (var connection = migratorConnection()) {
            assertThat(queryForString(connection,
                    "select to_regclass('app.course_session_change')::text")).isNull();
            assertThat(queryForString(connection, """
                    select to_regprocedure(
                        'app.list_course_session_changes(uuid,uuid)')::text
                    """)).isNull();
            assertThat(queryForString(connection, """
                    select to_regprocedure(
                        'app.record_course_session_change(uuid,uuid,uuid,text,timestamp without time zone,timestamp without time zone,timestamp without time zone,timestamp without time zone,text,text,uuid)')::text
                    """)).isNull();
            assertThat(queryForString(connection,
                    "select to_regprocedure('app.complete_course_session(uuid,uuid)')::text"))
                    .isNull();
            assertThat(queryForInt(connection, """
                    select count(*) from app.flyway_schema_history
                     where version::integer = 1 and success
                    """)).isEqualTo(1);
        }
    }

    private void migrateLatest() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), MIGRATOR_USER, MIGRATOR_PASSWORD)
                .defaultSchema("app").schemas("app").createSchemas(false).validateOnMigrate(true)
                .load().migrate();
    }

    private Connection migratorConnection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), MIGRATOR_USER, MIGRATOR_PASSWORD);
    }

    private static int queryForInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private static String queryForString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
