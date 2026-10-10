package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class CourseWorkspaceView {
    private static final DateTimeFormatter MONTH_YEAR_FORMAT =
            DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ITALIAN);
    private static final DateTimeFormatter DAY_MONTH_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN);
    private static final DateTimeFormatter DAY_MONTH_YEAR_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ITALIAN);

    private CourseWorkspaceView() {}

    static String formatCourseDateRange(CourseSummary course) {
        Objects.requireNonNull(course, "course");
        var start = course.startDate();
        var end = course.lastSessionEndsAt()
                .map(LocalDateTime::toLocalDate)
                .orElseGet(() -> start.plusDays((long) (course.sessionCount() - 1) * course.recurrenceDays()));
        return formatCourseDateRange(start, end.isBefore(start) ? start : end);
    }

    static String formatCourseDateRange(LocalDate start, LocalDate end) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (start.equals(end)) return CoursePresentation.COURSE_DATE_FORMAT.format(start);
        if (start.getYear() == end.getYear() && start.getMonth() == end.getMonth()) {
            return start.getDayOfMonth() + "–" + end.getDayOfMonth() + " "
                    + MONTH_YEAR_FORMAT.format(end);
        }
        if (start.getYear() == end.getYear()) {
            return DAY_MONTH_FORMAT.format(start) + " – " + DAY_MONTH_YEAR_FORMAT.format(end);
        }
        return CoursePresentation.COURSE_DATE_FORMAT.format(start) + " – "
                + CoursePresentation.COURSE_DATE_FORMAT.format(end);
    }

    static List<String> assignedRecipeNames(CourseSessionSummary session, List<RecipeSummary> catalog) {
        Objects.requireNonNull(catalog, "catalog");
        if (session == null) return List.of();
        return catalog.stream()
                .filter(recipe -> session.recipeIds().contains(recipe.id()))
                .map(RecipeSummary::name)
                .toList();
    }
}
