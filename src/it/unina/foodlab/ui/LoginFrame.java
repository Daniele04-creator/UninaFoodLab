package it.unina.foodlab.ui;

import it.unina.foodlab.dao.ChefDao;
import it.unina.foodlab.dao.CorsoDao;
import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Chef;
import it.unina.foodlab.util.AppSession;
import javafx.animation.FadeTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.util.Duration;

public class LoginFrame extends Application {

    @Override
    public void start(Stage stage) {
        // --- CAMPI ---
        TextField user = new TextField(); user.setPromptText("Username");
        PasswordField pass = new PasswordField(); pass.setPromptText("Password");
        TextField passVisible = new TextField(); passVisible.setPromptText("Password");
        passVisible.textProperty().bindBidirectional(pass.textProperty());
        passVisible.setManaged(false); passVisible.setVisible(false);

        // Icone + occhio (senza sfondo dietro)
        SVGPath icUser = iconUser(), icLock = iconLock();
        ToggleButton eye = new ToggleButton();
        eye.setGraphic(iconEye(false));
        eye.setCursor(Cursor.HAND);
        eye.setBackground(Background.EMPTY);
        eye.setBorder(Border.EMPTY);
        eye.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-background-insets: 0;" +
            "-fx-border-color: transparent;" +
            "-fx-padding: 0 6 0 6;" +
            "-fx-focus-color: transparent;" +
            "-fx-faint-focus-color: transparent;"
        );
        eye.setOnAction(e -> {
            boolean show = eye.isSelected();
            eye.setGraphic(iconEye(show));
            pass.setVisible(!show); pass.setManaged(!show);
            passVisible.setVisible(show); passVisible.setManaged(show);
            (show ? passVisible : pass).requestFocus();
            (show ? passVisible : pass).end();
        });

        // Righe input (opache, squadrate)
        HBox userRow = inputRow(icUser, user, null);
        HBox passRow = inputRow(icLock, new StackPane(pass, passVisible), eye);

        // Errore
        Label error = new Label(); error.getStyleClass().add("error-chip");
        error.setManaged(false); error.setVisible(false);

        // Bottone + spinner (squadrato)
        Button btn = new Button("Login"); btn.setDefaultButton(true);
        btn.setStyle(
            "-fx-background-color: linear-gradient(#3b82f6, #2563eb);" +
            "-fx-text-fill: white;" +
            "-fx-font-weight: 600;" +
            "-fx-padding: 10 16;" +
            "-fx-background-radius: 0;" +
            "-fx-border-radius: 0;"
        );
        ProgressIndicator spinner = new ProgressIndicator(); spinner.setMaxSize(16,16); spinner.setVisible(false);
        StackPane btnWrap = new StackPane(btn, spinner);
        StackPane.setAlignment(spinner, Pos.CENTER_RIGHT);
        StackPane.setMargin(spinner, new Insets(0, 12, 0, 0));

        // Card OPACA + BORDI QUADRI
        VBox form = new VBox(16, userRow, passRow, error, btnWrap); form.setFillWidth(true);
        VBox card = new VBox(8, titleLabel("UninaFoodLab"), subtitleLabel("Chef Login"), form);
        card.setPadding(new Insets(28));
        card.setMaxWidth(420);
        card.setStyle(
            "-fx-background-color: #0f131a;" +   // opaca
            "-fx-background-radius: 0;" +        // squadrata
            "-fx-border-color: #354155;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 0;"
        );

        // LAYER con pattern (non è root scena)
        StackPane backdrop = new StackPane();
        setPatternBackground(backdrop, "/login-pattern.png"); // pattern tiled

        Region scrim = new Region();
        scrim.setStyle("-fx-background-color: rgba(0,0,0,0.25);");
        scrim.setPickOnBounds(false);

        backdrop.getChildren().addAll(scrim, card);
        StackPane.setAlignment(card, Pos.CENTER);
        backdrop.setPadding(new Insets(24));

        // Root scena “vuoto” (eventuali regole .root del tuo CSS non toccano il pattern)
        StackPane outer = new StackPane(backdrop);

        // Azioni
        Runnable doLogin = () -> {
            String u = user.getText().trim();
            String p = (pass.isVisible() ? pass.getText() : passVisible.getText());
            if (u.isEmpty() || p.isEmpty()) { showError(error, "Inserisci username e password"); shake(card); return; }
            setBusy(true, btn, spinner);
            try { doLogin(stage, u, p); }
            catch (Exception ex) { showError(error, ex.getMessage() == null ? "Login fallito" : ex.getMessage()); shake(card); }
            finally { setBusy(false, btn, spinner); }
        };
        btn.setOnAction(e -> doLogin.run());
        user.setOnAction(e -> (pass.isVisible() ? pass : passVisible).requestFocus());
        pass.setOnAction(e -> doLogin.run());
        passVisible.setOnAction(e -> doLogin.run());

        // Scena + CSS
        Scene scene = new Scene(outer, 700, 540);
        
        outer.setStyle(outer.getStyle() + "; -fx-font-size: 14px; -fx-font-family: 'Segoe UI', Inter, system-ui, sans-serif;");

        stage.setTitle("UninaFoodLab - Chef Login");
        stage.setScene(scene);
        stage.show();
        user.requestFocus();
    }

    /* ================== LOGICA INVARIATA ================== */

    private void doLogin(Stage stage, String u, String p) {
        try {
            ChefDao chefDao = new ChefDao();
            if (!chefDao.authenticate(u, p)) throw new RuntimeException("Credenziali errate");

            Chef chef = chefDao.findByUsername(u);
            if (chef == null || chef.getCF_Chef() == null || chef.getCF_Chef().isBlank())
                throw new RuntimeException("Chef non trovato dopo l'autenticazione.");
            String cfChef = chef.getCF_Chef().trim();

            AppSession.setCfChef(cfChef);

            CorsoDao corsoDao = new CorsoDao(cfChef);
            SessioneDao sessioneDao = new SessioneDao(cfChef);

            CorsiPanel root = new CorsiPanel(corsoDao, sessioneDao);
            Scene scene = new Scene(root, 1000, 640);
            
            stage.setTitle("UninaFoodLab - Corsi di " +
                (chef.getNome() != null ? chef.getNome() + " " + chef.getCognome() : cfChef));
            stage.setScene(scene);
            stage.show();

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void main(String[] args) { launch(args); }

    /* ================== HELPER GRAFICI ================== */

    /** Background: tinta + immagine ripetuta. */
    private static void setPatternBackground(Region pane, String resourcePath) {
        var url = LoginFrame.class.getResource(resourcePath);
        if (url != null) {
            Image img = new Image(url.toExternalForm()); // dimensione nativa
            BackgroundFill fill = new BackgroundFill(Color.web("#0f131a"), CornerRadii.EMPTY, Insets.EMPTY);
            BackgroundImage bgImg = new BackgroundImage(
                img, BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT,
                BackgroundPosition.DEFAULT, BackgroundSize.DEFAULT
            );
            pane.setBackground(new Background(new BackgroundFill[]{fill}, new BackgroundImage[]{bgImg}));
        } else {
            pane.setBackground(new Background(new BackgroundFill(Color.web("#0f131a"), CornerRadii.EMPTY, Insets.EMPTY)));
            System.err.println("Pattern non trovato: " + resourcePath);
        }
    }

    private static Label titleLabel(String s) {
        Label l = new Label(s);
        l.setStyle("-fx-font-size: 22px; -fx-font-weight: 800; -fx-text-fill: white;");
        return l;
    }
    private static Label subtitleLabel(String s) {
        Label l = new Label(s);
        l.setStyle("-fx-font-size: 14px; -fx-text-fill: rgba(255,255,255,0.75);");
        return l;
    }

    /** Riga input opaca e squadrata, con icona a sinistra e trailing (es. occhio) a destra. */
    private static HBox inputRow(SVGPath icon, Node center, Node trailing) {
        StackPane icoWrap = new StackPane(icon);
        icoWrap.setMinSize(36,36);
        icoWrap.setMaxSize(36,36);
        icoWrap.setStyle("-fx-background-color: transparent;"); // niente bolla dietro l’icona

        // stile dei campi: opachi, squadrati
        String tfStyle =
            "-fx-background-color: transparent;" +  // lo sfondo viene dal contenitore riga
            "-fx-text-fill: white;" +
            "-fx-prompt-text-fill: rgba(255,255,255,0.65);" +
            "-fx-border-color: transparent;" +
            "-fx-focus-color: #4f46e5;" +
            "-fx-faint-focus-color: transparent;" +
            "-fx-background-radius: 0;" +
            "-fx-border-radius: 0;";

        if (center instanceof Region r) r.setStyle(tfStyle);
        if (center instanceof StackPane sp) {
            sp.getChildren().forEach(n -> { if (n instanceof Region rr) rr.setStyle(tfStyle); });
        }

        HBox.setHgrow(center, Priority.ALWAYS);

        HBox row = new HBox(10, icoWrap, center);
        if (trailing != null) {
            Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().addAll(spacer, trailing);
        }
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8));
        row.setStyle(
            "-fx-background-color: #151b24;" +   // opaco
            "-fx-background-radius: 0;" +        // squadrato
            "-fx-border-color: #2a3546;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 0;"
        );
        return row;
    }

    private static SVGPath iconUser() {
        SVGPath p = new SVGPath();
        p.setContent("M12 12c2.761 0 5-2.239 5-5s-2.239-5-5-5-5 2.239-5 5 2.239 5 5 5zm0 2c-3.866 0-7 3.134-7 7h2c0-2.761 2.239-5 5-5s5 2.239 5 5h2c0-3.866-3.134-7-7-7z");
        p.setFill(Color.web("#b9c2d0"));
        return p;
    }
    private static SVGPath iconLock() {
        SVGPath p = new SVGPath();
        p.setContent("M6 8V6a6 6 0 1 1 12 0v2h1a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1h1zm2 0h8V6a4 4 0 0 0-8 0v2z");
        p.setFill(Color.web("#b9c2d0"));
        return p;
    }
    private static SVGPath iconEye(boolean open) {
        SVGPath p = new SVGPath();
        if (open) {
            p.setContent("M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12zm11 4a4 4 0 1 0 0-8 4 4 0 0 0 0 8z");
        } else {
            p.setContent("M2 5l19 14-1.5 2L.5 7 2 5zm3.3 2.4C7.7 6.2 9.7 5 12 5c7 0 11 7 11 7-.7 1.1-1.7 2.3-3 3.3L18.6 13c.3-.6.4-1.2.4-1.9a5 5 0 0 0-5-5c-.7 0-1.3.1-1.9.4L5.3 7.4z");
        }
        p.setFill(Color.web("#c8d0dc"));
        p.setScaleX(0.9); p.setScaleY(0.9);
        return p;
    }

    private static void showError(Label lbl, String msg) {
        lbl.setText(msg);
        lbl.setManaged(true);
        lbl.setVisible(true);
        lbl.setStyle("-fx-text-fill: #ff6b6b; -fx-background-color: rgba(255,107,107,0.12); -fx-padding: 8 12; -fx-background-radius: 10;");
        FadeTransition ft = new FadeTransition(Duration.millis(180), lbl);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    private static void shake(Node n) {
        TranslateTransition t1 = new TranslateTransition(Duration.millis(60), n);
        t1.setFromX(0); t1.setToX(-8);
        TranslateTransition t2 = new TranslateTransition(Duration.millis(60), n);
        t2.setFromX(-8); t2.setToX(8);
        TranslateTransition t3 = new TranslateTransition(Duration.millis(60), n);
        t3.setFromX(8); t3.setToX(-4);
        TranslateTransition t4 = new TranslateTransition(Duration.millis(60), n);
        t4.setFromX(-4); t4.setToX(0);
        new SequentialTransition(t1,t2,t3,t4).play();
    }

    private static void setBusy(boolean busy, Button btn, ProgressIndicator spinner) {
        btn.setDisable(busy);
        spinner.setVisible(busy);
        btn.setText(busy ? "Verifica..." : "Login");
    }
}
