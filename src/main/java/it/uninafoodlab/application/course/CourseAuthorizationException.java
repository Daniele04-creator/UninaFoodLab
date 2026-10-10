package it.uninafoodlab.application.course;

public final class CourseAuthorizationException extends RuntimeException {

    public CourseAuthorizationException() {
        super("Operazione riservata agli chef");
    }

    public CourseAuthorizationException(String message) {
        super(message);
    }
}
