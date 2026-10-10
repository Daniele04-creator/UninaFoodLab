package it.uninafoodlab.presentation.course;

import static it.uninafoodlab.presentation.course.CourseWorkspaceView.formatCourseDateRange;

import it.uninafoodlab.application.course.CourseSummary;
import java.time.Clock;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

final class CourseCatalogCell extends ListCell<CourseSummary> {
    private final Clock clock;

    CourseCatalogCell(Clock clock) {
        this.clock = clock;
        setPrefWidth(0);
        setMinWidth(0);
    }

            @Override
            protected void updateItem(CourseSummary course, boolean empty) {
                super.updateItem(course, empty);
                setText(null);
                if (empty || course == null) {
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                var title = new Label(course.title().value());
                title.getStyleClass().add("course-list-title");
                title.setWrapText(true);
                title.setMinWidth(0);
                title.setMinHeight(Region.USE_PREF_SIZE);
                var meta = new Label(course.categoryName() + ", " + course.sessionCount()
                        + (course.sessionCount() == 1 ? " sessione" : " sessioni"));
                meta.getStyleClass().add("course-list-meta");
                meta.setWrapText(true);
                meta.setMinWidth(0);
                meta.setMinHeight(Region.USE_PREF_SIZE);
                var dateRange = new Label(formatCourseDateRange(course) + ", " + CourseOperationalView.catalogLabel(course, clock));
                dateRange.getStyleClass().add("course-list-dates");
                dateRange.setWrapText(true);
                dateRange.setMinWidth(0);
                dateRange.setMinHeight(Region.USE_PREF_SIZE);
                var content = new VBox(4.0, title, meta, dateRange);
                content.setMinWidth(0);
                var availableWidth = Bindings.createDoubleBinding(
                        () -> Math.max(0, getWidth() - getInsets().getLeft() - getInsets().getRight()),
                        widthProperty(), insetsProperty());
                content.prefWidthProperty().bind(availableWidth);
                content.maxWidthProperty().bind(content.prefWidthProperty());
                title.maxWidthProperty().bind(content.prefWidthProperty());
                meta.maxWidthProperty().bind(content.prefWidthProperty());
                dateRange.maxWidthProperty().bind(content.prefWidthProperty());
                setGraphic(content);
                setTooltip(new javafx.scene.control.Tooltip(course.title().value() + ", "
                        + CourseOperationalView.catalogLabel(course, clock)));
            }
}
