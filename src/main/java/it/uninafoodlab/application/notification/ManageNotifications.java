package it.uninafoodlab.application.notification;

import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ManageNotifications {

    private final NotificationRepository repository;

    public ManageNotifications(NotificationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public UUID send(
            AuthenticatedUser actor, Optional<UUID> courseId, NotificationDraft draft) {
        requireRole(actor, Role.CHEF);
        return repository.send(
                actor.sessionToken(), Objects.requireNonNull(courseId, "courseId"),
                Objects.requireNonNull(draft, "draft"));
    }

    public long recipientCount(AuthenticatedUser actor, Optional<UUID> courseId) {
        requireRole(actor, Role.CHEF);
        return repository.countRecipients(actor.sessionToken(), Objects.requireNonNull(courseId, "courseId"));
    }

    public void markRead(AuthenticatedUser actor, UUID notificationId) {
        requireRole(actor, Role.PARTICIPANT);
        repository.markRead(
                actor.sessionToken(), Objects.requireNonNull(notificationId, "notificationId"));
    }

    static void requireRole(AuthenticatedUser actor, Role expected) {
        Objects.requireNonNull(actor, "actor");
        if (actor.role() != expected) {
            throw new NotificationAuthorizationException();
        }
    }
}
