package it.unina.foodlab.ui;

import it.unina.foodlab.dao.ChefDao;
import it.unina.foodlab.dao.CorsoDao;
import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Chef;
import it.unina.foodlab.util.AppSession;
import javafx.application.Application;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class LoginFrame extends Application {

  @Override
  public void start(Stage stage) {
    // --- Campi ---
    TextField user = new TextField();
    user.setPromptText("Username");
    PasswordField pass = new PasswordField();
    pass.setPromptText("Password");

    Button btn = new Button("Login");
    btn.setDefaultButton(true);
    btn.getStyleClass().add("primary"); // stile moderno (dark)

    // Larghezze coerenti e centrabili
    final double FIELD_W = 260;
    user.setPrefWidth(FIELD_W);
    pass.setPrefWidth(FIELD_W);
    btn.setPrefWidth(FIELD_W);

    

    GridPane form = new GridPane();
    form.setHgap(12);
    form.setVgap(12);
    form.setPadding(new Insets(4));
    form.setAlignment(Pos.CENTER); // <— centra il contenuto del GridPane

    form.add(user, 0, 1);
    form.add(pass, 0, 3);

    // Centra ogni nodo nella sua cella
    
    GridPane.setHalignment(user, HPos.CENTER);
    GridPane.setHalignment(pass, HPos.CENTER);

    // Azioni: bottone centrato
    HBox actions = new HBox(btn);
    actions.setAlignment(Pos.CENTER);     // <— centrato
    actions.setPadding(new Insets(8, 0, 0, 0));
    form.add(actions, 0, 4);

    // --- Card scura centrata ---
    VBox card = new VBox(form);
    card.setSpacing(12);
    card.getStyleClass().add("card");
    card.setMaxWidth(380);

    StackPane root = new StackPane(card); // interfaccia sempre centrata
    StackPane.setAlignment(card, Pos.CENTER);
    root.setPadding(new Insets(24));

    // --- Eventi ---
    btn.setOnAction(e -> doLogin(stage, user.getText().trim(), pass.getText()));
    pass.setOnKeyPressed(ke -> {
      if (ke.getCode() == KeyCode.ENTER) {
        doLogin(stage, user.getText().trim(), pass.getText());
      }
    });

    // --- Scene + CSS dark ---
    Scene scene = new Scene(root, 460, 460);
    scene.getStylesheets().add(LoginFrame.class.getResource("/app.css").toExternalForm());

    stage.setTitle("UninaFoodLab - Chef Login");
    stage.setScene(scene);
    stage.show();

    user.requestFocus();
  }

  private void doLogin(Stage stage, String u, String p) {
    try {
      // 1) Autenticazione
      ChefDao chefDao = new ChefDao();
      if (!chefDao.authenticate(u, p)) {
        new Alert(Alert.AlertType.ERROR, "Credenziali errate").showAndWait();
        return;
      }

      // 2) Recupero Chef
      Chef chef = chefDao.findByUsername(u);
      if (chef == null || chef.getCF_Chef() == null || chef.getCF_Chef().isBlank()) {
        new Alert(Alert.AlertType.ERROR, "Chef non trovato dopo l'autenticazione.").showAndWait();
        return;
      }
      String cfChef = chef.getCF_Chef().trim();

      // 3) Sessione applicativa
      AppSession.setCfChef(cfChef);

      // 4) DAO per lo chef
      CorsoDao corsoDao = new CorsoDao(cfChef);
      SessioneDao sessioneDao = new SessioneDao(cfChef);

      // 5) Apri schermata corsi
      CorsiPanel root = new CorsiPanel(corsoDao, sessioneDao);
      Scene scene = new Scene(root, 1000, 640);
      scene.getStylesheets().add(LoginFrame.class.getResource("/app.css").toExternalForm());

      stage.setTitle("UninaFoodLab - Corsi di " + cfChef);
      stage.setScene(scene);
      stage.show();

    } catch (Exception ex) {
      ex.printStackTrace();
      Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
      alert.setHeaderText("Errore");
      alert.showAndWait();
    }
  }

  public static void main(String[] args) {
    launch(args);
  }
}