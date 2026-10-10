package it.uninafoodlab.presentation.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.domain.course.SessionMode;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseFormModelTest {

    private static final UUID CATEGORY_ID = UUID.randomUUID();

    @Test
    void commonSettingsApplyOnlyOnExplicitActionAndPreserveExcludedDrafts() {
        var model = new CourseFormModel();
        assertThat(model.hasSessionChanges()).isFalse();
        model.setSessionCount(3);
        model.setSessionOnlineUrl(2, "https://example.test/excluded");
        model.setSessionCount(2);
        model.setSessionVenue(0, "Sede personale");
        var common = new CourseFormModel.SessionDraft(SessionMode.ONLINE, "https://example.test/common", "Sede comune", 24);

        assertThat(model.sessionDrafts().getFirst().onlineUrl()).isEmpty();
        model.applyCommonSessionSettings(common);
        assertThat(model.sessionDrafts()).extracting(CourseFormModel.SessionDraft::onlineUrl)
                .containsExactly(common.onlineUrl(), common.onlineUrl());
        assertThat(model.sessionDrafts().getFirst().venue()).isEqualTo("Sede personale");
        model.setSessionCount(3);
        assertThat(model.sessionDrafts().getLast().onlineUrl()).isEqualTo("https://example.test/excluded");
        model.resetSessions(2);
        assertThat(model.hasSessionChanges()).isFalse();
    }

    @Test
    void commonSettingsRejectInvalidDetailsBeforeUpdatingAnySession() {
        var model = new CourseFormModel();
        model.setSessionOnlineUrl(0, "https://example.test/personal");
        var before = model.sessionDrafts();
        assertThatThrownBy(() -> model.applyCommonSessionSettings(new CourseFormModel.SessionDraft(
                SessionMode.ONLINE, "http://example.test/invalid", "", 12))).isInstanceOf(IllegalArgumentException.class);
        assertThat(model.sessionDrafts()).isEqualTo(before);
        assertThatThrownBy(() -> model.applyCommonSessionSettings(new CourseFormModel.SessionDraft(
                SessionMode.PRACTICAL, "", "Sede", 501))).isInstanceOf(IllegalArgumentException.class);
        assertThat(model.sessionDrafts()).isEqualTo(before);
        model.applyCommonSessionSettings(new CourseFormModel.SessionDraft(SessionMode.PRACTICAL, "", "Sede", 25));
        assertThat(model.sessionDrafts()).extracting(CourseFormModel.SessionDraft::mode)
                .containsExactly(SessionMode.PRACTICAL, SessionMode.PRACTICAL);
        assertThat(model.sessionDrafts().getFirst().onlineUrl()).isEqualTo("https://example.test/personal");
        assertThat(model.sessionDrafts()).extracting(CourseFormModel.SessionDraft::capacity).containsExactly(25, 25);
    }

    @Test
    void growsAndShrinksWhilePreservingExistingModes() {
        var model = new CourseFormModel();

        model.setSessionCount(3);
        model.setSessionMode(1, SessionMode.PRACTICAL);
        model.setSessionCount(4);

        assertThat(model.sessionModes())
                .containsExactly(
                        SessionMode.ONLINE,
                        SessionMode.PRACTICAL,
                        SessionMode.ONLINE,
                        SessionMode.ONLINE);

        model.setSessionCount(2);
        assertThat(model.sessionModes())
                .containsExactly(SessionMode.ONLINE, SessionMode.PRACTICAL);
        var modes = model.sessionModes();
        assertThatThrownBy(() -> modes.add(SessionMode.ONLINE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void restoresTemporarilyExcludedDraftsAndBothModeDetails() {
        var model = new CourseFormModel();
        model.setSessionCount(3);
        model.setSessionOnlineUrl(2, "https://example.test/lesson");
        model.setSessionMode(2, SessionMode.PRACTICAL);
        model.setSessionVenue(2, "Laboratorio personale");
        model.setSessionCapacity(2, 27);
        var practical = model.sessionDrafts().get(2);
        model.setSessionMode(2, SessionMode.ONLINE);
        assertThat(model.sessionDrafts().get(2).onlineUrl()).isEqualTo("https://example.test/lesson");
        model.setSessionCount(2);
        model.setSessionCount(3);
        model.setSessionMode(2, SessionMode.PRACTICAL);
        assertThat(model.sessionDrafts().get(2)).isEqualTo(practical);
        model.setSessionCount(2);
        model.resetSessions(2);
        model.setSessionCount(3);
        assertThat(model.sessionDrafts().get(2).onlineUrl()).isEmpty();
    }

    @Test
    void rejectsSessionCountsOutsideTheTraceBoundary() {
        var model = new CourseFormModel();

        assertThatThrownBy(() -> model.setSessionCount(1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Il numero di sessioni deve essere compreso tra 2 e 30");
        assertThatThrownBy(() -> model.setSessionCount(31))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Il numero di sessioni deve essere compreso tra 2 e 30");
    }

    @Test
    void resetRestoresFreshOnlineSessionDetails() {
        var model = new CourseFormModel();
        model.setSessionOnlineUrl(0, "https://example.test/old");

        model.resetSessions(2);

        assertThat(model.sessionDrafts().getFirst().onlineUrl())
                .isEmpty();
    }

    @Test
    void newOnlineSessionRequiresAnExplicitLinkWithoutSubmittingADemoAddress() {
        var model = new CourseFormModel();
        var startDate = LocalDate.of(2026, 9, 15);
        assertThatThrownBy(() -> model.toInput(CATEGORY_ID, "Titolo", "Descrizione",
                startDate, "18:30", 7))
                .hasMessage("Inserisci un URL HTTPS valido per la sessione 1");
    }

    @Test
    void convertsTheCompleteFormToAnExactApplicationInput() {
        var model = new CourseFormModel();
        model.setSessionCount(2);
        model.setSessionMode(1, SessionMode.PRACTICAL);
        model.setSessionOnlineUrl(0, "https://meet.example.test/pizza");
        model.setSessionVenue(1, "  Laboratorio Vesuvio  ");
        model.setSessionCapacity(1, 16);

        var input = model.toInput(
                CATEGORY_ID,
                "  Pizza contemporanea  ",
                "  Impasti e cottura  ",
                LocalDate.of(2026, 9, 1),
                "18:30",
                7,
                150);

        assertThat(input.categoryId()).isEqualTo(CATEGORY_ID);
        assertThat(input.title()).isEqualTo("Pizza contemporanea");
        assertThat(input.description()).isEqualTo("Impasti e cottura");
        assertThat(input.startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(input.startTime()).isEqualTo(LocalTime.of(18, 30));
        assertThat(input.recurrenceDays()).isEqualTo(7);
        assertThat(input.ingredientMarginPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(input.sessionDetails())
                .extracting(it.uninafoodlab.domain.course.SessionDetails::mode)
                .containsExactly(SessionMode.ONLINE, SessionMode.PRACTICAL);
        assertThat(input.sessionDetails().getFirst().durationMinutes()).isEqualTo(150);
        assertThat(input.sessionDetails().getFirst().onlineUrl())
                .contains(URI.create("https://meet.example.test/pizza"));
        assertThat(input.sessionDetails().getLast().venue()).contains("Laboratorio Vesuvio");
        assertThat(input.sessionDetails().getLast().capacity()).contains(16);
        assertThat(model.scheduledDate(1, input.startDate(), input.recurrenceDays()))
                .isEqualTo(LocalDate.of(2026, 9, 8));
    }

    @Test
    void reportsMissingOrMalformedFieldsInItalian() {
        var model = new CourseFormModel();
        var date = LocalDate.of(2026, 9, 1);

        assertThatThrownBy(() -> model.toInput(
                        null, "Titolo", "Descrizione", date, "18:30", 7, 120))
                .hasMessage("Seleziona una categoria");
        assertThatThrownBy(() -> model.toInput(
                        CATEGORY_ID, " ", "Descrizione", date, "18:30", 7, 120))
                .hasMessage("Inserisci il titolo del corso");
        assertThatThrownBy(() -> model.toInput(
                        CATEGORY_ID, "Titolo", " ", date, "18:30", 7, 120))
                .hasMessage("Inserisci la descrizione del corso");
        assertThatThrownBy(() -> model.toInput(
                        CATEGORY_ID, "Titolo", "Descrizione", null, "18:30", 7, 120))
                .hasMessage("Seleziona la data di inizio");
        assertThatThrownBy(() -> model.toInput(
                        CATEGORY_ID, "Titolo", "Descrizione", date, "18.30", 7, 120))
                .hasMessage("Inserisci un orario nel formato HH:mm");

        model.setSessionOnlineUrl(0, "http://example.test/non-sicuro");
        assertThatThrownBy(() -> model.toInput(
                        CATEGORY_ID, "Titolo", "Descrizione", date, "18:30", 7, 120))
                .hasMessage("Inserisci un URL HTTPS valido per la sessione 1");

        model.setSessionMode(0, SessionMode.PRACTICAL);
        model.setSessionVenue(0, " ");
        assertThatThrownBy(() -> model.toInput(
                        CATEGORY_ID, "Titolo", "Descrizione", date, "18:30", 7, 120))
                .hasMessage("Inserisci la sede della sessione 1");
    }

    @Test void validatesTheTitleLengthInItalianBeforeSubmittingTheCourse() {
        var model = new CourseFormModel();
        for (String title : new String[]{"ab", "x".repeat(31)}) {
            assertThatThrownBy(() -> model.toInput(CATEGORY_ID, title, "Descrizione",
                    LocalDate.of(2026, 10, 5), "18:30", 7))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Il titolo del corso deve contenere da 3 a 30 caratteri.");
        }
        for (String title : new String[]{"Pane\npizza", "Pane\rpizza", "\nPane",
                "Pane\u0085pizza", "Pane\u2028pizza", "Pane\u2029pizza"}) {
            assertThatThrownBy(() -> model.toInput(CATEGORY_ID, title, "Descrizione",
                    LocalDate.of(2026, 10, 5), "18:30", 7))
                    .hasMessage("Il titolo del corso deve essere su una sola riga.");
        }
        model.setSessionOnlineUrl(0, "https://example.test/session-1");
        model.setSessionOnlineUrl(1, "https://example.test/session-2");
        assertThat(model.toInput(CATEGORY_ID, "x".repeat(30), "Descrizione",
                LocalDate.of(2026, 10, 5), "18:30", 7).title()).hasSize(30);
        assertThat(model.toInput(CATEGORY_ID, "abc", "Descrizione",
                LocalDate.of(2026, 10, 5), "18:30", 7).title()).isEqualTo("abc");
    }
}
