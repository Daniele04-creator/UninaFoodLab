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
class SimplifiedSessionSchemaIT {

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
    void preservesPrivateSessionRecordsWithoutOfferingFrozenPreparationOrManualOutcomes() throws Exception {
        it.uninafoodlab.infrastructure.support.DemoDatabaseFixture.migrate(
                new it.uninafoodlab.infrastructure.config.ApplicationConfig(postgres.getJdbcUrl(),
                        "uninafoodlab_app", APP_PASSWORD, MIGRATOR_USER, MIGRATOR_PASSWORD, "test"));
        String session;
        String snapshotCount;
        try (var connection = migratorConnection(); var statement=connection.createStatement()) {
            session=queryForString(connection,"SELECT id::text FROM app.course_session ORDER BY id LIMIT 1");
            assertThat(session).isNotNull();
            snapshotCount=queryForString(connection,"SELECT count(*)::text FROM app.session_menu_snapshot");
            statement.execute("INSERT INTO app.session_preparation_revision(session_id,revision,reason,preparation,confirmed_enrollments,active) VALUES ('" + session + "',1,'Lista storica','[]','{}',FALSE)");
            statement.execute("INSERT INTO app.session_outcome(session_id,recipe_ids,enrollment_ids,note) VALUES ('" + session + "','{}','{}','Consuntivo storico') ON CONFLICT(session_id) DO UPDATE SET enrollment_ids='{}',note='Consuntivo storico'");
        }
        migrateLatest();
        try (var connection=migratorConnection()) {
            assertThat(queryForInt(connection,"SELECT count(*) FROM app.session_preparation_revision WHERE active")).isZero();
            assertThat(queryForString(connection,"SELECT reason FROM app.session_preparation_revision WHERE session_id='"+session+"'")).isEqualTo("Lista storica");
            assertThat(queryForString(connection,"SELECT count(*)::text FROM app.session_menu_snapshot")).isEqualTo(snapshotCount);
            assertThat(queryForString(connection,"SELECT note FROM app.session_outcome WHERE session_id='"+session+"'")).isEqualTo("Consuntivo storico");
            assertThat(queryForString(connection,"SELECT enrollment_ids::text FROM app.session_outcome WHERE session_id='"+session+"'")).isEqualTo("{}");
            assertThat(queryForString(connection,"SELECT to_regprocedure('app.consolidate_session_preparation(uuid,uuid,text)')::text")).isNull();
            assertThat(queryForString(connection,"SELECT to_regprocedure('app.record_session_outcome(uuid,uuid,uuid[],text)')::text")).isNull();
        }
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), "uninafoodlab_app", APP_PASSWORD)) {
            assertThat(queryForString(connection,"SELECT has_table_privilege(current_user,'app.session_preparation_revision','SELECT')::text")).isEqualTo("false");
            assertThat(queryForString(connection,"SELECT has_table_privilege(current_user,'app.session_outcome','SELECT')::text")).isEqualTo("false");
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
