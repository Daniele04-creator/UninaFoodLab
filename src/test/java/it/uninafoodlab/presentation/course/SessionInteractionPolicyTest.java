package it.uninafoodlab.presentation.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionInteractionPolicyTest {
    @Test
    void existingDeadlineSurvivesReschedulingAndIsValidatedSeparately() {
        var start = LocalDateTime.parse("2026-09-14T18:00");
        var custom = start.minusHours(24);
        assertThat(SessionInteractionPolicy.initialCutoff(Optional.of(custom), start.plusHours(1)))
                .isEqualTo(custom);
        assertThat(SessionInteractionPolicy.initialCutoff(Optional.empty(), start)).isEqualTo(start.minusMinutes(1));
        assertThatThrownBy(() -> SessionInteractionPolicy.requireBefore(custom, custom))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("precedere");
        var beforeCutoff = custom.minusMinutes(1);
        assertThatThrownBy(() -> SessionInteractionPolicy.requireBefore(custom, beforeCutoff))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lessonActionsAcceptOnlyWebAddressesWithAHostAndNoEmbeddedCredentials() {
        assertThat(SessionInteractionPolicy.usableLessonLink(Optional.of(URI.create("https://meet.example.org/lesson")))).isTrue();
        for (var address : new String[] {"javascript:alert(1)", "file:///C:/test", "https:/lesson", "https://user:pass@example.org/"}) {
            assertThat(SessionInteractionPolicy.usableLessonLink(Optional.of(URI.create(address)))).isFalse();
        }
        assertThat(SessionInteractionPolicy.usableLessonLink(Optional.empty())).isFalse();
    }
}
