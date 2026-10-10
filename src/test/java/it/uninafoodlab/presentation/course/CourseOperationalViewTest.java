package it.uninafoodlab.presentation.course;

import static org.assertj.core.api.Assertions.assertThat;

import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.IngredientMargin;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseOperationalViewTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final Clock NOW = Clock.fixed(
            Instant.parse("2026-08-26T12:00:00Z"), ROME);
    private static final LocalDateTime ROME_NOW = LocalDateTime.of(2026, 8, 26, 14, 0);

    @Test
    void activeCatalogContainsCoursesThatHaveNotEndedWithoutDraftPublishedDistinction() {
        var draft = course(
                "Corso in preparazione", CourseStatus.DRAFT, ROME_NOW.minusDays(1));
        var ongoing = course(
                "Corso in svolgimento", CourseStatus.PUBLISHED, ROME_NOW.plusMinutes(1));
        var future = course(
                "Corso futuro", CourseStatus.PUBLISHED, ROME_NOW.plusDays(2));
        var ended = course(
                "Corso terminato", CourseStatus.PUBLISHED, ROME_NOW);

        assertThat(CourseOperationalView.isArchived(ended, NOW)).isTrue();
        assertThat(List.of(future, draft, ongoing)).allSatisfy(course ->
                assertThat(CourseOperationalView.isArchived(course, NOW)).isFalse());
        assertThat(CourseOperationalView.catalogLabel(draft, NOW)).isEqualTo("In programma");
        assertThat(CourseOperationalView.catalogLabel(future, NOW)).isEqualTo("In programma");
    }

    @Test
    void labelsDistinguishTheScheduledStartInRomeWhileBothCoursesRemainActive() {
        var base = course("Corso di cucina", CourseStatus.PUBLISHED, ROME_NOW.plusDays(2));
        var future = startingAt(base, ROME_NOW.plusMinutes(1));
        var startingNow = startingAt(base, ROME_NOW);
        var started = startingAt(base, ROME_NOW.minusMinutes(1));

        assertThat(CourseOperationalView.catalogLabel(future, NOW)).isEqualTo("In programma");
        assertThat(CourseOperationalView.catalogLabel(startingNow, NOW)).isEqualTo("In corso");
        assertThat(CourseOperationalView.catalogLabel(started, NOW)).isEqualTo("In corso");
        assertThat(CourseOperationalView.catalogLabel(future, NOW.withZone(ZoneId.of("UTC"))))
                .isEqualTo("In programma");
        assertThat(List.of(future, startingNow, started)).allSatisfy(course ->
                assertThat(CourseOperationalView.isArchived(course, NOW)).isFalse());
    }

    private static CourseSummary startingAt(CourseSummary course, LocalDateTime start) {
        return new CourseSummary(course.id(), course.categoryId(), course.categoryName(), course.title(),
                course.description(), start.toLocalDate(), start.toLocalTime(), course.recurrenceDays(),
                course.sessionCount(), course.lastSessionEndsAt(), course.status(), course.ingredientMargin());
    }

    @Test
    void archiveContainsEndedAndCancelledCoursesWithClearLabels() {
        var ended = course(
                "Corso terminato", CourseStatus.PUBLISHED, ROME_NOW.minusMinutes(1));
        var legacyCompleted = course(
                "Corso completato legacy", CourseStatus.COMPLETED, ROME_NOW.minusDays(2));
        var allSessionsCancelled = courseWithoutActiveSessions(
                "Corso con sessioni annullate", CourseStatus.PUBLISHED);
        var legacyCancelled = courseWithoutActiveSessions(
                "Corso annullato legacy", CourseStatus.CANCELLED);
        var active = course(
                "Corso ancora attivo", CourseStatus.PUBLISHED, ROME_NOW.plusMinutes(1));

        assertThat(CourseOperationalView.isArchived(active, NOW)).isFalse();
        assertThat(List.of(ended, allSessionsCancelled, legacyCompleted, legacyCancelled)).allSatisfy(course ->
                assertThat(CourseOperationalView.isArchived(course, NOW)).isTrue());
        assertThat(CourseOperationalView.catalogLabel(ended, NOW)).isEqualTo("Concluso");
        assertThat(CourseOperationalView.catalogLabel(legacyCompleted, NOW)).isEqualTo("Concluso");
        assertThat(CourseOperationalView.catalogLabel(allSessionsCancelled, NOW))
                .isEqualTo("Annullato");
        assertThat(CourseOperationalView.catalogLabel(legacyCancelled, NOW))
                .isEqualTo("Annullato");
    }

    private static CourseSummary course(
            String title, CourseStatus status, LocalDateTime lastSessionEndsAt) {
        return course(title, status, Optional.of(lastSessionEndsAt));
    }

    private static CourseSummary courseWithoutActiveSessions(String title, CourseStatus status) {
        return course(title, status, Optional.empty());
    }

    private static CourseSummary course(
            String title, CourseStatus status, Optional<LocalDateTime> lastSessionEndsAt) {
        return new CourseSummary(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Cucina italiana",
                CourseTitle.of(title),
                "Descrizione completa del corso",
                LocalDate.of(2026, 9, 1),
                LocalTime.of(18, 30),
                7,
                2,
                lastSessionEndsAt,
                status,
                IngredientMargin.zero());
    }
}
