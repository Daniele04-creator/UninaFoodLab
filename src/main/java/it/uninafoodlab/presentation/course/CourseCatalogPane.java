package it.uninafoodlab.presentation.course;

import static it.uninafoodlab.presentation.course.CoursePresentation.*;
import static it.uninafoodlab.presentation.course.CourseWorkspaceView.formatCourseDateRange;

import it.uninafoodlab.application.course.CourseSummary;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;

final class CourseCatalogPane {
    private final Fields fields;
    private final Clock clock;
    private final CourseWorkspaceActions actions;
    private final ReactiveFilterGuard filterGuard;
    private boolean adjustingCourseSearch;
    private boolean searchPending;
    private final javafx.animation.PauseTransition searchDelay =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
    private Runnable resetPage = () -> {};
    private CourseOperationalView.CatalogSection catalogSection = CourseOperationalView.CatalogSection.ACTIVE;

    CourseCatalogPane(Fields fields, Clock clock, CourseWorkspaceActions actions,
            ReactiveFilterGuard filterGuard, Consumer<CourseSummary> onSelect) {
        this.fields = fields;
        this.clock = clock;
        this.actions = actions;
        this.filterGuard = filterGuard;
        searchDelay.setOnFinished(event -> {
            searchPending = true;
            resumeSearch();
        });
        fields.courseSearchField().textProperty().addListener((observable, previous, value) -> {
            if (!adjustingCourseSearch) {
                searchPending = false;
                searchDelay.playFromStart();
            }
        });
        fields.courseList().setPlaceholder(new Label("Nessun corso trovato."));
        fields.categoryFilter().setConverter(categoryFilterConverter());
        fields.categoryFilter().setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(CourseCategoryFilter item, boolean empty) {
                super.updateItem(item, empty);
                setText(categoryButtonLabel(item));
                setGraphic(null);
                if (empty || item == null) {
                    setTooltip(null);
                    setAccessibleText("Categoria");
                } else {
                    setTooltip(new Tooltip("Categoria selezionata: " + item.label()));
                    setAccessibleText("Categoria selezionata: " + item.label());
                }
            }
        });
        fields.courseList().setCellFactory(ignored -> new CourseCatalogCell(clock));
        fields.courseList().getSelectionModel().selectedItemProperty().addListener((observable, previous, course) -> {
            showCourseSummary(course);
            onSelect.accept(course);
        });
    }

    static String categoryButtonLabel(CourseCategoryFilter item) {
        return item == null || item.categoryId().isEmpty() ? "Tutte le categorie" : item.label();
    }

    void setResetPageAction(Runnable resetPage) { this.resetPage = resetPage; }

    void resetPagination() { resetPage.run(); }

    boolean isArchive() {
        var selected = fields.courseList().getSelectionModel().getSelectedItem();
        return selected == null ? catalogSection == CourseOperationalView.CatalogSection.ARCHIVE
                : CourseOperationalView.isArchived(selected, clock);
    }
    CourseOperationalView.CatalogSection section() { return catalogSection; }

    List<CourseSummary> display(List<CourseSummary> courses, long totalCourses) {
        updateCourseCount(totalCourses);
        fields.courseList().getItems().setAll(courses);
        return courses;
    }

    void showEmptyCatalog() {
        fields.selectedCourseLabel().setText(isArchive() ? "Nessun corso passato" : "Nessun corso per questi filtri");
        fields.selectedCourseMetaLabel().setText(isArchive() ? "I corsi passati e annullati compariranno qui."
                : "Modifica i filtri o crea un nuovo corso.");
        fields.selectedCourseDescriptionLabel().setText("");
        fields.selectedCourseFactsLabel().setText("");
    }

    private void showCourseSummary(CourseSummary course) {
        if (course == null) {
            fields.selectedCourseLabel().setText("Seleziona un corso");
            fields.selectedCourseMetaLabel().setText("Seleziona un elemento dal catalogo.");
            fields.selectedCourseDescriptionLabel().setText("Consulta qui programma, ricette e partecipanti.");
            fields.selectedCourseFactsLabel().setText("");
            return;
        }
        fields.selectedCourseLabel().setText(course.title().value());
        fields.selectedCourseMetaLabel().setText(course.categoryName() + ", " + CourseOperationalView.catalogLabel(course, clock));
        fields.selectedCourseDescriptionLabel().setText(course.description());
        var sessions = course.sessionCount() == 1 ? "1 sessione" : course.sessionCount() + " sessioni";
        fields.selectedCourseFactsLabel().setText(sessions + ", " + formatCourseDateRange(course));
    }

    private void updateCourseCount(long count) {
        var text = count + (count == 1 ? " corso" : " corsi");
        fields.courseCountLabel().setText(text);
        fields.courseCountLabel().setAccessibleText(text);
        fields.courseCountLabel().setVisible(true);
        fields.courseCountLabel().setManaged(true);
    }

    record Fields(ListView<CourseSummary> courseList,
            TextField courseSearchField,
            ComboBox<CourseCategoryFilter> categoryFilter,
            ToggleButton activeCoursesTab,
            ToggleButton archiveCoursesTab,
            ToggleButton allCoursesTab,
            Label courseCountLabel,
            Label selectedCourseLabel,
            Label selectedCourseMetaLabel,
            Label selectedCourseDescriptionLabel,
            Label selectedCourseFactsLabel) {}

    String search() { return fields.courseSearchField().getText(); }

    it.uninafoodlab.application.course.CourseFilter filter(java.util.Optional<java.util.UUID> categoryId) {
        var period = switch (catalogSection) {
            case ACTIVE -> it.uninafoodlab.application.course.CoursePeriod.ACTIVE;
            case ARCHIVE -> it.uninafoodlab.application.course.CoursePeriod.PAST;
            case ALL -> it.uninafoodlab.application.course.CoursePeriod.ALL;
        };
        return new it.uninafoodlab.application.course.CourseFilter(categoryId, search(), period,
                java.time.LocalDateTime.now(clock.withZone(java.time.ZoneId.of("Europe/Rome"))));
    }

    void clearCourseSearch() {
        searchDelay.stop();
        searchPending = false;
        adjustingCourseSearch = true;
        try {
            fields.courseSearchField().clear();
        } finally {
            adjustingCourseSearch = false;
        }
    }

    void resumeSearch() {
        if (searchPending && !actions.isBusy()) searchCourses();
    }

    void searchCourses() {
        if (actions.isBusy()) return;
        searchDelay.stop();
        searchPending = false;
        resetPage.run();
        actions.loadCourses(null);
    }

    void filterCourses() {
        if (!filterGuard.permitsReload(actions.isBusy())) {
            return;
        }

        resetPage.run();
        actions.loadCourses(null);
    }

    void resetFilters() {
        clearCourseSearch();
        resetPage.run();
        filterGuard.update(() -> fields.categoryFilter().getSelectionModel().selectFirst());

        if (filterGuard.permitsReload(actions.isBusy())) {
            actions.loadCourses(null);
        }
    }

    private void updateSectionButtons() {
        fields.activeCoursesTab().setSelected(catalogSection == CourseOperationalView.CatalogSection.ACTIVE);
        fields.archiveCoursesTab().setSelected(catalogSection == CourseOperationalView.CatalogSection.ARCHIVE);
        fields.allCoursesTab().setSelected(catalogSection == CourseOperationalView.CatalogSection.ALL);
    }

    void selectCatalogSection(CourseOperationalView.CatalogSection section) {
        resetPage.run();
        catalogSection = Objects.requireNonNull(section, "section");
        updateSectionButtons();
        fields.courseList().getSelectionModel().clearSelection();

        if (filterGuard.permitsReload(actions.isBusy())) {
            actions.loadCourses(null);
        }
    }
}
