package it.uninafoodlab.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationUseCasesTest {

    private static final UUID TOKEN = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID NOTIFICATION_ID = UUID.randomUUID();

    @Test
    void onlyChefCanPreviewRecipientsForTheExplicitTarget() {
        var repository = new RecordingRepository();
        var useCase = new ManageNotifications(repository);
        assertThat(useCase.recipientCount(chef(), Optional.of(COURSE_ID))).isEqualTo(2);
        assertThat(repository.counted).containsExactly(new Count(TOKEN, Optional.of(COURSE_ID)));
        var participantUser = participant();
        assertThatThrownBy(() -> useCase.recipientCount(participantUser, Optional.empty()))
                .isInstanceOf(NotificationAuthorizationException.class);
        assertThat(repository.counted).hasSize(1);
    }

    @Test
    void chefSendsValidatedCourseAndGlobalMessages() {
        var repository = new RecordingRepository();
        var useCase = new ManageNotifications(repository);
        var draft = NotificationDraft.of("Promemoria", "Porta il grembiule personale.");

        assertThat(useCase.send(chef(), Optional.of(COURSE_ID), draft))
                .isEqualTo(NOTIFICATION_ID);
        assertThat(repository.sent)
                .containsExactly(new Sent(TOKEN, Optional.of(COURSE_ID), draft));
    }

    @Test
    void participantReadsAndMarksOnlyOwnNotifications() {
        var repository = new RecordingRepository();
        var expected = new ParticipantNotification(
                NOTIFICATION_ID,
                "Cambio orario",
                "La lezione inizia alle 19:00.",
                NotificationReason.SCHEDULE_CHANGED,
                Optional.of("Pizza contemporanea"),
                Instant.parse("2027-05-01T10:00:00Z"),
                Optional.empty());
        repository.participantNotifications.add(expected);

        assertThat(new BrowseNotifications(repository).forParticipant(participant()))
                .containsExactly(expected);
        new ManageNotifications(repository).markRead(participant(), NOTIFICATION_ID);

        assertThat(repository.read).containsExactly(new Read(TOKEN, NOTIFICATION_ID));
    }

    @Test
    void wrongRolesAndInvalidDraftsAreRejectedBeforePersistence() {
        var repository = new RecordingRepository();

        var manageNotifications = new ManageNotifications(repository);
        var participantUser = participant();
        var draft = NotificationDraft.of("Avviso", "Messaggio valido");
        assertThatThrownBy(() -> manageNotifications
                        .send(participantUser, Optional.empty(),
                                draft))
                .isInstanceOf(NotificationAuthorizationException.class);
        var browseNotifications = new BrowseNotifications(repository);
        assertThatThrownBy(() -> browseNotifications.forChef(participantUser))
                .isInstanceOf(NotificationAuthorizationException.class);
        assertThatThrownBy(() -> NotificationDraft.of("x", "Messaggio valido"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.sent).isEmpty();
    }

    private static AuthenticatedUser chef() {
        return user(Role.CHEF, "chef.test");
    }

    private static AuthenticatedUser participant() {
        return user(Role.PARTICIPANT, "participant.test");
    }

    private static AuthenticatedUser user(Role role, String username) {
        return new AuthenticatedUser(
                UUID.randomUUID(), UUID.randomUUID(), TOKEN, Username.of(username),
                "Utente Test", role);
    }

    private record Sent(UUID token, Optional<UUID> courseId, NotificationDraft draft) {}

    private record Read(UUID token, UUID notificationId) {}

    private record Count(UUID token, Optional<UUID> courseId) {}

    private static final class RecordingRepository implements NotificationRepository {
        private final List<Count> counted = new ArrayList<>();

        @Override
        public long countRecipients(UUID token, Optional<UUID> courseId) {
            counted.add(new Count(token, courseId));
            return 2;
        }
        private final List<Sent> sent = new ArrayList<>();
        private final List<Read> read = new ArrayList<>();
        private final List<ParticipantNotification> participantNotifications = new ArrayList<>();

        @Override
        public UUID send(UUID token, Optional<UUID> courseId, NotificationDraft draft) {
            sent.add(new Sent(token, courseId, draft));
            return NOTIFICATION_ID;
        }

        @Override
        public void markRead(UUID token, UUID notificationId) {
            read.add(new Read(token, notificationId));
        }

        @Override
        public List<ParticipantNotification> findForParticipant(UUID token) {
            return participantNotifications;
        }

        @Override
        public List<ChefNotification> findForChef(UUID token) {
            return List.of();
        }
    }
}
