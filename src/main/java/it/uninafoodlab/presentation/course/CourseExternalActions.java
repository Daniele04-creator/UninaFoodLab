package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class CourseExternalActions {
    private CourseExternalActions() {}

    static void shopping(boolean disabled, CourseSummary course, Consumer<CourseSummary> action) {
        if (!disabled && course != null && action != null) action.accept(course);
    }

    static void communication(boolean busy, CourseSummary course, Consumer<UUID> action) {
        if (!busy && course != null && action != null) action.accept(course.id());
    }

    static void requirements(boolean busy, CourseSummary course, CourseSessionSummary session,
            boolean embedded, Runnable openEmbedded, BiConsumer<UUID, UUID> fallback) {
        if (busy || course == null || session == null) return;
        if (embedded) openEmbedded.run();
        else if (fallback != null) fallback.accept(course.id(), session.id());
    }
}
