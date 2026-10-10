package it.uninafoodlab.presentation.navigation;

import java.util.Objects;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;

final class ContentPane extends Pane {

    private final Parent content;

    ContentPane(Parent content) {
        this.content = Objects.requireNonNull(content, "content");
        content.setManaged(false);
        getChildren().add(content);
    }

    @Override
    protected void layoutChildren() {
        content.resizeRelocate(0.0, 0.0, getWidth(), getHeight());
    }
}
