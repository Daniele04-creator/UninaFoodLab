package it.uninafoodlab.presentation.common;

import java.math.BigDecimal;
import java.util.Objects;
import it.uninafoodlab.domain.catalog.MeasurementUnit;

public final class QuantityFormat {
    private QuantityFormat() {}

    public static String format(BigDecimal quantity) {
        return Objects.requireNonNull(quantity, "quantity")
                .stripTrailingZeros().toPlainString().replace('.', ',');
    }

    public static String quantity(BigDecimal quantity, MeasurementUnit unit) {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unit, "unit");
        return switch (unit) {
            case KILOGRAM -> format(quantity.movePointRight(3)) + " g";
            case LITER -> format(quantity.movePointRight(3)) + " ml";
            case TEASPOON -> format(quantity)
                    + (quantity.compareTo(BigDecimal.ONE) == 0 ? " cucchiaino" : " cucchiaini");
            default -> format(quantity) + " " + unit.symbol();
        };
    }
}
