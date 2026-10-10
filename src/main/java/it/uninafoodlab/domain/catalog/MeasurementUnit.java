package it.uninafoodlab.domain.catalog;

public enum MeasurementUnit {
    GRAM("g"),
    KILOGRAM("kg"),
    MILLILITER("ml"),
    LITER("l"),
    PIECE("pz"),
    TEASPOON("cucchiaini");

    private final String symbol;

    MeasurementUnit(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
