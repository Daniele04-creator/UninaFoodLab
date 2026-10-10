package it.uninafoodlab.application.enrollment;

public final class AttendanceUnavailableException extends RuntimeException {

    public AttendanceUnavailableException() {
        super("La sessione pratica non è disponibile per questa adesione");
    }
}
