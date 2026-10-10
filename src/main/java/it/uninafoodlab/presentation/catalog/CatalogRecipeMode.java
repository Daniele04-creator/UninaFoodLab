package it.uninafoodlab.presentation.catalog;


final class CatalogRecipeMode {
    private boolean editing;

    void open() { editing = false; }
    void edit() { editing = true; }
    void create() { editing = true; }
    boolean editing() { return editing; }
}
