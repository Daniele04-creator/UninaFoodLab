package it.uninafoodlab.application.enrollment;

public final class EnrollmentAuthorizationException extends RuntimeException {

    public EnrollmentAuthorizationException() {
        super("Operazione riservata ai partecipanti");
    }
}
