package it.uninafoodlab.application.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.course.CourseAuthorizationException;
import it.uninafoodlab.application.notification.NotificationDraft;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScheduleUseCasesTest {

    private static final UUID TOKEN = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final NotificationDraft DRAFT =
            NotificationDraft.of("Calendario aggiornato", "Consulta il nuovo calendario.");

    @Test
    void chefCanMoveAddAndCancelSessionsAtomicallyWithNotificationDrafts() {
        var repository = new RecordingRepository();
        var useCase = new ManageCourseSchedule(repository);
        var time = LocalDateTime.of(2027, 5, 12, 18, 30);
        var input = new NewSessionInput(
                time, SessionMode.PRACTICAL, 120, Optional.empty(),
                Optional.of("Laboratorio Vesuvio"), Optional.of(12));

        useCase.reschedule(chef(), SESSION_ID, time, DRAFT);
        assertThat(useCase.add(chef(), COURSE_ID, input, DRAFT)).isEqualTo(SESSION_ID);
        useCase.cancel(chef(), SESSION_ID, DRAFT);

        assertThat(repository.actions).containsExactly("MOVE", "ADD", "CANCEL");
    }

    @Test
    void participantCannotMutateTheChefCalendar() {
        var repository = new RecordingRepository();

        var manageCourseSchedule = new ManageCourseSchedule(repository);
        var participantUser = participant();
        assertThatThrownBy(() -> manageCourseSchedule
                        .cancel(participantUser, SESSION_ID, DRAFT))
                .isInstanceOf(CourseAuthorizationException.class);
        assertThat(repository.actions).isEmpty();
    }

    @Test
    void forwardsThePracticalAttendanceCutoffWithScheduleChanges() {
        var repository = new RecordingRepository();
        var useCase = new ManageCourseSchedule(repository);
        var scheduledAt = LocalDateTime.of(2027, 5, 12, 18, 30);
        var cutoff = scheduledAt.minusHours(48);
        var input = new NewSessionInput(
                scheduledAt, SessionMode.PRACTICAL, 120, Optional.empty(),
                Optional.of("Laboratorio Vesuvio"), Optional.of(12), Optional.of(cutoff));

        useCase.reschedule(chef(), SESSION_ID, scheduledAt, Optional.of(cutoff), DRAFT);
        useCase.add(chef(), COURSE_ID, input, DRAFT);

        assertThat(repository.rescheduledCutoff).contains(cutoff);
        assertThat(repository.addedCutoff).contains(cutoff);
    }

    @Test
    void locationCorrectionIsTrimmedAndCannotBeEmptyOrSubmittedByParticipant() {
        var repository = new RecordingRepository();
        var useCase = new ManageCourseSchedule(repository);
        var scheduledAt = LocalDateTime.of(2037, 5, 12, 18, 30);
        useCase.reschedule(chef(), SESSION_ID, scheduledAt, Optional.empty(), "  Nuova sede  ", DRAFT);
        assertThat(repository.rescheduledLocation).contains("Nuova sede");
        assertThatThrownBy(() -> useCase.reschedule(chef(), SESSION_ID, scheduledAt, Optional.empty(), "  ", DRAFT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.reschedule(participant(), SESSION_ID, scheduledAt, Optional.empty(), "Sede", DRAFT))
                .isInstanceOf(CourseAuthorizationException.class);
        assertThat(repository.actions).containsExactly("MOVE");
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

    private static final class RecordingRepository implements ScheduleRepository {
        private final List<String> actions = new ArrayList<>();
        private Optional<LocalDateTime> rescheduledCutoff = Optional.empty();
        private Optional<LocalDateTime> addedCutoff = Optional.empty();
        private Optional<String> rescheduledLocation = Optional.empty();

        @Override
        public void reschedule(UUID token, UUID sessionId, LocalDateTime scheduledAt,
                Optional<LocalDateTime> attendanceClosesAt, Optional<String> location, NotificationDraft draft) {
            actions.add("MOVE");
            rescheduledCutoff = attendanceClosesAt;
            rescheduledLocation = location;
        }

        @Override
        public void reschedule(UUID token, UUID sessionId, LocalDateTime scheduledAt,
                NotificationDraft draft) {
            actions.add("MOVE");
        }

        @Override
        public UUID add(UUID token, UUID courseId, NewSessionInput input,
                NotificationDraft draft) {
            actions.add("ADD");
            addedCutoff = input.attendanceClosesAt();
            return SESSION_ID;
        }

        @Override
        public void cancel(UUID token, UUID sessionId, NotificationDraft draft) {
            actions.add("CANCEL");
        }
    }
}
