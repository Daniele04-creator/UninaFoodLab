package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import java.util.UUID;
import javafx.scene.control.ScrollPane;

final class CourseDetailScroll {
    private final ScrollPane scroll;
    private Context context;

    CourseDetailScroll(ScrollPane scroll) {
        this.scroll = scroll;
    }

    void enter(CourseDetailSection section, CourseSummary course, CourseSessionSummary session) {

        if (course == null) return;
        boolean courseSection = section == CourseDetailSection.PROGRAM || section == CourseDetailSection.ENROLLMENTS;
        if (!courseSection && session == null) return;
        var next = new Context(section, course.id(), courseSection ? null : session.id());
        if (!next.equals(context)) {
            scroll.setVvalue(scroll.getVmin());
            scroll.setHvalue(scroll.getHmin());
            context = next;
        }
    }

    private record Context(CourseDetailSection section, UUID courseId, UUID sessionId) {}
}
