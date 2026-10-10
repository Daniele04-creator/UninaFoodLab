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
class PasswordRecoveryPrivilegesIT {

    private static final String MIGRATOR_USER = "uninafoodlab_migrator";
    private static final String MIGRATOR_PASSWORD = "recovery-removal-migrator-only";
    private static final String APP_PASSWORD = "recovery-removal-app-only";

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
    void keepsResetRowsPrivateWhileAuthenticatedPasswordChangeRemainsAvailable() throws Exception {
        migrateLatest();
        try (var connection = migratorConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                insert into app.account(id,username,password_hash,role)
                values ('00000000-0000-0000-0000-00000000a067','historical.reset',
                        '$pbkdf2-sha256$600000$historical-fixture','PARTICIPANT')
                """);
            statement.execute("""
                insert into app.account_password_reset(token_hash,account_id,expires_at)
                values (sha256(convert_to('historical-reset-fixture','UTF8')),
                        '00000000-0000-0000-0000-00000000a067',clock_timestamp()+interval '1 hour')
                """);
        }
        migrateLatest();
        try (var connection = migratorConnection()) {
            assertThat(queryForString(connection,"select to_regprocedure('app.reset_account_password(uuid,text)')::text")).isNull();
            assertThat(queryForString(connection,"select to_regprocedure('app.provision_password_reset(text,uuid,timestamp with time zone)')::text")).isNull();
            assertThat(queryForInt(connection,"select count(*) from app.account_password_reset where account_id='00000000-0000-0000-0000-00000000a067'"))
                    .isEqualTo(1);
            assertThat(queryForString(connection,"select to_regprocedure('app.change_account_password(uuid,bytea,text)')::text")).isNotNull();
        }
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), "uninafoodlab_app", APP_PASSWORD)) {
            assertThat(queryForString(connection,"select to_regprocedure('app.reset_account_password(uuid,text)')::text")).isNull();
            assertThat(queryForString(connection,"select has_function_privilege(current_user,'app.change_account_password(uuid,bytea,text)','EXECUTE')::text"))
                    .isEqualTo("true");
            assertThat(queryForString(connection,"select has_table_privilege(current_user,'app.account_password_reset','SELECT')::text"))
                    .isEqualTo("false");
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
