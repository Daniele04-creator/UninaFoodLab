package it.uninafoodlab.application.notification;

import java.util.Objects;

public record NotificationDraft(String title, String message) {

    public NotificationDraft {
        title = Objects.requireNonNull(title, "title").strip();
        message = Objects.requireNonNull(message, "message").strip();
        if (title.length() < 3 || title.length() > 120) {
            throw new IllegalArgumentException("Notification title must contain 3 to 120 characters");
        }
        if (message.length() < 3 || message.length() > 2000) {
            throw new IllegalArgumentException("Notification message must contain 3 to 2000 characters");
        }
    }

    public static NotificationDraft of(String title, String message) {
        return new NotificationDraft(title, message);
    }
}
