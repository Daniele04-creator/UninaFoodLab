package it.uninafoodlab.presentation.notification;

import it.uninafoodlab.application.course.BrowseCourses;
import it.uninafoodlab.application.course.CourseRepository;
import it.uninafoodlab.application.notification.BrowseNotifications;
import it.uninafoodlab.application.notification.ManageNotifications;
import it.uninafoodlab.application.notification.NotificationRepository;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;

public final class NotificationComposerUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            verifyComposerFeedback();
            System.out.println("PASS: notification field feedback, draft validity, recipients and compact footer");
        } finally {
            Platform.exit();
        }
    }

    private static void verifyComposerFeedback() throws Exception {
        var user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Username.of("smoke.chef"), "Chef sintetico", Role.CHEF);
        var repository = (NotificationRepository) Proxy.newProxyInstance(
                NotificationRepository.class.getClassLoader(), new Class<?>[]{NotificationRepository.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "countRecipients" -> 2L;
                    case "findForChef", "findForParticipant" -> List.of();
                    default -> throw new AssertionError("Composer smoke must never write data");
                });
        var courses = (CourseRepository) Proxy.newProxyInstance(CourseRepository.class.getClassLoader(),
                new Class<?>[]{CourseRepository.class}, (proxy, method, arguments) -> List.of());
        var controller = new NotificationWorkspaceController(user, new BrowseNotifications(repository),
                new ManageNotifications(repository), new BrowseCourses(courses));
        var stage = fx(() -> {
            var loader = new FXMLLoader(NotificationComposerUiSmoke.class.getResource(
                    "/it/uninafoodlab/presentation/notification/notification-workspace.fxml"));
            loader.setController(controller);
            Parent root = loader.load();
            var window = new Stage();
            window.setScene(new Scene(root, 1000, 600));
            window.getScene().getStylesheets().add(NotificationComposerUiSmoke.class.getResource(
                    "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
            window.show();
            button(root, "composeButton").fire();
            return window;
        });
        try {
            var root = stage.getScene().getRoot();
            fx(() -> {
                var titleFeedback = (Label) root.lookup("#titleFeedbackLabel");
                var messageFeedback = (Label) root.lookup("#messageFeedbackLabel");
                check(titleFeedback != null && messageFeedback != null,
                        "Both fields expose their own visible feedback and current character count");
                var title = text(root, "titleField");
                var message = text(root, "messageField");
                check(titleFeedback.getLabelFor() == title && messageFeedback.getLabelFor() == message,
                        "Feedback labels are associated with their input fields");
                check(titleFeedback.getText().contains("0/120") && messageFeedback.getText().contains("0/2000"),
                        "New drafts expose neutral counts without showing required errors");
                check(!titleFeedback.getStyleClass().contains("notification-field-error")
                                && !messageFeedback.getStyleClass().contains("notification-field-error"),
                        "An untouched empty composer must not show errors");
                check(!controller.hasUnsavedChanges(), "An untouched composer does not warn on exit");
                title.setText("Bozza");
                check(controller.hasUnsavedChanges(), "Text without recipients is protected on exit");
                title.clear();
                check(!controller.hasUnsavedChanges(), "Clearing a reverted composer clears exit protection");
                title.setText("a");
                check(titleFeedback.getText().contains("almeno 3") && title.getAccessibleHelp().contains("almeno 3"),
                        "A short edited title explains the missing characters visibly and accessibly");
                title.setText("x".repeat(121));
                message.setText("Testo valido");
                check(titleFeedback.getText().contains("elimina 1 carattere")
                                && titleFeedback.getText().contains("121/120") && button(root, "sendButton").isDisabled(),
                        "An overlong title blocks sending and explains exactly what to correct");
                title.setText("  Titolo valido  ");
                message.setText("x".repeat(2001));
                check(titleFeedback.getText().contains("13/120")
                                && messageFeedback.getText().contains("elimina 1 carattere")
                                && messageFeedback.getText().contains("2001/2000") && button(root, "sendButton").isDisabled(),
                        "Counters match domain trimming and an overlong message explains its own limit");
                message.setText("Messaggio valido");
                check(button(root, "sendButton").isDisabled(), "Valid text alone cannot bypass recipient selection");
                combo(root).getItems().setAll(NotificationWorkspaceController.CourseTarget.allCourses());
                combo(root).getSelectionModel().selectFirst();
                return null;
            });
            it.uninafoodlab.presentation.FxAwait.until(
                    () -> !button(root, "sendButton").isDisabled(), 10);
            fx(() -> {
                var title = text(root, "titleField");
                var message = text(root, "messageField");
                var titleFeedback = (Label) root.lookup("#titleFeedbackLabel");
                var messageFeedback = (Label) root.lookup("#messageFeedbackLabel");
                var other = new NotificationWorkspaceController.CourseTarget(Optional.of(UUID.randomUUID()), "Altro corso");
                combo(root).getItems().add(other);
                combo(root).setValue(other);
                check(controller.hasUnsavedChanges(), "Other recipient scopes keep their protected drafts");
                check(title.getText().isEmpty() && message.getText().isEmpty()
                                && !titleFeedback.getStyleClass().contains("notification-field-error")
                                && !messageFeedback.getStyleClass().contains("notification-field-error"),
                        "Changing to a new destination starts with a neutral empty draft");
                title.requestFocus();
                message.requestFocus();
                check(titleFeedback.getText().contains("almeno 3")
                                && !messageFeedback.getStyleClass().contains("notification-field-error"),
                        "Leaving an empty field explains its requirement without marking the untouched message");
                combo(root).getSelectionModel().selectFirst();
                check(title.getText().equals("  Titolo valido  ") && message.getText().equals("Messaggio valido"),
                        "Returning to recipients restores exact draft text");
                var busyMethod = controller.getClass().getDeclaredMethod("setBusy", boolean.class);
                busyMethod.setAccessible(true);
                busyMethod.invoke(controller, true);
                check(controller.isBusy(), "The shell sees an active notification operation");
                check(button(root, "sendButton").isDisabled() && title.isDisabled() && message.isDisabled(),
                        "Busy state keeps all existing send and input guards");
                busyMethod.invoke(controller, false);
                title.setText("x".repeat(121));
                message.setText("x".repeat(2001));
                root.applyCss();
                root.layout();
                var sendBounds = button(root, "sendButton").localToScene(button(root, "sendButton").getBoundsInLocal());
                check(sendBounds.getMaxY() <= root.getScene().getHeight(),
                        "Long validation messages leave the send footer reachable at compact height");
                it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/notification-validation-compact.png");
                var scroll = (ScrollPane) root.lookup(".workspace-scroll");
                scroll.setVvalue(scroll.getVmax());
                root.layout();
                check(messageFeedback.localToScene(messageFeedback.getBoundsInLocal()).getMaxY()
                                <= root.getScene().getHeight(),
                        "Scrolling reaches the complete message validation feedback");
                it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/notification-validation-message-compact.png");
                button(root, "backButton").fire();
                check(controller.hasUnsavedChanges(), "A hidden composer draft remains protected on exit");
                return null;
            });
        } finally {
            fx(() -> { controller.dispose(); stage.close(); return null; });
        }
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
    private static TextInputControl text(Parent root, String id) { return (TextInputControl) root.lookup("#" + id); }
    private static Button button(Parent root, String id) { return (Button) root.lookup("#" + id); }
    @SuppressWarnings("unchecked")
    private static ComboBox<NotificationWorkspaceController.CourseTarget> combo(Parent root) {
        return (ComboBox<NotificationWorkspaceController.CourseTarget>) root.lookup("#courseTargetField");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
