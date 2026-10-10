package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.application.planning.IngredientRequirement;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;

import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.application.Platform;

final class PlanningFeedback implements ShoppingListPrinter.PrintingProgress {
    private final Runnable controlsChanged;
    private boolean busy;
    private Runnable failedLoad;
    private boolean restoreRequirementsFocus;
    private javafx.scene.Node requirementsFocusBaseline;
    private final Button retryButton;
    private final Label feedbackLabel;
    private final ProgressIndicator progressIndicator;
    private final VBox requirementPanel;
    private final ScrollPane workspaceScroll;
    private final TableView<IngredientRequirement> requirementTable;
    PlanningFeedback(
            Runnable controlsChanged,
            Button retryButton,
            Label feedbackLabel,
            ProgressIndicator progressIndicator,
            VBox requirementPanel,
            ScrollPane workspaceScroll,
            TableView<IngredientRequirement> requirementTable) {
        this.controlsChanged = controlsChanged;
        this.retryButton = retryButton;
        this.feedbackLabel = feedbackLabel;
        this.progressIndicator = progressIndicator;
        this.requirementPanel = requirementPanel;
        this.workspaceScroll = workspaceScroll;
        this.requirementTable = requirementTable;
    }

    void setBusy(boolean value) {
        busy = value;
        if (value) {
            failedLoad = null;
            retryButton.setManaged(false);
            retryButton.setVisible(false);
            feedbackLabel.setManaged(false);
            feedbackLabel.setVisible(false);
        }
        progressIndicator.setManaged(value);
        progressIndicator.setVisible(value);
        controlsChanged.run();
        if (!value && restoreRequirementsFocus && requirementPanel.isVisible()) {
            restoreRequirementsFocus = false;
            var baseline = requirementsFocusBaseline;
            Platform.runLater(() -> {
                var scene = workspaceScroll.getScene();
                if (!busy && requirementPanel.isVisible() && scene != null
                        && (scene.getFocusOwner() == null || scene.getFocusOwner() == baseline)) requirementTable.requestFocus();
            });
        }
    }

    void retry() {
        if (busy || failedLoad == null) return;
        var retry = failedLoad;
        retry.run();
    }

    void fail(String message, Runnable retry) {
        fail(message);
        failedLoad = retry;
        retryButton.setManaged(true);
        retryButton.setVisible(true);
    }

    void fail(String message) {
        failedLoad = null;
        retryButton.setManaged(false);
        retryButton.setVisible(false);
        setBusy(false);
        showFeedback(message, true);
    }

    public void printingStarted() {
        setBusy(true);
        showFeedback("Stampa in corso…", false);
    }

    public void printingCompleted() {
        setBusy(false);
        showFeedback("Stampa completata.", false);
    }

    public void printingCancelled() {
        setBusy(false);
        showFeedback("Stampa annullata.", false);
    }

    public void printingFailed(String message) { fail(message); }

    private void showFeedback(String message, boolean error) {
        feedbackLabel.setText(message);
        feedbackLabel.setManaged(true);
        feedbackLabel.setVisible(true);
        feedbackLabel.getStyleClass().removeAll("course-feedback-error", "course-feedback-success");
        feedbackLabel.getStyleClass().add(error ? "course-feedback-error" : "course-feedback-success");
    }
    boolean isBusy() { return busy; }
    void cancelFocusRestore() { restoreRequirementsFocus = false; }
    void requestRequirementsFocus() {
        restoreRequirementsFocus = true;
        requirementsFocusBaseline = workspaceScroll.getScene() == null ? null : workspaceScroll.getScene().getFocusOwner();
    }
}
