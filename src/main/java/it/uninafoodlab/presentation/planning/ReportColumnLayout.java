package it.uninafoodlab.presentation.planning;

import javafx.css.PseudoClass;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;

final class ReportColumnLayout {
    private static final PseudoClass FIRST_COLUMN = PseudoClass.getPseudoClass("first-column");

    private ReportColumnLayout() {}

    static void resize(FlowPane pane, int columns) {
        var insets = pane.getInsets();
        var width = Math.max(0, pane.getWidth() - insets.getLeft() - insets.getRight()
                - pane.getHgap() * (columns - 1));
        for (int index = 0; index < pane.getChildren().size(); index++) {
            if (pane.getChildren().get(index) instanceof Region region) {
                var column = index % columns;

                var start = pane.snapSpaceX(width * column / columns);
                var end = pane.snapSpaceX(width * (column + 1) / columns);
                region.setMinWidth(0);

                region.setPrefWidth(Math.max(0, end - start - 0.0000001));
                region.pseudoClassStateChanged(FIRST_COLUMN, column == 0);
            }
        }
    }
}
