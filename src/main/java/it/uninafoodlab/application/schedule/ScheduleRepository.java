package it.uninafoodlab.application.schedule;

import it.uninafoodlab.application.notification.NotificationDraft;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleRepository {

    void reschedule(
            UUID sessionToken, UUID sessionId, LocalDateTime scheduledAt,
            Optional<LocalDateTime> attendanceClosesAt, Optional<String> location,
            NotificationDraft draft);

    default void reschedule(UUID sessionToken, UUID sessionId, LocalDateTime scheduledAt,
            Optional<LocalDateTime> attendanceClosesAt, NotificationDraft draft) {
        reschedule(sessionToken, sessionId, scheduledAt, attendanceClosesAt, Optional.empty(), draft);
    }

    default void reschedule(
            UUID sessionToken, UUID sessionId, LocalDateTime scheduledAt,
            NotificationDraft draft) {
        reschedule(sessionToken, sessionId, scheduledAt, Optional.empty(), draft);
    }

    UUID add(
            UUID sessionToken, UUID courseId, NewSessionInput input,
            NotificationDraft draft);

    void cancel(UUID sessionToken, UUID sessionId, NotificationDraft draft);

}
