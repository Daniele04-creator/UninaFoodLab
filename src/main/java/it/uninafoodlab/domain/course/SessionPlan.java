package it.uninafoodlab.domain.course;

import java.time.LocalDateTime;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

public record SessionPlan(
        int sequenceNumber,
        LocalDateTime scheduledAt,
        SessionDetails details,
        Optional<LocalDateTime> attendanceClosesAt) {

    public SessionPlan {
        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("session sequence number must be positive");
        }
        Objects.requireNonNull(scheduledAt, "scheduledAt");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(attendanceClosesAt, "attendanceClosesAt");
        if (details.mode() == SessionMode.ONLINE && attendanceClosesAt.isPresent()) {
            throw new IllegalArgumentException("online sessions must not have an attendance cutoff");
        }
        if (details.mode() == SessionMode.PRACTICAL) {
            var cutoff = attendanceClosesAt.orElseThrow(() ->
                    new IllegalArgumentException("practical sessions require an attendance cutoff"));
            if (cutoff.compareTo(scheduledAt) >= 0) {
                throw new IllegalArgumentException("practical attendance cutoff must precede the session");
            }
        }
    }

    public SessionPlan(int sequenceNumber, LocalDateTime scheduledAt, SessionDetails details) {
        this(
                sequenceNumber,
                scheduledAt,
                details,
                details.mode() == SessionMode.PRACTICAL
                        ? Optional.of(scheduledAt.minusMinutes(1))
                        : Optional.empty());
    }

    public SessionMode mode() {
        return details.mode();
    }

    public int durationMinutes() {
        return details.durationMinutes();
    }

    public Optional<URI> onlineUrl() {
        return details.onlineUrl();
    }

    public Optional<String> venue() {
        return details.venue();
    }

    public Optional<Integer> capacity() {
        return details.capacity();
    }
}
