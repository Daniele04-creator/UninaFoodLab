package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.application.course.PublishedCourseSummary;
import it.uninafoodlab.application.enrollment.EnrollmentSummary;
import it.uninafoodlab.application.enrollment.PracticalAttendanceSummary;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class ParticipantWorkspaceRules {

    static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("d MMMM yyyy 'alle' HH:mm", Locale.ITALIAN);
    static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN);
    static final ZoneId ROME = ZoneId.of("Europe/Rome");

    private ParticipantWorkspaceRules() {}

    static List<EnrollmentSummary> enrollmentContext(List<EnrollmentSummary> enrollments,
            EnrollmentSummary selectedEnrollment, PublishedCourseSummary course) {
        if (course == null || enrollments.stream().anyMatch(enrollment -> enrollment.courseId().equals(course.id()))) {
            return enrollments;
        }
        if (selectedEnrollment == null || !selectedEnrollment.courseId().equals(course.id())) return enrollments;
        var context = new java.util.ArrayList<>(enrollments);
        context.add(selectedEnrollment);
        return List.copyOf(context);
    }

    static Optional<PublishedCourseSessionSummary> nextAppointment(
            List<PublishedCourseSessionSummary> sessions, LocalDateTime now) {
        Objects.requireNonNull(sessions, "sessions");
        Objects.requireNonNull(now, "now");
        return sessions.stream()
                .filter(session -> session.status() != it.uninafoodlab.domain.course.SessionStatus.CANCELLED
                        && session.status() != it.uninafoodlab.domain.course.SessionStatus.COMPLETED
                        && session.scheduledAt().plusMinutes(session.durationMinutes()).isAfter(now))
                .min(Comparator.comparing(PublishedCourseSessionSummary::scheduledAt));
    }

    static List<PublishedCourseSummary> filterCourses(
            List<PublishedCourseSummary> values, String query) {
        var normalized = query == null ? "" : query.strip().toLowerCase(Locale.ITALIAN);
        return values.stream()
                .filter(course -> normalized.isBlank()
                        || (course.title().value() + " " + course.categoryName() + " "
                        + course.chefName() + " " + course.description())
                        .toLowerCase(Locale.ITALIAN).contains(normalized))
                .sorted(Comparator.comparing(PublishedCourseSummary::startDate)
                        .thenComparing(course -> course.title().value(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    static boolean canConfirmAttendance(PracticalAttendanceSummary session, LocalDateTime now) {
        if (!attendanceWindowOpen(session, now)) {
            return false;
        }
        return session.remainingPlaces() > 0
                || session.attendanceStatus().orElse(null) == AttendanceStatus.CONFIRMED;
    }

    static boolean canDeclineAttendance(PracticalAttendanceSummary session, LocalDateTime now) {
        return attendanceWindowOpen(session, now)
                && session.attendanceStatus().orElse(null) != AttendanceStatus.DECLINED;
    }

    static boolean canChangeAttendance(
            PracticalAttendanceSummary session, AttendanceStatus status, LocalDateTime now) {
        Objects.requireNonNull(status, "status");
        return status == AttendanceStatus.CONFIRMED
                ? canConfirmAttendance(session, now)
                : canDeclineAttendance(session, now);
    }

    private static boolean attendanceWindowOpen(PracticalAttendanceSummary session, LocalDateTime now) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(now, "now");
        return session.programReady() && session.isAttendanceOpen(now);
    }

    static boolean requiresConflictConfirmation(
            AttendanceStatus status, Optional<String> conflict) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(conflict, "conflict");
        return status == AttendanceStatus.CONFIRMED && conflict.isPresent();
    }

    static boolean canPersistAttendanceChange(
            AttendanceStatus status, Optional<String> conflict, boolean confirmationAccepted) {
        return !requiresConflictConfirmation(status, conflict) || confirmationAccepted;
    }

    static String conflictPrompt(PracticalAttendanceSummary candidate, String conflict) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(conflict, "conflict");
        return "La sessione del " + DATE_TIME.format(candidate.scheduledAt()) + " presso "
                + candidate.venue() + " si sovrappone a " + conflict + ".";
    }

    static boolean overlaps(PracticalAttendanceSummary first, PracticalAttendanceSummary second) {
        var firstEnd = first.scheduledAt().plusMinutes(first.durationMinutes());
        var secondEnd = second.scheduledAt().plusMinutes(second.durationMinutes());
        return first.scheduledAt().isBefore(secondEnd)
                && second.scheduledAt().isBefore(firstEnd);
    }

    static String summaryForCourses(int count) {
        return count + (count == 1
                ? " corso disponibile o nello storico personale"
                : " corsi disponibili o nello storico personale")
                + ". Scegli una scheda per consultare il programma.";
    }

    static String courseMeta(PublishedCourseSummary course) {
        return "Chef " + course.chefName() + "\nDal " + course.startDate().format(DATE)
                + "\n" + course.sessionCount() + (course.sessionCount() == 1 ? " sessione" : " sessioni");
    }

    static String sessionLocation(PublishedCourseSessionSummary session) {
        return session.mode() == SessionMode.ONLINE
                ? session.onlineUrl().map(uri -> "Online, link disponibile").orElse("Online")
                : session.venue().orElse("Sede da definire");
    }


    static Optional<String> authorizedOnlineLink(PublishedCourseSessionSummary session) {
        Objects.requireNonNull(session, "session");
        return session.mode() == SessionMode.ONLINE
                ? session.onlineUrl().filter(uri -> it.uninafoodlab.presentation.common.SessionLocationLinks
                        .fromVenue(Optional.of(uri.toString())).isPresent()).map(java.net.URI::toASCIIString)
                : Optional.empty();
    }

    static String formatMode(SessionMode mode) {
        return mode == SessionMode.ONLINE ? "Online" : "In presenza";
    }

    static String enrollmentStatus(EnrollmentStatus status) {
        return status == EnrollmentStatus.ACTIVE ? "Iscrizione attiva" : "Ritirato";
    }

    static boolean isEnrolled(List<EnrollmentSummary> enrollments, UUID courseId) {
        Objects.requireNonNull(enrollments, "enrollments");
        Objects.requireNonNull(courseId, "courseId");
        return enrollments.stream().anyMatch(item -> item.courseId().equals(courseId)
                && item.status() == EnrollmentStatus.ACTIVE);
    }

    static boolean enrollmentChanged(EnrollmentSummary previous, EnrollmentSummary current) {
        return current == null || previous == null || !current.courseId().equals(previous.courseId());
    }

    static String attendanceStatus(PracticalAttendanceSummary session) {
        return session.attendanceStatus().map(status -> status == AttendanceStatus.CONFIRMED
                ? "Presenza confermata" : "Hai rinunciato a questa sessione")
                .orElse("Nessuna risposta registrata");
    }
}
