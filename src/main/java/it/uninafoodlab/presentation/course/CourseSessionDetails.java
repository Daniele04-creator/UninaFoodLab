package it.uninafoodlab.presentation.course;

import static it.uninafoodlab.presentation.course.CoursePresentation.DATE_TIME_FORMAT;
import static it.uninafoodlab.presentation.course.CoursePresentation.modeLabel;
import static it.uninafoodlab.presentation.course.CoursePresentation.setEditorVisible;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.presentation.common.SessionLocationLinks;
import java.awt.Desktop;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

final class CourseSessionDetails {
    record Fields(Label selectedSessionContext, Label sessionLocationCaption, Label sessionLocationValue,
            HBox programLessonActions, HBox detailLessonActions,
            Button openProgramLessonButton, Button copyProgramLessonButton,
            Button openDetailLessonButton, Button copyDetailLessonButton,
            Label sessionCapacityValue, Label sessionCutoffValue, VBox sessionRecipeLinks, Button sessionMenuButton) {}

    private final Fields fields;
    private final TableView<CourseSessionSummary> table;
    private final CourseWorkspaceActions actions;

    CourseSessionDetails(Fields fields, TableView<CourseSessionSummary> table, CourseWorkspaceActions actions) {
        this.fields = fields;
        this.table = table;
        this.actions = actions;
    }

    void update(CourseSessionSummary session, boolean busy) {
        setEditorVisible(fields.selectedSessionContext(), false);
        fields.selectedSessionContext().setText(session == null ? "Seleziona una sessione." :
                DATE_TIME_FORMAT.format(session.scheduledAt()) + ", " + modeLabel(session.mode())
                        + ", " + session.durationMinutes() + " minuti");
        updateLocation(session, busy);
        fields.sessionCapacityValue().setText(session == null ? "—" : session.capacity()
                .map(value -> value + " posti").orElse("Non prevista"));
        fields.sessionCutoffValue().setText(session == null ? "—" : session.attendanceClosesAt()
                .map(DATE_TIME_FORMAT::format).orElse("Non prevista"));
        fields.sessionRecipeLinks().getChildren().clear();
        setEditorVisible(fields.sessionRecipeLinks(), false);
        setEditorVisible(fields.sessionMenuButton(), session != null);
        fields.sessionMenuButton().setDisable(busy);
        fields.sessionMenuButton().setText("Gestisci sessione");
    }

    private void updateLocation(CourseSessionSummary session, boolean busy) {
        var onlineSession = session != null && session.mode() == SessionMode.ONLINE;
        var positionLink = session != null && session.mode() == SessionMode.PRACTICAL
                && SessionLocationLinks.fromVenue(session.venue()).isPresent();
        fields.sessionLocationCaption().setText(onlineSession ? "Link della lezione" : "Sede o posizione");
        fields.sessionLocationValue().setText(positionLink ? "Posizione" : session == null ? "—" : session.venue()
                .orElseGet(() -> session.onlineUrl().map(url -> "Lezione online").orElse("—")));
        for (var container : List.of(fields.programLessonActions(), fields.detailLessonActions())) {
            setEditorVisible(container, onlineSession || positionLink);
        }
        var usableLink = SessionInteractionPolicy.usableLessonLink(selectedSessionLink());
        configureLinkActions(onlineSession, busy || !usableLink);
        if (onlineSession && !usableLink) {
            fields.sessionLocationValue().setText("Link della lezione non disponibile.");
        }
    }

    private void configureLinkActions(boolean online, boolean disabled) {
        for (var button : List.of(fields.openProgramLessonButton(), fields.openDetailLessonButton())) {
            button.setText(online ? "Apri lezione" : "Apri posizione");
            button.setAccessibleText(button.getText());
        }
        for (var button : List.of(fields.copyProgramLessonButton(), fields.copyDetailLessonButton())) {
            button.setText("Copia link");
            button.setAccessibleText(online ? "Copia link della lezione" : "Copia link della posizione");
        }
        for (var button : List.of(fields.openProgramLessonButton(), fields.copyProgramLessonButton(),
                fields.openDetailLessonButton(), fields.copyDetailLessonButton())) {
            button.setDisable(disabled);
        }
    }

    void openLesson() {
        var link = selectedSessionLink();
        if (!SessionInteractionPolicy.usableLessonLink(link)) return;
        var position = selectedPosition();
        actions.runAsync("open-lesson", () -> {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IllegalStateException("Browser non disponibile");
            }
            Desktop.getDesktop().browse(link.orElseThrow());
            return true;
        }, ignored -> actions.showFeedback(position ? "Posizione aperta nel browser." : "Lezione aperta nel browser.", false),
                "Impossibile aprire il browser. Usa Copia link e incolla l'indirizzo nel browser.");
    }

    void copyLessonLink() {
        var link = selectedSessionLink();
        if (!SessionInteractionPolicy.usableLessonLink(link)) return;
        var content = new ClipboardContent();
        content.putString(link.orElseThrow().toString());
        boolean copied = Clipboard.getSystemClipboard().setContent(content);
        var success = selectedPosition() ? "Link della posizione copiato." : "Link della lezione copiato.";
        actions.showFeedback(copied ? success : "Impossibile copiare il link. Riprova.", !copied);
    }

    private Optional<URI> selectedSessionLink() {
        var session = table.getSelectionModel().getSelectedItem();
        if (session == null) return Optional.empty();
        return session.mode() == SessionMode.ONLINE ? session.onlineUrl() : SessionLocationLinks.fromVenue(session.venue());
    }

    private boolean selectedPosition() {
        var session = table.getSelectionModel().getSelectedItem();
        return session != null && session.mode() == SessionMode.PRACTICAL;
    }
}
