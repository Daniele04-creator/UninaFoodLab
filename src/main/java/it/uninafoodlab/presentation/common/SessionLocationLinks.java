package it.uninafoodlab.presentation.common;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class SessionLocationLinks {
    private SessionLocationLinks() {}

    public static Optional<URI> fromVenue(Optional<String> venue) {
        return venue.flatMap(value -> {
            try {
                var uri = URI.create(value.strip());
                return isLocationLink(uri) ? Optional.of(uri) : Optional.empty();
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        });
    }

    private static boolean isLocationLink(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && uri.getHost().matches("[A-Za-z0-9][A-Za-z0-9.-]*") && uri.getUserInfo() == null;
    }

    public static CompletableFuture<Void> open(URI uri) {
        if (!isLocationLink(uri)) throw new IllegalArgumentException("Inserisci un link HTTPS alla posizione.");
        return CompletableFuture.runAsync(() -> {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IllegalStateException("Browser non disponibile");
            }
            try {
                Desktop.getDesktop().browse(uri);
            } catch (IOException exception) {
                throw new IllegalStateException("Impossibile aprire la posizione nel browser.", exception);
            }
        });
    }
}
