package it.uninafoodlab.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
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
class EnrichPresentationDemoIT {
    @Container private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");
    private static ApplicationConfig config;

    @BeforeAll static void startDatabase() throws Exception {
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(Path.of("database", "bootstrap", "001__create_roles.sql")), "/tmp/roles.sql");
        var result = POSTGRES.execInContainer("psql", "--username=" + POSTGRES.getUsername(),
                "--dbname=" + POSTGRES.getDatabaseName(), "--set=ON_ERROR_STOP=1",
                "--set=migrator_password=demo-test-migrator", "--set=app_password=demo-test-app", "--file=/tmp/roles.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        config = new ApplicationConfig("jdbc:postgresql://localhost:" + POSTGRES.getMappedPort(5432)
                + "/" + POSTGRES.getDatabaseName(), "uninafoodlab_app", "demo-test-app",
                "uninafoodlab_migrator", "demo-test-migrator", "development");
        DatabaseMigrator.migrate(config);
    }

    @Test void addsDenseVariedDataAtomicallyAndPreservesExistingEdits() throws Exception {
        var date = LocalDate.now(java.time.ZoneId.of("Europe/Rome"));
        assertThatThrownBy(() -> EnrichPresentationDemo.enrich(config, date))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Demo");
        assertThat(scalar("SELECT count(*) FROM app.course")).isZero();

        assertThat(InitializePresentationDemo.prepare(config, date.minusDays(9))).isTrue();
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(), config.databaseMigratorPassword())) {
            DemoSql.execute(connection, "UPDATE app.account SET active=false WHERE role='PARTICIPANT'");
        }
        assertThat(scalar("SELECT count(*) FROM app.account WHERE role='PARTICIPANT' AND NOT active")).isEqualTo(18);
        var blockedSessions = new java.util.ArrayList<java.util.UUID>();
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(), config.databaseMigratorPassword())) {
            for (int hour : new int[]{9, 12, 15, 18}) {
                var id = java.util.UUID.randomUUID();
                blockedSessions.add(id);
                DemoSql.execute(connection, """
                        INSERT INTO app.course_session(id,course_id,sequence_number,scheduled_at,mode,status,
                            duration_minutes,online_url,venue,capacity,attendance_closes_at)
                        VALUES (?,?,?,?,'ONLINE','SCHEDULED',120,'https://example.org/blocked',NULL,NULL,NULL)
                        """, id, PresentationDemoCourses.id("course-2"), 4 + blockedSessions.size(),
                        date.plusDays(2).atTime(hour, 0));
            }
            assertThatThrownBy(() -> EnrichPresentationDemo.enrich(config, date))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Nessun orario libero");
            assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM app.course")).isEqualTo(3);
            assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM app.recipe")).isEqualTo(12);
            assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM app.schema_metadata WHERE component LIKE 'presentation_demo_enriched:%'")).isZero();
        } finally {
            try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(), config.databaseMigratorPassword())) {
                for (var id : blockedSessions) DemoSql.execute(connection, "DELETE FROM app.course_session WHERE id=?", id);
            }
        }
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(), config.databaseMigratorPassword())) {
            DemoSql.execute(connection, "UPDATE app.chef_profile SET display_name='Marta personalizzata'");
            DemoSql.execute(connection, "CREATE FUNCTION app.reject_dense_demo() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Injected dense demo failure'; END $$");
            DemoSql.execute(connection, "CREATE TRIGGER reject_dense_demo BEFORE INSERT ON app.notification FOR EACH ROW EXECUTE FUNCTION app.reject_dense_demo()");
            try {
                assertThatThrownBy(() -> EnrichPresentationDemo.enrich(config, date))
                        .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("Injected dense demo failure");
                assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM app.course")).isEqualTo(3);
                assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM app.recipe")).isEqualTo(12);
                assertThat(DemoSql.scalar(connection, "SELECT count(*) FROM app.schema_metadata WHERE component LIKE 'presentation_demo_enriched:%'")).isZero();
            } finally {
                DemoSql.execute(connection, "DROP TRIGGER reject_dense_demo ON app.notification");
                DemoSql.execute(connection, "DROP FUNCTION app.reject_dense_demo()");
            }
        }
        assertThat(EnrichPresentationDemo.enrich(config, date)).isTrue();
        for (int removed : new int[]{0, 1, 3, 4, 5, 6, 7}) {
            assertThat(scalar("SELECT count(*) FROM app.course WHERE id=?",
                    EnrichmentFixture.id("course-" + removed))).isZero();
        }
        assertThat(scalar("SELECT count(*) FROM app.course WHERE id=?",
                EnrichmentFixture.id("course-2"))).isEqualTo(1);
        assertThat(scalar("SELECT count(*) FROM app.course")).isEqualTo(4);
        assertThat(scalar("SELECT count(*) FROM app.course_session")).isEqualTo(15);
        assertThat(scalar("SELECT count(*) FROM app.course WHERE status='CANCELLED'")).isZero();
        assertThat(scalar("SELECT count(*) FROM app.course WHERE status='COMPLETED'")).isZero();
        assertThat(scalar("SELECT count(*) FROM app.course WHERE char_length(title)>30")).isZero();
        assertThat(scalar("SELECT count(*) FROM app.recipe")).isEqualTo(14);
        assertThat(scalar("SELECT count(*) FROM app.ingredient")).isEqualTo(44);
        assertThat(scalar("SELECT count(*) FROM app.notification")).isEqualTo(8);
        assertThat(scalar("SELECT count(*) FROM app.enrollment")).isEqualTo(51);
        assertThat(scalar("SELECT count(*) FROM app.enrollment WHERE participant_profile_id=?",
                EnrichmentFixture.id("explorer-profile"))).isEqualTo(1);
        assertThat(scalar("SELECT count(*) FROM app.account WHERE role='PARTICIPANT' AND NOT active")).isEqualTo(18);
        assertThat(scalar("SELECT count(*) FROM app.account WHERE role='PARTICIPANT' AND active")).isEqualTo(1);
        assertThat(scalar("SELECT count(*) FROM app.session_outcome")).isZero();
        assertThat(scalar("SELECT count(*) FROM app.course_session s JOIN app.course c ON c.id=s.course_id WHERE c.title='Dolci da condividere'")).isEqualTo(4);
        assertThat(scalar("SELECT count(*) FROM app.chef_profile WHERE display_name='Marta personalizzata'")).isEqualTo(1);
        assertThat(scalar("SELECT count(*) FROM app.practical_attendance a JOIN app.enrollment e ON e.id=a.enrollment_id JOIN app.course_session s ON s.id=a.session_id WHERE e.course_id<>s.course_id")).isZero();
        assertThat(scalar("""
                SELECT count(*) FROM app.course_session a JOIN app.course ca ON ca.id=a.course_id
                JOIN app.course_session b ON a.id<b.id JOIN app.course cb ON cb.id=b.course_id
                WHERE ca.chef_profile_id=cb.chef_profile_id AND a.status='SCHEDULED' AND b.status='SCHEDULED'
                    AND a.scheduled_at < b.scheduled_at+make_interval(mins=>b.duration_minutes)
                    AND b.scheduled_at < a.scheduled_at+make_interval(mins=>a.duration_minutes)
                """)).isZero();
        assertThat(scalar("SELECT count(*) FROM app.course WHERE title='Il pranzo della domenica' AND start_time=TIME '09:00'")).isEqualTo(1);
        assertAppRoleReportAndSnapshot(date);
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(), config.databaseMigratorPassword())) {
            DemoSql.execute(connection, "UPDATE app.participant_profile SET display_name='Esploratrice personalizzata' WHERE id=?",
                    EnrichmentFixture.id("explorer-profile"));
        }
        assertThat(EnrichPresentationDemo.enrich(config, date.plusDays(1))).isFalse();
        assertThat(scalar("SELECT count(*) FROM app.participant_profile WHERE id=? AND display_name='Esploratrice personalizzata'",
                EnrichmentFixture.id("explorer-profile"))).isEqualTo(1);
        assertThat(scalar("SELECT count(*) FROM app.enrollment WHERE participant_profile_id=? AND course_id=?",
                EnrichmentFixture.id("explorer-profile"), EnrichmentFixture.id("course-2"))).isEqualTo(1);
        removeExplorerToSimulatePreviousVersion();
        assertThat(EnrichPresentationDemo.enrich(config, date.plusDays(1))).isTrue();
        assertThat(scalar("SELECT count(*) FROM app.course")).isEqualTo(4);
        assertThat(scalar("SELECT count(*) FROM app.account WHERE role='PARTICIPANT' AND NOT active")).isEqualTo(18);
        assertExplorerCanBrowse();
        assertThat(scalar("SELECT count(*) FROM app.practical_attendance a JOIN app.enrollment e ON e.id=a.enrollment_id WHERE e.participant_profile_id=? AND a.status='CONFIRMED'",
                EnrichmentFixture.id("explorer-profile"))).isZero();
        assertThat(scalar("SELECT count(*) FROM app.practical_attendance a JOIN app.enrollment e ON e.id=a.enrollment_id WHERE e.participant_profile_id=? AND a.status='DECLINED'",
                EnrichmentFixture.id("explorer-profile"))).isEqualTo(1);
        assertThat(EnrichPresentationDemo.enrich(config, date.plusDays(1))).isFalse();
        assertThat(scalar("SELECT count(*) FROM app.course")).isEqualTo(4);
        assertThat(scalar("SELECT count(*) FROM app.chef_profile WHERE display_name='Marta personalizzata'")).isEqualTo(1);
        assertAppRoleReportAndSnapshot(date);
    }

    @Test void refusesRemoteAndProductionTargetsWithoutConnecting() {
        for (var cfg : new ApplicationConfig[]{
                new ApplicationConfig("jdbc:invalid", "x", "x", "x", "x", "production"),
                new ApplicationConfig("jdbc:postgresql://example.org/demo", "x", "x", "x", "x", "development")}) {
            assertThatThrownBy(() -> EnrichPresentationDemo.enrich(cfg, LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static long scalar(String sql, Object... values) throws Exception {
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(), config.databaseMigratorPassword())) {
            return DemoSql.scalar(connection, sql, values);
        }
    }

    private static void removeExplorerToSimulatePreviousVersion() throws Exception {
        var profile = EnrichmentFixture.id("explorer-profile");
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(), config.databaseMigratorPassword())) {
            DemoSql.execute(connection, "DELETE FROM app.practical_attendance WHERE enrollment_id IN (SELECT id FROM app.enrollment WHERE participant_profile_id=?)", profile);
            DemoSql.execute(connection, "DELETE FROM app.notification_recipient WHERE participant_profile_id=?", profile);
            DemoSql.execute(connection, "DELETE FROM app.enrollment WHERE participant_profile_id=?", profile);
            DemoSql.execute(connection, "DELETE FROM app.participant_profile WHERE id=?", profile);
            DemoSql.execute(connection, "DELETE FROM app.account WHERE id=?", EnrichmentFixture.id("explorer-account"));
            DemoSql.execute(connection, "DELETE FROM app.schema_metadata WHERE component=?", EnrichmentParticipant.MARKER);
        }
    }

    private static void assertExplorerCanBrowse() throws Exception {
        try (var runtime = ApplicationRuntime.start(config)) {
            var participant = runtime.authenticateUser().execute("demo.esploratore", "MartaRossi!2026".toCharArray());
            assertThat(participant.role()).isEqualTo(it.uninafoodlab.domain.identity.Role.PARTICIPANT);
            try (var app = runtime.dataSource().getConnection()) {
                assertThat(DemoSql.scalar(app, "SELECT count(*) FROM app.list_participant_enrollments(?)",
                        participant.sessionToken())).isEqualTo(1);
                assertThat(DemoSql.scalar(app, "SELECT count(*) FROM app.list_published_courses(?,NULL) WHERE course_id=?",
                        participant.sessionToken(), EnrichmentFixture.id("course-2"))).isEqualTo(1);
                assertThat(DemoSql.scalar(app, "SELECT count(*) FROM app.list_participant_notifications(?)",
                        participant.sessionToken())).isEqualTo(2);
            }
        }
    }

    private static void assertAppRoleReportAndSnapshot(LocalDate date) throws Exception {
        try (var runtime = ApplicationRuntime.start(config)) {
            var chef = runtime.authenticateUser().execute("marta.rossi", "MartaRossi!2026".toCharArray());
            try (var app = runtime.dataSource().getConnection();
                    var report = app.prepareStatement("SELECT * FROM app.chef_monthly_report(?,?,?)")) {
                report.setObject(1, chef.sessionToken());
                var month = java.time.YearMonth.from(date.minusDays(9).minusMonths(1));
                report.setInt(2, month.getYear());
                report.setInt(3, month.getMonthValue());
                try (var rows = report.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("total_courses")).isEqualTo(1);
                    assertThat(rows.getLong("online_sessions")).isEqualTo(1);
                    assertThat(rows.getLong("practical_sessions")).isEqualTo(3);
                    assertThat(rows.getInt("minimum_recipes")).isEqualTo(1);
                    assertThat(rows.getInt("maximum_recipes")).isEqualTo(2);
                    assertThat(rows.getBigDecimal("average_recipes")).isBetween(
                            new java.math.BigDecimal("1"), new java.math.BigDecimal("2"));
                }
            }
            try (var migrator = DriverManager.getConnection(config.databaseUrl(),
                    config.databaseMigratorUser(), config.databaseMigratorPassword());
                    var sessionQuery = migrator.prepareStatement("""
                            SELECT s.id FROM app.course_session s JOIN app.course c ON c.id=s.course_id
                            WHERE c.title='Il pranzo della domenica'
                                AND s.sequence_number=1
                            """)) {
                try (var rows = sessionQuery.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    var session = rows.getObject(1, java.util.UUID.class);
                    try (var app = runtime.dataSource().getConnection();
                            var menu = app.prepareStatement("SELECT name,doses FROM app.saved_session_menu(?,?)")) {
                        menu.setObject(1, chef.sessionToken());
                        menu.setObject(2, session);
                        try (var dishes = menu.executeQuery()) {
                            assertThat(dishes.next()).isTrue();
                            var firstDoses = dishes.getString("doses");
                            assertThat(dishes.next()).isTrue();
                            var secondDoses = dishes.getString("doses");
                            assertThat(firstDoses + secondDoses).contains("Patate", "Farina tipo 00");
                            assertThat(dishes.next()).isFalse();
                        }
                    }
                }
            }
        }
    }
}
