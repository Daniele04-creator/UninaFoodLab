package it.uninafoodlab.presentation.course;

import static it.uninafoodlab.presentation.course.CoursePresentation.modeLabel;
import static it.uninafoodlab.presentation.course.CoursePresentation.sessionStatusLabel;
import static it.uninafoodlab.presentation.course.CourseSessionModeView.locationLabel;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.presentation.common.LabIcons;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

final class CourseSessionAgenda {
    private static final double MIN_SESSION_ROW_HEIGHT = 95;
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ITALIAN);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN);

    private final VBox agenda;
    private final VBox commands;
    private final TableView<CourseSessionSummary> table;
    private final Clock clock;
    private final VBox recipeLinks;
    private final MenuNamesLoader menuNames;
    private long menuVersion;
    private Runnable openSession;

    void setOpenSession(Runnable action) { openSession = action; }

    CourseSessionAgenda(VBox agenda, VBox commands, TableView<CourseSessionSummary> table, Clock clock) {
        this(agenda, commands, table, clock, null, ignored -> List.of());
    }

    CourseSessionAgenda(VBox agenda, VBox commands, TableView<CourseSessionSummary> table, Clock clock,
            VBox recipeLinks, Function<CourseSessionSummary, List<String>> menuNames) {
        this(agenda, commands, table, clock, recipeLinks,
                (session, onSuccess, ignored) -> onSuccess.accept(menuNames.apply(session)));
    }

    CourseSessionAgenda(VBox agenda, VBox commands, TableView<CourseSessionSummary> table, Clock clock,
            VBox recipeLinks, MenuNamesLoader menuNames) {
        this.agenda = Objects.requireNonNull(agenda, "agenda");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.table = Objects.requireNonNull(table, "table");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.recipeLinks = recipeLinks;
        this.menuNames = Objects.requireNonNull(menuNames, "menuNames");
    }

    void render(boolean busy) {
        var currentMenuVersion = ++menuVersion;
        var focusedSessionId = focusedSessionId();
        ToggleButton focusTarget = null;

        if (commands.getParent() instanceof Pane parent) parent.getChildren().remove(commands);
        agenda.getChildren().clear();
        if (table.getItems().isEmpty()) {
            showEmptyState();
            return;
        }

        var track = new VBox(0);
        track.getStyleClass().add("course-session-track");
        var selected = table.getSelectionModel().getSelectedItem();
        clearUnselectedMenu(selected);
        for (var session : table.getItems()) {
            boolean expanded = selected != null && selected.id().equals(session.id());
            var row = sessionRow(session, expanded, busy);
            if (session.id().equals(focusedSessionId)) focusTarget = row;
            track.getChildren().add(row);
            if (expanded && openSession == null) {
                renderMenuNames(session, currentMenuVersion);
                track.getChildren().add(commands);
            }
        }
        agenda.getChildren().add(track);
        if (focusTarget != null) focusTarget.requestFocus();
    }

    private void clearUnselectedMenu(CourseSessionSummary selected) {
        if (selected != null || recipeLinks == null) return;
        recipeLinks.getChildren().clear();
        recipeLinks.setManaged(false);
        recipeLinks.setVisible(false);
    }

    private Object focusedSessionId() {
        var focusOwner = agenda.getScene() == null ? null : agenda.getScene().getFocusOwner();
        return focusOwner instanceof ToggleButton toggle
                && toggle.getStyleClass().contains("course-agenda-session") ? toggle.getUserData() : null;
    }

    private void showEmptyState() {
        var label = new Label("Nessuna sessione disponibile");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("page-description");
        agenda.getChildren().add(label);
    }

    private ToggleButton sessionRow(CourseSessionSummary session, boolean expanded, boolean busy) {
        var date = dateBlock(session);
        var context = sessionContext(session);
        var statusText = sessionStatusLabel(session);
        var state = sessionState(session, statusText);

        var arrow = new Label(openSession == null ? "" : "Apri");
        arrow.setGraphic(LabIcons.icon(expanded ? "chevron-down" : "chevron-right", "dark", 16));
        arrow.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        arrow.getStyleClass().add("course-session-chevron");
        var grid = new GridPane();
        grid.getStyleClass().add("course-session-summary");
        grid.setHgap(16);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.add(date, 0, 0);
        grid.add(context, 1, 0);
        grid.add(state, 2, 0);
        grid.add(arrow, 3, 0);
        grid.getColumnConstraints().addAll(dateColumn(), flexibleColumn(), stateColumn(), arrowColumn());

        var row = new ToggleButton();
        row.setUserData(session.id());
        row.setGraphic(grid);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setMinWidth(0);
        row.setSelected(expanded);
        row.setDisable(busy);
        row.getStyleClass().add("course-agenda-session");
        row.setAccessibleText(session.scheduledAt().format(
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + ", "
                + modeLabel(session.mode()) + ", " + locationLabel(session) + ", "
                + statusText + (expanded ? ", dettagli aperti" : ", apri dettagli"));
        grid.prefWidthProperty().bind(Bindings.max(0, row.widthProperty().subtract(24)));
        grid.maxWidthProperty().bind(grid.prefWidthProperty());
        row.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> preferredRowHeight(row, grid), row.widthProperty(), row.insetsProperty(), grid.widthProperty()));
        row.setOnAction(event -> {
            if (!expanded) table.getSelectionModel().select(session);
            else row.setSelected(true);
            if (openSession != null) openSession.run();
        });
        return row;
    }

    private static VBox sessionContext(CourseSessionSummary session) {
        var time = new Label(timeRange(session));
        time.getStyleClass().add("course-session-time");

        var mode = CourseSessionModeView.label(session.mode());
        var location = new Label(locationLabel(session));
        location.getStyleClass().add("course-session-location");
        location.setWrapText(true);
        location.setMinWidth(0);
        if (session.mode() == SessionMode.PRACTICAL && session.venue().isPresent()) {
            location.setGraphic(LabIcons.icon("map-pin", "dark", 14));
            location.setGraphicTextGap(6);
        }
        var context = new VBox(3, time, mode, location);
        context.setMinWidth(0);
        context.getStyleClass().add("course-session-context");
        return context;
    }

    private VBox sessionState(CourseSessionSummary session, String statusText) {
        var status = new Label(statusText);
        status.getStyleClass().addAll("course-session-state", CourseSessionTable.statusStyle(statusText));
        status.setWrapText(true);
        status.setMinWidth(0);
        var state = new VBox(6, status);
        if (session.mode() == SessionMode.PRACTICAL) state.getChildren().add(CourseSessionTable.recipeCountBadge(session, clock));
        state.setMinWidth(0);
        state.setMaxWidth(Double.MAX_VALUE);
        state.getStyleClass().add("course-session-state-block");
        return state;
    }

    private static double preferredRowHeight(ToggleButton row, GridPane grid) {
        double width = grid.getWidth();
        if (width <= 0) return MIN_SESSION_ROW_HEIGHT;
        var insets = row.getInsets();
        return Math.max(MIN_SESSION_ROW_HEIGHT,
                grid.prefHeight(width) + insets.getTop() + insets.getBottom());
    }

    private static ColumnConstraints dateColumn() {
        var column = new ColumnConstraints(70);
        column.setMinWidth(70);
        column.setPrefWidth(70);
        column.setMaxWidth(70);
        return column;
    }

    private static ColumnConstraints flexibleColumn() {
        var column = new ColumnConstraints();
        column.setMinWidth(0);
        column.setHgrow(Priority.ALWAYS);
        return column;
    }

    private static ColumnConstraints stateColumn() {
        var column = new ColumnConstraints(112);
        column.setMinWidth(90);
        column.setPrefWidth(112);
        column.setHgrow(Priority.NEVER);
        return column;
    }

    private static ColumnConstraints arrowColumn() {
        var column = new ColumnConstraints(90);
        column.setMinWidth(90);
        column.setPrefWidth(90);
        column.setMaxWidth(90);
        return column;
    }

    private static StackPane dateBlock(CourseSessionSummary session) {
        var weekday = new Label(session.scheduledAt().format(WEEKDAY).toUpperCase(Locale.ITALIAN));
        weekday.getStyleClass().add("course-session-weekday");
        var day = new Label(Integer.toString(session.scheduledAt().getDayOfMonth()));
        day.getStyleClass().add("course-session-day");
        var month = new Label(session.scheduledAt().format(MONTH).toUpperCase(Locale.ITALIAN));
        month.getStyleClass().add("course-session-month");
        var date = new VBox(0, weekday, day, month);
        date.setAlignment(Pos.CENTER);
        date.getStyleClass().add("course-session-date");
        var marker = new Region();
        marker.getStyleClass().add("course-session-marker");
        var wrapper = new StackPane(date, marker);
        wrapper.getStyleClass().add("course-session-date-block");
        return wrapper;
    }

    private static String timeRange(CourseSessionSummary session) {
        var start = session.scheduledAt();
        return TIME.format(start) + " – " + TIME.format(start.plusMinutes(session.durationMinutes()));
    }

    private void renderMenuNames(CourseSessionSummary session, long version) {
        if (recipeLinks == null) return;
        recipeLinks.getChildren().clear();
        if (session.mode() == SessionMode.ONLINE || session.recipeIds().isEmpty()) {
            recipeLinks.setManaged(false);
            recipeLinks.setVisible(false);
            return;
        }
        recipeLinks.getChildren().add(menuMessage("Caricamento del menu salvato…"));
        recipeLinks.setManaged(true);
        recipeLinks.setVisible(true);
        menuNames.load(session, names -> {
            if (version == menuVersion) showMenuNames(names);
        }, () -> {
            if (version == menuVersion) showMenuUnavailable();
        });
    }

    private void showMenuNames(List<String> names) {
        recipeLinks.getChildren().clear();
        if (names.isEmpty()) {
            recipeLinks.setManaged(false);
            recipeLinks.setVisible(false);
            return;
        }
        var heading = new Label("Menu della sessione");
        heading.getStyleClass().add("course-session-menu-title");
        recipeLinks.getChildren().add(heading);
        for (var name : names) {
            var recipe = new Label(name);
            recipe.setWrapText(true);
            recipe.setMinWidth(0);
            recipe.setMaxWidth(Double.MAX_VALUE);
            recipe.getStyleClass().add("course-session-recipe-name");
            recipeLinks.getChildren().add(recipe);
        }
        recipeLinks.setManaged(true);
        recipeLinks.setVisible(true);
    }

    private void showMenuUnavailable() {
        recipeLinks.getChildren().setAll(menuMessage("Menu salvato non disponibile. Riprova ad aprire la sessione."));
        recipeLinks.setManaged(true);
        recipeLinks.setVisible(true);
    }

    private static Label menuMessage(String text) {
        var message = new Label(text);
        message.setWrapText(true);
        message.getStyleClass().add("page-description");
        return message;
    }

    @FunctionalInterface
    interface MenuNamesLoader {
        void load(CourseSessionSummary session, Consumer<List<String>> onSuccess, Runnable onFailure);
    }
}
