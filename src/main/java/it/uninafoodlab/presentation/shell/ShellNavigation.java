package it.uninafoodlab.presentation.shell;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

final class ShellNavigation {

    record Item(Button button, String icon) {}

    private final StackPane contentHost;
    private final List<Item> items;

    ShellNavigation(StackPane contentHost, List<Item> items) {
        this.contentHost = contentHost;
        this.items = List.copyOf(items);
    }

    void show(Button selected, Node content) {
        for (var item : items) {
            var button = item.button();
            button.getStyleClass().remove("navigation-button-selected");
            button.setGraphic(it.uninafoodlab.presentation.common.LabIcons.icon(item.icon(), "dark", 18));
        }
        selected.getStyleClass().add("navigation-button-selected");
        contentHost.getChildren().setAll(content);
    }
}
