package it.uninafoodlab.presentation.common;

public interface WorkspaceState {
    java.util.function.BooleanSupplier CLEAN = () -> false;

    boolean hasUnsavedChanges();
    boolean isBusy();
    default void dispose() {}
}
