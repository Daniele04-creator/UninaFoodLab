package it.uninafoodlab.domain.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CourseTitleTest {

    @Test
    void trimsAValidTitle() {
        assertThat(CourseTitle.of("  Pane a lievitazione naturale  ").value())
                .isEqualTo("Pane a lievitazione naturale");
    }

    @Test
    void rejectsNullBlankShortAndOversizedTitles() {
        assertThatThrownBy(() -> CourseTitle.of(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CourseTitle.of("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CourseTitle.of("ab")).isInstanceOf(IllegalArgumentException.class);
        var oversized = "x".repeat(31);
        assertThatThrownBy(() -> CourseTitle.of(oversized))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void acceptsTheExactThreeAndThirtyCharacterBoundaries() {
        assertThat(CourseTitle.of("abc").value()).isEqualTo("abc");
        assertThat(CourseTitle.of("x".repeat(30)).value()).hasSize(30);
        assertThat(CourseTitle.of("  " + "x".repeat(30) + "  ").value()).hasSize(30);
    }

    @Test void rejectsLineBreaksInsteadOfCreatingAMultilineTitle() {
        for (String title : new String[]{"Pane\npizza", "Pane\rpizza", "Pane\r\npizza", "\nPane",
                "Pane\u0085pizza", "Pane\u2028pizza", "Pane\u2029pizza"}) {
            assertThatThrownBy(() -> CourseTitle.of(title)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
