package it.uninafoodlab.application.account;

import it.uninafoodlab.application.security.PasswordHasher;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Username;
import java.util.Arrays;
import java.util.Objects;

public final class AccountLifecycle {

    private static final int MINIMUM_PASSWORD_LENGTH = 12;

    private final AccountLifecycleRepository repository;
    private final PasswordHasher passwordHasher;

    public AccountLifecycle(AccountLifecycleRepository repository, PasswordHasher passwordHasher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    }

    public AuthenticatedUser registerParticipant(
            String rawUsername, String rawDisplayName, char[] password) {
        return createAccount(rawUsername, rawDisplayName, password,
                (username, displayName, hash) -> repository.registerParticipant(username, displayName, hash));
    }

    public AuthenticatedUser registerChef(
            String rawUsername, String rawDisplayName, char[] password) {
        return createAccount(rawUsername, rawDisplayName, password,
                (username, displayName, hash) -> repository.registerChef(username, displayName, hash));
    }

    public void changePassword(AuthenticatedUser actor, char[] currentPassword, char[] newPassword) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(currentPassword, "currentPassword");
        Objects.requireNonNull(newPassword, "newPassword");
        try {
            requirePassword(currentPassword);
            requirePassword(newPassword);
            repository.changePassword(actor.sessionToken(), actor.username(), currentPassword,
                    passwordHasher.hash(newPassword));
        } finally {
            Arrays.fill(currentPassword, '\0');
            Arrays.fill(newPassword, '\0');
        }
    }

    public void logout(AuthenticatedUser actor) {
        Objects.requireNonNull(actor, "actor");
        repository.logout(actor.sessionToken());
    }

    private AuthenticatedUser createAccount(
            String rawUsername, String rawDisplayName, char[] password, AccountCreator creator) {
        Objects.requireNonNull(password, "password");
        try {
            var username = username(rawUsername);
            var displayName = displayName(rawDisplayName);
            requirePassword(password);
            return creator.create(username, displayName, passwordHasher.hash(password));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static Username username(String rawUsername) {
        if (rawUsername == null) {
            throw new AccountLifecycleException("Scegli uno username valido.");
        }
        try {
            return Username.of(rawUsername);
        } catch (IllegalArgumentException exception) {
            throw new AccountLifecycleException("Scegli uno username valido.", exception);
        }
    }

    private static String displayName(String rawDisplayName) {
        if (rawDisplayName == null) {
            throw new AccountLifecycleException("Inserisci il nome da mostrare.");
        }
        var value = rawDisplayName.strip();
        if (value.isEmpty() || value.length() > 100) {
            throw new AccountLifecycleException("Inserisci un nome da mostrare valido.");
        }
        return value;
    }

    private static void requirePassword(char[] password) {
        if (password.length < MINIMUM_PASSWORD_LENGTH) {
            throw new AccountLifecycleException(
                    "La password deve contenere almeno 12 caratteri.");
        }
    }

    @FunctionalInterface
    private interface AccountCreator {
        AuthenticatedUser create(Username username, String displayName, String passwordHash);
    }
}
