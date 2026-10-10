package it.uninafoodlab.infrastructure.security;

public final class PasswordHashingException extends RuntimeException {

    public PasswordHashingException(String message, Throwable cause) {
        super(message, cause);
    }
}
