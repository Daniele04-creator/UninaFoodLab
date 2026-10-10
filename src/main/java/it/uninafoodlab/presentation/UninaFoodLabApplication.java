package it.uninafoodlab.presentation;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import it.uninafoodlab.infrastructure.config.SystemEnvironment;
import it.uninafoodlab.presentation.navigation.SceneNavigator;
import javafx.application.Application;
import javafx.stage.Stage;

public final class UninaFoodLabApplication extends Application {

    private ApplicationRuntime applicationRuntime;

    @Override
    public void init() {
        var applicationConfig = ApplicationConfig.from(SystemEnvironment.INSTANCE);
        applicationRuntime = ApplicationRuntime.start(applicationConfig);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("UninaFoodLab");
        stage.getIcons().setAll(it.uninafoodlab.presentation.common.LabBrand.windowIcons());
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        new SceneNavigator(stage, applicationRuntime).showLogin();
    }

    @Override
    public void stop() {
        if (applicationRuntime != null) {
            applicationRuntime.close();
        }
    }
}
