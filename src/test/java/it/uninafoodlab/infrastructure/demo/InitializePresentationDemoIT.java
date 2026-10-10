package it.uninafoodlab.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import it.uninafoodlab.infrastructure.database.DatabaseMigrator;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class InitializePresentationDemoIT {
    @Container private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");
    private static ApplicationConfig config;

    @BeforeAll static void startDatabase() throws Exception {
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(Path.of("database", "bootstrap", "001__create_roles.sql")), "/tmp/roles.sql");
        var result = POSTGRES.execInContainer("psql", "--username=" + POSTGRES.getUsername(), "--dbname=" + POSTGRES.getDatabaseName(), "--set=ON_ERROR_STOP=1", "--set=migrator_password=demo-test-migrator", "--set=app_password=demo-test-app", "--file=/tmp/roles.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        config = new ApplicationConfig("jdbc:postgresql://localhost:" + POSTGRES.getMappedPort(5432) + "/" + POSTGRES.getDatabaseName(), "uninafoodlab_app", "demo-test-app", "uninafoodlab_migrator", "demo-test-migrator", "development");
        DatabaseMigrator.migrate(config);
    }

    @Test void initializesEmptyDatabaseAndPreservesChangesOnRerun() throws Exception {
        var date = LocalDate.now(java.time.ZoneId.of("Europe/Rome"));
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(), config.databaseMigratorPassword())) {

            DemoSql.execute(connection, "INSERT INTO app.category(name, description) VALUES ('Categoria personale', 'Da conservare')");
            assertThatThrownBy(() -> InitializePresentationDemo.prepare(config, date))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("non vuoto");
            assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM app.category WHERE name='Categoria personale'")).isEqualTo(1);
            assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM app.account")).isZero();
            DemoSql.execute(connection, "DELETE FROM app.category WHERE name='Categoria personale'");
            DemoSql.execute(connection, "CREATE FUNCTION app.reject_initial_demo() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Injected demo failure'; END $$");
            DemoSql.execute(connection, "CREATE TRIGGER reject_initial_demo BEFORE INSERT ON app.course FOR EACH ROW EXECUTE FUNCTION app.reject_initial_demo()");
            try {
                assertThatThrownBy(() -> InitializePresentationDemo.prepare(config, date)).isInstanceOf(java.sql.SQLException.class);
                assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM app.account")).isZero();
                assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM app.schema_metadata WHERE component='presentation_demo_initialized'")).isZero();
                assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM pg_trigger WHERE tgname='practical_attendance_guard' AND tgenabled='O'")).isEqualTo(1);
            } finally {
                DemoSql.execute(connection, "DROP TRIGGER reject_initial_demo ON app.course");
                DemoSql.execute(connection, "DROP FUNCTION app.reject_initial_demo()");
            }
            assertThat(InitializePresentationDemo.prepare(config, date)).isTrue();
            assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM app.course")).isEqualTo(3);
            assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM app.recipe")).isGreaterThanOrEqualTo(12);
            assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM app.course WHERE char_length(title)>30")).isZero();
            for (int removed : new int[]{0, 1, 3}) {
                assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM app.course WHERE id=?",
                        PresentationDemoCourses.id("course-" + removed))).isZero();
            }
            for (int kept : new int[]{2, 4, 5}) {
                assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM app.course WHERE id=?",
                        PresentationDemoCourses.id("course-" + kept))).isEqualTo(1);
            }
            DemoSql.execute(connection, "UPDATE app.chef_profile SET display_name='Chef personalizzato'");
            assertThat(InitializePresentationDemo.prepare(config, date.plusDays(1))).isFalse();
            assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM app.chef_profile WHERE display_name='Chef personalizzato'")).isEqualTo(1);
            assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM app.course")).isEqualTo(3);
        }
        try (var runtime = ApplicationRuntime.start(config)) {
            assertThat(runtime.authenticateUser().execute("marta.rossi", "MartaRossi!2026".toCharArray()).role()).isEqualTo(Role.CHEF);
            assertThat(runtime.authenticateUser().execute("demo.partecipante.1", "MartaRossi!2026".toCharArray()).role()).isEqualTo(Role.PARTICIPANT);
        }
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseUser(), config.databasePassword())) {
            assertThatThrownBy(() -> DemoSql.scalar(connection, "SELECT count(*) FROM app.account"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("permission denied");
        }
    }

    @Test void rejectsRemoteAndProductionDatabasesBeforeConnecting() {
        for (var cfg : new ApplicationConfig[]{
                new ApplicationConfig("jdbc:invalid", "x", "x", "x", "x", "production"),
                new ApplicationConfig("jdbc:postgresql://example.org/demo", "x", "x", "x", "x", "development")}) {
            assertThatThrownBy(() -> InitializePresentationDemo.prepare(cfg, LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
