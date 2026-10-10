package it.uninafoodlab.presentation.planning;

import java.time.YearMonth;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javafx.scene.control.ComboBox;

import javafx.util.StringConverter;
import javafx.application.Platform;

final class ReportPeriodSelector {
    private final ComboBox<Month> periodField;
    private final ComboBox<Integer> reportYearField;
    private final java.util.function.BooleanSupplier visible;
    private final Runnable reload;
    private boolean updatingPeriod;

    ReportPeriodSelector(ComboBox<Month> periodField, ComboBox<Integer> reportYearField,
            java.util.function.BooleanSupplier visible, Runnable reload) {
        this.periodField = periodField;
        this.reportYearField = reportYearField;
        this.visible = visible;
        this.reload = reload;
    }

    void configure() {
        var now = YearMonth.now(ZoneId.of("Europe/Rome"));
        periodField.getItems().setAll(Month.values());
        periodField.setConverter(new StringConverter<>() {
            @Override public String toString(Month month) {
                if (month == null) return "";
                var label = month.getDisplayName(TextStyle.FULL, Locale.ITALIAN);
                return Character.toUpperCase(label.charAt(0)) + label.substring(1);
            }
            @Override public Month fromString(String value) { return null; }
        });
        periodField.setValue(now.getMonth());
        reportYearField.getItems().setAll(reportYears());
        reportYearField.setValue(now.getYear());
        reportYearField.setOnShown(event -> Platform.runLater(() -> {
            if (reportYearField.getSkin() instanceof javafx.scene.control.skin.ComboBoxListViewSkin<?> skin
                    && skin.getPopupContent() instanceof javafx.scene.control.ListView<?> list) {
                list.scrollTo(Math.max(0, reportYearField.getSelectionModel().getSelectedIndex() - 3));
            }
        }));
        periodField.valueProperty().addListener((observable, previous, current) -> reportPeriodChanged());
        reportYearField.valueProperty().addListener((observable, previous, current) -> reportPeriodChanged());
    }

    static List<Integer> reportYears() {
        return java.util.stream.IntStream.rangeClosed(2000, 2200).boxed().toList();
    }

    private void reportPeriodChanged() {
        if (!updatingPeriod && visible.getAsBoolean()) reload.run();
    }

    static Optional<YearMonth> adjacentReportPeriod(YearMonth period, int offset) {
        var target = period.plusMonths(offset);
        return target.getYear() >= 2000 && target.getYear() <= 2200
                ? Optional.of(target) : Optional.empty();
    }

    void shiftMonth(int offset) {
        var current = YearMonth.of(reportYearField.getValue(), periodField.getValue());
        adjacentReportPeriod(current, offset).ifPresent(target -> {
            updatingPeriod = true;
            try {
                reportYearField.setValue(target.getYear());
                periodField.setValue(target.getMonth());
            } finally { updatingPeriod = false; }
            reload.run();
        });
    }

    void showCurrentMonth() {
        var now = YearMonth.now(ZoneId.of("Europe/Rome"));
        updatingPeriod = true;
        try {
            periodField.setValue(now.getMonth());
            reportYearField.setValue(now.getYear());
        } finally { updatingPeriod = false; }
        reload.run();
    }
}
