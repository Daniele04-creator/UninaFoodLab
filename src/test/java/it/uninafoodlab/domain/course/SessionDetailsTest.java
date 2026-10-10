package it.uninafoodlab.domain.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class SessionDetailsTest {

    @Test
    void modelsAnOnlineSessionWithAnHttpsUrlOnly() {
        var details = SessionDetails.online(
                90, URI.create("https://meet.uninafoodlab.local/pizza"));

        assertThat(details.mode()).isEqualTo(SessionMode.ONLINE);
        assertThat(details.durationMinutes()).isEqualTo(90);
        assertThat(details.onlineUrl()).contains(
                URI.create("https://meet.uninafoodlab.local/pizza"));
        assertThat(details.venue()).isEmpty();
        assertThat(details.capacity()).isEmpty();
    }

    @Test
    void modelsAPracticalSessionWithVenueAndPositiveCapacityOnly() {
        var details = SessionDetails.practical(120, "  Laboratorio Vesuvio  ", 14);

        assertThat(details.mode()).isEqualTo(SessionMode.PRACTICAL);
        assertThat(details.durationMinutes()).isEqualTo(120);
        assertThat(details.onlineUrl()).isEmpty();
        assertThat(details.venue()).contains("Laboratorio Vesuvio");
        assertThat(details.capacity()).contains(14);
    }

    @Test
    void rejectsInvalidDurationUrlVenueAndCapacity() {
        var validUrl = URI.create("https://meet.uninafoodlab.local/pizza");
        var httpUrl = URI.create("http://example.test");
        var missingHostUrl = URI.create("https://?query");
        var credentialsUrl = URI.create("https://user@example.test/session");
        assertThatThrownBy(() -> SessionDetails.online(
                        0, validUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duration");
        assertThatThrownBy(() -> SessionDetails.online(90, httpUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> SessionDetails.online(90, missingHostUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> SessionDetails.online(
                        90, credentialsUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> SessionDetails.practical(90, " ", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("venue");
        assertThatThrownBy(() -> SessionDetails.practical(90, "Laboratorio", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity");
    }
}
