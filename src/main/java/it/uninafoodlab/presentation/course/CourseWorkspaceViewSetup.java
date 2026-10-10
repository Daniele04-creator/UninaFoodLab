package it.uninafoodlab.presentation.course;

import it.uninafoodlab.presentation.common.LabIcons;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

final class CourseWorkspaceViewSetup {
    private static final PseudoClass COMPACT = PseudoClass.getPseudoClass("compact");

    private CourseWorkspaceViewSetup() {}

    record Controls(Button newCourse, Button addSession, Button participants,
            Button communication, Button manageSession) {}

    static void configureIcons(Controls controls) {
        controls.newCourse().setGraphic(LabIcons.icon("plus-square", "light", 16));
        controls.addSession().setGraphic(LabIcons.icon("plus-square", "dark", 16));
        controls.participants().setGraphic(LabIcons.icon("users", "dark", 16));
        controls.communication().setGraphic(LabIcons.icon("message-square", "dark", 16));
        controls.manageSession().setGraphic(LabIcons.icon("settings", "light", 16));
    }

    static void configureReturns(Button... buttons) {
        for (var button : buttons) {
            button.setGraphic(LabIcons.back(16));
            button.setGraphicTextGap(6);
            button.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        }
    }

    static void configureCreationLayout(GridPane overview, TilePane sessions, Button newCategory) {
        newCategory.setGraphic(LabIcons.icon("plus-square", "dark", 16));
        newCategory.setMinWidth(190);
        newCategory.setPrefWidth(190);
        newCategory.setWrapText(true);
        newCategory.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        overview.widthProperty().addListener((observable, previous, width) -> resizeCreationOverview(overview));
        sessions.widthProperty().addListener((observable, previous, width) -> resizeSessionEditors(sessions));
        resizeCreationOverview(overview);
        resizeSessionEditors(sessions);
    }

    private static void resizeCreationOverview(GridPane overview) {
        boolean wide = overview.getWidth() > 998;
        var first = new ColumnConstraints();
        first.setPercentWidth(wide ? 100 * 1.1 / 2.1 : 100);
        first.setHgrow(Priority.ALWAYS);
        overview.getColumnConstraints().setAll(first);
        if (wide) {
            var second = new ColumnConstraints();
            second.setPercentWidth(100 / 2.1);
            second.setHgrow(Priority.ALWAYS);
            overview.getColumnConstraints().add(second);
        }
        GridPane.setColumnIndex(overview.getChildren().get(1), wide ? 1 : 0);
        GridPane.setRowIndex(overview.getChildren().get(1), wide ? 0 : 1);
    }

    private static void resizeSessionEditors(TilePane sessions) {
        int columns = 1;
        sessions.setPrefColumns(columns);
        sessions.setPrefTileWidth(Math.max(0, (sessions.getWidth() - (columns - 1) * sessions.getHgap()) / columns - 2));
    }

    static void bindExplorerRailWidth(HBox layout, VBox rail) {
        layout.widthProperty().addListener((observable, previous, width) ->
                resizeExplorerRail(width.doubleValue(), rail));
        resizeExplorerRail(layout.getWidth(), rail);
    }

    static double explorerRailWidth(double contentWidth) {
        if (contentWidth >= 1280) {
            return Math.min(435, Math.max(420, contentWidth * 0.29));
        }
        return 320;
    }

    private static void resizeExplorerRail(double contentWidth, VBox rail) {
        double width = explorerRailWidth(contentWidth);
        rail.setMinWidth(width);
        rail.setPrefWidth(width);
        rail.setMaxWidth(width);
        rail.pseudoClassStateChanged(COMPACT, width < 380);
    }
}
