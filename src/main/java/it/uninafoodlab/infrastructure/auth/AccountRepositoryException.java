package it.uninafoodlab.infrastructure.auth;

public final class AccountRepositoryException extends RuntimeException {

    public AccountRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
