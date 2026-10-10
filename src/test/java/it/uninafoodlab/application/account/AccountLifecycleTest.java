package it.uninafoodlab.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import it.uninafoodlab.infrastructure.security.Pbkdf2PasswordHasher;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountLifecycleTest {

    @Test
    void registersOnlyParticipantsAndWipesTheSuppliedPassword() {
        var repository = new RecordingRepository();
        var lifecycle = new AccountLifecycle(repository, new Pbkdf2PasswordHasher());
        var password = "VeryLongSecret!".toCharArray();

        var user = lifecycle.registerParticipant("new.participant", "Nuova Persona", password);

        assertThat(user.role()).isEqualTo(Role.PARTICIPANT);
        assertThat(repository.registeredRole).isEqualTo(Role.PARTICIPANT);
        assertThat(repository.passwordHash).startsWith("$pbkdf2-sha256$600000$");
        assertThat(password).containsOnly('\0');
    }

    @Test
    void registersChefsWithoutAnInvitationAndWipesTheSuppliedPassword() {
        var repository = new RecordingRepository();
        var lifecycle = new AccountLifecycle(repository, new Pbkdf2PasswordHasher());
        var password = "VeryLongChefSecret!".toCharArray();

        var user = lifecycle.registerChef("new.chef", "Nuovo Chef", password);

        assertThat(user.role()).isEqualTo(Role.CHEF);
        assertThat(repository.registeredRole).isEqualTo(Role.CHEF);
        assertThat(repository.passwordHash).startsWith("$pbkdf2-sha256$600000$");
        assertThat(password).containsOnly('\0');
    }

    @Test
    void rejectsShortPasswordsBeforeCallingTheRepositoryAndWipesThem() {
        var repository = new RecordingRepository();
        var lifecycle = new AccountLifecycle(repository, new Pbkdf2PasswordHasher());
        var password = "short".toCharArray();

        assertThatThrownBy(() -> lifecycle.registerParticipant("valid.user", "Nome valido", password))
                .isInstanceOf(AccountLifecycleException.class)
                .hasMessageContaining("almeno 12");

        assertThat(repository.passwordHash).isNull();
        assertThat(password).containsOnly('\0');
    }

    @Test
    void changesPasswordsWithTheAuthenticatedSessionAndWipesBothBuffers() {
        var repository = new RecordingRepository();
        var lifecycle = new AccountLifecycle(repository, new Pbkdf2PasswordHasher());
        var current = "CurrentSecret!".toCharArray();
        var replacement = "ReplacementSecret!".toCharArray();

        lifecycle.changePassword(repository.user(Role.CHEF), current, replacement);

        assertThat(repository.changedSession).isEqualTo(repository.user(Role.CHEF).sessionToken());
        assertThat(repository.passwordHash).startsWith("$pbkdf2-sha256$600000$");
        assertThat(current).containsOnly('\0');
        assertThat(replacement).containsOnly('\0');
    }

    private static final class RecordingRepository implements AccountLifecycleRepository {
        private String passwordHash;
        private Role registeredRole;
        private UUID changedSession;

        @Override
        public AuthenticatedUser registerParticipant(Username username, String displayName, String hash) {
            passwordHash = hash;
            registeredRole = Role.PARTICIPANT;
            return user(Role.PARTICIPANT);
        }

        @Override
        public AuthenticatedUser registerChef(Username username, String displayName, String hash) {
            passwordHash = hash;
            registeredRole = Role.CHEF;
            return user(Role.CHEF);
        }

        @Override
        public void changePassword(UUID sessionToken, Username username, char[] currentPassword, String hash) {
            changedSession = sessionToken;
            passwordHash = hash;
        }

        @Override public void logout(UUID sessionToken) { }

        private AuthenticatedUser user(Role role) {
            return new AuthenticatedUser(UUID.fromString("10000000-0000-0000-0000-000000000001"),
                    UUID.fromString("20000000-0000-0000-0000-000000000001"),
                    UUID.fromString("30000000-0000-0000-0000-000000000001"),
                    Username.of("valid.user"), "Nome valido", role);
        }
    }
}
