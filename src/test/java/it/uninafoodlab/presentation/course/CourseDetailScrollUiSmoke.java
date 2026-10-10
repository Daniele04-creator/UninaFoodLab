package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.domain.course.*;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public final class CourseDetailScrollUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var task = new FutureTask<Void>(() -> {
                var courses = new ListView<CourseSummary>();
                var course = new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Pasta", CourseTitle.of("Corso cucina"),
                        "Programma con più sessioni", LocalDate.of(2037, 2, 1), LocalTime.of(18, 30), 7, 3,
                        Optional.of(LocalDateTime.of(2037, 2, 15, 20, 30)), CourseStatus.PUBLISHED,
                        IngredientMargin.of(java.math.BigDecimal.ZERO));
                courses.getItems().add(course); courses.getSelectionModel().select(course);
                var sessions = new TableView<CourseSessionSummary>();
                var first = session(1); var third = session(3);
                sessions.getItems().addAll(first, third); sessions.getSelectionModel().select(third);
                var recipes = new ComboBox<CourseSessionSummary>(); recipes.getItems().addAll(first, third);
                var program = new VBox(new Label("Programma del corso")); program.setPrefHeight(1600);
                var menu = new VBox(new Label("Ricette della sessione")); menu.setPrefHeight(1400);
                var scroll = new ScrollPane(new VBox(program, menu)); scroll.setFitToWidth(true);
                var fields = new CourseDetailPane.Fields(courses, sessions, recipes,
                        new Button(), new Button(), new Label(), new ToggleButton(), new ToggleButton(),
                        new ToggleButton(), new ToggleButton(), new ToggleButton(), program, menu,
                        new HBox(), new VBox(), new VBox(), new VBox(), new VBox(), scroll,
                        new StackPane(), new TextField(), new Label(), new Label(), new FlowPane(),
                        new Label(), new Label(), new Label(), new HBox(),
                        new Button(), new Button(), new Button());
                var actions = (CourseWorkspaceActions) Proxy.newProxyInstance(CourseDetailScrollUiSmoke.class.getClassLoader(),
                        new Class<?>[]{CourseWorkspaceActions.class}, (proxy, method, arguments) -> method.getReturnType() == boolean.class ? false : null);
                var pane = new CourseDetailPane(fields, actions, () -> {}, () -> {}, () -> false, () -> null);
                var stage = new Stage(); stage.setScene(new Scene(scroll, 800, 500)); stage.show();
                pane.showProgram(); scroll.applyCss(); scroll.layout(); scroll.setVvalue(.85);
                check(scroll.getVvalue() > .8, "Program fixture starts scrolled down");
                pane.showRecipes(); scroll.applyCss(); scroll.layout();
                check(scroll.getVvalue() == scroll.getVmin(), "Opening a session starts its menu at the top");
                scroll.setVvalue(.6);
                pane.selectCourseDetailSection(CourseDetailSection.RECIPES);
                check(Math.abs(scroll.getVvalue() - .6) < .001, "Refreshing the same session preserves scroll");
                sessions.getSelectionModel().clearSelection(); pane.updateSessionDetailHeader();
                sessions.getSelectionModel().select(third); pane.updateSessionDetailHeader();
                check(Math.abs(scroll.getVvalue() - .6) < .001,
                        "Temporarily empty selection during reload preserves the same session scroll");
                sessions.getSelectionModel().select(first); pane.updateSessionDetailHeader();
                check(scroll.getVvalue() == scroll.getVmin(), "Switching sessions in the same menu resets scroll");
                scroll.setVvalue(.5); pane.showProgram();
                check(scroll.getVvalue() == scroll.getVmin(), "Returning to the program opens its beginning");
                scroll.setVvalue(.4); pane.showProgram();
                check(Math.abs(scroll.getVvalue() - .4) < .001, "Refreshing the same program preserves scroll");
                stage.hide(); return null;
            });
            Platform.runLater(task); task.get(15, TimeUnit.SECONDS);
            System.out.println("PASS: session context starts at top while same-context refresh preserves scroll");
        } finally { Platform.exit(); }
    }

    private static CourseSessionSummary session(int sequence) {
        return new CourseSessionSummary(UUID.randomUUID(), sequence, LocalDateTime.of(2037, 2, sequence, 18, 30),
                SessionMode.PRACTICAL, SessionStatus.SCHEDULED, 120, Optional.empty(), Optional.of("Laboratorio"), Optional.of(12), 1);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
