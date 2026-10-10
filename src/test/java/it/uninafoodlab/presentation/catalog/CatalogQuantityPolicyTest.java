package it.uninafoodlab.presentation.catalog;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CatalogQuantityPolicyTest {
    @Test void acceptsDecimalPersonDosesAndTemporaryEmptyEdits() {
        assertThat(CatalogQuantityPolicy.acceptsEdit("")).isTrue();
        assertThat(CatalogQuantityPolicy.acceptsEdit("125")).isTrue();
        assertThat(CatalogQuantityPolicy.parse("125")).isEqualByComparingTo("125");
        assertThat(CatalogQuantityPolicy.acceptsEdit("12,")).isTrue();
        assertThat(CatalogQuantityPolicy.parse("12,125")).isEqualByComparingTo("12.125");
        assertThat(CatalogQuantityPolicy.parse("0.5")).isEqualByComparingTo("0.5");
        assertThat(CatalogQuantityPolicy.parse("0,001")).isEqualByComparingTo("0.001");
    }

    @Test void preventsUnrepresentableQuantitiesWithoutReplacingTheSavedValue() {
        for (String text : new String[]{"-1", "abc", "1e3", "1.2345", "1000000000000"}) {
            assertThat(CatalogQuantityPolicy.acceptsEdit(text)).as(text).isFalse();
        }
        for (String text : new String[]{"", "0", "0,000"}) {
            assertThatThrownBy(() -> CatalogQuantityPolicy.parse(text)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(CatalogQuantityPolicy.acceptsEdit("999999999999")).isTrue();
        assertThat(CatalogQuantityPolicy.parse("999999999999")).isEqualByComparingTo("999999999999");
    }
}
