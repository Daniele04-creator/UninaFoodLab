package it.uninafoodlab.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AuthenticateUserTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_TOKEN = UUID.fromString("70000000-0000-0000-0000-000000000001");

    @Test
    void authenticatesThroughTheRepositoryAndClearsThePassword() {
        var password = "secret".toCharArray();
        var useCase = new AuthenticateUser((username, suppliedPassword) ->
                "secret".contentEquals(new String(suppliedPassword))
                        ? Optional.of(user(username))
                        : Optional.empty());

        var authenticated = useCase.execute(" Chef.Demo ", password);

        assertThat(authenticated.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(authenticated.profileId()).isEqualTo(PROFILE_ID);
        assertThat(authenticated.sessionToken()).isEqualTo(SESSION_TOKEN);
        assertThat(authenticated.username()).isEqualTo(Username.of("chef.demo"));
        assertThat(authenticated.role()).isEqualTo(Role.CHEF);
        assertThat(password).containsOnly('\0');
    }

    @Test
    void authenticatesParticipantAccountsAndClearsThePassword() {
        var password = "secret".toCharArray();
        var useCase = new AuthenticateUser((username, suppliedPassword) -> Optional.of(
                new AuthenticatedUser(ACCOUNT_ID, PROFILE_ID, SESSION_TOKEN,
                        username, "Demo Participant", Role.PARTICIPANT)));

        assertThat(useCase.execute("participant.demo", password).role())
                .isEqualTo(Role.PARTICIPANT);
        assertThat(password).containsOnly('\0');
    }

    @Test
    void rejectsWrongCredentialsWithAGenericMessageAndClearsThePassword() {
        var password = "wrong".toCharArray();
        var useCase = new AuthenticateUser((username, suppliedPassword) -> Optional.empty());

        assertThatThrownBy(() -> useCase.execute("chef.demo", password))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Username o password non validi");
        assertThat(password).containsOnly('\0');
    }

    @Test
    void delegatesUnknownAccountsToTheConstantShapeDatabaseHandshake() {
        var invocations = new AtomicInteger();
        var useCase = new AuthenticateUser((username, suppliedPassword) -> {
            invocations.incrementAndGet();
            return Optional.empty();
        });

        var password = "secret".toCharArray();
        assertThatThrownBy(() -> useCase.execute("missing.user", password))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(invocations).hasValue(1);
    }

    @Test
    void rejectsMissingUsernameWithoutCallingTheRepositoryAndClearsThePassword() {
        var password = "secret".toCharArray();
        var useCase = new AuthenticateUser((username, suppliedPassword) -> {
            throw new AssertionError("Repository must not receive a missing username");
        });

        assertThatThrownBy(() -> useCase.execute(null, password))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Username o password non validi");
        assertThat(password).containsOnly('\0');
    }

    @Test
    void rejectsMalformedUsernameWithoutCallingTheRepositoryAndClearsThePassword() {
        var password = "secret".toCharArray();
        var useCase = new AuthenticateUser((username, suppliedPassword) -> {
            throw new AssertionError("Repository must not receive an invalid username");
        });

        assertThatThrownBy(() -> useCase.execute("not valid", password))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Username o password non validi");
        assertThat(password).containsOnly('\0');
    }

    private static AuthenticatedUser user(Username username) {
        return new AuthenticatedUser(
                ACCOUNT_ID,
                PROFILE_ID,
                SESSION_TOKEN,
                username,
                "Chef Sofia Esposito",
                Role.CHEF);
    }
}
