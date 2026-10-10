package it.uninafoodlab.application.course;

import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public record CourseSessionSummary(
        UUID id,
        int sequenceNumber,
        LocalDateTime scheduledAt,
        SessionMode mode,
        SessionStatus status,
        int durationMinutes,
        Optional<URI> onlineUrl,
        Optional<String> venue,
        Optional<Integer> capacity,
        Optional<LocalDateTime> attendanceClosesAt,
        Optional<LocalDateTime> completedAt,
        List<UUID> recipeIds,
        int recipeCount) {

    public CourseSessionSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(scheduledAt, "scheduledAt");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(onlineUrl, "onlineUrl");
        venue = Objects.requireNonNull(venue, "venue").map(String::strip);
        Objects.requireNonNull(capacity, "capacity");
        Objects.requireNonNull(attendanceClosesAt, "attendanceClosesAt");
        Objects.requireNonNull(completedAt, "completedAt");
        recipeIds = List.copyOf(recipeIds);
        if (sequenceNumber < 1
                || durationMinutes < 1
                || recipeCount < 0) {
            throw new IllegalArgumentException("course session summary is invalid");
        }
    }

    public CourseSessionSummary(
            UUID id, int sequenceNumber, LocalDateTime scheduledAt, SessionMode mode, SessionStatus status,
            int durationMinutes, Optional<URI> onlineUrl, Optional<String> venue,
            Optional<Integer> capacity, Optional<LocalDateTime> attendanceClosesAt,
            Optional<LocalDateTime> completedAt, Optional<UUID> recipeId, int recipeCount) {
        this(id, sequenceNumber, scheduledAt, mode, status, durationMinutes, onlineUrl, venue, capacity,
                attendanceClosesAt, completedAt, recipeId.stream().toList(), recipeCount);
    }

    public Optional<UUID> recipeId() {
        return recipeIds.stream().findFirst();
    }

    public CourseSessionSummary(
            UUID id, int sequenceNumber, LocalDateTime scheduledAt, SessionMode mode, SessionStatus status,
            int durationMinutes, Optional<URI> onlineUrl, Optional<String> venue,
            Optional<Integer> capacity, Optional<LocalDateTime> attendanceClosesAt,
            Optional<LocalDateTime> completedAt, int recipeCount) {
        this(id, sequenceNumber, scheduledAt, mode, status, durationMinutes, onlineUrl, venue, capacity,
                attendanceClosesAt, completedAt, Optional.empty(), recipeCount);
    }

    public CourseSessionSummary(
            UUID id, int sequenceNumber, LocalDateTime scheduledAt, SessionMode mode, SessionStatus status,
            int durationMinutes, Optional<URI> onlineUrl, Optional<String> venue,
            Optional<Integer> capacity, int recipeCount) {
        this(id, sequenceNumber, scheduledAt, mode, status, durationMinutes, onlineUrl, venue, capacity,
                Optional.empty(), Optional.empty(), Optional.empty(), recipeCount);
    }
}
