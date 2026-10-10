package it.uninafoodlab.presentation.catalog;

import java.math.BigDecimal;


final class CatalogQuantityPolicy {
    private CatalogQuantityPolicy() {}

    static boolean acceptsEdit(String value) {
        return value.isEmpty() || value.matches("\\d{1,12}([,.]\\d{0,3})?");
    }

    static BigDecimal parse(String value) {
        if (!acceptsEdit(value) || value.isEmpty()) throw new IllegalArgumentException("Inserisci una quantità positiva con al massimo 3 decimali per una persona.");
        var amount = new BigDecimal(value.replace(',', '.'));
        if (amount.signum() <= 0) throw new IllegalArgumentException("La quantità deve essere maggiore di zero.");
        return amount;
    }
}
