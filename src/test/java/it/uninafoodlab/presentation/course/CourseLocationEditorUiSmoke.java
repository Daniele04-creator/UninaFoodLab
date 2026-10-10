package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.IngredientMargin;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.presentation.FxSnapshot;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class CourseLocationEditorUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var task = new FutureTask<Void>(() -> {
                var time = LocalDateTime.of(2037, 4, 5, 18, 30);
                var selected = new AtomicReference<CourseSessionSummary>();
                var clock = Clock.fixed(Instant.parse("2037-04-01T10:00:00Z"), ZoneId.of("Europe/Rome"));
                var editor = new CourseScheduleEditor(clock, () -> null, selected::get);
                for (var mode : SessionMode.values()) {
                    var session = new CourseSessionSummary(UUID.randomUUID(), 1, time, mode, SessionStatus.SCHEDULED,
                            120, mode == SessionMode.ONLINE ? Optional.of(URI.create("https://meet.example.test/original")) : Optional.empty(),
                            mode == SessionMode.PRACTICAL ? Optional.of("Laboratorio originale") : Optional.empty(),
                            mode == SessionMode.PRACTICAL ? Optional.of(12) : Optional.empty(), 0);
                    selected.set(session);
                    editor.populateScheduleEditor(session);
                    editor.rescheduleOriginalDate = time;
                    editor.suggestedScheduleMessage = null;
                    editor.updateSuggestedScheduleMessage();
                    ((GridPane) editor.scheduleDialogContent).getChildren().clear();
                    editor.scheduleDialogContent = editor.createScheduleDialogContent(true);
                    check(editor.scheduleLocationField.getParent() != null && !editor.scheduleLocationField.isDisabled()
                            && editor.scheduleLocationField.isEditable(), "Session location must be editable");
                    check(editor.scheduleModeField.getParent() == null && editor.scheduleDurationField.getParent() == null
                            && editor.scheduleCapacityField.getParent() == null, "Existing mode, duration and capacity remain fixed");
                    var newLocation = mode == SessionMode.ONLINE ? "https://meet.example.test/new-room" : "Nuovo laboratorio";
                    editor.scheduleLocationField.setText(newLocation);
                    check(editor.scheduleNotificationMessageField.getText().contains(newLocation)
                            && editor.scheduleNotificationMessageField.getText().contains("Data e ora restano invariate"),
                            "Suggested notice describes a location correction without claiming a time change");
                    editor.scheduleNotificationMessageField.setText("Messaggio personalizzato dello chef.");
                    editor.scheduleLocationField.setText(newLocation + (mode == SessionMode.ONLINE ? "/2" : " 2"));
                    check(editor.scheduleNotificationMessageField.getText().equals("Messaggio personalizzato dello chef."),
                            "Editing the location preserves the chef's custom notice");
                    var root = new ScrollPane(editor.scheduleDialogContent); root.setFitToWidth(true);
                    var scene = new Scene(root, 760, 800);
                    scene.getStylesheets().add(CourseLocationEditorUiSmoke.class.getResource("/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
                    var stage = new Stage(); stage.setScene(scene); stage.show(); root.applyCss(); root.layout();
                    FxSnapshot.save(root, "target/native-match/calendar-" + mode.name().toLowerCase(java.util.Locale.ROOT) + "-location.png");
                    stage.hide(); root.setContent(null);
                }
                ((GridPane) editor.scheduleDialogContent).getChildren().clear();
                editor.resetScheduleEditorForNewSession();
                editor.scheduleDialogContent = editor.createScheduleDialogContent(false);
                check(GridPane.getColumnSpan(editor.scheduleLocationField) == 1
                                && editor.scheduleCapacityField.getParent() != null,
                        "Returning to add-session restores separate location and capacity fields");
                var root = new ScrollPane(editor.scheduleDialogContent);
                root.setFitToWidth(true);
                var scene = new Scene(root, 540, 720);
                scene.getStylesheets().add(CourseLocationEditorUiSmoke.class.getResource("/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
                var stage = new Stage(); stage.setScene(scene); stage.show(); root.applyCss(); root.layout();
                var modeCell = editor.scheduleModeField.getButtonCell();
                check(modeCell.getText().equals("In presenza") && modeCell.getWidth() >= modeCell.prefWidth(-1) - 1,
                        "New session must show the whole In presenza label without ellipsis");
                check(editor.scheduleLocationField.getPromptText().equals("Indirizzo o link HTTPS"),
                        "Practical location must allow an address or HTTPS position link");
                FxSnapshot.save(root, "target/native-match/new-session-mode.png");
                var timeChoices = editor.scheduleAttendanceTimeField.getChildren().stream()
                        .filter(javafx.scene.control.ComboBox.class::isInstance)
                        .map(javafx.scene.control.ComboBox.class::cast).toList();
                for (var choice : timeChoices) {
                    var texts = choice.lookupAll(".text").stream().filter(javafx.scene.text.Text.class::isInstance)
                            .map(javafx.scene.text.Text.class::cast).map(javafx.scene.text.Text::getText).toList();
                    check(texts.contains("%02d".formatted(choice.getValue())),
                            "The attendance deadline must keep both time values fully visible: value="
                                    + choice.getValue() + ", width=" + choice.getWidth() + ", rendered="
                                    + texts);
                }
                stage.hide(); root.setContent(null);
                verifyCancellationCopy(editor, selected);
                return null;
            });
            Platform.runLater(task); task.get(20, TimeUnit.SECONDS);
            System.out.println("PASS: existing calendar edits online/practical location, preserves session characteristics and custom notices");
        } finally { Platform.exit(); }
    }

    private static void verifyCancellationCopy(CourseScheduleEditor editor,
            AtomicReference<CourseSessionSummary> selected) throws Exception {
        var course = new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Categoria sintetica",
                CourseTitle.of("W".repeat(CourseTitle.MAX_LENGTH)), "Descrizione sintetica",
                selected.get().scheduledAt().toLocalDate(), selected.get().scheduledAt().toLocalTime(),
                7, 2, Optional.of(selected.get().scheduledAt().plusDays(7).plusMinutes(120)),
                CourseStatus.PUBLISHED, IngredientMargin.zero());
        var owner = new Label("Calendario sintetico");
        var stage = new Stage();
        var actions = (CourseWorkspaceActions) java.lang.reflect.Proxy.newProxyInstance(
                CourseWorkspaceActions.class.getClassLoader(), new Class<?>[] {CourseWorkspaceActions.class},
                (proxy, method, arguments) -> {
                    throw new AssertionError("Conservative dismissal must not invoke " + method.getName());
                });
        var commands = new CourseScheduleCommands(null, null, editor,
                new CourseOperationDialogs(owner, () -> false), null, actions,
                () -> course, selected::get, () -> false);
        try {
            for (int[] viewport : new int[][] {{1000, 600}, {1440, 900}}) {
                stage.setScene(new Scene(new VBox(owner), viewport[0], viewport[1]));
                stage.show();
                var inspection = new FutureTask<Void>(() -> {
                    var pane = Window.getWindows().stream().filter(Window::isShowing)
                            .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance)
                            .map(DialogPane.class::cast).findFirst().orElseThrow();
                    var keep = pane.getButtonTypes().stream().filter(type -> type.getText().equals("Mantieni sessione"))
                            .findFirst().orElseThrow();
                    try {
                        pane.applyCss(); pane.layout();
                        FxSnapshot.save(pane, "target/native-match/cancellation-dialog-" + viewport[0] + ".png");
                        var consequence = pane.getContent().lookupAll(".label").stream()
                                .filter(Label.class::isInstance).map(Label.class::cast)
                                .filter(label -> label.getText().startsWith("La sessione non sarà"))
                                .findFirst().orElseThrow();
                        check(consequence.getHeight() >= consequence.prefHeight(consequence.getWidth()) - 1,
                                "Cancellation consequence must keep its wrapped text height: allocated="
                                        + consequence.getHeight() + ", preferred=" + consequence.prefHeight(consequence.getWidth()));
                        check(consequence.lookupAll(".text").stream().filter(javafx.scene.text.Text.class::isInstance)
                                        .map(javafx.scene.text.Text.class::cast).anyMatch(text -> text.getText().contains("Comunicazioni")),
                                "Cancellation consequence must render the full Comunicazioni destination without ellipsis");
                        var context = pane.getContent().lookupAll(".label").stream()
                                .filter(Label.class::isInstance).map(Label.class::cast)
                                .filter(label -> label.getText().startsWith(course.title().value())).findFirst().orElseThrow();
                        check(context.getHeight() >= context.prefHeight(context.getWidth()) - 1,
                                "Long course context must keep its wrapped text height");
                        check(context.lookupAll(".text").stream().filter(javafx.scene.text.Text.class::isInstance)
                                        .map(javafx.scene.text.Text.class::cast).anyMatch(text -> text.getText().equals(context.getText())),
                                "Maximum-width valid course title and date must render without ellipsis");
                        check(((Button) pane.lookupButton(keep)).isDefaultButton(), "Keep session remains the default action");
                    } finally {
                        ((Button) pane.lookupButton(keep)).fire();
                    }
                    return null;
                });
                Platform.runLater(inspection);
                commands.cancelSession();
                inspection.get(10, TimeUnit.SECONDS);
                stage.hide();
            }
        } finally { stage.close(); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
