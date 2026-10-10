package it.uninafoodlab.application.notification;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ParticipantNotification(
        UUID id,
        String title,
        String message,
        NotificationReason reason,
        Optional<String> courseTitle,
        Instant createdAt,
        Optional<Instant> readAt,
        Optional<UUID> courseId) {

    public ParticipantNotification(UUID id, String title, String message, NotificationReason reason,
            Optional<String> courseTitle, Instant createdAt, Optional<Instant> readAt) {
        this(id, title, message, reason, courseTitle, createdAt, readAt, Optional.empty());
    }

    public ParticipantNotification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(courseTitle, "courseTitle");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(readAt, "readAt");
        Objects.requireNonNull(courseId, "courseId");
    }
}
