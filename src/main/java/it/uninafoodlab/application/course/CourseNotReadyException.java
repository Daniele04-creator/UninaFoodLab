package it.uninafoodlab.application.course;

public final class CourseNotReadyException extends RuntimeException {

    public CourseNotReadyException() {
        super("Ogni sessione pratica deve avere almeno una ricetta prima della pubblicazione");
    }
}
