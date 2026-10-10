package it.uninafoodlab.presentation.course;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;

public final class NumericSpinnerUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var verification = new FutureTask<>(() -> {
                var spinner = new Spinner<Integer>();
                spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 30, 2));
                var feedback = new java.util.concurrent.atomic.AtomicReference<String>();
                NumericSpinner.configure(spinner, "Il numero di sessioni deve essere compreso tra 2 e 30", feedback::set);
                var nextField = new javafx.scene.control.Button("Campo successivo");
                var stage = new javafx.stage.Stage();
                stage.setScene(new javafx.scene.Scene(new javafx.scene.layout.VBox(spinner, nextField), 500, 220));
                stage.show();
                for (String value : new String[]{"12", "15", "30"}) {
                    var editor = spinner.getEditor();
                    editor.selectAll();
                    editor.replaceSelection(value.substring(0, 1));
                    editor.replaceSelection(value.substring(1));
                    check(editor.getText().equals(value), "Typing must preserve the complete number " + value);
                    spinner.commitValue();
                    check(spinner.getValue() == Integer.parseInt(value), "Completed typing must commit " + value);
                }
                for (String invalid : new String[]{"31", "0", "", "999999999999"}) {
                    var editor = spinner.getEditor();
                    editor.setText(invalid);
                    spinner.commitValue();
                    check(editor.getText().equals(invalid), "Invalid input remains visible without clamping");
                    check(feedback.get() != null && feedback.get().contains("2 e 30"), "Validation explains numeric bounds");
                    try {
                        NumericSpinner.requireValue(spinner, "Il numero di sessioni deve essere compreso tra 2 e 30");
                        throw new AssertionError("Invalid input cannot submit the previous spinner value");
                    } catch (IllegalArgumentException expected) {
                        check(expected.getMessage().contains("sessioni"), "Submission identifies the invalid field");
                    }
                    spinner.increment();
                    check(editor.getText().equals(invalid), "Arrow cannot silently replace invalid text with a stale value");
                }
                spinner.getEditor().setText("12");
                spinner.increment();
                check(spinner.getValue() == 13 && spinner.getEditor().getText().equals("13"), "Increment follows corrected valid text");
                spinner.decrement();
                check(spinner.getValue() == 12, "Decrement remains native and usable");
                check(NumericSpinner.requireValue(spinner, "Il numero di sessioni deve essere compreso tra 2 e 30") == 12, "Recovery clears invalid state and submits intended value");
                check(spinner.getEditor().getAccessibleHelp() == null, "Recovery clears invalid help");
                spinner.getEditor().requestFocus();
                spinner.getEditor().setText("31");
                nextField.requestFocus();
                check(spinner.getEditor().getText().equals("31"), "Native focus loss preserves invalid intent");
                spinner.getEditor().fireEvent(new javafx.event.ActionEvent());
                check(spinner.getEditor().getText().equals("31"), "Native Enter preserves invalid intent");
                verifyCreationSubmission(stage);
                stage.close();
                return null;
            });
            Platform.runLater(verification);
            verification.get(10, TimeUnit.SECONDS);
            System.out.println("PASS NumericSpinnerUiSmoke: native integer editing");
        } finally { Platform.exit(); }
    }

    private static void verifyCreationSubmission(javafx.stage.Stage stage) {
        var submitted = new java.util.concurrent.atomic.AtomicReference<it.uninafoodlab.application.course.CourseCreation>();
        var repository = (it.uninafoodlab.application.course.CourseRepository) java.lang.reflect.Proxy.newProxyInstance(
                NumericSpinnerUiSmoke.class.getClassLoader(), new Class<?>[]{it.uninafoodlab.application.course.CourseRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("createAndPublish")) {
                        submitted.set((it.uninafoodlab.application.course.CourseCreation) args[0]);
                        return java.util.UUID.randomUUID();
                    }
                    throw new AssertionError("Unexpected repository call: " + method.getName());
                });
        var feedback = new java.util.concurrent.atomic.AtomicReference<String>();
        var actions = (CourseWorkspaceActions) java.lang.reflect.Proxy.newProxyInstance(
                NumericSpinnerUiSmoke.class.getClassLoader(), new Class<?>[]{CourseWorkspaceActions.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("runAsync")) ((java.util.concurrent.Callable<?>) args[1]).call();
                    if (method.getName().equals("showFeedback")) feedback.set((String) args[0]);
                    return null;
                });
        var fields = new CourseCreationForm.Fields(new javafx.scene.control.ComboBox<>(),
                new javafx.scene.control.TextField(), new javafx.scene.control.TextArea(),
                new javafx.scene.control.DatePicker(), new TimeSelector(), new Spinner<>(), new Spinner<>(), new Spinner<>(),
                new javafx.scene.layout.TilePane(), new javafx.scene.control.Label());
        var user = new it.uninafoodlab.domain.identity.AuthenticatedUser(java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), it.uninafoodlab.domain.identity.Username.of("smoke.numeric"),
                "Chef sintetico", it.uninafoodlab.domain.identity.Role.CHEF);
        var form = new CourseCreationForm(fields, java.time.Clock.fixed(java.time.Instant.parse("2026-10-01T08:00:00Z"),
                java.time.ZoneOffset.UTC), new it.uninafoodlab.application.course.CreateCourse(repository), user, actions);
        form.configureForm();
        fields.categoryField().setValue(new it.uninafoodlab.application.catalog.CategorySummary(java.util.UUID.randomUUID(), "Prova", "Categoria sintetica"));
        fields.titleField().setText("Corso sintetico");
        fields.descriptionField().setText("Descrizione sintetica");
        stage.getScene().setRoot(new javafx.scene.layout.VBox(fields.recurrenceField(), fields.sessionCountField(),
                fields.durationField(), fields.titleField()));
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        fields.sessionCountField().getEditor().setText("31");
        fields.sessionCountField().getEditor().requestFocus();
        fields.titleField().requestFocus();
        form.createCourse();
        check(submitted.get() == null, "Invalid session count cannot write a course using the old count");
        check(fields.sessionCountField().getEditor().getText().equals("31"), "Rejected submission preserves typed session count");
        check(stage.getScene().getFocusOwner() == fields.sessionCountField().getEditor()
                        || stage.getScene().getFocusOwner() == fields.sessionCountField(),
                "Rejected submission focuses the session count: " + stage.getScene().getFocusOwner() + "; feedback=" + feedback.get());
        check(feedback.get().contains("numero di sessioni"), "Rejected submission identifies count bounds");
        fields.sessionCountField().getEditor().setText("12");
        check(fields.sessionModeEditor().getChildren().size() == 12, "Valid full entry rebuilds twelve drafts live");
        for (var editor : fields.sessionModeEditor().getChildren()) {
            ((javafx.scene.control.TextField) editor.lookup(".text-field")).setText("https://example.test/session");
        }
        fields.recurrenceField().getEditor().setText("0");
        form.createCourse();
        check(submitted.get() == null && feedback.get().contains("frequenza"), "Invalid frequency cannot submit stale calendar values");
        check(stage.getScene().getFocusOwner() == fields.recurrenceField()
                        || stage.getScene().getFocusOwner() == fields.recurrenceField().getEditor(),
                "Rejected frequency receives focus");
        fields.recurrenceField().getEditor().setText("15");
        fields.durationField().getEditor().setText("0");
        form.createCourse();
        check(submitted.get() == null && feedback.get().contains("durata"), "Invalid duration cannot submit stale values");
        check(stage.getScene().getFocusOwner() == fields.durationField()
                        || stage.getScene().getFocusOwner() == fields.durationField().getEditor(),
                "Rejected duration receives focus");
        fields.durationField().getEditor().setText("90");
        form.createCourse();
        check(submitted.get() != null && submitted.get().schedule().sessions().size() == 12,
                "Corrected submission writes exactly twelve sessions");
        check(submitted.get().schedule().recurrenceDays() == 15
                        && submitted.get().schedule().sessions().getFirst().details().durationMinutes() == 90,
                "Submission uses complete typed recurrence and duration");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
