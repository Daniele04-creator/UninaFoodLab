package it.uninafoodlab.presentation.notification;

import it.uninafoodlab.presentation.notification.NotificationWorkspaceController.HistoryCourse;
import it.uninafoodlab.presentation.notification.NotificationWorkspaceController.NotificationView;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import static javafx.scene.layout.Priority.ALWAYS;
import javafx.scene.layout.VBox;
import static javafx.scene.layout.Region.USE_PREF_SIZE;

final class NotificationHistoryView {
    private static final DateTimeFormatter ARCHIVE_DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ITALIAN).withZone(ZoneId.of("Europe/Rome"));
    private static final HistoryCourse ALL_HISTORY = new HistoryCourse(true, Optional.empty(), "Tutti i corsi");
    private List<NotificationView> history = List.of();
    private boolean updatingHistoryCourses;
    private final ListView<NotificationView> notificationList;
    private final ComboBox<HistoryCourse> historyCourseField;
    private final TextField historySearchField;
    private final Label detailTitleLabel;
    private final Label detailCourseLabel;
    private final Label detailDateLabel;
    private final Label detailMessageLabel;
    private final Label detailStatusLabel;
    private final Label sentBadge;
    private final BiConsumer<String, Boolean> feedback;
    private Button lastOpenButton;

    NotificationHistoryView(ListView<NotificationView> notificationList,
            ComboBox<HistoryCourse> historyCourseField,
            TextField historySearchField,
            Label detailTitleLabel,
            Label detailCourseLabel,
            Label detailDateLabel,
            Label detailMessageLabel,
            Label detailStatusLabel,
            Label sentBadge,
            BiConsumer<String, Boolean> feedback, Runnable selectionChanged, Consumer<NotificationView> openDetail) {
        this.notificationList = notificationList;
        this.historyCourseField = historyCourseField;
        this.historySearchField = historySearchField;
        this.detailTitleLabel = detailTitleLabel;
        this.detailCourseLabel = detailCourseLabel;
        this.detailDateLabel = detailDateLabel;
        this.detailMessageLabel = detailMessageLabel;
        this.detailStatusLabel = detailStatusLabel;
        this.sentBadge = sentBadge;
        this.feedback = feedback;
        historyCourseField.getItems().setAll(ALL_HISTORY);
        historyCourseField.setValue(ALL_HISTORY);
        historyCourseField.valueProperty().addListener((observable, previous, selected) -> {
            if (!updatingHistoryCourses) {
                applyHistoryFilters();
            }
        });
        historySearchField.textProperty().addListener((observable, previous, value) -> applyHistoryFilters());
        notificationList.setPlaceholder(new Label("Nessuna comunicazione corrisponde ai filtri."));
        notificationList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(NotificationView item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                var summary = notificationSummary(item);
                var open = new Button("Apri comunicazione →");
                open.getStyleClass().addAll("lab-text-action", "notification-open");
                open.setMinWidth(USE_PREF_SIZE);
                open.setAccessibleText("Apri comunicazione: " + item.title());
                open.setOnAction(event -> {
                    notificationList.getSelectionModel().select(item);
                    showDetail(item);
                    lastOpenButton = open;
                    openDetail.accept(item);
                });
                var actions = notificationActions(item, open);
                var row = new HBox(24, summary, actions);
                row.setAlignment(javafx.geometry.Pos.TOP_LEFT);
                row.setMinWidth(0.0);
                row.prefWidthProperty().bind(Bindings.max(0.0,
                        notificationList.widthProperty().subtract(66.0)));
                row.maxWidthProperty().bind(row.prefWidthProperty());
                setGraphic(row);
            }
        });
        notificationList.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> {
                    selectionChanged.run();
                    showDetail(selected);
                });
    }

    private static VBox notificationSummary(NotificationView notification) {
        var title = new Label(notification.title());
        title.getStyleClass().add("notification-row-title");
        title.setWrapText(true);
        title.setMinWidth(0.0);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setMinHeight(USE_PREF_SIZE);
        var course = new Label(notification.courseLabel());
        course.getStyleClass().add("notification-row-course");
        course.setWrapText(true);
        course.setMinWidth(0.0);
        course.setMaxWidth(USE_PREF_SIZE);
        course.setMinHeight(USE_PREF_SIZE);
        var message = new Label(notification.message().replaceAll("\\s+", " ").strip());
        message.setWrapText(true);
        message.setMinHeight(0);
        message.setMaxHeight(48);
        message.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        message.setMaxWidth(Double.MAX_VALUE);
        message.getStyleClass().add("notification-card-message");
        var summary = new VBox(8, title, course, message);
        summary.setMinWidth(0);
        HBox.setHgrow(summary, ALWAYS);
        return summary;
    }

    private static VBox notificationActions(NotificationView notification, Button open) {
        var date = new Label(ARCHIVE_DATE.format(notification.createdAt()) + ", " + notification.status());
        date.getStyleClass().add("notification-row-date");
        date.setMinHeight(USE_PREF_SIZE);
        date.setMinWidth(USE_PREF_SIZE);
        var actions = new VBox(12, date, open);
        actions.setMinWidth(USE_PREF_SIZE);
        actions.setMaxWidth(USE_PREF_SIZE);
        actions.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        return actions;
    }

    void restoreFocus() {
        if (lastOpenButton != null && lastOpenButton.getScene() != null) lastOpenButton.requestFocus();
        else notificationList.requestFocus();
    }

    void replaceHistory(List<NotificationView> history, UUID createdNotificationId) {
        this.history = List.copyOf(history);
        var currentFilter = historyCourseField.getValue();
        var courses = history.stream().map(NotificationView::historyCourse)
                .distinct().sorted(Comparator.comparing(HistoryCourse::label)).toList();
        updatingHistoryCourses = true;
        try {
            historyCourseField.getItems().setAll(ALL_HISTORY);
            historyCourseField.getItems().addAll(courses);
            historyCourseField.setValue(courses.stream().filter(item -> item.equals(currentFilter))
                    .findFirst().orElse(ALL_HISTORY));
        } finally {
            updatingHistoryCourses = false;
        }
        var created = history.stream().filter(item -> item.id().equals(createdNotificationId)).findFirst();
        if (created.isPresent()) {
            revealCreatedNotification(created.orElseThrow());
            return;
        }
        applyHistoryFilters();
    }

    private void revealCreatedNotification(NotificationView created) {
        var course = historyCourseField.getValue();
        var currentCourse = course == null || ALL_HISTORY.equals(course) ? Optional.<HistoryCourse>empty() : Optional.of(course);
        var query = historySearchField.getText();
        var filters = filtersForCreated(created, currentCourse, query);
        var changed = !filters.course().equals(currentCourse) || !Objects.equals(filters.query(), query);
        updatingHistoryCourses = true;
        try {
            historyCourseField.setValue(filters.course().orElse(ALL_HISTORY));
            historySearchField.setText(filters.query());
        } finally { updatingHistoryCourses = false; }
        applyHistoryFilters();
        notificationList.getSelectionModel().select(created);
        notificationList.scrollTo(created);
        feedback.accept("Comunicazione inviata."
                + (changed ? " Filtri incompatibili aggiornati per mostrarla." : ""), false);
    }

    static HistoryFilters filtersForCreated(NotificationView created, Optional<HistoryCourse> course, String query) {
        var visibleCourse = filterHistory(List.of(created), course, "").isEmpty() ? Optional.<HistoryCourse>empty() : course;
        var visibleQuery = filterHistory(List.of(created), Optional.empty(), query).isEmpty() ? "" : query;
        return new HistoryFilters(visibleCourse, visibleQuery);
    }

    record HistoryFilters(Optional<HistoryCourse> course, String query) {}

    private void applyHistoryFilters() {
        if (updatingHistoryCourses) return;
        var current = notificationList.getSelectionModel().getSelectedItem();
        var selectedId = current == null ? null : current.id();
        var filter = historyCourseField.getValue();
        var filtered = filterHistory(history, filter == null || ALL_HISTORY.equals(filter)
                ? Optional.empty() : Optional.of(filter), historySearchField.getText());
        notificationList.getItems().setAll(filtered);
        var selection = notificationToSelect(filtered, selectedId);
        if (selection.isEmpty()) {
            notificationList.getSelectionModel().clearSelection();
            showDetail(null);
        } else {
            notificationList.getSelectionModel().select(selection.orElseThrow());
        }
    }

    static List<NotificationView> filterHistory(List<NotificationView> history,
            Optional<HistoryCourse> course, String query) {
        var needle = query == null ? "" : query.strip().toLowerCase(Locale.ITALIAN);
        return history.stream()
                .filter(item -> course.isEmpty() || course.orElseThrow().matches(item))
                .filter(item -> (item.title() + " " + item.message() + " " + item.courseLabel())
                        .toLowerCase(Locale.ITALIAN).contains(needle))
                .toList();
    }

    static Optional<NotificationView> notificationToSelect(
            List<NotificationView> notifications, UUID selectedId) {
        if (notifications.isEmpty()) {
            return Optional.empty();
        }
        if (selectedId != null) {
            var selected = notifications.stream()
                    .filter(notification -> notification.id().equals(selectedId))
                    .findFirst();
            if (selected.isPresent()) {
                return selected;
            }
        }
        return Optional.of(notifications.getFirst());
    }

    private void showDetail(NotificationView selected) {
        sentBadge.setManaged(selected != null);
        sentBadge.setVisible(sentBadge.isManaged());
        if (selected == null) {
            detailTitleLabel.setText("Seleziona una notifica");
            detailCourseLabel.setText("Tutte le notifiche");
            detailDateLabel.setText("");
            detailStatusLabel.setText("");
            detailMessageLabel.setText("Il contenuto completo della notifica apparirà qui.");
            return;
        }
        detailTitleLabel.setText(selected.title());
        detailCourseLabel.setText(selected.courseLabel());
        detailDateLabel.setText(ARCHIVE_DATE.format(selected.createdAt()));
        detailStatusLabel.setText(selected.status());
        detailMessageLabel.setText(selected.message());
    }

}
