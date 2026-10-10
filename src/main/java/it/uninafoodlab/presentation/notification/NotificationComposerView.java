package it.uninafoodlab.presentation.notification;

import it.uninafoodlab.application.course.CourseSummary;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

final class NotificationComposerView {
    private final VBox composeCard;
    private final HBox historyPanel;
    private final Button backButton;
    private final Button composeButton;
    private final Label workspaceTitleLabel;
    private final ComboBox<NotificationWorkspaceController.CourseTarget> courseTargetField;
    private final TextField titleField;
    private final Runnable refreshRecipients;
    private final TextArea messageField;
    private final Label titleFeedbackLabel;
    private final Label messageFeedbackLabel;
    private final Button sendButton;
    private final Label recipientFooterLabel;
    private final BiConsumer<String, Boolean> feedback;
    private final NotificationDrafts composerDrafts = new NotificationDrafts();
    private boolean restoringDraft;
    private boolean titleEdited;
    private boolean messageEdited;

    NotificationComposerView(VBox composeCard,
            HBox historyPanel,
            Button backButton,
            Button composeButton,
            Label workspaceTitleLabel,
            ComboBox<NotificationWorkspaceController.CourseTarget> courseTargetField,
            TextField titleField,
            TextArea messageField, Label titleFeedbackLabel, Label messageFeedbackLabel,
            Button sendButton, Label recipientFooterLabel,
            BiConsumer<String, Boolean> feedback,
            Runnable refreshRecipients, Runnable refreshButtons) {
        this.composeCard = composeCard;
        this.historyPanel = historyPanel;
        this.backButton = backButton;
        this.composeButton = composeButton;
        this.workspaceTitleLabel = workspaceTitleLabel;
        this.courseTargetField = courseTargetField;
        this.titleField = titleField;
        this.refreshRecipients = refreshRecipients;
        this.messageField = messageField;
        this.titleFeedbackLabel = titleFeedbackLabel;
        this.messageFeedbackLabel = messageFeedbackLabel;
        this.sendButton = sendButton;
        this.recipientFooterLabel = recipientFooterLabel;
        this.feedback = feedback;
        titleField.textProperty().addListener((observable, before, after) -> {
            if (!restoringDraft) titleEdited = true;
            refreshButtons.run();
        });
        messageField.textProperty().addListener((observable, before, after) -> {
            if (!restoringDraft) messageEdited = true;
            refreshButtons.run();
        });
        titleField.focusedProperty().addListener((observable, before, after) -> {
            if (before && !after && !restoringDraft && !titleField.isDisabled() && composeCard.isVisible()) {
                titleEdited = true;
                refreshButtons.run();
            }
        });
        messageField.focusedProperty().addListener((observable, before, after) -> {
            if (before && !after && !restoringDraft && !messageField.isDisabled() && composeCard.isVisible()) {
                messageEdited = true;
                refreshButtons.run();
            }
        });
    }

    void setOpen(boolean open) {
        composeCard.setManaged(open);
        composeCard.setVisible(open);
        historyPanel.setManaged(!open);
        historyPanel.setVisible(!open);
        backButton.setManaged(open);
        backButton.setVisible(open);
        composeButton.setManaged(!open);
        composeButton.setVisible(!open);
        workspaceTitleLabel.setText(open ? "Nuova comunicazione" : "Comunicazioni");
        workspaceTitleLabel.setManaged(true);
        workspaceTitleLabel.setVisible(true);
        composeButton.setText(open ? "Torna alle comunicazioni" : "Nuova comunicazione");
        composeButton.setAccessibleText(open ? "Torna alle comunicazioni conservando la bozza" : "Scrivi una nuova comunicazione");
        composeButton.getStyleClass().removeAll("button-primary", "hero-action", "button-secondary");
        composeButton.getStyleClass().add(open ? "button-secondary" : "button-primary");
        if (open) {
            backButton.setAccessibleText("Torna alle comunicazioni conservando la bozza");
            refreshRecipients.run();
            if (courseTargetField.getValue() == null) {
                courseTargetField.requestFocus();
            } else {
                titleField.requestFocus();
            }
        }
    }

    void updateActions(boolean busy, boolean hasRecipients, String countLabel) {
        updateFieldFeedback(titleField, titleFeedbackLabel, "Oggetto", 120, titleEdited);
        updateFieldFeedback(messageField, messageFeedbackLabel, "Messaggio", 2000, messageEdited);
        recipientFooterLabel.setText(countLabel);
        sendButton.setDisable(busy || courseTargetField.getValue() == null || !hasRecipients || !hasValidDraft());
        courseTargetField.setDisable(busy);
        titleField.setDisable(busy);
        messageField.setDisable(busy);
        composeButton.setDisable(busy);
    }

    private static void updateFieldFeedback(javafx.scene.control.TextInputControl field, Label label,
            String name, int maximum, boolean edited) {
        int count = field.getText().strip().length();
        boolean invalid = edited && (count < 3 || count > maximum);
        String help = "Da 3 a " + maximum + " caratteri.";
        if (invalid) {
            help = count < 3 ? name + ": inserisci almeno 3 caratteri."
                    : name + ": elimina " + (count - maximum)
                            + (count - maximum == 1 ? " carattere." : " caratteri.");
        }
        label.setText(help + " " + count + "/" + maximum + " caratteri.");
        field.setAccessibleHelp(label.getText());
        label.getStyleClass().remove("notification-field-error");
        if (invalid) label.getStyleClass().add("notification-field-error");
    }

    private boolean hasValidDraft() {
        try {
            it.uninafoodlab.application.notification.NotificationDraft.of(titleField.getText(), messageField.getText());
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    boolean hasUnsavedChanges() {
        return composerDrafts.hasUnsavedChanges(titleField.getText(), messageField.getText());
    }

    void restoreTargetDraft(NotificationWorkspaceController.CourseTarget target) {
        var previous = composerDrafts.currentTarget();
        var title = titleField.getText();
        var message = messageField.getText();
        var draft = composerDrafts.changeTarget(target, title, message);
        restoringDraft = true;
        try {
            titleEdited = !draft.title().isBlank();
            messageEdited = !draft.message().isBlank();
            titleField.textProperty().setValue(draft.title());
            messageField.textProperty().setValue(draft.message());
        } finally {
            restoringDraft = false;
        }
        updateFieldFeedback(titleField, titleFeedbackLabel, "Oggetto", 120, titleEdited);
        updateFieldFeedback(messageField, messageFeedbackLabel, "Messaggio", 2000, messageEdited);
        if (previous != null && target != null
                && !previous.courseId().equals(target.courseId())
                && (!title.isBlank() || !message.isBlank())) {
            feedback.accept("Bozza di «" + previous.label()
                    + "» conservata. La ritrovi selezionando di nuovo quei destinatari.", false);
        }
    }

    void selectCourse(UUID requestedCourse) {
        if (requestedCourse == null) {
            return;
        }
        var target = courseTargetField.getItems().stream()
                .filter(item -> item.courseId().filter(requestedCourse::equals).isPresent()).findFirst();
        courseTargetField.setValue(target.orElse(null));
    }

    void replaceCourses(List<CourseSummary> courses,
            UUID requestedCourse) {
        var previousTarget = courseTargetField.getValue();
        courseTargetField.getItems().setAll(NotificationWorkspaceController.CourseTarget.allCourses());
        courses.stream().map(NotificationWorkspaceController.CourseTarget::course)
                .forEach(courseTargetField.getItems()::add);
        courseTargetField.getSelectionModel().clearSelection();
        if (requestedCourse != null) {
            selectCourse(requestedCourse);
        } else if (previousTarget != null) {
            courseTargetField.setValue(courseTargetField.getItems().stream()
                    .filter(item -> item.courseId().equals(previousTarget.courseId()))
                    .findFirst().orElse(null));
        }
    }
}
