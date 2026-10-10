package it.uninafoodlab.application.enrollment;

import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PracticalAttendanceSummary(
        UUID sessionId,
        int sequenceNumber,
        LocalDateTime scheduledAt,
        int durationMinutes,
        String venue,
        int capacity,
        int confirmedCount,
        int remainingPlaces,
        LocalDateTime attendanceClosesAt,
        Optional<AttendanceStatus> attendanceStatus,
        int recipeCount) {

    public PracticalAttendanceSummary {
        if (recipeCount < 0) {
            throw new IllegalArgumentException("recipeCount must not be negative");
        }
        Objects.requireNonNull(sessionId, "sessionId");
        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber must be positive");
        }
        Objects.requireNonNull(scheduledAt, "scheduledAt");
        if (durationMinutes < 1) {
            throw new IllegalArgumentException("durationMinutes must be positive");
        }
        Objects.requireNonNull(venue, "venue");
        venue = venue.strip();
        if (venue.isEmpty()) {
            throw new IllegalArgumentException("venue must not be blank");
        }
        if (capacity < 1 || confirmedCount < 0 || remainingPlaces < 0
                || confirmedCount + remainingPlaces != capacity) {
            throw new IllegalArgumentException("capacity counters are inconsistent");
        }
        Objects.requireNonNull(attendanceClosesAt, "attendanceClosesAt");
        if (!attendanceClosesAt.isBefore(scheduledAt)) {
            throw new IllegalArgumentException("attendance cutoff must precede the session");
        }
        Objects.requireNonNull(attendanceStatus, "attendanceStatus");
    }

    public PracticalAttendanceSummary(
            UUID sessionId, int sequenceNumber, LocalDateTime scheduledAt, int durationMinutes,
            String venue, int capacity, int confirmedCount, int remainingPlaces,
            LocalDateTime attendanceClosesAt, Optional<AttendanceStatus> attendanceStatus) {
        this(sessionId, sequenceNumber, scheduledAt, durationMinutes, venue, capacity, confirmedCount,
                remainingPlaces, attendanceClosesAt, attendanceStatus, 1);
    }

    public boolean programReady() {
        return recipeCount > 0;
    }

    public PracticalAttendanceSummary(
            UUID sessionId, int sequenceNumber, LocalDateTime scheduledAt, int durationMinutes,
            String venue, int capacity, int confirmedCount, int remainingPlaces,
            Optional<AttendanceStatus> attendanceStatus) {
        this(sessionId, sequenceNumber, scheduledAt, durationMinutes, venue, capacity, confirmedCount,
                remainingPlaces, scheduledAt.minusMinutes(1), attendanceStatus);
    }

    public boolean isAttendanceOpen(LocalDateTime now) {
        return Objects.requireNonNull(now, "now").isBefore(attendanceClosesAt);
    }
}
