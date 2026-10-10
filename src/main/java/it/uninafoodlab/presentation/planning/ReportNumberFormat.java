package it.uninafoodlab.presentation.planning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

final class ReportNumberFormat {
    private ReportNumberFormat() {}

    static String format(BigDecimal value) {
        return Objects.requireNonNull(value, "value")
                .setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
                .replace('.', ',');
    }

    static String format(Number value) {
        Objects.requireNonNull(value, "value");
        return format(value instanceof BigDecimal decimal
                ? decimal
                : new BigDecimal(value.toString()));
    }
}
