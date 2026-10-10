package it.uninafoodlab.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UsernameTest {

    @Test
    void normalizesWhitespaceAndCase() {
        assertThat(Username.of("  Chef.Demo ").value()).isEqualTo("chef.demo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "name with spaces", "utente@demo", "àbc"})
    void rejectsInvalidValues(String value) {
        assertThatThrownBy(() -> Username.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username must contain 3 to 50 lowercase letters, numbers, dots, underscores or hyphens");
    }

    @Test
    void authenticatedUserRejectsABlankDisplayName() {
        var accountId = UUID.randomUUID();
        var profileId = UUID.randomUUID();
        var sessionToken = UUID.randomUUID();
        var username = Username.of("chef.demo");
        assertThatThrownBy(() -> new AuthenticatedUser(
                        accountId,
                        profileId,
                        sessionToken,
                        username,
                        "  ",
                        Role.CHEF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Display name must not be blank");
    }
}
