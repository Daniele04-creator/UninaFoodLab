package it.uninafoodlab.application.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import java.net.URI;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoursePublicationUseCasesTest {

    private static final UUID CHEF_ID = UUID.randomUUID();
    private static final UUID CHEF_SESSION_TOKEN = UUID.randomUUID();
    private static final UUID PARTICIPANT_SESSION_TOKEN = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    @Test
    void publishesTheSelectedCourseAsItsAuthenticatedChef() {
        var repository = new RecordingRepository();

        new PublishCourse(repository).execute(chef(), COURSE_ID);

        assertThat(repository.publishedSessionToken).isEqualTo(CHEF_SESSION_TOKEN);
        assertThat(repository.publishedCourseId).isEqualTo(COURSE_ID);
        var publishCourse = new PublishCourse(repository);
        var participantUser = participant();
        assertThatThrownBy(() -> publishCourse.execute(participantUser, COURSE_ID))
                .isInstanceOf(CourseAuthorizationException.class);
    }

    @Test
    void letsOnlyParticipantsBrowseImmutablePublishedCourseDetails() {
        var repository = new RecordingRepository();
        var course = new PublishedCourseSummary(
                COURSE_ID,
                CATEGORY_ID,
                "Cucina campana",
                "Chef Test",
                CourseTitle.of("Pizza contemporanea"),
                "Impasti e cottura",
                LocalDate.of(2027, 5, 1),
                LocalTime.of(18, 30),
                2);
        var session = new PublishedCourseSessionSummary(
                UUID.randomUUID(),
                1,
                LocalDateTime.of(2027, 5, 1, 18, 30),
                SessionMode.ONLINE,
                90,
                Optional.of(URI.create("https://meet.uninafoodlab.local/test")),
                Optional.empty(),
                Optional.empty());
        repository.publishedCourses.add(course);
        repository.publishedSessions.add(session);
        var browse = new BrowsePublishedCourses(repository);

        var filter = new CourseFilter(Optional.of(CATEGORY_ID), "", CoursePeriod.ALL, LocalDateTime.of(2027, 5, 1, 18, 30));
        var page = new PageRequest(0, 12);
        var courses = browse.courses(participant(), filter, page).items();
        var sessions = browse.sessions(participant(), COURSE_ID);

        assertThat(courses).containsExactly(course);
        assertThat(sessions).containsExactly(session);
        assertThatThrownBy(() -> courses.add(course))
                .isInstanceOf(UnsupportedOperationException.class);
        var chefUser = chef();
        assertThatThrownBy(() -> browse.courses(chefUser, filter, page))
                .isInstanceOf(CourseAuthorizationException.class)
                .hasMessage("Operazione riservata ai partecipanti");
    }

    @Test
    void publicMenuIsImmutableAndRequiresParticipantAuthorization() {
        var repository = new RecordingRepository();
        var recipe = new PublishedSessionRecipeSummary(UUID.randomUUID(), UUID.randomUUID(),
                "Pizza Margherita", "Preparazione in laboratorio", 4, BigDecimal.ONE);
        repository.publishedMenu.add(recipe);
        var browse = new BrowsePublishedCourses(repository);

        var menu = browse.menu(participant(), COURSE_ID);

        assertThat(menu).containsExactly(recipe);
        assertThat(repository.menuSessionToken).isEqualTo(PARTICIPANT_SESSION_TOKEN);
        assertThat(repository.menuCourseId).isEqualTo(COURSE_ID);
        assertThatThrownBy(() -> menu.add(recipe)).isInstanceOf(UnsupportedOperationException.class);
        var chefUser = chef();
        assertThatThrownBy(() -> browse.menu(chefUser, COURSE_ID))
                .isInstanceOf(CourseAuthorizationException.class);
    }

    private static AuthenticatedUser chef() {
        return new AuthenticatedUser(
                UUID.randomUUID(), CHEF_ID, CHEF_SESSION_TOKEN,
                Username.of("chef.test"), "Chef Test", Role.CHEF);
    }

    private static AuthenticatedUser participant() {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                PARTICIPANT_SESSION_TOKEN,
                Username.of("participant.test"),
                "Partecipante Test",
                Role.PARTICIPANT);
    }

    private static final class RecordingRepository implements CourseRepository {

        private UUID publishedSessionToken;
        private UUID publishedCourseId;
        private final List<PublishedCourseSummary> publishedCourses = new ArrayList<>();
        private final List<PublishedCourseSessionSummary> publishedSessions = new ArrayList<>();
        private final List<PublishedSessionRecipeSummary> publishedMenu = new ArrayList<>();
        private UUID menuSessionToken;
        private UUID menuCourseId;

        @Override
        public List<PublishedSessionRecipeSummary> publishedMenu(UUID sessionToken, UUID courseId) {
            menuSessionToken = sessionToken;
            menuCourseId = courseId;
            return publishedMenu;
        }

        @Override
        public UUID create(CourseCreation course) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UUID createAndPublish(CourseCreation course) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CourseSummary> findByChef(UUID sessionToken, Optional<UUID> categoryId) {
            return List.of();
        }

        @Override
        public List<CourseSessionSummary> sessions(UUID sessionToken, UUID courseId) {
            return List.of();
        }

        @Override
        public void assignRecipe(UUID sessionToken, UUID sessionId, UUID recipeId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void publish(UUID sessionToken, UUID courseId) {
            publishedSessionToken = sessionToken;
            publishedCourseId = courseId;
        }

        @Override
        public Page<PublishedCourseSummary> findPublished(
                UUID sessionToken, CourseFilter filter, PageRequest page) {
            assertThat(sessionToken).isEqualTo(PARTICIPANT_SESSION_TOKEN);
            assertThat(filter.categoryId()).contains(CATEGORY_ID);
            assertThat(page).isEqualTo(new PageRequest(0, 12));
            return new Page<>(publishedCourses, publishedCourses.size());
        }

        @Override
        public List<PublishedCourseSessionSummary> publishedSessions(
                UUID sessionToken, UUID courseId) {
            assertThat(sessionToken).isEqualTo(PARTICIPANT_SESSION_TOKEN);
            return publishedSessions;
        }
    }
}
