package it.uninafoodlab.application.course;

import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PublishedCourseSessionSummary(
        UUID id,
        int sequenceNumber,
        LocalDateTime scheduledAt,
        SessionMode mode,
        int durationMinutes,
        Optional<URI> onlineUrl,
        Optional<String> venue,
        Optional<Integer> capacity,
        Optional<LocalDateTime> attendanceClosesAt,
        SessionStatus status) {

    public PublishedCourseSessionSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(scheduledAt, "scheduledAt");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(onlineUrl, "onlineUrl");
        venue = Objects.requireNonNull(venue, "venue").map(String::strip);
        Objects.requireNonNull(capacity, "capacity");
        Objects.requireNonNull(attendanceClosesAt, "attendanceClosesAt");
        Objects.requireNonNull(status, "status");
        if (sequenceNumber < 1 || durationMinutes < 1) {
            throw new IllegalArgumentException("published session summary is invalid");
        }
    }

    public PublishedCourseSessionSummary(
            UUID id, int sequenceNumber, LocalDateTime scheduledAt, SessionMode mode,
            int durationMinutes, Optional<URI> onlineUrl, Optional<String> venue,
            Optional<Integer> capacity) {
        this(id, sequenceNumber, scheduledAt, mode, durationMinutes, onlineUrl, venue, capacity,
                Optional.empty(), SessionStatus.SCHEDULED);
    }

    public PublishedCourseSessionSummary(UUID id, int sequenceNumber, LocalDateTime scheduledAt,
            SessionMode mode, int durationMinutes, Optional<URI> onlineUrl, Optional<String> venue,
            Optional<Integer> capacity, Optional<LocalDateTime> attendanceClosesAt) {
        this(id, sequenceNumber, scheduledAt, mode, durationMinutes, onlineUrl, venue, capacity,
                attendanceClosesAt, SessionStatus.SCHEDULED);
    }
}
