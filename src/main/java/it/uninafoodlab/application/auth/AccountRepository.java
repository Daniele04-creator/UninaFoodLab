package it.uninafoodlab.application.auth;

import it.uninafoodlab.domain.identity.Username;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import java.util.Optional;

public interface AccountRepository {

    Optional<AuthenticatedUser> authenticate(Username username, char[] password);
}
