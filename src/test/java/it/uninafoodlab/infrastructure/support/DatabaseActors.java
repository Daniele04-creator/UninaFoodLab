package it.uninafoodlab.infrastructure.support;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Username;
import it.uninafoodlab.infrastructure.auth.JdbcAccountRepository;
import it.uninafoodlab.infrastructure.security.Pbkdf2PasswordHasher;
import java.util.Arrays;

public final class DatabaseActors {

    private DatabaseActors() {}

    public static AuthenticatedUser authenticate(
            ApplicationRuntime runtime, String username, char[] password) {
        try {
            return new JdbcAccountRepository(runtime.dataSource(), new Pbkdf2PasswordHasher())
                    .authenticate(Username.of(username), password)
                    .orElseThrow(() -> new AssertionError("Synthetic database actor is missing"));
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
