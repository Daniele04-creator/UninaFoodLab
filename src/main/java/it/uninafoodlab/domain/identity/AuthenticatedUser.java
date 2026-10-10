package it.uninafoodlab.domain.identity;

import java.util.Objects;
import java.util.UUID;

public record AuthenticatedUser(
        UUID accountId,
        UUID profileId,
        UUID sessionToken,
        Username username,
        String displayName,
        Role role) {

    public AuthenticatedUser {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(sessionToken, "sessionToken");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(role, "role");
        displayName = displayName.strip();
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("Display name must not be blank");
        }
    }

    @Override
    public String toString() {
        return "AuthenticatedUser[role=" + role + ", identity and session redacted]";
    }
}
