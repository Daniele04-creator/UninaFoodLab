package it.uninafoodlab.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Pbkdf2PasswordHasherTest {

    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();

    @Test
    void createsSaltedHashesThatCanBeVerified() {
        var password = "ChefDemo!2026".toCharArray();

        var first = hasher.hash(password);
        var second = hasher.hash(password);

        assertThat(first)
                .startsWith("$pbkdf2-sha256$600000$")
                .doesNotContain("ChefDemo!2026")
                .isNotEqualTo(second);
        assertThat(hasher.matches(password, first)).isTrue();
        assertThat(hasher.matches(password, second)).isTrue();
    }

    @Test
    void rejectsAWrongPassword() {
        var encoded = hasher.hash("ParticipantDemo!2026".toCharArray());

        assertThat(hasher.matches("wrong-password".toCharArray(), encoded)).isFalse();
    }

    @Test
    void malformedHashesFailClosed() {
        assertThat(hasher.matches("password".toCharArray(), "not-a-valid-hash")).isFalse();
        assertThat(hasher.matches(
                        "password".toCharArray(), "$pbkdf2-sha256$wrong$bad$bad"))
                .isFalse();
    }

    @Test
    void rejectsEmptyPasswordsWithoutEchoingThem() {
        assertThatThrownBy(() -> hasher.hash(new char[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password must not be empty");
    }
}
