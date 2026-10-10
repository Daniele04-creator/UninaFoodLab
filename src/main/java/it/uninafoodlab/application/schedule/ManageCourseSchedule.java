package it.uninafoodlab.application.schedule;

import it.uninafoodlab.application.course.CourseAuthorizationException;
import it.uninafoodlab.application.notification.NotificationDraft;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ManageCourseSchedule {

    private static final String DRAFT = "draft";

    private final ScheduleRepository repository;

    public ManageCourseSchedule(ScheduleRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void reschedule(
            AuthenticatedUser actor, UUID sessionId, LocalDateTime scheduledAt,
            Optional<LocalDateTime> attendanceClosesAt,
            NotificationDraft draft) {
        requireChef(actor);
        repository.reschedule(
                actor.sessionToken(), Objects.requireNonNull(sessionId, "sessionId"),
                Objects.requireNonNull(scheduledAt, "scheduledAt"),
                Objects.requireNonNull(attendanceClosesAt, "attendanceClosesAt"),
                Objects.requireNonNull(draft, DRAFT));
    }

    public void reschedule(
            AuthenticatedUser actor, UUID sessionId, LocalDateTime scheduledAt,
            NotificationDraft draft) {
        reschedule(actor, sessionId, scheduledAt, Optional.empty(), draft);
    }

    public void reschedule(AuthenticatedUser actor, UUID sessionId, LocalDateTime scheduledAt,
            Optional<LocalDateTime> attendanceClosesAt, String location, NotificationDraft draft) {
        requireChef(actor);
        var normalized = Objects.requireNonNull(location, "location").strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Indica il link HTTPS o la sede della sessione.");
        repository.reschedule(actor.sessionToken(), Objects.requireNonNull(sessionId, "sessionId"),
                Objects.requireNonNull(scheduledAt, "scheduledAt"),
                Objects.requireNonNull(attendanceClosesAt, "attendanceClosesAt"), Optional.of(normalized),
                Objects.requireNonNull(draft, DRAFT));
    }

    public UUID add(
            AuthenticatedUser actor, UUID courseId, NewSessionInput input,
            NotificationDraft draft) {
        requireChef(actor);
        return repository.add(
                actor.sessionToken(), Objects.requireNonNull(courseId, "courseId"),
                Objects.requireNonNull(input, "input"),
                Objects.requireNonNull(draft, DRAFT));
    }

    public void cancel(
            AuthenticatedUser actor, UUID sessionId, NotificationDraft draft) {
        requireChef(actor);
        repository.cancel(
                actor.sessionToken(), Objects.requireNonNull(sessionId, "sessionId"),
                Objects.requireNonNull(draft, DRAFT));
    }

    private static void requireChef(AuthenticatedUser actor) {
        Objects.requireNonNull(actor, "actor");
        if (actor.role() != Role.CHEF) {
            throw new CourseAuthorizationException();
        }
    }
}
