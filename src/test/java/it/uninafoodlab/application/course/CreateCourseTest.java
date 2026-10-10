package it.uninafoodlab.application.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.IngredientMargin;
import it.uninafoodlab.domain.course.SessionDetails;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionPlan;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateCourseTest {

    private static final UUID CHEF_PROFILE_ID = UUID.randomUUID();
    private static final UUID CHEF_SESSION_TOKEN = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID CREATED_COURSE_ID = UUID.randomUUID();

    @Test
    void createsAValidatedCourseOwnedByTheAuthenticatedChef() {
        var repository = new RecordingCourseRepository();
        var useCase = new CreateCourse(repository);
        var details = new ArrayList<>(List.of(online(), practical()));

        var createdId = useCase.execute(
                chef(),
                new NewCourseInput(
                        CATEGORY_ID,
                        "  Pizza contemporanea  ",
                        "  Impasti, maturazione e cottura.  ",
                        LocalDate.of(2026, 9, 1),
                        LocalTime.of(18, 30),
                        7,
                        BigDecimal.ZERO,
                        details));
        details.set(0, practical());

        assertThat(createdId).isEqualTo(CREATED_COURSE_ID);
        assertThat(repository.createdCourse.sessionToken()).isEqualTo(CHEF_SESSION_TOKEN);
        assertThat(repository.createdCourse.categoryId()).isEqualTo(CATEGORY_ID);
        assertThat(repository.createdCourse.title().value()).isEqualTo("Pizza contemporanea");
        assertThat(repository.createdCourse.description())
                .isEqualTo("Impasti, maturazione e cottura.");
        assertThat(repository.createdCourse.ingredientMargin().value())
                .isEqualByComparingTo("0");
        assertThat(repository.createdCourse.schedule().sessions())
                .extracting(SessionPlan::mode)
                .containsExactly(SessionMode.ONLINE, SessionMode.PRACTICAL);
        assertThat(repository.publishedCourseId).isEqualTo(CREATED_COURSE_ID);
    }

    @Test
    void rejectsNonzeroMarginsBeforeWritingTheCourse() {
        var repository = new RecordingCourseRepository();
        var input = validInput();
        var withMargin = new NewCourseInput(input.categoryId(), input.title(), input.description(),
                input.startDate(), input.startTime(), input.recurrenceDays(),
                new BigDecimal("5"), input.sessionDetails());

        var createCourse = new CreateCourse(repository);
        var chefUser = chef();
        assertThatThrownBy(() -> createCourse.execute(chefUser, withMargin))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.createdCourse).isNull();
        assertThat(repository.publishedCourseId).isNull();
    }

    @Test
    void rejectsParticipantsBeforeCallingTheRepository() {
        var repository = new RecordingCourseRepository();
        var useCase = new CreateCourse(repository);

        var participantUser = participant();
        var input = validInput();
        assertThatThrownBy(() -> useCase.execute(participantUser, input))
                .isInstanceOf(CourseAuthorizationException.class)
                .hasMessage("Operazione riservata agli chef");
        assertThat(repository.createdCourse).isNull();
    }

    @Test
    void rejectsBlankDescriptions() {
        var useCase = new CreateCourse(new RecordingCourseRepository());
        var invalid = new NewCourseInput(
                CATEGORY_ID,
                "Titolo valido",
                "   ",
                LocalDate.of(2026, 9, 1),
                LocalTime.of(18, 30),
                7,
                BigDecimal.ZERO,
                List.of(online(), practical()));

        var chefUser = chef();
        assertThatThrownBy(() -> useCase.execute(chefUser, invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void browsingReturnsImmutableCopiesAndRejectsParticipants() {
        var repository = new RecordingCourseRepository();
        var expectedCourse = new CourseSummary(
                CREATED_COURSE_ID,
                CATEGORY_ID,
                "Cucina campana",
                CourseTitle.of("Pizza contemporanea"),
                "Impasti e cottura",
                LocalDate.of(2026, 9, 1),
                LocalTime.of(18, 30),
                7,
                2,
                Optional.of(LocalDateTime.of(2026, 9, 8, 20, 0)),
                CourseStatus.DRAFT,
                IngredientMargin.zero());
        repository.courses.add(expectedCourse);
        var session = new CourseSessionSummary(
                UUID.randomUUID(),
                1,
                LocalDateTime.of(2026, 9, 1, 18, 30),
                SessionMode.ONLINE,
                SessionStatus.SCHEDULED,
                90,
                Optional.of(URI.create("https://meet.uninafoodlab.local/pizza")),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0);
        repository.sessions.add(session);
        var browse = new BrowseCourses(repository);

        var courses = browse.courses(chef(), Optional.of(CATEGORY_ID));
        var sessions = browse.sessions(chef(), CREATED_COURSE_ID);

        assertThat(courses).containsExactly(expectedCourse);
        assertThat(sessions).containsExactly(session);
        assertThatThrownBy(() -> courses.add(expectedCourse))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> sessions.add(session))
                .isInstanceOf(UnsupportedOperationException.class);
        var participantUser = participant();
        assertThatThrownBy(() -> browse.courses(participantUser, Optional.empty()))
                .isInstanceOf(CourseAuthorizationException.class);
    }

    private static NewCourseInput validInput() {
        return new NewCourseInput(
                CATEGORY_ID,
                "Pizza contemporanea",
                "Impasti, maturazione e cottura.",
                LocalDate.of(2026, 9, 1),
                LocalTime.of(18, 30),
                7,
                BigDecimal.ZERO,
                List.of(online(), practical()));
    }

    private static SessionDetails online() {
        return SessionDetails.online(
                90, URI.create("https://meet.uninafoodlab.local/pizza"));
    }

    private static SessionDetails practical() {
        return SessionDetails.practical(120, "Laboratorio Vesuvio", 14);
    }

    private static AuthenticatedUser chef() {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                CHEF_PROFILE_ID,
                CHEF_SESSION_TOKEN,
                Username.of("chef.test"),
                "Chef Test",
                Role.CHEF);
    }

    private static AuthenticatedUser participant() {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Username.of("participant.test"),
                "Partecipante Test",
                Role.PARTICIPANT);
    }

    private static final class RecordingCourseRepository implements CourseRepository {

        private CourseCreation createdCourse;
        private UUID publishedCourseId;
        private final List<CourseSummary> courses = new ArrayList<>();
        private final List<CourseSessionSummary> sessions = new ArrayList<>();

        @Override
        public UUID createAndPublish(CourseCreation course) {
            createdCourse = course;
            publishedCourseId = CREATED_COURSE_ID;
            return CREATED_COURSE_ID;
        }

        @Override
        public UUID create(CourseCreation course) {
            createdCourse = course;
            return CREATED_COURSE_ID;
        }

        @Override
        public List<CourseSummary> findByChef(
                UUID sessionToken, Optional<UUID> categoryId) {
            return courses;
        }

        @Override
        public List<CourseSessionSummary> sessions(UUID sessionToken, UUID courseId) {
            return sessions;
        }

        @Override
        public void assignRecipe(UUID sessionToken, UUID sessionId, UUID recipeId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void publish(UUID sessionToken, UUID courseId) {
            assertThat(sessionToken).isEqualTo(CHEF_SESSION_TOKEN);
            publishedCourseId = courseId;
        }

        @Override
        public List<PublishedCourseSessionSummary> publishedSessions(
                UUID sessionToken, UUID courseId) {
            throw new UnsupportedOperationException();
        }
    }
}
