package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.application.planning.SessionLifecycleRepository.ReportRow;
import it.uninafoodlab.presentation.common.LabIcons;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

final class ReportSessionRow {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(
            "d MMMM uuuu 'alle' HH:mm", Locale.ITALIAN);

    private ReportSessionRow() {}

    static VBox create(ReportRow row) {
        var title = new Label(row.courseTitle());
        title.setWrapText(true);
        title.setMinWidth(0);
        title.setMinHeight(Region.USE_PREF_SIZE);
        title.setMaxWidth(Double.MAX_VALUE);
        title.getStyleClass().add("report-session-title");
        HBox.setHgrow(title, Priority.ALWAYS);
        var status = new Label(PlanningLabels.stateLabel(row.state()));
        status.setMinWidth(Region.USE_PREF_SIZE);
        status.getStyleClass().addAll("report-session-state",
                "CANCELLED".equals(row.state()) ? "report-session-cancelled" : "report-session-neutral");
        var heading = new HBox(18, title, status);
        heading.setAlignment(Pos.TOP_LEFT);
        var date = new Label(DATE.format(row.scheduledAt()));
        date.getStyleClass().add("ops-muted");
        var metadata = new FlowPane(18, 8, date, mode(row.mode()));
        if ("COMPLETED".equals(row.state()) && !"ONLINE".equals(row.mode()) && row.recipeCount() != null) {
            var recipes = new Label(row.recipeCount() + (row.recipeCount() == 1 ? " ricetta realizzata" : " ricette realizzate"));
            recipes.getStyleClass().add("ops-muted");
            metadata.getChildren().add(recipes);
        }
        var card = new VBox(12, heading, metadata);
        card.getStyleClass().add("report-session-row");
        return card;
    }

    private static Label mode(String value) {
        boolean online = "ONLINE".equals(value);
        var label = new Label(online ? "Online" : "In presenza");
        label.setGraphic(LabIcons.icon(online ? "video" : "cooking-pot", "dark", 16));
        label.setGraphicTextGap(6);
        label.getStyleClass().add("report-session-mode");
        return label;
    }
}
