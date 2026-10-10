package it.uninafoodlab.application.course;

import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class BrowseCourses {

    private final CourseRepository courseRepository;

    public BrowseCourses(CourseRepository courseRepository) {
        this.courseRepository = Objects.requireNonNull(courseRepository, "courseRepository");
    }

    public List<CourseSummary> courses(
            AuthenticatedUser actor, Optional<UUID> categoryId) {
        return courses(actor, categoryId, Optional.empty());
    }

    public List<CourseSummary> courses(
            AuthenticatedUser actor,
            Optional<UUID> categoryId,
            Optional<CourseStatus> status) {
        requireChef(actor);
        return List.copyOf(courseRepository.findByChef(
                actor.sessionToken(),
                Objects.requireNonNull(categoryId, "categoryId"),
                Objects.requireNonNull(status, "status")));
    }

    public List<CourseSessionSummary> sessions(AuthenticatedUser actor, UUID courseId) {
        requireChef(actor);
        return List.copyOf(courseRepository.sessions(
                actor.sessionToken(), Objects.requireNonNull(courseId, "courseId")));
    }

    public Page<CourseSummary> courses(AuthenticatedUser actor, CourseFilter filter, PageRequest page) {
        requireChef(actor);
        return courseRepository.findByChef(actor.sessionToken(),
                Objects.requireNonNull(filter, "filter"), Objects.requireNonNull(page, "page"));
    }

    public Map<UUID, List<CourseSessionSummary>> sessions(AuthenticatedUser actor, List<UUID> courseIds) {
        requireChef(actor);
        return Map.copyOf(courseRepository.sessions(actor.sessionToken(), List.copyOf(courseIds)));
    }

    public Optional<CourseSummary> course(AuthenticatedUser actor, UUID courseId) {
        requireChef(actor);
        return courseRepository.findByChef(actor.sessionToken(), Objects.requireNonNull(courseId, "courseId"));
    }

    private static void requireChef(AuthenticatedUser actor) {
        Objects.requireNonNull(actor, "actor");
        if (actor.role() != Role.CHEF) {
            throw new CourseAuthorizationException();
        }
    }
}
