package it.uninafoodlab.application.auth;

public final class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Username o password non validi");
    }
}
