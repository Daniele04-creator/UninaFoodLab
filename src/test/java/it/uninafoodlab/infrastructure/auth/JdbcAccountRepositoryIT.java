package it.uninafoodlab.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.application.account.AccountLifecycle;
import it.uninafoodlab.application.account.AccountLifecycleException;
import it.uninafoodlab.application.auth.AuthenticateUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import it.uninafoodlab.infrastructure.account.AccountProvisioning;
import it.uninafoodlab.infrastructure.account.JdbcAccountLifecycleRepository;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import it.uninafoodlab.infrastructure.security.Pbkdf2PasswordHasher;
import java.nio.file.Path;
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
class JdbcAccountRepositoryIT {

    private static final String MIGRATOR_PASSWORD = "repository-migrator-only";
    private static final String APP_PASSWORD = "repository-app-only";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");

    private static ApplicationRuntime runtime;
    private static JdbcAccountRepository repository;
    private static ApplicationConfig config;
    private static AccountLifecycle lifecycle;

    @BeforeAll
    static void startDatabase() throws Exception {
        POSTGRES.copyFileToContainer(
                MountableFile.forHostPath(
                        Path.of("database", "bootstrap", "001__create_roles.sql")),
                "/tmp/001__create_roles.sql");
        var result = POSTGRES.execInContainer(
                "psql",
                "--username=" + POSTGRES.getUsername(),
                "--dbname=" + POSTGRES.getDatabaseName(),
                "--set=ON_ERROR_STOP=1",
                "--set=migrator_password=" + MIGRATOR_PASSWORD,
                "--set=app_password=" + APP_PASSWORD,
                "--file=/tmp/001__create_roles.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();

        config = new ApplicationConfig(
                POSTGRES.getJdbcUrl(),
                "uninafoodlab_app",
                APP_PASSWORD,
                "uninafoodlab_migrator",
                MIGRATOR_PASSWORD,
                "development");
        runtime = it.uninafoodlab.infrastructure.support.DemoDatabaseFixture.start(config);
        repository = new JdbcAccountRepository(
                runtime.dataSource(), new Pbkdf2PasswordHasher());
        lifecycle = new AccountLifecycle(
                new JdbcAccountLifecycleRepository(runtime.dataSource(), new Pbkdf2PasswordHasher()),
                new Pbkdf2PasswordHasher());
    }

    @AfterAll
    static void stopDatabase() {
        runtime.close();
    }

    @Test
    void authenticatesBothRoleSpecificProfilesAndIssuesOpaqueSessions() {
        var chef = repository.authenticate(
                Username.of("marta.rossi"), "MartaRossi!2026".toCharArray()).orElseThrow();
        var participant = repository.authenticate(
                Username.of("luca.bianchi"), "LucaBianchi!2026".toCharArray()).orElseThrow();

        assertThat(chef.role()).isEqualTo(Role.CHEF);
        assertThat(chef.displayName()).isEqualTo("Marta Rossi");
        assertThat(chef.profileId()).isNotNull();
        assertThat(chef.sessionToken()).isNotEqualTo(chef.profileId());
        assertThat(participant.role()).isEqualTo(Role.PARTICIPANT);
        assertThat(participant.displayName()).isEqualTo("Luca Bianchi");
        assertThat(participant.profileId()).isNotNull();
        assertThat(participant.sessionToken()).isNotEqualTo(participant.profileId());
    }

    @Test
    void returnsEmptyForAnUnknownUsername() {
        assertThat(repository.authenticate(
                        Username.of("unknown.demo"), "secret".toCharArray()))
                .isEmpty();
    }

    @Test
    void authenticatesBothRolesThroughTheSharedLoginBoundary() {
        var authenticate = new AuthenticateUser(repository);
        var chefPassword = "MartaRossi!2026".toCharArray();
        var participantPassword = "LucaBianchi!2026".toCharArray();

        assertThat(authenticate.execute("marta.rossi", chefPassword).role()).isEqualTo(Role.CHEF);
        assertThat(authenticate.execute("luca.bianchi", participantPassword).role())
                .isEqualTo(Role.PARTICIPANT);
        assertThat(chefPassword).containsOnly('\0');
        assertThat(participantPassword).containsOnly('\0');
    }

    @Test
    void registersParticipantsAndRejectsDuplicateUsernamesWithoutElevatedRoles() {
        var password = "RegistrationSecret!".toCharArray();

        var participant = lifecycle.registerParticipant("new.participant", "Nuova Partecipante", password);

        assertThat(participant.role()).isEqualTo(Role.PARTICIPANT);
        assertThat(password).containsOnly('\0');
        assertThat(repository.authenticate(Username.of("new.participant"),
                "RegistrationSecret!".toCharArray())).isPresent();
        assertThatThrownBy(() -> lifecycle.registerParticipant("new.participant", "Duplicata",
                "AnotherSecret!".toCharArray()))
                .isInstanceOf(AccountLifecycleException.class)
                .hasMessageContaining("registrazione");
    }

    @Test
    void registersChefsWithoutAnInvitationAndRejectsDuplicateUsernames() throws SQLException {
        var chef = lifecycle.registerChef("new.chef", "Nuovo Chef", "ChefRegistrationSecret!".toCharArray());

        assertThat(chef.role()).isEqualTo(Role.CHEF);
        assertThat(repository.authenticate(Username.of("new.chef"),
                "ChefRegistrationSecret!".toCharArray())).isPresent();
        assertThatThrownBy(() -> lifecycle.registerParticipant("new.chef", "Nome duplicato",
                "AnotherSecret!".toCharArray()))
                .isInstanceOf(AccountLifecycleException.class)
                .hasMessageContaining("registrazione");
        assertThat(count("select count(*) from app.account where username='new.chef'")).isOne();
        assertThat(count("select count(*) from app.chef_profile where account_id="
                + "(select id from app.account where username='new.chef')")).isOne();
    }

    @Test
    void changesAuthenticatedPasswordsAndRevokesThePreviousSession() throws SQLException {
        var participant = lifecycle.registerParticipant("credential.user", "Credenziale Utente",
                "OriginalSecret!".toCharArray());
        lifecycle.changePassword(participant, "OriginalSecret!".toCharArray(),
                "ChangedSecret!".toCharArray());
        assertThat(authSessionExists(participant.sessionToken())).isFalse();
        assertThat(repository.authenticate(Username.of("credential.user"),
                "OriginalSecret!".toCharArray())).isEmpty();
        assertThat(repository.authenticate(Username.of("credential.user"),
                "ChangedSecret!".toCharArray())).isPresent();

    }

    @Test
    void keepsProfilesAndHistoryWhenControlledProvisioningDisablesAnAccount() throws SQLException {
        var participant = lifecycle.registerParticipant("suspended.user", "Utente Sospeso",
                "SuspendSecret!".toCharArray());

        AccountProvisioning.disableAccount(config, "suspended.user");

        assertThat(repository.authenticate(Username.of("suspended.user"),
                "SuspendSecret!".toCharArray())).isEmpty();
        try (var connection = DriverManager.getConnection(config.databaseUrl(),
                config.databaseMigratorUser(), config.databaseMigratorPassword());
                var statement = connection.prepareStatement("""
                        select account.active, count(participant_profile.id)
                          from app.account left join app.participant_profile
                            on participant_profile.account_id = account.id
                         where account.id = ? group by account.active
                        """)) {
            statement.setObject(1, participant.accountId());
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(1)).isFalse();
                assertThat(result.getLong(2)).isEqualTo(1L);
            }
        }
    }

    @Test
    void doesNotGiveTheDesktopRoleAnyLegacyProvisioningPrivilege() throws SQLException {
        assertApplicationSqlFails("select app.activate_chef_invitation(?, ?, ?, ?)",
                UUID.randomUUID(), "legacy.chef", "Legacy Chef", "invalid-hash", "42883");
        assertApplicationSqlFails("select app.provision_chef_invitation(?, ?)", UUID.randomUUID(),
                java.time.OffsetDateTime.now().plusHours(1), "42883");
        assertApplicationSqlFails("select app.provision_password_reset(?, ?, ?)", "new.chef",
                UUID.randomUUID(), java.time.OffsetDateTime.now().plusHours(1), "42883");
        assertApplicationSqlFails("select app.open_auth_session(?)", UUID.randomUUID(), "42501");
        assertApplicationSqlFails("select count(*) from app.chef_invitation", "42501");
    }

    private static long count(String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(),
                config.databaseMigratorPassword()); var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void assertApplicationSqlFails(String sql, Object... values) {
        var expectedState = (String) values[values.length - 1];
        var parameters = java.util.Arrays.copyOf(values, values.length - 1);
        assertThatThrownBy(() -> {
            try (var connection = runtime.dataSource().getConnection();
                    var statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < parameters.length; index++) {
                    statement.setObject(index + 1, parameters[index]);
                }
                statement.execute();
            }
        }).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo(expectedState));
    }

    private static boolean authSessionExists(UUID token) throws SQLException {
        try (var connection = DriverManager.getConnection(config.databaseUrl(),
                config.databaseMigratorUser(), config.databaseMigratorPassword());
                var statement = connection.prepareStatement("""
                        select exists(select 1 from app.auth_session
                                      where token_hash = sha256(convert_to(?,'UTF8')))
                        """)) {
            statement.setString(1, token.toString());
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }
}
