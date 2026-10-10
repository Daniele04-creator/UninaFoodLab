package it.uninafoodlab.domain.course;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

public record SessionDetails(
        SessionMode mode,
        int durationMinutes,
        Optional<URI> onlineUrl,
        Optional<String> venue,
        Optional<Integer> capacity) {

    private static final int MAX_DURATION_MINUTES = 12 * 60;

    public SessionDetails {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(onlineUrl, "onlineUrl");
        venue = Objects.requireNonNull(venue, "venue").map(String::strip);
        Objects.requireNonNull(capacity, "capacity");
        if (durationMinutes < 1 || durationMinutes > MAX_DURATION_MINUTES) {
            throw new IllegalArgumentException("session duration must contain 1 to 720 minutes");
        }
        if (mode == SessionMode.ONLINE) {
            var url = onlineUrl.orElseThrow(() ->
                    new IllegalArgumentException("online session requires an HTTPS URL"));
            validateOnlineUrl(url);
            if (venue.isPresent() || capacity.isPresent()) {
                throw new IllegalArgumentException(
                        "online session cannot define venue or capacity");
            }
        } else {
            if (onlineUrl.isPresent()) {
                throw new IllegalArgumentException(
                        "practical session cannot define an online URL");
            }
            if (venue.isEmpty() || venue.orElseThrow().isEmpty()) {
                throw new IllegalArgumentException("practical session venue must not be blank");
            }
            if (capacity.isEmpty() || capacity.orElseThrow() < 1) {
                throw new IllegalArgumentException("practical session capacity must be positive");
            }
        }
    }

    private static void validateOnlineUrl(URI url) {
        if (!"https".equalsIgnoreCase(url.getScheme())
                || url.getHost() == null
                || !url.getHost().matches("[A-Za-z0-9][A-Za-z0-9.-]*")
                || url.getUserInfo() != null) {
            throw new IllegalArgumentException("online session requires an HTTPS URL");
        }
    }

    public static SessionDetails online(int durationMinutes, URI onlineUrl) {
        return new SessionDetails(
                SessionMode.ONLINE,
                durationMinutes,
                Optional.of(Objects.requireNonNull(onlineUrl, "onlineUrl")),
                Optional.empty(),
                Optional.empty());
    }

    public static SessionDetails practical(int durationMinutes, String venue, int capacity) {
        return new SessionDetails(
                SessionMode.PRACTICAL,
                durationMinutes,
                Optional.empty(),
                Optional.ofNullable(venue),
                Optional.of(capacity));
    }
}
