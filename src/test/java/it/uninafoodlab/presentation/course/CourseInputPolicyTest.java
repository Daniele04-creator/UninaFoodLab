package it.uninafoodlab.presentation.course;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class CourseInputPolicyTest {
    @Test
    void integerEditingPreservesIncompleteAndOutOfRangeIntentUntilValidation() {
        for (String editable : new String[] {"", "1", "12", "15", "30", "31", "0", "999999999999"}) {
            assertThat(CourseInputPolicy.acceptsIntegerEdit(editable)).isTrue();
        }
        for (String invalid : new String[] {"-1", "12.5", "abc", " 12"}) {
            assertThat(CourseInputPolicy.acceptsIntegerEdit(invalid)).isFalse();
        }
        assertThat(CourseInputPolicy.acceptsInteger("1", 2, 30)).isFalse();
        assertThat(CourseInputPolicy.acceptsInteger("12", 2, 30)).isTrue();
        assertThat(CourseInputPolicy.acceptsInteger("31", 2, 30)).isFalse();
    }
    @Test
    void integerEntryRejectsInvalidAndOutOfRangeValuesInsteadOfClamping() {
        for (String invalid : new String[] {"", "-1", "0", "501", "12.5", "abc", "999999999999"}) {
            assertThat(CourseInputPolicy.acceptsInteger(invalid, 1, 500)).isFalse();
        }
        for (String valid : new String[] {"1", "127", "500"}) {
            assertThat(CourseInputPolicy.acceptsInteger(valid, 1, 500)).isTrue();
        }
    }
}
