package it.uninafoodlab.presentation.course;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Optional;

final class SessionInteractionPolicy {
    private SessionInteractionPolicy() {}

    static LocalDateTime initialCutoff(Optional<LocalDateTime> existing, LocalDateTime scheduledAt) {
        return existing.orElseGet(() -> scheduledAt.minusMinutes(1));
    }

    static void requireBefore(LocalDateTime cutoff, LocalDateTime scheduledAt) {
        if (!cutoff.isBefore(scheduledAt)) {
            throw new IllegalArgumentException("La chiusura adesioni deve precedere la sessione.");
        }
    }

    static boolean usableLessonLink(Optional<URI> link) {
        return link.filter(uri -> ("https".equalsIgnoreCase(uri.getScheme())
                        || "http".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null && uri.getUserInfo() == null).isPresent();
    }
}
