package it.uninafoodlab.presentation.common;

import it.uninafoodlab.domain.catalog.MeasurementUnit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class QuantityFormatTest {
    @Test
    void normalizesEquivalentUnitsExactlyWithoutRounding() {
        var liter = it.uninafoodlab.domain.catalog.MeasurementUnit.LITER;
        var milliliter = it.uninafoodlab.domain.catalog.MeasurementUnit.MILLILITER;
        var kilogram = it.uninafoodlab.domain.catalog.MeasurementUnit.KILOGRAM;
        var gram = it.uninafoodlab.domain.catalog.MeasurementUnit.GRAM;
        assertThat(QuantityFormat.quantity(new BigDecimal("0.02"), liter)).isEqualTo("20 ml");
        assertThat(QuantityFormat.quantity(new BigDecimal("20"), milliliter)).isEqualTo("20 ml");
        assertThat(QuantityFormat.quantity(new BigDecimal("0.2"), kilogram)).isEqualTo("200 g");
        assertThat(QuantityFormat.quantity(new BigDecimal("2000"), gram)).isEqualTo("2000 g");
        assertThat(QuantityFormat.quantity(new BigDecimal("1500"), gram)).isEqualTo("1500 g");
        assertThat(QuantityFormat.quantity(new BigDecimal("1250"), milliliter)).isEqualTo("1250 ml");
        assertThat(QuantityFormat.quantity(new BigDecimal("0.000125"), liter)).isEqualTo("0,125 ml");
        assertThat(QuantityFormat.quantity(BigDecimal.ONE, it.uninafoodlab.domain.catalog.MeasurementUnit.TEASPOON))
                .isEqualTo("1 cucchiaino");
        assertThat(QuantityFormat.quantity(new BigDecimal("0.5"), it.uninafoodlab.domain.catalog.MeasurementUnit.TEASPOON))
                .isEqualTo("0,5 cucchiaini");
    }

    @Test
    void showsItalianDecimalsWithoutDatabasePaddingOrRounding() {
        assertThat(QuantityFormat.format(new BigDecimal("250.000"))).isEqualTo("250");
        assertThat(QuantityFormat.format(new BigDecimal("1.500"))).isEqualTo("1,5");
        assertThat(QuantityFormat.format(new BigDecimal("0.125"))).isEqualTo("0,125");
        assertThat(QuantityFormat.format(new BigDecimal("1000.000"))).isEqualTo("1000");
    }
}
