package it.unina.foodlab.util;

import it.unina.foodlab.controller.LoginController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/LoginFrame.fxml"));
        Parent root = loader.load();

        LoginController controller = loader.getController();
        controller.setStage(stage);

        stage.setTitle("UninaFoodLab - Chef Login");
        stage.setScene(new Scene(root, 700, 540));
        stage.show();

        controller.requestInitialFocus();
    }

    public static void main(String[] args) { launch(args); }
}