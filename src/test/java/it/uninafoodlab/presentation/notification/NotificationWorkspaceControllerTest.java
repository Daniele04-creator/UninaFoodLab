package it.uninafoodlab.presentation.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationWorkspaceControllerTest {

    @Test
    void exitChecksIncludeOtherDestinationsButIgnoreAClearedCurrentDraft() {
        var drafts = new NotificationDrafts();
        var first = new NotificationWorkspaceController.CourseTarget(Optional.of(UUID.randomUUID()), "Primo corso");
        var second = new NotificationWorkspaceController.CourseTarget(Optional.of(UUID.randomUUID()), "Secondo corso");
        assertThat(drafts.hasUnsavedChanges("", "")).isEqualTo(false);
        drafts.changeTarget(first, "", "");
        assertThat(drafts.hasUnsavedChanges("Titolo", "Messaggio")).isEqualTo(true);
        drafts.changeTarget(second, "Titolo", "Messaggio");
        assertThat(drafts.hasUnsavedChanges("", "")).isEqualTo(true);
        drafts.changeTarget(first, "", "");
        assertThat(drafts.hasUnsavedChanges("", "")).isEqualTo(false);
        drafts.changeTarget(null, "", "");
        assertThat(drafts.hasUnsavedChanges("", "")).isEqualTo(false);
    }

    @Test
    void sendingCurrentDraftStillProtectsAnUnsentDraftForAnotherCourse() {
        var drafts = new NotificationDrafts();
        var first = new NotificationWorkspaceController.CourseTarget(Optional.of(UUID.randomUUID()), "Primo corso");
        var second = new NotificationWorkspaceController.CourseTarget(Optional.of(UUID.randomUUID()), "Secondo corso");
        drafts.changeTarget(first, "", "");
        drafts.changeTarget(second, "Bozza primo", "Messaggio primo");
        drafts.changeTarget(null, "", "");
        assertThat(drafts.hasUnsavedChanges("", "")).isEqualTo(true);
        drafts.changeTarget(first, "", "");
        drafts.changeTarget(null, "", "");
        assertThat(drafts.hasUnsavedChanges("", "")).isEqualTo(false);
    }

    @Test
    void sameNamedEditionsKeepHistoryAndDraftsIndependent() {
        var firstId = UUID.randomUUID();
        var secondId = UUID.randomUUID();
        var first = new NotificationWorkspaceController.NotificationView(UUID.randomUUID(), "Avviso", "Prima edizione",
                Optional.of("Pizza"), Instant.now(), "1 destinatario", Optional.of(firstId), Optional.of(java.time.LocalDate.of(2026, 9, 14)));
        var second = new NotificationWorkspaceController.NotificationView(UUID.randomUUID(), "Avviso", "Seconda edizione",
                Optional.of("Pizza"), Instant.now(), "1 destinatario", Optional.of(secondId), Optional.of(java.time.LocalDate.of(2026, 10, 14)));
        assertThat(NotificationHistoryView.filterHistory(List.of(first, second), Optional.of(first.historyCourse()), ""))
                .containsExactly(first);
        assertThat(first.courseLabel()).isEqualTo("Pizza, 14/09/2026");
        assertThat(second.courseLabel()).isEqualTo("Pizza, 14/10/2026");
        var drafts = new NotificationDrafts();
        var a = new NotificationWorkspaceController.CourseTarget(Optional.of(firstId), first.courseLabel());
        var b = new NotificationWorkspaceController.CourseTarget(Optional.of(secondId), second.courseLabel());
        drafts.changeTarget(a, "", "");
        assertThat(drafts.changeTarget(b, "Prima", "Testo prima").empty()).isTrue();
        assertThat(drafts.changeTarget(a, "Seconda", "Testo seconda").message()).isEqualTo("Testo prima");
    }

    @Test
    void newlyRegisteredMessagePreservesCompatibleFiltersAndClearsOnlyThoseThatHideIt() {
        var item = notification();
        var compatible = NotificationHistoryView.filtersForCreated(item,
                Optional.of(item.historyCourse()), "PRESENZA");
        assertThat(compatible.course()).contains(item.historyCourse());
        assertThat(compatible.query()).isEqualTo("PRESENZA");
        var incompatible = NotificationHistoryView.filtersForCreated(item,
                Optional.of(new NotificationWorkspaceController.HistoryCourse(false, Optional.of(UUID.randomUUID()), "Altro corso")), "assente");
        assertThat(incompatible.course()).isEmpty();
        assertThat(incompatible.query()).isEmpty();
        assertThat(NotificationHistoryView.filterHistory(List.of(item), incompatible.course(), incompatible.query()))
                .containsExactly(item);
    }

    @Test
    void switchingCourseKeepsSeparateDraftsAndRestoresTheirExactText() {
        var drafts = new NotificationDrafts();
        var courseA = new NotificationWorkspaceController.CourseTarget(Optional.of(UUID.randomUUID()), "Corso A");
        var courseB = new NotificationWorkspaceController.CourseTarget(Optional.of(UUID.randomUUID()), "Corso B");
        drafts.changeTarget(courseA, "", "");

        var blankB = drafts.changeTarget(courseB, "  Titolo A  ", "Prima riga A\nSeconda riga A");
        assertThat(blankB.title()).isEmpty();
        assertThat(blankB.message()).isEmpty();

        var restoredA = drafts.changeTarget(courseA, "Titolo B", "Messaggio B");
        assertThat(restoredA.title()).isEqualTo("  Titolo A  ");
        assertThat(restoredA.message()).isEqualTo("Prima riga A\nSeconda riga A");
        assertThat(drafts.changeTarget(courseB, restoredA.title(), restoredA.message()).message())
                .isEqualTo("Messaggio B");
    }

    @Test
    void globalDraftIsSeparateAndSuccessfulSendDoesNotRestoreTheSentMessage() {
        var drafts = new NotificationDrafts();
        var course = new NotificationWorkspaceController.CourseTarget(Optional.of(UUID.randomUUID()), "Corso");
        var global = NotificationWorkspaceController.CourseTarget.allCourses();
        drafts.changeTarget(global, "", "");
        drafts.changeTarget(course, "Avviso globale", "Per tutti i corsi");
        var globalDraft = drafts.changeTarget(global, "Titolo corso", "Solo per questo corso");
        assertThat(globalDraft.message()).isEqualTo("Per tutti i corsi");
        drafts.changeTarget(null, "", "");
        assertThat(drafts.changeTarget(global, "", "").message()).isEmpty();
        assertThat(drafts.changeTarget(course, "", "").message()).isEqualTo("Solo per questo corso");
    }

    @Test
    void initialDraftCanBeAssignedToItsFirstDestinationAndCourseRenameKeepsIt() {
        var drafts = new NotificationDrafts();
        var courseId = UUID.randomUUID();
        var selected = drafts.changeTarget(new NotificationWorkspaceController.CourseTarget(
                Optional.of(courseId), "Prima etichetta"), "Senza destinatario", "Testo preparato");
        assertThat(selected.message()).isEqualTo("Testo preparato");
        assertThat(drafts.changeTarget(new NotificationWorkspaceController.CourseTarget(
                Optional.of(courseId), "Etichetta aggiornata"), selected.title(), selected.message()).message())
                .isEqualTo("Testo preparato");
    }

    @Test
    void historySearchCombinesCourseAndMessageFilters() {
        var notice = notification();
        assertThat(NotificationHistoryView.filterHistory(
                List.of(notice), Optional.of(notice.historyCourse()), "  PRESENZA "))
                .containsExactly(notice);
        assertThat(NotificationHistoryView.filterHistory(
                List.of(notice), Optional.of(new NotificationWorkspaceController.HistoryCourse(false, Optional.of(UUID.randomUUID()), "Altro corso")), "presenza")).isEmpty();
        assertThat(NotificationHistoryView.filterHistory(
                List.of(notice), Optional.empty(), "assente")).isEmpty();
    }

    @Test
    void recipientSummaryMakesGlobalAndCourseScopeExplicit() {
        assertThat(NotificationRecipients.recipientSummary(
                NotificationWorkspaceController.CourseTarget.allCourses(), 3))
                .contains("3 partecipanti", "tutti i tuoi corsi", "una sola volta");
        assertThat(NotificationRecipients.recipientSummary(
                new NotificationWorkspaceController.CourseTarget(Optional.of(UUID.randomUUID()), "Pizza"), 1))
                .contains("1 partecipante", "Pizza");
    }

    @Test
    void refreshedHistoryKeepsTheSelectedNotificationWhenItStillExists() {
        var first = notification(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        var selected = notification(UUID.fromString("00000000-0000-0000-0000-000000000002"));

        assertThat(NotificationHistoryView.notificationToSelect(
                        List.of(first, selected), selected.id()))
                .contains(selected);
        assertThat(NotificationHistoryView.notificationToSelect(
                        List.of(first), selected.id()))
                .contains(first);
        assertThat(NotificationHistoryView.notificationToSelect(List.of(), selected.id()))
                .isEmpty();
    }

    @Test
    void automaticRefreshLifecycleStopsForNavigationAndCannotRestartAfterLogout() {
        var lifecycle = new NotificationRefreshLifecycle();

        assertThat(lifecycle.isActive()).isFalse();
        lifecycle.activate();
        assertThat(lifecycle.isActive()).isTrue();
        lifecycle.deactivate();
        assertThat(lifecycle.isActive()).isFalse();
        lifecycle.activate();
        lifecycle.dispose();
        lifecycle.activate();
        assertThat(lifecycle.isActive()).isFalse();
    }

    @Test
    void queuedRefreshIsDiscardedWhenTheWorkspaceBecomesInactive() {
        var lifecycle = new NotificationRefreshLifecycle();

        lifecycle.activate();
        lifecycle.requestFollowUp();
        lifecycle.deactivate();
        lifecycle.activate();

        assertThat(lifecycle.consumeFollowUp()).isFalse();
        lifecycle.requestFollowUp();
        lifecycle.dispose();
        assertThat(lifecycle.consumeFollowUp()).isFalse();
    }

    private static NotificationWorkspaceController.NotificationView notification() {
        return notification(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    private static NotificationWorkspaceController.NotificationView notification(UUID id) {
        return new NotificationWorkspaceController.NotificationView(
                id,
                "Promemoria",
                "Conferma la tua presenza.",
                Optional.of("Cucina vegetale creativa"),
                Instant.parse("2026-08-14T10:40:00Z"),
                "3 destinatari");
    }
}
