package it.uninafoodlab.presentation.common;

import java.util.List;
import java.util.function.IntConsumer;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.TraversalDirection;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public final class ResultPager extends HBox {
    public static final int PAGE_SIZE = 12;
    private final Label range = new Label();
    private final Label position = new Label();
    private final Button previous = new Button("Pagina precedente");
    private final Button next = new Button("Pagina successiva");
    private int page;
    private PendingFocus pendingFocus;
    private final EventHandler<InputEvent> cancelFocusRestore = event -> {
        if (event.getTarget() instanceof Node target && !containsNode(target)) clearPendingFocus();
    };

    public ResultPager(IntConsumer onPageChange) {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);
        getStyleClass().add("result-pager");
        range.getStyleClass().add("result-page-range");
        position.getStyleClass().add("result-page-position");
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        previous.setAccessibleText("Pagina precedente");
        next.setAccessibleText("Pagina successiva");
        previous.setGraphic(LabIcons.back(16));
        next.setGraphic(LabIcons.icon("chevron-right", "dark", 16));
        widthProperty().addListener((observable, oldWidth, newWidth) -> {
            boolean labels = newWidth.doubleValue() >= 580;
            previous.setText(labels ? "Pagina precedente" : "");
            next.setText(labels ? "Pagina successiva" : "");
            range.setVisible(newWidth.doubleValue() >= 760);
            range.setManaged(range.isVisible());
        });
        previous.setOnAction(event -> {
            rememberFocus(previous, page - 1);
            onPageChange.accept(page - 1);
        });
        next.setOnAction(event -> {
            rememberFocus(next, page + 1);
            onPageChange.accept(page + 1);
        });
        disabledProperty().addListener((observable, before, disabled) -> {
            if (!disabled) scheduleFocusRestore();
        });
        sceneProperty().addListener((observable, before, scene) -> clearPendingFocus());
        getChildren().addAll(range, spacer, previous, position, next);
        update(0, 0, "risultati");
    }

    private void rememberFocus(Button activated, int requestedPage) {
        clearPendingFocus();
        if (!activated.isFocused() || getScene() == null) return;
        pendingFocus = new PendingFocus(activated, activated.isFocusVisible(), requestedPage, getScene());
        getScene().addEventFilter(KeyEvent.KEY_PRESSED, cancelFocusRestore);
        getScene().addEventFilter(MouseEvent.MOUSE_PRESSED, cancelFocusRestore);
    }

    private boolean containsNode(Node node) {
        for (var parent = node; parent != null; parent = parent.getParent()) {
            if (parent == this) return true;
        }
        return false;
    }

    private void scheduleFocusRestore() {
        var pending = pendingFocus;
        if (pending != null) Platform.runLater(() -> restoreFocus(pending));
    }

    private void restoreFocus(PendingFocus pending) {
        if (pendingFocus != pending || isDisabled()) return;
        clearPendingFocus();
        if (!isVisible() || getScene() != pending.scene()) return;
        var activated = pending.button();
        var alternative = activated == previous ? next : previous;
        var target = activated.isDisabled() ? alternative : activated;
        if (target.isDisabled()) return;
        focusButton(target, pending.keyboard());
    }

    private void focusButton(Button target, boolean keyboard) {
        var origin = target == previous ? next : previous;
        var direction = target == previous ? TraversalDirection.PREVIOUS : TraversalDirection.NEXT;
        if (!keyboard || !origin.requestFocusTraversal(direction)) target.requestFocus();
    }

    private void clearPendingFocus() {
        if (pendingFocus == null) return;
        pendingFocus.scene().removeEventFilter(KeyEvent.KEY_PRESSED, cancelFocusRestore);
        pendingFocus.scene().removeEventFilter(MouseEvent.MOUSE_PRESSED, cancelFocusRestore);
        pendingFocus = null;
    }

    public void update(int requestedPage, int total, String noun) {
        page = clamp(requestedPage, total);
        int first = total == 0 ? 0 : page * PAGE_SIZE + 1;
        int last = Math.min(total, (page + 1) * PAGE_SIZE);
        range.setText("Da " + first + " a " + last + " di " + total + " " + noun);
        position.setText("Pagina " + (page + 1) + " di " + count(total));
        previous.setDisable(page == 0);
        next.setDisable(page + 1 >= count(total));
        setVisible(total > PAGE_SIZE);
        setManaged(isVisible());
        if (pendingFocus != null && page == clamp(pendingFocus.requestedPage(), total)) scheduleFocusRestore();
    }

    public static int count(int total) {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public static int clamp(int requestedPage, int total) {
        return Math.max(0, Math.min(requestedPage, count(total) - 1));
    }

    public static <T> List<T> slice(List<T> results, int requestedPage) {
        int from = clamp(requestedPage, results.size()) * PAGE_SIZE;
        return List.copyOf(results.subList(from, Math.min(results.size(), from + PAGE_SIZE)));
    }

    private record PendingFocus(Button button, boolean keyboard, int requestedPage, Scene scene) {}
}
