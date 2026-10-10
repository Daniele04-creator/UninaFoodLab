package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.presentation.common.LabIcons;
import java.util.UUID;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;


final class ParticipantDetailNavigation {
    private ParticipantDetailNavigation() {}

    static void configureBack(Button button, String text, String accessibleText) {
        button.setText(text);
        button.setGraphic(LabIcons.back(16));
        button.setAccessibleText(accessibleText);
    }

    static void focusBack(Node panel) {
        Platform.runLater(() -> ((VBox) panel).getChildren().getFirst().requestFocus());
    }

    static boolean focusAction(Parent pane, UUID id) {
        var match = pane.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> id != null && id.equals(button.getUserData())).findFirst();
        match.ifPresent(Button::requestFocus);
        return match.isPresent();
    }

    static void move(Node node, VBox target, int index) {
        if (node.getParent() == target) return;
        if (node.getParent() instanceof Pane parent) parent.getChildren().remove(node);
        target.getChildren().add(Math.min(index, target.getChildren().size()), node);
    }

    static void visible(Node node, boolean value) {
        node.setVisible(value);
        node.setManaged(value);
    }

    static boolean visibleWithin(Node node, Node ancestor) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible()) return false;
            if (current == ancestor) break;
        }
        return true;
    }
}
