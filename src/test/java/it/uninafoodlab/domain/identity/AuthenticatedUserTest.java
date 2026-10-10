package it.uninafoodlab.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthenticatedUserTest {
    @Test
    void diagnosticTextDoesNotExposeSessionToken() {
        var token = UUID.randomUUID();
        var user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), token,
                Username.of("test.chef"), "Chef sintetico", Role.CHEF);

        assertThat(String.valueOf(user)).contains("AuthenticatedUser").doesNotContain(token.toString());
        assertThat(user.sessionToken()).isEqualTo(token);
    }
}
