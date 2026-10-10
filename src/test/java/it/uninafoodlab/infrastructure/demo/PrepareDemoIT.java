package it.uninafoodlab.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import it.uninafoodlab.infrastructure.database.DatabaseMigrator;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class PrepareDemoIT {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");
    private static ApplicationConfig config;

    @BeforeAll
    static void startDatabase() throws Exception {
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(
                Path.of("database", "bootstrap", "001__create_roles.sql")), "/tmp/roles.sql");
        var result = POSTGRES.execInContainer("psql", "--username=" + POSTGRES.getUsername(),
                "--dbname=" + POSTGRES.getDatabaseName(), "--set=ON_ERROR_STOP=1",
                "--set=migrator_password=demo-test-migrator", "--set=app_password=demo-test-app",
                "--file=/tmp/roles.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        config = new ApplicationConfig(POSTGRES.getJdbcUrl(), "uninafoodlab_app", "demo-test-app",
                "uninafoodlab_migrator", "demo-test-migrator", "development");
        it.uninafoodlab.infrastructure.support.DemoDatabaseFixture.migrate(config);
    }

    @Test
    void createsRelativePastAndFutureCoursesOnceWithoutChangingExistingRows() throws Exception {
        var date = LocalDate.of(2035, 3, 15);
        var initialCount = scalar("SELECT count(*) FROM app.course");
        assertThat(PrepareDemo.prepare(config, date)).isTrue();
        assertThat(PrepareDemo.prepare(config, date)).isFalse();
        assertThat(scalar("SELECT count(*) FROM app.course")).isEqualTo(initialCount + 2);
        assertThat(scalar("SELECT count(*) FROM app.course_session WHERE course_id = '"
                + PrepareDemo.id(date, "future") + "' AND scheduled_at >= DATE '2035-03-22'"))
                .isEqualTo(2);
        assertThat(scalar("SELECT count(*) FROM app.course_session WHERE course_id = '"
                + PrepareDemo.id(date, "past") + "' AND scheduled_at < DATE '2035-03-15'"))
                .isEqualTo(2);
        assertThat(scalar("SELECT count(*) FROM app.session_recipe WHERE session_id IN "
                + "(SELECT id FROM app.course_session WHERE course_id IN ('"
                + PrepareDemo.id(date, "future") + "', '" + PrepareDemo.id(date, "past") + "'))"))
                .isEqualTo(5);
    }

    @Test
    void refusesConflictingPartialScenariosAndRollsBack() throws Exception {
        var date = LocalDate.of(2035, 5, 20);
        PrepareDemo.prepare(config, date);
        try (var connection = DriverManager.getConnection(config.databaseUrl(),
                config.databaseMigratorUser(), config.databaseMigratorPassword());
                var statement = connection.prepareStatement("UPDATE app.course SET title = ? WHERE id = ?")) {
            statement.setString(1, "Personalizzato dallo chef");
            statement.setObject(2, PrepareDemo.id(date, "future"));
            statement.executeUpdate();
        }
        var initialCount = scalar("SELECT count(*) FROM app.course");
        assertThatThrownBy(() -> PrepareDemo.prepare(config, date))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("modificato");
        assertThat(scalar("SELECT count(*) FROM app.course")).isEqualTo(initialCount);
    }

    @Test
    void preservesEditedSessionsInsteadOfAcceptingThemAsTheOriginalFixture() throws Exception {
        var changes = new String[]{
            "UPDATE app.course_session SET venue = 'Sede personalizzata' WHERE id = ?",
            "UPDATE app.course_session SET duration_minutes = 120 WHERE id = ?",
            "DELETE FROM app.session_recipe WHERE session_id = ?"
        };
        for (int index = 0; index < changes.length; index++) {
            var date = LocalDate.of(2037, 1, 15).plusMonths(index);
            PrepareDemo.prepare(config, date);
            try (var connection = DriverManager.getConnection(config.databaseUrl(),
                    config.databaseMigratorUser(), config.databaseMigratorPassword());
                    var statement = connection.prepareStatement(changes[index])) {
                statement.setObject(1, PrepareDemo.id(date, "future-1"));
                assertThat(statement.executeUpdate()).isPositive();
            }
            var count = scalar("SELECT count(*) FROM app.course");
            assertThatThrownBy(() -> PrepareDemo.prepare(config, date))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("modificato");
            assertThat(scalar("SELECT count(*) FROM app.course")).isEqualTo(count);

            assertThatThrownBy(() -> PrepareDemo.prepare(config, date))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("modificato");
        }
    }

    @Test
    void rollsBackTheEntireScenarioWhenAUserCourseConflicts() throws Exception {
        var date = LocalDate.of(2035, 7, 20);
        try (var connection = DriverManager.getConnection(config.databaseUrl(),
                config.databaseMigratorUser(), config.databaseMigratorPassword());
                var statement = connection.prepareStatement("""
                        INSERT INTO app.course (chef_profile_id, category_id, title, description,
                            start_date, start_time, recurrence_days, session_count)
                        VALUES ('20000000-0000-0000-0000-000000000001',
                            '30000000-0000-0000-0000-000000000001', ?, 'Corso personale',
                            DATE '2035-06-08', TIME '10:00', 7, 1)
                        """)) {
            statement.setString(1, "Demo archivio · " + date);
            statement.executeUpdate();
        }
        var initialCount = scalar("SELECT count(*) FROM app.course");
        assertThatThrownBy(() -> PrepareDemo.prepare(config, date)).isInstanceOf(SQLException.class);
        assertThat(scalar("SELECT count(*) FROM app.course")).isEqualTo(initialCount);
        assertThat(scalar("SELECT count(*) FROM app.course WHERE id = '" + PrepareDemo.id(date, "future") + "'"))
                .isZero();
    }

    @Test
    void refusesNonDevelopmentEnvironmentBeforeConnecting() {
        var production = new ApplicationConfig("jdbc:invalid", "unused", "unused", "unused", "unused", "production");
        var today = LocalDate.now();
        assertThatThrownBy(() -> PrepareDemo.prepare(production, today))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("development");
    }

    private static long scalar(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(config.databaseUrl(),
                config.databaseMigratorUser(), config.databaseMigratorPassword());
                var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
