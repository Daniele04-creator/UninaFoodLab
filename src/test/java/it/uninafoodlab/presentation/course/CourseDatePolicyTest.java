package it.uninafoodlab.presentation.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CourseDatePolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 25);

    @Test
    void acceptsTodayAndFutureDates() {
        assertThat(CourseDatePolicy.requireNotPast(TODAY, TODAY)).isEqualTo(TODAY);
        assertThat(CourseDatePolicy.requireNotPast(TODAY.plusDays(1), TODAY))
                .isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void rejectsDatesBeforeToday() {
        var yesterday = TODAY.minusDays(1);
        assertThatThrownBy(() -> CourseDatePolicy.requireNotPast(yesterday, TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La data non può precedere oggi.");
    }
}
