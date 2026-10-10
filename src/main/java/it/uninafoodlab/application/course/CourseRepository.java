package it.uninafoodlab.application.course;

import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository {

    UUID create(CourseCreation course);

    UUID createAndPublish(CourseCreation course);

    default Page<CourseSummary> findByChef(UUID sessionToken, CourseFilter filter, PageRequest page) {
        throw new UnsupportedOperationException();
    }

    default Page<PublishedCourseSummary> findPublished(UUID sessionToken, CourseFilter filter, PageRequest page) {
        throw new UnsupportedOperationException();
    }

    default Map<UUID, List<CourseSessionSummary>> sessions(UUID sessionToken, List<UUID> courseIds) {
        throw new UnsupportedOperationException();
    }

    default Optional<CourseSummary> findByChef(UUID sessionToken, UUID courseId) {
        throw new UnsupportedOperationException();
    }

    default List<PublishedCourseSummary> findPublishedByIds(UUID sessionToken, List<UUID> courseIds) {
        throw new UnsupportedOperationException();
    }

    default Map<UUID, List<PublishedCourseSessionSummary>> publishedSessions(UUID sessionToken, List<UUID> courseIds) {
        throw new UnsupportedOperationException();
    }

    List<CourseSummary> findByChef(UUID sessionToken, Optional<UUID> categoryId);

    default List<CourseSummary> findByChef(
            UUID sessionToken,
            Optional<UUID> categoryId,
            Optional<CourseStatus> status) {
        return findByChef(sessionToken, categoryId).stream()
                .filter(course -> status.isEmpty() || course.status() == status.orElseThrow())
                .toList();
    }

    List<CourseSessionSummary> sessions(UUID sessionToken, UUID courseId);

    void assignRecipe(UUID sessionToken, UUID sessionId, UUID recipeId);

    default void removeRecipe(UUID sessionToken, UUID sessionId, UUID recipeId) {
        throw new UnsupportedOperationException();
    }

    default void publish(UUID sessionToken, UUID courseId) {
        throw new UnsupportedOperationException();
    }

    List<PublishedCourseSessionSummary> publishedSessions(UUID sessionToken, UUID courseId);

    default List<PublishedSessionRecipeSummary> publishedMenu(UUID sessionToken, UUID courseId) {
        throw new UnsupportedOperationException();
    }
}
