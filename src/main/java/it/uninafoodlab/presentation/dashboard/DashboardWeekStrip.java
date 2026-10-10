package it.uninafoodlab.presentation.dashboard;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class DashboardWeekStrip {
    private DashboardWeekStrip() {}

    static void render(HBox container, LocalDate monday, LocalDate today, LocalDate selected,
                       java.util.Map<LocalDate, Long> sessionCounts, Consumer<LocalDate> selectDay) {
        container.setSpacing(2);
        boolean sameWeek = container.getChildren().size() == 7;
        for (int index = 0; sameWeek && index < 7; index++) {
            sameWeek = monday.plusDays(index).equals(container.getChildren().get(index).getUserData());
        }
        if (!sameWeek) container.getChildren().clear();
        for (int index = 0; index < 7; index++) {
            var day = monday.plusDays(index);
            var button = sameWeek ? (Button) container.getChildren().get(index) : dayButton(day);
            updateDayButton(button, day, today, selected, sessionCounts);
            button.setOnAction(event -> selectDay.accept(day));
            if (!sameWeek) container.getChildren().add(button);
        }
    }
    private static Button dayButton(LocalDate day) {
        var column = new VBox(3);
        column.setAlignment(Pos.CENTER);
        column.setMinWidth(0);
        column.setMouseTransparent(true);
        var button = new Button();
        button.setGraphic(column);
        button.setMinWidth(0);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPrefWidth(90);
        HBox.setHgrow(button, Priority.ALWAYS);
        button.getStyleClass().addAll("lab-week-day", "lab-week-day-button");
        button.setUserData(day);
        var weekday = new Label(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ITALIAN));
        weekday.getStyleClass().add("lab-week-name");
        var number = new Label(Integer.toString(day.getDayOfMonth()));
        number.getStyleClass().add("lab-week-number");
        var today = new Label("Oggi");
        today.getStyleClass().add("agenda-week-today");
        column.getChildren().addAll(weekday, number, today);
        return button;
    }

    private static void updateDayButton(Button button, LocalDate day, LocalDate today, LocalDate selected,
            java.util.Map<LocalDate, Long> sessionCounts) {
        setClass(button, "lab-week-day-today", day.equals(today));
        setClass(button, "lab-week-day-selected", day.equals(selected));
        setClass(button, "lab-week-day-session", sessionCounts.containsKey(day));
        var column = (VBox) button.getGraphic();
        ((Label) column.getChildren().getFirst()).setText(
                day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ITALIAN));
        var todayLabel = column.getChildren().get(2);
        todayLabel.setVisible(day.equals(today));
        todayLabel.setManaged(day.equals(today));
        var count = sessionCounts.getOrDefault(day, 0L);
        button.setTooltip(new javafx.scene.control.Tooltip(count == 0 ? "Nessuna sessione"
                : count + (count == 1 ? " sessione" : " sessioni")));
        button.setAccessibleText(day.format(java.time.format.DateTimeFormatter.ofPattern(
                "EEEE d MMMM uuuu", Locale.ITALIAN)) + (day.equals(today) ? ", oggi" : "")
                + (day.equals(selected) ? ", selezionato" : "")
                + (sessionCounts.containsKey(day) ? ", sessioni in programma" : ", nessuna sessione"));
    }

    private static void setClass(Button button, String style, boolean enabled) {
        button.getStyleClass().remove(style);
        if (enabled) button.getStyleClass().add(style);
    }

}
