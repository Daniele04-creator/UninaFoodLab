package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.catalog.CategorySummary;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.notification.NotificationDraft;
import it.uninafoodlab.domain.course.SessionMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import javafx.scene.control.Label;
import javafx.util.StringConverter;

final class CoursePresentation {
    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    static final DateTimeFormatter PROGRAM_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.ITALIAN);
    static final DateTimeFormatter COURSE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ITALIAN);
    static final ZoneId ROME = ZoneId.of("Europe/Rome");

    private CoursePresentation() {}

    static boolean sessionStatusNeedsAttention(String status) {
        return "Programmata".equals(status);
    }

    static String sessionStatusLabel(CourseSessionSummary session) {
        return switch (session.status()) {
            case SCHEDULED -> "Programmata";
            case COMPLETED -> "Svolta";
            case CANCELLED -> "Annullata";
        };
    }

    static String programSessionName(CourseSessionSummary session) {
        return "Sessione " + session.sequenceNumber();
    }

    static String programModeLabel(CourseSessionSummary session) {
        return modeLabel(session.mode());
    }

    static String recipeAssignmentLabel(CourseSessionSummary session) {
        Objects.requireNonNull(session, "session");
        if (session.mode() == SessionMode.ONLINE) {
            return "Non prevista";
        }
        if (session.recipeCount() == 0) {
            return "Nessuna ricetta";
        }
        return session.recipeCount() + (session.recipeCount() == 1 ? " ricetta" : " ricette");
    }

    static String recipeActionLabel(
            List<UUID> assignedRecipeIds, Optional<UUID> selectedRecipeId) {
        Objects.requireNonNull(assignedRecipeIds, "assignedRecipeIds");
        Objects.requireNonNull(selectedRecipeId, "selectedRecipeId");
        return selectedRecipeId.filter(assignedRecipeIds::contains).isPresent() ? "Già associata" : "Aggiungi";
    }

    static String formatSessionPreview(LocalDate date, String time) {
        var formattedDate = formatCourseStartDate(date);
        var formattedTime = time == null || time.isBlank() ? "ora da definire" : time.strip();
        return formattedDate + ", " + formattedTime;
    }

    static String formatCourseStartDate(LocalDate date) {
        return COURSE_DATE_FORMAT.format(date);
    }

    static String formatCourseSummary(int sessionCount, int recurrenceDays, int durationMinutes) {
        var sessions = sessionCount == 1 ? "1 sessione" : sessionCount + " sessioni";
        var recurrence = recurrenceDays == 1 ? "ogni giorno" : "ogni " + recurrenceDays + " giorni";
        return sessions + ", " + recurrence + ", " + durationMinutes + " min";
    }

    static NotificationDraft validatedNotification(String title, String message) {
        if (title == null || title.strip().length() < 3 || title.strip().length() > 120) {
            throw new IllegalArgumentException("Il titolo della comunicazione deve contenere da 3 a 120 caratteri.");
        }
        if (message == null || message.strip().length() < 3 || message.strip().length() > 2000) {
            throw new IllegalArgumentException("Il messaggio deve contenere da 3 a 2000 caratteri.");
        }
        return NotificationDraft.of(title, message);
    }

    static String scheduleMessage(String courseTitle, LocalDateTime previous, LocalDateTime updated) {
        return previous == null
                ? "Al corso «" + courseTitle + "» è stata aggiunta una sessione il " + DATE_TIME_FORMAT.format(updated) + "."
                : "La sessione del corso «" + courseTitle + "» prevista il " + DATE_TIME_FORMAT.format(previous)
                    + " è stata spostata al " + DATE_TIME_FORMAT.format(updated) + ".";
    }

    static String modeLabel(SessionMode mode) {
        return mode == SessionMode.ONLINE ? "Online" : "In presenza";
    }

    static StringConverter<CategorySummary> categoryConverter() {
        return converter(category -> category == null ? "" : category.name());
    }

    static StringConverter<CourseCategoryFilter> categoryFilterConverter() {
        return converter(option -> option == null ? "" : option.label());
    }

    static StringConverter<SessionMode> modeConverter() {
        return converter(mode -> mode == null ? "" : modeLabel(mode));
    }

    static StringConverter<LocalDate> courseDateConverter(LocalDate earliestDate) {
        Objects.requireNonNull(earliestDate, "earliestDate");
        return new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : formatCourseStartDate(date);
            }

            @Override
            public LocalDate fromString(String value) {
                if (value == null || value.isBlank()) {
                    return null;
                }
                var candidate = value.strip();
                LocalDate parsed;
                try {
                    parsed = LocalDate.parse(candidate, COURSE_DATE_FORMAT);
                } catch (DateTimeParseException _) {
                    parsed = LocalDate.parse(candidate, DATE_FORMAT);
                }
                return CourseDatePolicy.requireNotPast(parsed, earliestDate);
            }
        };
    }

    static <T> StringConverter<T> converter(Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return label.apply(value);
            }

            @Override
            public T fromString(String value) {
                return null;
            }
        };
    }

    static Label readOnlyValue(String value) {
        var label = new Label(value);
        label.setWrapText(true);
        label.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("page-description");
        return label;
    }

    static void setEditorVisible(javafx.scene.Node editor, boolean visible) {
        editor.setManaged(visible);
        editor.setVisible(visible);
    }
}
