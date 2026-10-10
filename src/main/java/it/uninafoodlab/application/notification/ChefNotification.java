package it.uninafoodlab.application.notification;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ChefNotification(
        UUID id,
        String title,
        String message,
        NotificationReason reason,
        Optional<String> courseTitle,
        Instant createdAt,
        long recipientCount,
        Optional<UUID> courseId,
        Optional<java.time.LocalDate> courseStartDate) {

    public ChefNotification(UUID id, String title, String message, NotificationReason reason,
            Optional<String> courseTitle, Instant createdAt, long recipientCount) {
        this(id, title, message, reason, courseTitle, createdAt, recipientCount, Optional.empty(), Optional.empty());
    }

    public ChefNotification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(courseTitle, "courseTitle");
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(courseStartDate, "courseStartDate");
        Objects.requireNonNull(createdAt, "createdAt");
        if (recipientCount < 0) {
            throw new IllegalArgumentException("Recipient count must not be negative");
        }
    }
}
