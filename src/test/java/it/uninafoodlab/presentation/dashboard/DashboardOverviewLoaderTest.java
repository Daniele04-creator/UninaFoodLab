package it.uninafoodlab.presentation.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import it.uninafoodlab.application.course.BrowseCourses;
import it.uninafoodlab.application.course.CourseRepository;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.IngredientMargin;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DashboardOverviewLoaderTest {
    @Test
    void emptyCourseCatalogDoesNotLoadAnySessions() {
        var repository = (CourseRepository) Proxy.newProxyInstance(CourseRepository.class.getClassLoader(),
                new Class<?>[] {CourseRepository.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("findByChef")) return List.of();
                    throw new AssertionError("Empty catalog must not read sessions");
                });
        var actor = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Username.of("test.chef"), "Chef", Role.CHEF);

        var snapshot = new DashboardOverviewLoader(actor, new BrowseCourses(repository)).load();

        assertThat(snapshot.sessions()).isEmpty();
        assertThat(snapshot.menusToPrepare()).isEmpty();
    }

    @Test
    void loadsAllCourseSessionsInOneBatchAndKeepsChronologicalMenuDestinations() {
        var now = LocalDateTime.now(ZoneId.of("Europe/Rome"));
        var firstCourse = course("Pasta", now);
        var secondCourse = course("Pizza", now);
        var firstSession = session(now.plusDays(1), 0);
        var secondSession = session(now.plusDays(2), 1);
        var sessionsByCourse = Map.of(firstCourse.id(), List.of(firstSession), secondCourse.id(), List.of(secondSession));
        var singleReads = new AtomicInteger();
        var batchReads = new AtomicInteger();
        var courseRepository = (CourseRepository) Proxy.newProxyInstance(CourseRepository.class.getClassLoader(),
                new Class<?>[] {CourseRepository.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("findByChef")) return List.of(secondCourse, firstCourse);
                    if (method.getName().equals("sessions") && arguments[1] instanceof UUID courseId) {
                        singleReads.incrementAndGet();
                        return sessionsByCourse.get(courseId);
                    }
                    if (method.getName().equals("sessions") && arguments[1] instanceof List<?> courseIds) {
                        assertThat(courseIds).isEqualTo(List.of(secondCourse.id(), firstCourse.id()));
                        batchReads.incrementAndGet();
                        return sessionsByCourse;
                    }
                    throw new AssertionError("Unexpected repository call: " + method.getName());
                });
        var actor = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Username.of("test.chef"), "Chef", Role.CHEF);
        var snapshot = new DashboardOverviewLoader(actor, new BrowseCourses(courseRepository)).load();

        assertThat(snapshot.sessions()).extracting(item -> item.session().id())
                .containsExactly(firstSession.id(), secondSession.id());
        assertThat(snapshot.menusToPrepare()).extracting(item -> item.courseId()).containsExactly(firstCourse.id());
        assertThat(singleReads).hasValue(0);
        assertThat(batchReads).hasValue(1);
    }

    private static CourseSummary course(String title, LocalDateTime at) {
        return new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Cucina", CourseTitle.of(title),
                "Corso sintetico", at.toLocalDate(), at.toLocalTime(), 7, 2, Optional.empty(),
                CourseStatus.PUBLISHED, IngredientMargin.zero());
    }

    private static CourseSessionSummary session(LocalDateTime at, int recipeCount) {
        return new CourseSessionSummary(UUID.randomUUID(), 1, at, SessionMode.PRACTICAL,
                SessionStatus.SCHEDULED, 60, Optional.empty(), Optional.of("Laboratorio"), Optional.of(10),
                Optional.of(at.minusMinutes(1)), Optional.empty(), recipeCount);
    }
}
