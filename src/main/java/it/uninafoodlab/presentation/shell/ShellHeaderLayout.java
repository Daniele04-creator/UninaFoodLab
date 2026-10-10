package it.uninafoodlab.presentation.shell;

import javafx.css.PseudoClass;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

final class ShellHeaderLayout {
    private static final PseudoClass STACKED = PseudoClass.getPseudoClass("stacked");

    private ShellHeaderLayout() {}

    static void arrange(double width, GridPane header, HBox navigation, HBox profile) {
        boolean wide = width >= 1100;
        int columns = wide ? 3 : 2;
        if (header.getColumnConstraints().size() == columns) return;
        var brandColumn = new ColumnConstraints();
        var flexibleColumn = new ColumnConstraints();
        flexibleColumn.setHgrow(Priority.ALWAYS);
        header.getColumnConstraints().setAll(brandColumn, flexibleColumn);
        if (wide) header.getColumnConstraints().add(new ColumnConstraints());
        GridPane.setColumnIndex(navigation, wide ? 1 : 0);
        GridPane.setRowIndex(navigation, wide ? 0 : 1);
        GridPane.setColumnSpan(navigation, wide ? 1 : 2);
        GridPane.setColumnIndex(profile, wide ? 2 : 1);
        header.pseudoClassStateChanged(STACKED, !wide);
    }
}
