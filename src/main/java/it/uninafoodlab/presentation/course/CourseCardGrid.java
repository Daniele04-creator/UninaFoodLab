package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.presentation.common.LabIcons;
import it.uninafoodlab.presentation.common.ResultPager;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javafx.collections.ListChangeListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

final class CourseCardGrid {
    private static final double CARD_GAP = 20;
    private static final double MIN_CARD_WIDTH = 340;

    private static final double SNAP_RESERVE_PER_COLUMN = 2;
    private final GridPane cards;
    private final DoubleProperty cardWidth = new SimpleDoubleProperty(MIN_CARD_WIDTH);
    private final ListView<CourseSummary> selection;
    private final Clock clock;
    private final Runnable open;
    private final Map<UUID, List<CourseSessionSummary>> sessions = new HashMap<>();
    private int page;
    private int totalCourses;
    private java.util.function.IntConsumer loadPage;
    private ResultPager bottomPager;
    private Runnable scrollToTop = () -> {};

    CourseCardGrid(GridPane cards, ListView<CourseSummary> selection, Clock clock, Runnable open) {
        this.cards = cards;
        this.selection = selection;
        this.clock = clock;
        this.open = open;
        cards.disableProperty().bind(selection.disableProperty());
        cards.setMinWidth(0);
        cards.setMaxWidth(Double.MAX_VALUE);
        selection.getItems().addListener((ListChangeListener<CourseSummary>) change -> render());
        cards.widthProperty().addListener((observable, oldWidth, width) -> resizeTiles());
        render();
    }

    void installPagination(HBox bottomHost, Runnable scrollToTop) {
        this.scrollToTop = scrollToTop;
        bottomPager = new ResultPager(this::selectPage);
        bottomPager.disableProperty().bind(selection.disableProperty());
        bottomHost.getChildren().setAll(bottomPager);
        HBox.setHgrow(bottomPager, Priority.ALWAYS);
        bottomHost.managedProperty().bind(bottomPager.managedProperty());
        bottomHost.visibleProperty().bind(bottomPager.visibleProperty());
        render();
    }

    void resetPage() {
        page = 0;
        scrollToTop.run();
    }

    private void selectPage(int selectedPage) {
        if (loadPage != null) loadPage.accept(ResultPager.clamp(selectedPage, totalCourses));
    }

    void setPageLoader(java.util.function.IntConsumer loadPage) { this.loadPage = loadPage; }

    int page() { return page; }

    void acceptPage(int page, long totalCourses) {
        int previousPage = this.page;
        this.totalCourses = Math.toIntExact(totalCourses);
        this.page = ResultPager.clamp(page, this.totalCourses);
        if (this.page != previousPage) scrollToTop.run();
    }

    void setSessions(Map<UUID, List<CourseSessionSummary>> loaded) {
        sessions.clear();
        loaded.forEach((id, items) -> sessions.put(id, List.copyOf(items)));
        render();
    }

    void updateSessions(UUID courseId, List<CourseSessionSummary> loaded) {
        sessions.put(courseId, List.copyOf(loaded));
        render();
    }

    static int columnCount(double availableWidth, int visibleCourses) {
        int fit = (int) ((Math.max(0, availableWidth) + CARD_GAP) / (MIN_CARD_WIDTH + CARD_GAP));
        return Math.max(1, Math.min(Math.min(3, Math.max(1, visibleCourses)), fit));
    }

    static double tileWidth(double availableWidth, int visibleCourses) {
        int columns = columnCount(availableWidth, visibleCourses);
        double reserve = columns > 1 ? columns * SNAP_RESERVE_PER_COLUMN : 0;
        return Math.max(0, (availableWidth - (columns - 1) * CARD_GAP - reserve) / columns);
    }

    private void resizeTiles() {
        if (cards.getWidth() > 0) {
            double contentWidth = cards.getWidth() - cards.getInsets().getLeft() - cards.getInsets().getRight();
            int columns = columnCount(contentWidth, selection.getItems().size());
            cardWidth.set(tileWidth(contentWidth, selection.getItems().size()));
            for (int index = 0; index < cards.getChildren().size(); index++) {
                var child = cards.getChildren().get(index);
                GridPane.setConstraints(child, index % columns, index / columns);
                GridPane.setHgrow(child, Priority.ALWAYS);
            }
        }
    }

    private void render() {
        if (bottomPager != null) {
            bottomPager.update(page, totalCourses, "corsi");
        }
        cards.getChildren().clear();
        if (selection.getItems().isEmpty()) {
            var empty = new Label("Nessun corso in questa vista.");
            empty.setWrapText(true);
            empty.getStyleClass().add("page-description");
            cards.getChildren().add(empty);
            resizeTiles();
            return;
        }
        for (var course : selection.getItems()) {
            var category = label(course.categoryName(), "course-card-category");
            var title = label(course.title().value(), "course-card-title");
            title.setWrapText(false);
            title.setMaxWidth(Double.MAX_VALUE);
            title.setTooltip(new javafx.scene.control.Tooltip(course.title().value()));
            title.setMinHeight(Region.USE_PREF_SIZE);
            var dates = label("Dal " + CoursePresentation.COURSE_DATE_FORMAT.format(course.startDate()),
                    "course-card-facts");
            var metrics = label(course.sessionCount() + (course.sessionCount() == 1 ? " sessione" : " sessioni"),
                    "course-card-facts");
            var frequency = label(course.recurrenceDays() == 1 ? "Ogni giorno"
                    : "Ogni " + course.recurrenceDays() + " giorni", "course-card-facts");
            var facts = new FlowPane(16, 6, metrics, frequency, dates);
            var appointment = appointment(course);
            var button = new Button("Apri corso");
            button.getStyleClass().addAll("button-quiet", "course-card-open");
            button.setAccessibleText("Apri corso " + course.title().value());
            button.setOnAction(event -> {
                selection.getSelectionModel().select(course);
                open.run();
            });
            var flex = new Region();
            VBox.setVgrow(flex, Priority.ALWAYS);
            var card = new VBox(8, category, title, courseState(course), facts, appointment, flex, button);
            card.setUserData(course.id());
            card.getStyleClass().add("course-catalog-card");
            card.setMinWidth(0);
            card.prefWidthProperty().bind(cardWidth);
            card.setMinHeight(210);
            card.setMaxWidth(Double.MAX_VALUE);
            card.setMaxHeight(Double.MAX_VALUE);
            cards.getChildren().add(card);
        }
        resizeTiles();
    }

    private javafx.scene.Node appointment(CourseSummary course) {
        var next = course.status() == CourseStatus.CANCELLED ? java.util.Optional.<CourseSessionSummary>empty()
                : CourseCatalogSnapshot.nextSession(sessions.getOrDefault(course.id(), List.of()), clock);
        if (next.isEmpty()) {
            var empty = new Region();
            empty.setVisible(false);
            empty.setManaged(false);
            return empty;
        }
        var session = next.orElseThrow();
        var caption = label("Prossima sessione:", "course-card-appointment-caption");
        var date = label(CoursePresentation.PROGRAM_DATE_FORMAT.format(session.scheduledAt()),
                "course-card-appointment-date");
        date.setGraphic(LabIcons.icon("calendar", "dark", 16));
        date.setMaxWidth(Region.USE_PREF_SIZE);
        var mode = CourseSessionModeView.label(session.mode());
        mode.getStyleClass().add("course-card-mode");
        mode.setMaxWidth(Region.USE_PREF_SIZE);
        var facts = new FlowPane(12, 6, date, mode);
        facts.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        var band = new VBox(4, caption, facts);
        band.getStyleClass().add("course-card-appointment");
        return band;
    }

    private Label courseState(CourseSummary course) {
        var state = label(CourseOperationalView.catalogLabel(course, clock), "course-card-badge");
        state.setMaxWidth(Region.USE_PREF_SIZE);
        state.setMinWidth(Region.USE_PREF_SIZE);
        if ("In corso".equals(state.getText())) state.getStyleClass().add("course-card-active");
        if ("In programma".equals(state.getText())) state.getStyleClass().add("course-card-planned");
        if ("Concluso".equals(state.getText())) state.getStyleClass().add("course-card-concluded");
        if ("Annullato".equals(state.getText())) state.getStyleClass().add("course-card-cancelled");
        return state;
    }

    private static Label label(String text, String style) {
        var label = new Label(text);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.getStyleClass().add(style);
        return label;
    }
}
