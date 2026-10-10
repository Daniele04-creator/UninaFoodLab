package it.uninafoodlab.application.auth;

import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Username;
import java.util.Arrays;
import java.util.Objects;

public final class AuthenticateUser {

    private final AccountRepository accountRepository;

    public AuthenticateUser(AccountRepository accountRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
    }

    public AuthenticatedUser execute(String rawUsername, char[] password) {
        Objects.requireNonNull(password, "password");
        try {
            var username = normalizeUsername(rawUsername);
            if (password.length == 0) {
                throw new InvalidCredentialsException();
            }
            return accountRepository.authenticate(username, password)
                    .orElseThrow(InvalidCredentialsException::new);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private Username normalizeUsername(String rawUsername) {
        if (rawUsername == null) {
            throw new InvalidCredentialsException();
        }
        try {
            return Username.of(rawUsername);
        } catch (IllegalArgumentException _) {
            throw new InvalidCredentialsException();
        }
    }
}
