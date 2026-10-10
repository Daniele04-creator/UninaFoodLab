package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.course.NewCourseInput;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.SessionDetails;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.presentation.common.UserErrorMessages;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CourseFormModel {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private final List<SessionDraft> sessions = new ArrayList<>(List.of(onlineDraft(), onlineDraft()));

    private int activeSessionCount = 2;

    public void setSessionCount(int count) {
        if (count < 2 || count > 30) {
            throw new IllegalArgumentException(
                    "Il numero di sessioni deve essere compreso tra 2 e 30");
        }
        while (sessions.size() < count) {
            sessions.add(onlineDraft());
        }
        activeSessionCount = count;
    }

    public void resetSessions(int count) {
        if (count < 2 || count > 30) {
            throw new IllegalArgumentException(
                    "Il numero di sessioni deve essere compreso tra 2 e 30");
        }
        sessions.clear();
        activeSessionCount = count;
        for (var index = 1; index <= count; index++) {
            sessions.add(onlineDraft());
        }
    }

    public void setSessionMode(int index, SessionMode mode) {
        Objects.requireNonNull(mode, "mode");
        var current = sessions.get(index);
        if (current.mode() != mode) {
            sessions.set(index, new SessionDraft(mode, current.onlineUrl(),
                    current.venue(), current.capacity()));
        }
    }

    public void setSessionOnlineUrl(int index, String onlineUrl) {
        var current = sessions.get(index);
        sessions.set(index, new SessionDraft(
                current.mode(), onlineUrl, current.venue(), current.capacity()));
    }

    public void setSessionVenue(int index, String venue) {
        var current = sessions.get(index);
        sessions.set(index, new SessionDraft(
                current.mode(), current.onlineUrl(), venue, current.capacity()));
    }

    public void setSessionCapacity(int index, int capacity) {
        var current = sessions.get(index);
        sessions.set(index, new SessionDraft(
                current.mode(), current.onlineUrl(), current.venue(), capacity));
    }

    public List<SessionMode> sessionModes() {
        return sessionDrafts().stream().map(SessionDraft::mode).toList();
    }

    public List<SessionDraft> sessionDrafts() {
        return List.copyOf(sessions.subList(0, activeSessionCount));
    }

    public boolean hasSessionChanges() {
        return activeSessionCount != 2 || sessions.stream().anyMatch(draft -> !draft.equals(onlineDraft()));
    }

    public void applyCommonSessionSettings(SessionDraft settings) {
        Objects.requireNonNull(settings, "settings");
        if (settings.mode() == SessionMode.ONLINE) {
            onlineDetails(0, settings, 120);
        } else {
            required(settings.venue(), "Inserisci la sede comune delle sessioni");
            if (settings.capacity() < 1 || settings.capacity() > 500) {
                throw new IllegalArgumentException("La capienza comune deve essere compresa tra 1 e 500 posti");
            }
        }
        for (int index = 0; index < activeSessionCount; index++) {
            var previous = sessions.get(index);
            sessions.set(index, settings.mode() == SessionMode.ONLINE
                    ? new SessionDraft(settings.mode(), settings.onlineUrl(), previous.venue(), previous.capacity())
                    : new SessionDraft(settings.mode(), previous.onlineUrl(), settings.venue(), settings.capacity()));
        }
    }

    public LocalDate scheduledDate(int zeroBasedIndex, LocalDate startDate, int recurrenceDays) {
        Objects.requireNonNull(startDate, "startDate");
        if (zeroBasedIndex < 0 || zeroBasedIndex >= activeSessionCount) {
            throw new IllegalArgumentException("Indice sessione non valido");
        }
        validateRecurrence(recurrenceDays);
        return startDate.plusDays((long) zeroBasedIndex * recurrenceDays);
    }

    public NewCourseInput toInput(
            UUID categoryId,
            String title,
            String description,
            LocalDate startDate,
            String startTime,
            int recurrenceDays) {
        return toInput(
                categoryId,
                title,
                description,
                startDate,
                startTime,
                recurrenceDays,
                120);
    }

    public NewCourseInput toInput(
            UUID categoryId,
            String title,
            String description,
            LocalDate startDate,
            String startTime,
            int recurrenceDays,
            int durationMinutes) {
        if (categoryId == null) {
            throw new IllegalArgumentException("Seleziona una categoria");
        }
        var normalizedTitle = validatedTitle(title);
        var normalizedDescription = required(
                description, "Inserisci la descrizione del corso");
        if (startDate == null) {
            throw new IllegalArgumentException("Seleziona la data di inizio");
        }
        validateRecurrence(recurrenceDays);
        var parsedTime = parseStartTime(startTime);
        if (durationMinutes < 1 || durationMinutes > 720) {
            throw new IllegalArgumentException(
                    "La durata deve essere compresa tra 1 e 720 minuti");
        }
        return new NewCourseInput(
                categoryId,
                normalizedTitle,
                normalizedDescription,
                startDate,
                parsedTime,
                recurrenceDays,
                BigDecimal.ZERO,
                sessionDetails(durationMinutes));
    }

    private static String validatedTitle(String title) {
        required(title, "Inserisci il titolo del corso");
        try {
            return CourseTitle.of(title).value();
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(UserErrorMessages.validation(failure,
                    "Il titolo del corso deve contenere da 3 a 30 caratteri."), failure);
        }
    }

    private static LocalTime parseStartTime(String startTime) {
        try {
            return LocalTime.parse(required(startTime, "Inserisci un orario nel formato HH:mm"), TIME_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Inserisci un orario nel formato HH:mm", exception);
        }
    }

    private List<SessionDetails> sessionDetails(int durationMinutes) {
        var details = new ArrayList<SessionDetails>(activeSessionCount);
        for (var index = 0; index < activeSessionCount; index++) {
            var draft = sessions.get(index);
            if (draft.mode() == SessionMode.ONLINE) {
                details.add(onlineDetails(index, draft, durationMinutes));
            } else {
                var venue = required(
                        draft.venue(), "Inserisci la sede della sessione " + (index + 1));
                if (draft.capacity() < 1) {
                    throw new IllegalArgumentException(
                            "Inserisci una capienza valida per la sessione " + (index + 1));
                }
                details.add(SessionDetails.practical(
                        durationMinutes, venue, draft.capacity()));
            }
        }
        return List.copyOf(details);
    }

    private static SessionDetails onlineDetails(
            int index, SessionDraft draft, int durationMinutes) {
        try {
            var uri = URI.create(required(
                    draft.onlineUrl(),
                    "Inserisci un URL HTTPS valido per la sessione " + (index + 1)));
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getHost().isBlank()) {
                throw new IllegalArgumentException();
            }
            return SessionDetails.online(durationMinutes, uri);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Inserisci un URL HTTPS valido per la sessione " + (index + 1),
                    exception);
        }
    }

    private static SessionDraft onlineDraft() {
        return new SessionDraft(
                SessionMode.ONLINE,
                "",
                "Laboratorio UninaFoodLab",
                12);
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }

    private static void validateRecurrence(int recurrenceDays) {
        if (recurrenceDays < 1 || recurrenceDays > 31) {
            throw new IllegalArgumentException(
                    "La frequenza deve essere compresa tra 1 e 31 giorni");
        }
    }

    public record SessionDraft(
            SessionMode mode, String onlineUrl, String venue, int capacity) {

        public SessionDraft {
            Objects.requireNonNull(mode, "mode");
            onlineUrl = onlineUrl == null ? "" : onlineUrl;
            venue = venue == null ? "" : venue;
        }
    }
}
