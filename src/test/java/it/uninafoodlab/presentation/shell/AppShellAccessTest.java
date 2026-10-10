package it.uninafoodlab.presentation.shell;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppShellAccessTest {

    @Test
    void participantCannotConstructTheDesktopShell() {
        var participant = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Username.of("luca.bianchi"), "Luca Bianchi", Role.PARTICIPANT);

        assertThatThrownBy(() -> new AppShellController(participant,
                null, null, null, null, null, null, null, null, null, null, () -> {}))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Desktop access is restricted to chefs");
    }
}
