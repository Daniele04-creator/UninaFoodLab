package it.uninafoodlab.application.schedule;

public final class ScheduleConflictException extends RuntimeException {

    public ScheduleConflictException() {
        super("Lo Chef ha già una sessione programmata in questo intervallo.");
    }
}
