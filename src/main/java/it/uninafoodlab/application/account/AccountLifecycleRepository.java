package it.uninafoodlab.application.account;

import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Username;
import java.util.UUID;

public interface AccountLifecycleRepository {

    AuthenticatedUser registerParticipant(Username username, String displayName, String passwordHash);

    AuthenticatedUser registerChef(Username username, String displayName, String passwordHash);

    void changePassword(UUID sessionToken, Username username, char[] currentPassword, String newPasswordHash);

    void logout(UUID sessionToken);
}
