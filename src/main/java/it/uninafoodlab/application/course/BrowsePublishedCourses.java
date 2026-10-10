package it.uninafoodlab.application.course;

import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.domain.identity.Role;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class BrowsePublishedCourses {

    private final CourseRepository courseRepository;

    public BrowsePublishedCourses(CourseRepository courseRepository) {
        this.courseRepository = Objects.requireNonNull(courseRepository, "courseRepository");
    }

    public List<PublishedCourseSessionSummary> sessions(
            AuthenticatedUser actor, UUID courseId) {
        requireParticipant(actor);
        return List.copyOf(courseRepository.publishedSessions(
                actor.sessionToken(),
                Objects.requireNonNull(courseId, "courseId")));
    }

    public Page<PublishedCourseSummary> courses(AuthenticatedUser actor, CourseFilter filter, PageRequest page) {
        requireParticipant(actor);
        return courseRepository.findPublished(actor.sessionToken(),
                Objects.requireNonNull(filter, "filter"), Objects.requireNonNull(page, "page"));
    }

    public List<PublishedCourseSummary> courses(AuthenticatedUser actor, List<UUID> courseIds) {
        requireParticipant(actor);
        return List.copyOf(courseRepository.findPublishedByIds(actor.sessionToken(), List.copyOf(courseIds)));
    }

    public Map<UUID, List<PublishedCourseSessionSummary>> sessions(AuthenticatedUser actor, List<UUID> courseIds) {
        requireParticipant(actor);
        return Map.copyOf(courseRepository.publishedSessions(actor.sessionToken(), List.copyOf(courseIds)));
    }

    public List<PublishedSessionRecipeSummary> menu(AuthenticatedUser actor, UUID courseId) {
        requireParticipant(actor);
        return List.copyOf(courseRepository.publishedMenu(actor.sessionToken(),
                Objects.requireNonNull(courseId, "courseId")));
    }

    private static void requireParticipant(AuthenticatedUser actor) {
        Objects.requireNonNull(actor, "actor");
        if (actor.role() != Role.PARTICIPANT) {
            throw new CourseAuthorizationException("Operazione riservata ai partecipanti");
        }
    }
}
