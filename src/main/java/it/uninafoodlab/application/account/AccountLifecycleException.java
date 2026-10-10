package it.uninafoodlab.application.account;

public final class AccountLifecycleException extends RuntimeException {

    public AccountLifecycleException(String message) {
        super(message);
    }

    public AccountLifecycleException(String message, Throwable cause) {
        super(message, cause);
    }
}
