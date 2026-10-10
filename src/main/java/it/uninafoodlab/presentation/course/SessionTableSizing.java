package it.uninafoodlab.presentation.course;

import javafx.scene.control.TableView;

final class SessionTableSizing {

    private static final int MIN_VISIBLE_ROWS = 1;
    private static final int MAX_VISIBLE_ROWS = 9;
    private static final double ROW_HEIGHT = 52.0;
    private static final double TABLE_CHROME_HEIGHT = 44.0;

    private SessionTableSizing() {}

    static double preferredHeight(int itemCount) {
        var visibleRows = Math.clamp(itemCount, MIN_VISIBLE_ROWS, MAX_VISIBLE_ROWS);
        return TABLE_CHROME_HEIGHT + visibleRows * ROW_HEIGHT;
    }

    static void apply(TableView<?> table, int itemCount) {
        var height = preferredHeight(itemCount);
        table.setFixedCellSize(ROW_HEIGHT);
        table.setMinHeight(height);
        table.setPrefHeight(height);
        table.setMaxHeight(preferredHeight(MAX_VISIBLE_ROWS));
    }
}
