package it.uninafoodlab.presentation.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionLocationLinksTest {
    @Test
    void preservesAnHttpsLocationLinkIncludingItsPathAndQuery() {
        var address = "https://maps.example.org/place/laboratorio?route=walking";
        assertThat(SessionLocationLinks.fromVenue(Optional.of("  " + address + "  ")))
                .contains(URI.create(address));
    }

    @Test
    void textAddressesAndMissingLocationsStayReadableWithoutAWebAction() {
        assertThat(SessionLocationLinks.fromVenue(Optional.of("Laboratorio, via Roma 12"))).isEmpty();
        assertThat(SessionLocationLinks.fromVenue(Optional.empty())).isEmpty();
        assertThat(SessionLocationLinks.fromVenue(Optional.of(""))).isEmpty();
    }

    @Test
    void refusesMalformedLinksOtherProtocolsAndEmbeddedCredentials() {
        for (var address : new String[]{"http://maps.example.org/", "https:/place", "https://",
                "https://maps.example.org/a b", "https://user:pass@maps.example.org/", "file:///C:/test",
                "javascript:alert(1)", "https://invalid_host/path"}) {
            assertThat(SessionLocationLinks.fromVenue(Optional.of(address))).as(address).isEmpty();
        }
    }

    @Test
    void browserOpeningRejectsNonLocationUrisBeforeStartingAnyWork() {
        assertThatThrownBy(() -> SessionLocationLinks.open(URI.create("file:///C:/test")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTPS");
    }
}
