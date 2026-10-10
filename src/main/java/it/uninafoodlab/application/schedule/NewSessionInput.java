package it.uninafoodlab.application.schedule;

import it.uninafoodlab.domain.course.SessionDetails;
import it.uninafoodlab.domain.course.SessionMode;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

public record NewSessionInput(
        LocalDateTime scheduledAt,
        SessionMode mode,
        int durationMinutes,
        Optional<URI> onlineUrl,
        Optional<String> venue,
        Optional<Integer> capacity,
        Optional<LocalDateTime> attendanceClosesAt) {

    public NewSessionInput {
        Objects.requireNonNull(scheduledAt, "scheduledAt");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(onlineUrl, "onlineUrl");
        Objects.requireNonNull(venue, "venue");
        Objects.requireNonNull(capacity, "capacity");
        Objects.requireNonNull(attendanceClosesAt, "attendanceClosesAt");
        new SessionDetails(mode, durationMinutes, onlineUrl, venue, capacity);
        if (mode == SessionMode.ONLINE && attendanceClosesAt.isPresent()) {
            throw new IllegalArgumentException("online sessions must not have an attendance cutoff");
        }
        if (mode == SessionMode.PRACTICAL) {
            var cutoff = attendanceClosesAt.orElseThrow(() ->
                    new IllegalArgumentException("practical sessions require an attendance cutoff"));
            if (!cutoff.isBefore(scheduledAt)) {
                throw new IllegalArgumentException("practical attendance cutoff must precede the session");
            }
        }
    }

    public NewSessionInput(
            LocalDateTime scheduledAt,
            SessionMode mode,
            int durationMinutes,
            Optional<URI> onlineUrl,
            Optional<String> venue,
            Optional<Integer> capacity) {
        this(
                scheduledAt,
                mode,
                durationMinutes,
                onlineUrl,
                venue,
                capacity,
                mode == SessionMode.PRACTICAL
                        ? Optional.of(scheduledAt.minusMinutes(1))
                        : Optional.empty());
    }
}
