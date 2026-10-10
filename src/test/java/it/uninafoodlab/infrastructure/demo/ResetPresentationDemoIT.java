package it.uninafoodlab.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class ResetPresentationDemoIT {
    @Container private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");
    private static ApplicationConfig config;
    @BeforeAll static void startDatabase() throws Exception {
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(Path.of("database", "bootstrap", "001__create_roles.sql")), "/tmp/roles.sql");
        var result = POSTGRES.execInContainer("psql", "--username=" + POSTGRES.getUsername(), "--dbname=" + POSTGRES.getDatabaseName(), "--set=ON_ERROR_STOP=1", "--set=migrator_password=demo-test-migrator", "--set=app_password=demo-test-app", "--file=/tmp/roles.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        config = new ApplicationConfig("jdbc:postgresql://localhost:" + POSTGRES.getMappedPort(5432) + "/" + POSTGRES.getDatabaseName(), "uninafoodlab_app", "demo-test-app", "uninafoodlab_migrator", "demo-test-migrator", "development");
        DatabaseMigrator.migrate(config);
    }
    @Test void createsDemoChefExplicitlyOnAnEmptyInstallation() throws Exception {
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(), config.databaseMigratorPassword()); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE app.account CASCADE");
        }
        ResetPresentationDemo.reset(config, LocalDate.now(java.time.ZoneId.of("Europe/Rome")));
        assertThat(scalar("SELECT count(*) FROM app.account WHERE username='marta.rossi' AND role='CHEF' AND active")).isEqualTo(1);
        assertThat(scalar("SELECT count(*) FROM app.chef_profile")).isEqualTo(1);
        assertThat(scalar("SELECT count(*) FROM app.schema_metadata WHERE component='first_chef_setup_pending'")).isZero();
        assertThat(scalar("SELECT count(*) FROM app.course")).isEqualTo(3);
    }
    @Test void replacesScenarioDeterministicallyWithRichCoherentData() throws Exception {
        var date = LocalDate.now(java.time.ZoneId.of("Europe/Rome"));
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(), config.databaseMigratorPassword()); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO app.auth_session(token_hash,account_id,expires_at) SELECT decode('010203','hex'),account_id,clock_timestamp()+interval '1 hour' FROM app.chef_profile WHERE id='20000000-0000-0000-0000-000000000001' ON CONFLICT DO NOTHING");
        }
        ResetPresentationDemo.reset(config, date);
        assertThat(scalar("SELECT count(*) FROM app.auth_session")).isZero();
        assertThat(scalar("SELECT count(*) FROM app.course")).isEqualTo(3);
        assertThat(scalar("SELECT count(*) FROM app.course_session")).isEqualTo(12);
        assertThat(scalar("SELECT count(*) FROM app.course_session WHERE mode='ONLINE' AND duration_minutes=75")).isEqualTo(3);
        assertThat(scalar("SELECT count(*) FROM app.course_session WHERE mode='PRACTICAL' AND duration_minutes=150 AND capacity=14")).isEqualTo(9);
        assertThat(scalar("SELECT count(*) FROM app.course_session WHERE status='CANCELLED'")).isZero();
        assertThat(scalar("SELECT count(*) FROM app.participant_profile")).isEqualTo(18);
        assertThat(scalar("SELECT count(*) FROM app.account WHERE role='PARTICIPANT' AND active")).isEqualTo(18);
        assertThat(scalar("SELECT count(*) FROM app.session_outcome")).isZero();
        assertThat(scalar("SELECT count(*) FROM app.enrollment")).isEqualTo(42);
        assertThat(scalar("SELECT count(*) FROM app.notification")).isEqualTo(6);
        assertThat(scalar("SELECT count(*) FROM app.notification_recipient")).isEqualTo(84);
        assertThat(scalar("SELECT count(*) FROM app.session_recipe")).isEqualTo(11);
        assertThat(scalar("SELECT count(*) FROM app.recipe")).isGreaterThanOrEqualTo(12);
        assertThat(scalar("SELECT count(*) FROM app.recipe WHERE base_servings <> 1 OR length(description) < 300")).isZero();
        assertThat(scalar("SELECT count(*) FROM app.recipe_ingredient WHERE quantity <> trunc(quantity) OR quantity <= 0")).isZero();
        assertThat(scalar("SELECT count(*) FROM app.practical_attendance WHERE status='CONFIRMED'")).isEqualTo(85);
        assertThat(scalar("SELECT count(*) FROM app.practical_attendance WHERE status='DECLINED'")).isEqualTo(11);
        assertThat(scalar("SELECT count(*) FROM app.practical_attendance WHERE updated_at > TIMESTAMPTZ '" + date + " 00:00:00 Europe/Rome'")).isZero();
        assertThat(scalar("SELECT count(*) FROM app.practical_attendance a JOIN app.enrollment e ON e.id=a.enrollment_id JOIN app.course_session s ON s.id=a.session_id WHERE e.course_id<>s.course_id")).isZero();
        assertThat(scalar("SELECT count(*) FROM app.notification_recipient r JOIN app.notification n ON n.id=r.notification_id WHERE NOT EXISTS (SELECT 1 FROM app.enrollment e WHERE e.course_id=n.course_id AND e.participant_profile_id=r.participant_profile_id)")).isZero();
        assertThat(scalar("SELECT count(*) FROM pg_trigger WHERE tgname='practical_attendance_guard' AND tgenabled='O'")).isEqualTo(1);
        assertThat(scalar("SELECT count(*) FROM app.account WHERE username='marta.rossi' AND active")).isEqualTo(1);
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(), config.databaseMigratorPassword()); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO app.auth_session(token_hash,account_id,expires_at) SELECT decode('010203','hex'),account_id,clock_timestamp()+interval '1 hour' FROM app.chef_profile WHERE id='20000000-0000-0000-0000-000000000001' ON CONFLICT DO NOTHING");
        }
        ResetPresentationDemo.reset(config, date);
        assertThat(scalar("SELECT count(*) FROM app.auth_session")).isZero();
        assertThat(scalar("SELECT count(*) FROM app.course")).isEqualTo(3);
        assertThat(scalar("SELECT count(*) FROM app.participant_profile")).isEqualTo(18);
        assertThat(scalar("SELECT count(*) FROM app.session_recipe")).isEqualTo(11);
    }
    @Test void keepsTheExistingAdditiveDemoCommandUsable() throws Exception {
        ResetPresentationDemo.reset(config, LocalDate.now());
        assertThat(PrepareDemo.prepare(config, LocalDate.of(2038, 5, 17))).isTrue();
        assertThat(scalar("SELECT count(*) FROM app.course")).isEqualTo(5);
    }
    @Test void allowsEnrichmentAgainAfterReplacingAnEnrichedDemo() throws Exception {
        var date = LocalDate.now(java.time.ZoneId.of("Europe/Rome"));
        ResetPresentationDemo.reset(config, date);
        assertThat(EnrichPresentationDemo.enrich(config, date)).isTrue();
        assertThat(scalar("SELECT count(*) FROM app.course WHERE title='Il pranzo della domenica'")).isEqualTo(1);
        assertThat(scalar("SELECT count(*) FROM app.account WHERE username='demo.esploratore' AND active")).isEqualTo(1);

        ResetPresentationDemo.reset(config, date);
        assertThat(scalar("SELECT count(*) FROM app.course")).isEqualTo(3);
        assertThat(scalar("SELECT count(*) FROM app.account WHERE username='demo.esploratore'")).isZero();
        assertThat(EnrichPresentationDemo.enrich(config, date)).isTrue();
        assertThat(scalar("SELECT count(*) FROM app.course WHERE title='Il pranzo della domenica'")).isEqualTo(1);
        assertThat(scalar("""
                SELECT count(*) FROM app.enrollment e
                JOIN app.participant_profile p ON p.id=e.participant_profile_id
                JOIN app.account a ON a.id=p.account_id
                JOIN app.course c ON c.id=e.course_id
                WHERE a.username='demo.esploratore' AND a.active
                    AND e.status='ACTIVE' AND c.title='Il pranzo della domenica'
                """)).isEqualTo(1);
        assertThat(EnrichPresentationDemo.enrich(config, date)).isFalse();
        assertThat(scalar("SELECT count(*) FROM app.course")).isEqualTo(4);
    }
    @Test void rejectsProductionAndRemoteTargetsBeforeConnecting() {
        var today = LocalDate.now();
        for (var cfg : new ApplicationConfig[]{new ApplicationConfig("jdbc:invalid", "x", "x", "x", "x", "production"), new ApplicationConfig("jdbc:postgresql://example.org/demo", "x", "x", "x", "x", "development"), new ApplicationConfig("jdbc:postgresql://localhost/demo?host=example.org", "x", "x", "x", "x", "development")}) {
            assertThatThrownBy(() -> ResetPresentationDemo.reset(cfg, today)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void rollsBackEveryDeletionAndRestoresGuardsWhenSeedingFails() throws Exception {
        var beforeCourses = scalar("SELECT count(*) FROM app.course");
        var beforeRecipes = scalar("SELECT count(*) FROM app.recipe");
        var beforeAccounts = scalar("SELECT count(*) FROM app.account");
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(), config.databaseMigratorPassword()); var statement = connection.createStatement()) {
            statement.execute("CREATE FUNCTION app.reject_demo_test_course() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Injected demo failure'; END $$");
            statement.execute("CREATE TRIGGER reject_demo_test_course BEFORE INSERT ON app.course FOR EACH ROW EXECUTE FUNCTION app.reject_demo_test_course()");
            try {
                assertThatThrownBy(() -> ResetPresentationDemo.reset(config, LocalDate.now())).isInstanceOf(java.sql.SQLException.class).hasMessageContaining("Injected demo failure");
                assertThat(scalar("SELECT count(*) FROM app.course")).isEqualTo(beforeCourses);
                assertThat(scalar("SELECT count(*) FROM app.recipe")).isEqualTo(beforeRecipes);
                assertThat(scalar("SELECT count(*) FROM app.account")).isEqualTo(beforeAccounts);
                assertThat(scalar("SELECT count(*) FROM pg_trigger WHERE tgname='practical_attendance_guard' AND tgenabled='O'")).isEqualTo(1);
            } finally {
                statement.execute("DROP TRIGGER reject_demo_test_course ON app.course");
                statement.execute("DROP FUNCTION app.reject_demo_test_course()");
            }
        }
    }
    private static long scalar(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(), config.databaseMigratorPassword()); var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) { rows.next(); return rows.getLong(1); }
    }
}
