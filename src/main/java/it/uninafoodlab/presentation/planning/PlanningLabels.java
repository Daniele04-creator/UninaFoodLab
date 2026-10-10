package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.planning.IngredientRequirement;
import it.uninafoodlab.application.planning.MonthlyReport;
import it.uninafoodlab.application.planning.SessionParticipant;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.time.format.DateTimeFormatter;

final class PlanningLabels {
    private PlanningLabels() {}
    static String stateLabel(String state) {
        return switch (state) {
            case "COMPLETED" -> "Svolta";
            case "CANCELLED" -> "Annullata";
            default -> "Programmata";
        };
    }

    static String sessionContext(CourseSessionSummary session) {
        return session.venue().orElse("Laboratorio") + session.attendanceClosesAt()
                .map(date -> ", Chiusura adesioni: " + date.format(DateTimeFormatter.ofPattern("d MMMM uuuu 'alle' HH:mm", Locale.ITALIAN)))
                .orElse("");
    }

    static String recipeStatisticsLabel(MonthlyReport report) {
        Objects.requireNonNull(report, "report");
        if (report.averageRecipes().isEmpty()) {
            return "Media —, Min —, Max —";
        }
        var average = ReportNumberFormat.format(report.averageRecipes().orElseThrow());
        return "Media " + average
                + ", Min " + report.minimumRecipes().orElseThrow()
                + ", Max " + report.maximumRecipes().orElseThrow();
    }

    static String periodLabel(YearMonth period) {
        if (period == null) {
            return "";
        }
        var month = period.getMonth().getDisplayName(TextStyle.FULL, Locale.ITALIAN);
        return Character.toUpperCase(month.charAt(0)) + month.substring(1) + " " + period.getYear();
    }

    static String quantityLabel(IngredientRequirement requirement) {
        return it.uninafoodlab.presentation.common.QuantityFormat.quantity(requirement.requiredQuantity(), requirement.unit());
    }

    static String planningSessionLabel(CourseSessionSummary session) {
        return session.scheduledAt().format(DateTimeFormatter.ofPattern("d MMMM uuuu, HH:mm", Locale.ITALIAN))
                + switch (session.status()) {
                    case CANCELLED -> ", Annullata";
                    case COMPLETED -> ", Svolta";
                    case SCHEDULED -> "";
                };
    }

    static String initials(String name) {
        return java.util.Arrays.stream(name.strip().split("\\s+"))
                .filter(part -> !part.isBlank())
                .limit(2)
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ITALIAN))
                .reduce("", String::concat);
    }

    static String participantSummary(List<SessionParticipant> participants) {
        Objects.requireNonNull(participants, "participants");
        var confirmed = participants.stream()
                .filter(participant -> participant.enrollmentStatus() == EnrollmentStatus.ACTIVE)
                .filter(participant -> participant.attendanceStatus()
                        .filter(status -> status == AttendanceStatus.CONFIRMED)
                        .isPresent())
                .count();
        var pending = participants.stream()
                .filter(participant -> participant.enrollmentStatus() == EnrollmentStatus.ACTIVE)
                .filter(participant -> participant.attendanceStatus().isEmpty())
                .count();
        var declined = participants.stream()
                .filter(participant -> participant.enrollmentStatus() == EnrollmentStatus.ACTIVE)
                .filter(participant -> participant.attendanceStatus()
                        .filter(status -> status == AttendanceStatus.DECLINED)
                        .isPresent())
                .count();
        var withdrawn = participants.stream()
                .filter(participant -> participant.enrollmentStatus() == EnrollmentStatus.WITHDRAWN)
                .count();

        var parts = new ArrayList<String>();
        parts.add(confirmed + (confirmed == 1 ? " confermato" : " confermati"));
        parts.add(pending + " senza scelta");
        parts.add(declined + (declined == 1 ? " rinuncia" : " rinunce"));
        if (withdrawn > 0) {
            parts.add(withdrawn + (withdrawn == 1 ? " ritirato" : " ritirati"));
        }
        return String.join(", ", parts);
    }
}
