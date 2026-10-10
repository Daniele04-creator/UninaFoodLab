package it.uninafoodlab.presentation.participant;

import static it.uninafoodlab.presentation.participant.ParticipantDetailNavigation.visible;

import it.uninafoodlab.application.course.CoursePeriod;
import java.util.List;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Node;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

final class ParticipantCoursePages {
    private final ToggleGroup browseFilter = new ToggleGroup();
    private final ToggleGroup mineFilter = new ToggleGroup();
    private final HBox browseTabs = new HBox(8);
    private final HBox mineTabs = new HBox(8);
    private final ParticipantGridPager browsePager;
    private final ParticipantGridPager minePager;

    ParticipantCoursePages(VBox root, ObservableBooleanValue busy, Runnable loadBrowse, Runnable loadMine) {
        var browsePanel = (VBox) root.lookup("#browsePanel");
        var minePanel = (VBox) root.lookup("#minePanel");
        browsePager = new ParticipantGridPager(browsePanel, root.lookup("#courseGrid"), busy, page -> loadBrowse.run());
        minePager = new ParticipantGridPager(minePanel, root.lookup("#enrollmentGrid"), busy, page -> loadMine.run());
        configureFilter(browseTabs, browseFilter, List.of("Disponibili", "Tutti"), busy,
                () -> { browsePager.reset(); loadBrowse.run(); });
        configureFilter(mineTabs, mineFilter, List.of("Attivi", "Passati", "Tutti"), busy,
                () -> { minePager.reset(); loadMine.run(); });
        browsePanel.getChildren().addFirst(browseTabs);
        minePanel.getChildren().addFirst(mineTabs);
    }

    private static void configureFilter(HBox tabs, ToggleGroup filter, List<String> names,
            ObservableBooleanValue busy, Runnable reload) {
        for (String name : names) {
            var tab = new ToggleButton(name);
            tab.disableProperty().bind(busy);
            tab.setToggleGroup(filter);
            tab.getStyleClass().add("course-catalog-tab");
            tabs.getChildren().add(tab);
        }
        filter.selectToggle((Toggle) tabs.getChildren().getFirst());
        filter.selectedToggleProperty().addListener((observable, previous, selected) -> {
            if (selected == null) { filter.selectToggle(previous); return; }
            reload.run();
        });
    }

    void resetBrowsePage() { browsePager.reset(); }
    int browsePage() { return browsePager.page(); }
    int minePage() { return minePager.page(); }
    CoursePeriod browsePeriod() { return selectedPeriod(browseFilter); }
    CoursePeriod minePeriod() { return selectedPeriod(mineFilter); }
    void updateBrowsePage(int index, int total) { browsePager.update(index, total, "Corsi disponibili"); }
    void updateMinePage(int index, int total) { minePager.update(index, total, "I miei corsi"); }

    void showBrowse(boolean show) {
        browsePager.show(show);
        visible(browseTabs, show);
    }

    void showMine(boolean show) {
        minePager.show(show);
        visible(mineTabs, show);
    }

    void focusMineFilter() {
        if (mineFilter.getSelectedToggle() instanceof Node selected) selected.requestFocus();
    }

    private static CoursePeriod selectedPeriod(ToggleGroup filter) {
        String text = filter.getSelectedToggle() instanceof ToggleButton selected ? selected.getText() : "Tutti";
        return switch (text) {
            case "Disponibili", "Attivi" -> CoursePeriod.ACTIVE;
            case "Passati" -> CoursePeriod.PAST;
            default -> CoursePeriod.ALL;
        };
    }
}
