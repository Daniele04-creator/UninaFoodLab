package it.uninafoodlab.presentation.course;

import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.stage.Screen;
import javafx.stage.Window;

final class CourseOperationDialogs {
    private final Node owner;
    private final BooleanSupplier busy;
    private Dialog<ButtonType> activeScheduleDialog;
    private Label scheduleFeedbackLabel;

    CourseOperationDialogs(Node owner, BooleanSupplier busy) {
        this.owner = owner;
        this.busy = busy;
    }

    void configure(Dialog<ButtonType> dialog) {
        it.uninafoodlab.presentation.common.DialogTheme.apply(dialog,
                owner.getScene() == null ? null : owner.getScene().getWindow());
        dialog.getDialogPane().getStyleClass().addAll("app-dialog", "course-schedule-dialog", "product-course-dialog");
        dialog.getDialogPane().setPrefWidth(760);
        dialog.setResizable(true);
        dialog.setOnCloseRequest(event -> {
            if (busy.getAsBoolean()) event.consume();
        });
        dialog.setOnShown(event -> {
            dialog.getDialogPane().applyCss();
            if (dialog.getDialogPane().lookup(".button-bar") instanceof ButtonBar bar) {
                bar.setButtonOrder("+CO");
            }
            fit(dialog);
            Platform.runLater(() -> {
                if (dialog.isShowing()) fit(dialog);
            });
        });
    }

    void buttons(Dialog<ButtonType> dialog, ButtonType save, String style, Runnable operation) {
        boolean destructive = "button-danger".equals(style);
        var cancel = new ButtonType(destructive ? "Mantieni sessione" : "Annulla", ButtonBar.ButtonData.CANCEL_CLOSE);
        var pane = dialog.getDialogPane();
        pane.getButtonTypes().setAll(cancel, save);
        var cancelButton = (Button) pane.lookupButton(cancel);
        cancelButton.getStyleClass().addAll("course-button", "button-secondary");
        var submit = (Button) pane.lookupButton(save);
        submit.getStyleClass().addAll("course-button", style);
        submit.setDefaultButton(!destructive);
        cancelButton.setDefaultButton(destructive);
        submit.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (!busy.getAsBoolean()) operation.run();
        });
    }

    void show(Dialog<ButtonType> dialog, Label feedback) {
        activeScheduleDialog = dialog;
        scheduleFeedbackLabel = feedback;
        try {
            dialog.showAndWait();
        } finally {
            activeScheduleDialog = null;
            scheduleFeedbackLabel = null;
        }
    }

    void close() {
        if (activeScheduleDialog != null) activeScheduleDialog.close();
    }

    void setBusy(boolean value) {
        if (activeScheduleDialog != null) activeScheduleDialog.getDialogPane().setDisable(value);
    }

    boolean showError(String message) {
        if (activeScheduleDialog == null || scheduleFeedbackLabel == null) {
            return false;
        }
        scheduleFeedbackLabel.setText(message);
        return true;
    }

    private void fit(Dialog<?> dialog) {
        var window = dialog.getOwner();
        var bounds = screenBounds(window);
        double availableWidth = Math.min(bounds.getWidth(), window == null ? bounds.getWidth() : window.getWidth()) - 32;
        double availableHeight = Math.min(bounds.getHeight(), window == null ? bounds.getHeight() : window.getHeight()) - 32;

        var pane = dialog.getDialogPane();
        double width = Math.clamp(pane.prefWidth(-1), Math.min(760, availableWidth), availableWidth);
        double height = Math.clamp(pane.prefHeight(width) + 40, Math.min(450, availableHeight), availableHeight);

        pane.setMinSize(0, 0);
        pane.setPrefSize(Math.max(0, width - 16), Math.max(0, height - 40));
        dialog.setWidth(width);
        dialog.setHeight(height);
        double centerX = window == null ? bounds.getMinX() + bounds.getWidth() / 2 : window.getX() + window.getWidth() / 2;
        double centerY = window == null ? bounds.getMinY() + bounds.getHeight() / 2 : window.getY() + window.getHeight() / 2;
        dialog.setX(Math.clamp(centerX - width / 2, bounds.getMinX(), bounds.getMaxX() - width));
        dialog.setY(Math.clamp(centerY - height / 2, bounds.getMinY(), bounds.getMaxY() - height));
    }

    private Rectangle2D screenBounds(Window window) {
        if (window == null) {
            return Screen.getPrimary().getVisualBounds();
        }
        return Screen.getScreensForRectangle(window.getX(), window.getY(), window.getWidth(), window.getHeight())
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
    }
}
