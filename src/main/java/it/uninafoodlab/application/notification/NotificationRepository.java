package it.uninafoodlab.application.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    long countRecipients(UUID sessionToken, Optional<UUID> courseId);

    UUID send(UUID sessionToken, Optional<UUID> courseId, NotificationDraft draft);

    void markRead(UUID sessionToken, UUID notificationId);

    List<ParticipantNotification> findForParticipant(UUID sessionToken);

    List<ChefNotification> findForChef(UUID sessionToken);
}
