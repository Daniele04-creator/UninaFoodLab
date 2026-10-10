package it.uninafoodlab.application.notification;

import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import java.util.List;
import java.util.Objects;

public final class BrowseNotifications {

    private final NotificationRepository repository;

    public BrowseNotifications(NotificationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<ParticipantNotification> forParticipant(AuthenticatedUser actor) {
        ManageNotifications.requireRole(actor, Role.PARTICIPANT);
        return List.copyOf(repository.findForParticipant(actor.sessionToken()));
    }

    public List<ChefNotification> forChef(AuthenticatedUser actor) {
        ManageNotifications.requireRole(actor, Role.CHEF);
        return List.copyOf(repository.findForChef(actor.sessionToken()));
    }
}
