package it.uninafoodlab.presentation.participant;

import static it.uninafoodlab.presentation.participant.ParticipantCourseCards.label;
import static it.uninafoodlab.presentation.participant.ParticipantDetailNavigation.configureBack;
import static it.uninafoodlab.presentation.participant.ParticipantDetailNavigation.focusAction;
import static it.uninafoodlab.presentation.participant.ParticipantDetailNavigation.focusBack;
import static it.uninafoodlab.presentation.participant.ParticipantDetailNavigation.move;
import static it.uninafoodlab.presentation.participant.ParticipantDetailNavigation.visible;

import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.application.course.PublishedCourseSummary;
import it.uninafoodlab.application.enrollment.EnrollmentSummary;
import it.uninafoodlab.application.notification.ParticipantNotification;
import it.uninafoodlab.presentation.common.LabBrand;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.Optional;
import it.uninafoodlab.application.course.CoursePeriod;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;


final class ParticipantCardLayout {
    private final VBox root;
    private final ListView<PublishedCourseSummary> courses;
    private final ListView<EnrollmentSummary> enrollments;
    private final ListView<ParticipantNotification> messages;
    private final ToggleButton browse;
    private final ToggleButton mine;
    private final ToggleButton inbox;
    private final Supplier<List<PublishedCourseSummary>> allCourses;
    private final Supplier<List<EnrollmentSummary>> allEnrollments;
    private final Supplier<Map<UUID, List<PublishedCourseSessionSummary>>> ownSessions;
    private final Clock clock;
    private final ProgressIndicator progress;
    private final Consumer<PublishedCourseSummary> openProgram;
    private final FlowPane courseGrid;
    private final FlowPane enrollmentGrid;
    private final VBox messageCards;
    private final Node courseDetail;
    private final Node attendanceDetail;
    private final Node messageDetail;
    private final Label title;
    private final Node filters;
    private final ParticipantCoursePages pages;
    private UUID returnCourse;
    private UUID returnEnrollment;
    private UUID returnMessage;
    private boolean restoreBrowseFocus;
    private boolean restoreMineFocus;
    private boolean restoreMessageFocus;

    static ParticipantCardLayout install(VBox root, ListView<PublishedCourseSummary> courses,
            ListView<EnrollmentSummary> enrollments, ListView<ParticipantNotification> messages,
            ToggleButton browse, ToggleButton mine, ToggleButton inbox,
            Supplier<List<PublishedCourseSummary>> allCourses, Supplier<List<EnrollmentSummary>> allEnrollments,
            Supplier<Map<UUID, List<PublishedCourseSessionSummary>>> ownSessions,
            Clock clock, Consumer<PublishedCourseSummary> openProgram, Label logo, ProgressIndicator progress,
            Runnable loadBrowse, Runnable loadMine) {
        return new ParticipantCardLayout(root, courses, enrollments, messages, browse, mine,
                inbox, allCourses, allEnrollments, ownSessions, clock, openProgram, logo, progress, loadBrowse, loadMine);
    }

    private ParticipantCardLayout(VBox root, ListView<PublishedCourseSummary> courses,
            ListView<EnrollmentSummary> enrollments, ListView<ParticipantNotification> messages,
            ToggleButton browse, ToggleButton mine, ToggleButton inbox,
            Supplier<List<PublishedCourseSummary>> allCourses, Supplier<List<EnrollmentSummary>> allEnrollments,
            Supplier<Map<UUID, List<PublishedCourseSessionSummary>>> ownSessions,
            Clock clock, Consumer<PublishedCourseSummary> openProgram, Label logo, ProgressIndicator progress,
            Runnable loadBrowse, Runnable loadMine) {
        this.root = root;
        this.courses = courses;
        this.enrollments = enrollments;
        this.messages = messages;
        this.browse = browse;
        this.mine = mine;
        this.inbox = inbox;
        this.allCourses = allCourses;
        this.allEnrollments = allEnrollments;
        this.ownSessions = ownSessions;
        this.clock = clock;
        this.progress = progress;
        this.openProgram = openProgram;
        logo.setGraphic(LabBrand.logo(168));
        courseGrid = (FlowPane) root.lookup("#courseGrid");
        enrollmentGrid = (FlowPane) root.lookup("#enrollmentGrid");
        messageCards = (VBox) root.lookup("#notificationCards");
        courseDetail = root.lookup("#courseDetailPanel");
        attendanceDetail = root.lookup("#attendancePanel");
        messageDetail = root.lookup("#notificationDetailPanel");
        title = (Label) root.lookup("#workspaceTitle");
        filters = root.lookup(".participant-filter-sheet");
        pages = new ParticipantCoursePages(root, progress.visibleProperty(), loadBrowse, loadMine);
        configureNavigation();
    }

    private void configureNavigation() {
        courses.getItems().addListener((ListChangeListener<PublishedCourseSummary>) change -> renderCourses());
        enrollments.getItems().addListener((ListChangeListener<EnrollmentSummary>) change -> renderEnrollments());
        messages.getItems().addListener((ListChangeListener<ParticipantNotification>) change -> renderMessages());
        browse.addEventHandler(javafx.event.ActionEvent.ACTION, event -> showBrowseRoute());
        mine.addEventHandler(javafx.event.ActionEvent.ACTION, event -> showMineRoute());
        inbox.addEventHandler(javafx.event.ActionEvent.ACTION, event -> showMessagesRoute());
        configureBack((Button) ((VBox) courseDetail).getChildren().getFirst(), "Tutti i corsi", "Torna ai corsi disponibili");
        configureBack((Button) ((VBox) attendanceDetail).getChildren().getFirst(), "I miei corsi", "Torna ai miei corsi");
        ((VBox) courseDetail).getChildren().getFirst().addEventHandler(javafx.event.ActionEvent.ACTION,
                event -> { restoreBrowseFocus = true; showBrowseRoute(); });
        ((VBox) attendanceDetail).getChildren().getFirst().addEventHandler(javafx.event.ActionEvent.ACTION,
                event -> { restoreMineFocus = true; showMineRoute(); });
        var back = new Button("Tutte le comunicazioni");
        back.getStyleClass().add("button-quiet");
        configureBack(back, "Tutte le comunicazioni", "Torna alle comunicazioni");
        back.setOnAction(event -> { restoreMessageFocus = true; showMessagesRoute(); });
        ((VBox) messageDetail).getChildren().addFirst(back);
        progress.visibleProperty().addListener(
                (observable, previous, busy) -> { if (!busy) restoreFocus(); });
        renderCourses(); renderEnrollments(); renderMessages(); showBrowseRoute();
    }

    void resetBrowsePage() { pages.resetBrowsePage(); }
    int browsePage() { return pages.browsePage(); }
    int minePage() { return pages.minePage(); }
    CoursePeriod browsePeriod() { return pages.browsePeriod(); }
    CoursePeriod minePeriod() { return pages.minePeriod(); }
    void updateBrowsePage(int index, int total) { pages.updateBrowsePage(index, total); }
    void updateMinePage(int index, int total) { pages.updateMinePage(index, total); }

    private void showBrowseRoute() {
        var panel = (VBox) root.lookup("#browsePanel");
        move(courseDetail, panel, panel.getChildren().size());
        visible(((VBox) courseDetail).getChildren().getFirst(), true);
        visible(courseGrid, true); visible(courseDetail, false); visible(filters, true);
        pages.showBrowse(true);
        setHeading("Corsi disponibili");
        restoreFocus();
    }

    private void showMineRoute() {
        visible(enrollmentGrid, true); visible(attendanceDetail, false);
        pages.showMine(true);
        setHeading("I miei corsi");
        restoreFocus();
    }

    private void showMessagesRoute() {
        visible(messageCards, true); visible(messageDetail, false); visible(root.lookup("#unreadCount"), true);
        setHeading("Comunicazioni");
        restoreFocus();
    }

    private void renderCourses() {
        courseGrid.getChildren().clear();
        var pageItems = courses.getItems();
        for (var item : pageItems) {
            var card = ParticipantCourseCards.available(item, allEnrollments.get(), LocalDateTime.now(clock), () -> openCourse(item));
            ParticipantCourseCards.action(card).disableProperty().bind(courses.disableProperty());
            ParticipantCourseCards.size(card, courseGrid, pageItems.size());
            courseGrid.getChildren().add(card);
        }
        if (pageItems.isEmpty()) courseGrid.getChildren().add(label(
                "Nessun corso trovato. Prova a cambiare i filtri.", "participant-card-copy"));
    }

    private void openCourse(PublishedCourseSummary item) {
        returnCourse = item.id();
        courses.getSelectionModel().select(item);
        visible(courseGrid, false); visible(courseDetail, true); visible(filters, false);
        pages.showBrowse(false);
        setHeading(item.title().value());
        focusBack(courseDetail);
    }

    private void renderEnrollments() {
        enrollmentGrid.getChildren().clear();
        var pageItems = enrollments.getItems();
        var coursesById = indexCourses();
        var sessionsByCourse = ownSessions.get();
        var now = LocalDateTime.now(clock);
        for (var item : pageItems) {
            var summary = Optional.ofNullable(coursesById.get(item.courseId()));
            var card = ParticipantCourseCards.enrollment(item, summary,
                    sessionsByCourse.getOrDefault(item.courseId(), List.of()), now, () -> openEnrollment(item, summary));
            ParticipantCourseCards.action(card).disableProperty().bind(enrollments.disableProperty());
            ParticipantCourseCards.size(card, enrollmentGrid, pageItems.size());
            enrollmentGrid.getChildren().add(card);
        }
        if (pageItems.isEmpty()) enrollmentGrid.getChildren().add(label(
                minePeriod() == CoursePeriod.ALL ? "Nessuna iscrizione. Apri Corsi disponibili per iscriverti."
                : "Nessun corso in questa vista. Seleziona Tutti per vedere le tue iscrizioni.", "participant-card-copy"));
    }

    private Map<UUID, PublishedCourseSummary> indexCourses() {
        var coursesById = new HashMap<UUID, PublishedCourseSummary>();
        for (var course : allCourses.get()) coursesById.putIfAbsent(course.id(), course);
        return coursesById;
    }

    private void openEnrollment(EnrollmentSummary item, java.util.Optional<PublishedCourseSummary> summary) {
        returnEnrollment = item.courseId();
        enrollments.getSelectionModel().select(item);
        openProgram.accept(summary.orElse(null));
        visible(courseDetail, summary.isPresent());
        summary.ifPresent(course -> {
            move(courseDetail, (VBox) attendanceDetail, 1);
            visible(((VBox) courseDetail).getChildren().getFirst(), false);
        });
        visible(root.lookup("#attendanceDetail"), summary.isEmpty());
        ((VBox) attendanceDetail).getStyleClass().remove("participant-program");
        ((VBox) attendanceDetail).getStyleClass().remove("surface-card");
        if (summary.isEmpty()) ((VBox) attendanceDetail).getStyleClass().add("participant-program");
        visible(enrollmentGrid, false); visible(attendanceDetail, true);
        pages.showMine(false);
        setHeading(item.title().value());
        focusBack(attendanceDetail);
    }

    private void renderMessages() {
        ParticipantMessageRows.render(messageCards, messages.getItems(),
                progress.visibleProperty(), this::openMessage);
    }

    private void openMessage(ParticipantNotification item) {
        returnMessage = item.id();
        messages.getSelectionModel().select(item);
        visible(messageCards, false); visible(messageDetail, true); visible(root.lookup("#unreadCount"), false);
        setHeading("Comunicazioni");
        ((Button) ((VBox) messageDetail).getChildren().getFirst()).requestFocus();
    }

    void showCourseRoute(PublishedCourseSummary course) {
        var panel = (VBox) root.lookup("#browsePanel");
        move(courseDetail, panel, panel.getChildren().size());
        visible(((VBox) courseDetail).getChildren().getFirst(), true);
        visible(courseGrid, false); visible(courseDetail, true); visible(filters, false);
        pages.showBrowse(false);
        setHeading(course.title().value());
        focusBack(courseDetail);
    }

    private void restoreFocus() {
        Platform.runLater(() -> {
            if (progress.isVisible()) return;
            restoreBrowseFocus();
            restoreMineFocus();
            restoreMessageFocus();
        });
    }

    private void restoreBrowseFocus() {
        if (!restoreBrowseFocus || !courseGrid.isVisible()) return;
        if (!focusAction(courseGrid, returnCourse)) root.lookup("#courseSearch").requestFocus();
        restoreBrowseFocus = false;
    }

    private void restoreMineFocus() {
        if (!restoreMineFocus || !enrollmentGrid.isVisible()) return;
        if (!focusAction(enrollmentGrid, returnEnrollment)) pages.focusMineFilter();
        restoreMineFocus = false;
    }

    private void restoreMessageFocus() {
        if (!restoreMessageFocus || !messageCards.isVisible()) return;
        if (!focusAction(messageCards, returnMessage)) inbox.requestFocus();
        restoreMessageFocus = false;
    }

    private void setHeading(String text) {
        title.setText(text); visible(root.lookup(".participant-page-heading"), true);
    }
}
