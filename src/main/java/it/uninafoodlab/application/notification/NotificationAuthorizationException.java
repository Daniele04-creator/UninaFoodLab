package it.uninafoodlab.application.notification;

public final class NotificationAuthorizationException extends RuntimeException {

    public NotificationAuthorizationException() {
        super("Operazione non disponibile per questo profilo");
    }
}
