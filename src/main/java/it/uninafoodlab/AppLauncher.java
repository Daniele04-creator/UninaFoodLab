package it.uninafoodlab;

import it.uninafoodlab.presentation.UninaFoodLabApplication;
import javafx.application.Application;

public final class AppLauncher {

    private AppLauncher() {}

    public static void main(String[] args) {
        Application.launch(UninaFoodLabApplication.class, args);
    }
}
