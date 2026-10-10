package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.enrollment.AttendanceCommitment;
import it.uninafoodlab.application.enrollment.PracticalAttendanceSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class ParticipantWorkspaceAttendance {
    private ParticipantWorkspaceAttendance() {}

    static String warning(UUID courseId, List<PracticalAttendanceSummary> sessions,
            List<AttendanceCommitment> commitments) {
        return sessions.stream().map(session -> conflict(session, courseId, commitments))
                .flatMap(Optional::stream).findFirst()
                .map(message -> "Attenzione: una tua presenza confermata si sovrappone a "
                        + message + ". Controlla gli orari prima di rispondere.").orElse("");
    }

    static Optional<String> conflict(PracticalAttendanceSummary candidate, UUID currentCourseId,
            List<AttendanceCommitment> commitments) {
        var candidateEnd = candidate.scheduledAt().plusMinutes(candidate.durationMinutes());
        return commitments.stream().filter(commitment -> !commitment.courseId().equals(currentCourseId))
                .filter(commitment -> candidate.scheduledAt().isBefore(
                        commitment.scheduledAt().plusMinutes(commitment.durationMinutes()))
                        && commitment.scheduledAt().isBefore(candidateEnd))
                .map(commitment -> "il corso \"" + commitment.courseTitle().value() + "\" alle "
                        + ParticipantWorkspaceRules.DATE_TIME.format(commitment.scheduledAt()))
                .findFirst();
    }
}
