package it.uninafoodlab.infrastructure.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.account.AccountLifecycle;
import it.uninafoodlab.application.account.AccountLifecycleException;
import it.uninafoodlab.infrastructure.account.JdbcAccountLifecycleRepository;
import it.uninafoodlab.infrastructure.auth.JdbcAccountRepository;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import it.uninafoodlab.infrastructure.security.Pbkdf2PasswordHasher;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class FirstRunSetupIT {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");
    private static ApplicationConfig config;

    @BeforeAll
    static void bootstrap() throws Exception {
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(
                Path.of("database/bootstrap/001__create_roles.sql")), "/tmp/roles.sql");
        var result = POSTGRES.execInContainer("psql", "--username=" + POSTGRES.getUsername(),
                "--dbname=" + POSTGRES.getDatabaseName(), "--set=ON_ERROR_STOP=1",
                "--set=migrator_password=integration-migrator-only",
                "--set=app_password=integration-app-only", "--file=/tmp/roles.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        config = new ApplicationConfig(POSTGRES.getJdbcUrl(), "uninafoodlab_app", "integration-app-only",
                "uninafoodlab_migrator", "integration-migrator-only", "development");
    }

    @BeforeEach
    void resetIsolatedContainerSchema() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA app CASCADE");
            statement.execute("CREATE SCHEMA app AUTHORIZATION uninafoodlab_migrator");
            statement.execute("GRANT USAGE ON SCHEMA app TO uninafoodlab_app");
        }
    }

    @Test
    void newInstallationAllowsEitherRoleAsTheFirstUser() throws Exception {
        DatabaseMigrator.migrate(config);
        assertThat(scalar("select count(*) from app.account")).isZero();
        assertThat(scalar("select count(*) from app.schema_metadata "
                + "where component in ('fresh_install_empty_pending','first_chef_setup_pending')")).isZero();

        try (var dataSource = DataSourceFactory.create(config)) {
            var lifecycle = lifecycle(dataSource);
            var chef = lifecycle.registerChef("first.chef", "Primo Chef", "FirstChefSecret!".toCharArray());
            var participant = lifecycle.registerParticipant("first.participant", "Prima Persona",
                    "ParticipantSecret!".toCharArray());

            assertThat(chef.role()).isEqualTo(Role.CHEF);
            assertThat(participant.role()).isEqualTo(Role.PARTICIPANT);
            assertThat(scalar("select count(*) from app.account")).isEqualTo(2);
            assertThat(scalar("select count(*) from app.chef_profile")).isOne();
            assertThat(scalar("select count(*) from app.participant_profile")).isOne();
        }
    }

    @Test
    void participantCanRegisterBeforeTheFirstChef() throws Exception {
        DatabaseMigrator.migrate(config);
        try (var dataSource = DataSourceFactory.create(config)) {
            var lifecycle = lifecycle(dataSource);
            assertThat(lifecycle.registerParticipant("first.participant", "Prima Persona",
                    "ParticipantSecret!".toCharArray()).role()).isEqualTo(Role.PARTICIPANT);
            assertThat(lifecycle.registerChef("first.chef", "Primo Chef",
                    "FirstChefSecret!".toCharArray()).role()).isEqualTo(Role.CHEF);
        }
    }

    @Test
    void invalidRegistrationRollsBackAndCanBeRetried() throws Exception {
        DatabaseMigrator.migrate(config);
        try (var dataSource = DataSourceFactory.create(config)) {
            var repository = new JdbcAccountLifecycleRepository(dataSource, new Pbkdf2PasswordHasher());
            assertThatThrownBy(() -> repository.registerChef(Username.of("first.chef"), "Chef", "invalid-hash"))
                    .isInstanceOf(AccountLifecycleException.class);
            assertThat(scalar("select count(*) from app.account")).isZero();
            assertThat(lifecycle(dataSource).registerChef("first.chef", "Primo Chef",
                    "FirstChefSecret!".toCharArray()).role()).isEqualTo(Role.CHEF);
        }
    }

    @Test
    void openRegistrationAllowsMultipleChefsAndConcurrentAttempts() throws Exception {
        DatabaseMigrator.migrate(config);
        try (var dataSource = DataSourceFactory.create(config); var executor = Executors.newFixedThreadPool(2)) {
            var lifecycle = lifecycle(dataSource);
            var first = executor.submit(() -> lifecycle.registerChef("first.chef", "Primo Chef",
                    "FirstChefSecret!".toCharArray()));
            var second = executor.submit(() -> lifecycle.registerChef("second.chef", "Secondo Chef",
                    "SecondChefSecret!".toCharArray()));
            assertThat(List.of(first.get().role(), second.get().role()))
                    .containsExactlyInAnyOrder(Role.CHEF, Role.CHEF);
            assertThat(scalar("select count(*) from app.chef_profile")).isEqualTo(2);
        }
    }

    @Test
    void restartPreservesAccountsAndAuthenticatedPasswordChanges() throws Exception {
        DatabaseMigrator.migrate(config);
        try (var dataSource = DataSourceFactory.create(config)) {
            var hasher = new Pbkdf2PasswordHasher();
            var lifecycle = new AccountLifecycle(new JdbcAccountLifecycleRepository(dataSource, hasher), hasher);
            var chef = lifecycle.registerChef("own.chef", "Primo Chef", "FirstChefSecret!".toCharArray());
            DatabaseMigrator.migrate(config);
            lifecycle.changePassword(chef, "FirstChefSecret!".toCharArray(), "ReplacementSecret!".toCharArray());
            assertThat(new JdbcAccountRepository(dataSource, hasher)
                    .authenticate(Username.of("own.chef"), "ReplacementSecret!".toCharArray())).isPresent();
            assertThat(scalar("select count(*) from app.account")).isOne();
            assertThat(scalar("select count(*) from app.schema_metadata "
                    + "where component='first_chef_setup_pending'")).isZero();
        }
    }

    @Test
    void applicationRoleCannotUseLegacyInvitationOrFirstRunEntryPoints() throws Exception {
        DatabaseMigrator.migrate(config);
        try (var dataSource = DataSourceFactory.create(config)) {
            assertApplicationSqlFails(dataSource, "42883",
                    "select app.activate_chef_invitation(?, ?, ?, ?)",
                    java.util.UUID.randomUUID(), "legacy.chef", "Legacy Chef", "invalid-hash");
            assertApplicationSqlFails(dataSource, "42883", "select app.provision_chef_invitation(?, ?)",
                    java.util.UUID.randomUUID(), java.time.OffsetDateTime.now().plusHours(1));
            assertApplicationSqlFails(dataSource, "42883", "select app.initialize_first_chef(?, ?, ?)",
                    "legacy.chef", "Legacy Chef", "invalid-hash");
            assertApplicationSqlFails(dataSource, "42501", "select count(*) from app.chef_invitation");
        }
    }

    @Test
    void repeatedInitialMigrationPreservesAnEmptyOpenInstallation() throws Exception {
        assertThat(DatabaseMigrator.migrate(config).migrationsExecuted).isEqualTo(2);
        assertThat(DatabaseMigrator.migrate(config).migrationsExecuted).isZero();
        assertThat(scalar("select count(*) from app.account")).isZero();
        assertThat(scalar("select count(*) from app.schema_metadata "
                + "where component in ('fresh_install_empty_pending','first_chef_setup_pending')")).isZero();
    }

    @Test
    void scheduleProtectionUpgradePreservesAccountsSessionsAndApplicationPrivileges() throws Exception {
        assertThat(initialFlyway().target("1").load().migrate().migrationsExecuted).isOne();
        var originalChecksum = scalar("select checksum from app.flyway_schema_history where version::integer=1");
        try (var dataSource = DataSourceFactory.create(config)) {
            var chef = lifecycle(dataSource).registerChef("upgrade.chef", "Chef conservato",
                    "UpgradeSchedule!2026".toCharArray());
            assertThat(DatabaseMigrator.migrate(config).migrationsExecuted).isOne();
            assertThat(scalar("select checksum from app.flyway_schema_history where version::integer=1"))
                    .isEqualTo(originalChecksum);
            assertThat(scalar("select count(*) from app.account where username='upgrade.chef'")).isOne();
            assertThat(scalar("select count(*) from app.auth_session")).isOne();
            assertThat(scalar("""
                    select (has_function_privilege('uninafoodlab_app',
                        'app.reschedule_course_session(uuid,uuid,timestamp without time zone,timestamp without time zone,text,text,text)',
                        'EXECUTE') and not has_table_privilege('uninafoodlab_app','app.account','SELECT'))::integer
                    """)).isOne();
            assertThat(new JdbcAccountRepository(dataSource, new Pbkdf2PasswordHasher())
                    .authenticate(Username.of("upgrade.chef"), "UpgradeSchedule!2026".toCharArray()))
                    .get().extracting(it.uninafoodlab.domain.identity.AuthenticatedUser::accountId)
                    .isEqualTo(chef.accountId());
        }
    }

    @Test
    void failedFreshMigrationCanBeRetriedAndLeavesRegistrationOpen() throws Exception {
        var emptySchemaCreated = new AtomicBoolean();
        var failingCallback = new Callback() {
            @Override public boolean supports(Event event, Context context) { return event == Event.AFTER_EACH_MIGRATE; }
            @Override public boolean canHandleInTransaction(Event event, Context context) { return true; }
            @Override public String getCallbackName() { return "fail-after-initial-schema"; }
            @Override public void handle(Event event, Context context) {
                try (var statement = context.getConnection().createStatement();
                        var result = statement.executeQuery("select count(*) from app.account")) {
                    result.next();
                    emptySchemaCreated.set(result.getInt(1) == 0);
                } catch (SQLException exception) {
                    throw new FlywayException("Unable to inspect the initial schema", exception);
                }
                throw new FlywayException("Simulated failure after initial schema");
            }
        };

        assertThatThrownBy(() -> initialFlyway().callbacks(failingCallback).load().migrate())
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("Simulated failure after initial schema");
        assertThat(emptySchemaCreated).isTrue();
        assertThat(scalar("select count(*) from pg_catalog.pg_class c join pg_catalog.pg_namespace n "
                + "on n.oid=c.relnamespace where n.nspname='app' and c.relname='account'")).isZero();
        assertThat(DatabaseMigrator.migrate(config).migrationsExecuted).isEqualTo(2);
        assertThat(scalar("select count(*) from app.account")).isZero();
        assertThat(scalar("select count(*) from app.schema_metadata "
                + "where component='first_chef_setup_pending'")).isZero();
    }

    @Test
    void simultaneousMigratorsProduceOneEmptyInstallationWithoutAFirstChefMarker() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> DatabaseMigrator.migrate(config));
            Future<?> second = executor.submit(() -> DatabaseMigrator.migrate(config));
            first.get();
            second.get();
        }
        assertThat(scalar("select count(*) from app.account")).isZero();
        assertThat(scalar("select count(*) from app.schema_metadata "
                + "where component='first_chef_setup_pending'")).isZero();
    }

    @Test
    void restartPreservesCustomizedDemoDataAndOpenRegistration() throws Exception {
        it.uninafoodlab.infrastructure.support.DemoDatabaseFixture.migrate(config);
        executeAsMigrator("update app.chef_profile set display_name='Nome personalizzato' "
                + "where account_id=(select id from app.account where username='marta.rossi')");
        long accounts = scalar("select count(*) from app.account");
        long courses = scalar("select count(*) from app.course");
        DatabaseMigrator.migrate(config);
        assertThat(scalar("select count(*) from app.account")).isEqualTo(accounts);
        assertThat(scalar("select count(*) from app.course")).isEqualTo(courses);
        assertThat(scalar("select count(*) from app.chef_profile where display_name='Nome personalizzato'"))
                .isOne();
        try (var dataSource = DataSourceFactory.create(config)) {
            assertThat(lifecycle(dataSource).registerChef("upgraded.chef", "Chef Aggiornato",
                    "UpgradedChefSecret!".toCharArray()).role()).isEqualTo(Role.CHEF);
        }
    }

    private static AccountLifecycle lifecycle(javax.sql.DataSource dataSource) {
        var hasher = new Pbkdf2PasswordHasher();
        return new AccountLifecycle(new JdbcAccountLifecycleRepository(dataSource, hasher), hasher);
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration initialFlyway() {
        return Flyway.configure().dataSource(config.databaseUrl(), config.databaseMigratorUser(),
                config.databaseMigratorPassword()).defaultSchema("app").schemas("app").createSchemas(false);
    }

    private static long scalar(String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(),
                config.databaseMigratorPassword()); var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void executeAsMigrator(String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(),
                config.databaseMigratorPassword()); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void assertApplicationSqlFails(javax.sql.DataSource dataSource, String expectedState,
            String sql, Object... values) {
        assertThatThrownBy(() -> {
            try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
                statement.execute();
            }
        }).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo(expectedState));
    }

}
