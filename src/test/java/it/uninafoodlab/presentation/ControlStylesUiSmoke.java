package it.uninafoodlab.presentation;

import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;


public final class ControlStylesUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var task = new FutureTask<Void>(() -> {
                var save = new Button("Salva"); save.getStyleClass().add("button-primary");
                var recipes = new ToggleButton("Ricette"); recipes.getStyleClass().add("catalog-tab"); recipes.setSelected(true);
                var tabs = new HBox(recipes); tabs.getStyleClass().add("catalog-workspace");
                var cancel = new Button("Annulla"); cancel.getStyleClass().add("button-secondary");
                var field = new TextField("Nome ricetta");
                var date = new DatePicker(java.time.LocalDate.now().plusDays(2)); date.setEditable(false);
                var spinner = new Spinner<Integer>(1, 30, 2);
                var choices = new ComboBox<String>(); choices.getItems().add("Pratica"); choices.getSelectionModel().selectFirst();
                var list = new ListView<String>(); list.getItems().add("Ricetta"); list.setPrefHeight(80);
                List<Control> controls = List.of(save, cancel, field, date, spinner, choices, list);
                var root = new VBox(14); root.getStyleClass().add("workspace-page");
                root.getChildren().addAll(controls); root.getChildren().add(tabs); root.setStyle("-fx-padding: 24;");
                var scene = new Scene(root, 700, 650);
                scene.getStylesheets().add(ControlStylesUiSmoke.class.getResource("styles/application.css").toExternalForm());
                scene.getStylesheets().add(ControlStylesUiSmoke.class.getResource("catalog/catalog-workspace.css").toExternalForm());
                var stage = new Stage(); stage.setScene(scene); stage.show();
                for (var control : controls) {
                    control.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
                    control.pseudoClassStateChanged(PseudoClass.getPseudoClass("focus-visible"), true);
                    root.applyCss(); root.layout();
                    for (var stroke : control.getBorder().getStrokes()) {
                        var inset = stroke.getInsets();
                        if (Math.min(Math.min(inset.getTop(), inset.getRight()), Math.min(inset.getBottom(), inset.getLeft())) < 0) {
                            throw new AssertionError(control.getClass().getSimpleName() + " paints its focus border outside layout bounds");
                        }
                    }
                }
                recipes.pseudoClassStateChanged(PseudoClass.getPseudoClass("focus-visible"), true);
                root.applyCss(); root.layout();
                checkFocusColor(save, Color.web("#173946"), "primary action");
                checkFocusColor(recipes, Color.web("#173946"), "selected catalog tab");
                checkFill(root, Color.web("#FAFAF8"), "workspace backdrop");
                if (!save.getFont().getFamily().equals("Manrope")) {
                    throw new AssertionError("The bundled Manrope font must load without network access");
                }
                checkClear(date.lookup(".text-field"), "DatePicker editor");
                checkClear(date.lookup(".arrow-button"), "DatePicker calendar button");
                checkClear(choices.lookup(".list-cell"), "ComboBox value");
                checkClear(choices.lookup(".arrow-button"), "ComboBox arrow button");
                checkClear(spinner.lookup(".text-field"), "Spinner editor");
                checkClear(spinner.lookup(".increment-arrow-button"), "Spinner increment button");
                checkClear(spinner.lookup(".decrement-arrow-button"), "Spinner decrement button");
                FxSnapshot.save(root, "target/ui-polish/shared-control-focus.png");
                date.show();
                var popup = Window.getWindows().stream().filter(window -> window != stage && window.isShowing())
                        .findFirst().orElseThrow(() -> new AssertionError("DatePicker calendar did not open"));
                popup.getScene().getRoot().applyCss();
                popup.getScene().getRoot().layout();
                FxSnapshot.save(popup.getScene().getRoot(), "target/ui-polish/shared-calendar-popup.png");
                var selectedDay = popup.getScene().getRoot().lookupAll(".day-cell").stream()
                        .filter(node -> node.getStyleClass().contains("selected"))
                        .findFirst().orElseThrow(() -> new AssertionError("Selected calendar day not found"));
                selectedDay.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
                popup.getScene().getRoot().applyCss();
                checkFocusColors((Region) selectedDay, "selected calendar day");
                selectedDay.getStyleClass().add("today");
                popup.getScene().getRoot().applyCss();
                checkFocusColors((Region) selectedDay, "selected calendar day that is today");
                date.hide();
                checkItalianDialogButtons(stage);
                stage.hide();
                checkWorkspaceControls();
                return null;
            });
            Platform.runLater(task); task.get(20, TimeUnit.SECONDS);
            System.out.println("PASS: focused shared controls have one surface and borders inside layout bounds");
        } finally { Platform.exit(); }
    }

    private static void checkItalianDialogButtons(Stage owner) {
        var dialog = new Dialog<Void>();
        dialog.setTitle("Operazione non completata");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        it.uninafoodlab.presentation.common.DialogTheme.apply(dialog, owner);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.show();
        if (!"Annulla".equals(((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).getText())
                || !"Chiudi".equals(((Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).getText())) {
            throw new AssertionError("Dialog buttons must be Italian before and after applying the shared theme");
        }
        dialog.close();
    }

    private static void checkWorkspaceControls() throws java.io.IOException {
        var active = new ToggleButton("Attivi"); active.setSelected(true);
        var past = new ToggleButton("Passati");
        var all = new ToggleButton("Tutti");
        for (var tab : List.of(active, past, all)) tab.getStyleClass().add("course-catalog-tab");
        var menu = new ToggleButton("Menu"); menu.setSelected(true);
        var attendance = new ToggleButton("Adesioni");
        for (var tab : List.of(menu, attendance)) tab.getStyleClass().add("course-section-tab");
        var open = new Button("Apri corso"); open.getStyleClass().add("button-secondary");
        var card = new VBox(open); card.getStyleClass().add("course-catalog-card");
        var add = new Button("Aggiungi sessione"); add.getStyleClass().add("course-add-session-button");
        var cancel = new Button("Annulla creazione");
        cancel.getStyleClass().addAll("course-create-action", "button-secondary");
        var chef = new VBox(12, new HBox(8, active, past, all), new HBox(8, menu, attendance), card, add, cancel);
        chef.getStyleClass().add("course-chef-workspace");
        var available = new ToggleButton("Corsi disponibili"); available.setSelected(true);
        var mine = new ToggleButton("I miei corsi");
        var participant = new HBox(8, available, mine); participant.getStyleClass().add("participant-topbar");
        var field = new TextField();
        var choice = new ComboBox<String>(); choice.getItems().add("Categoria"); choice.getSelectionModel().selectFirst();
        var date = new DatePicker(java.time.LocalDate.of(2026, 10, 1));
        var quantity = new Spinner<Integer>(1, 30, 2);
        var inputs = new HBox(8, field, choice, date, quantity);
        var root = new VBox(16, chef, participant, inputs); root.getStyleClass().add("workspace-page");
        var scene = new Scene(root, 1100, 700);
        for (var stylesheet : List.of("styles/application.css", "course/course-workspace.css", "participant/participant.css")) {
            scene.getStylesheets().add(ControlStylesUiSmoke.class.getResource(stylesheet).toExternalForm());
        }
        var stage = new Stage(); stage.setScene(scene); stage.show(); root.applyCss(); root.layout();
        try {
            for (var control : List.of(active, past, all, menu, attendance, open, add, cancel, available, mine)) {
                control.requestFocus();
                control.pseudoClassStateChanged(PseudoClass.getPseudoClass("focus-visible"), true);
                root.applyCss(); root.layout();
                checkFocusColor(control, Color.web("#173946"), control.getText());
                if (control.getBorder().getStrokes().stream().noneMatch(stroke -> stroke.getWidths().getTop() >= 2)) {
                    throw new AssertionError(control.getText() + " needs a visible focus outline, not only the selection underline");
                }
                if (control == past) FxSnapshot.save(root, "target/ui-polish/course-filter-keyboard-focus.png");
                control.pseudoClassStateChanged(PseudoClass.getPseudoClass("focus-visible"), false);
            }
            for (var input : List.of(field, choice, date, quantity)) {
                input.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), false);
            }
            root.applyCss(); root.layout();
            for (var input : List.of(field, choice, date, quantity)) {
                var border = input.getBorder().getStrokes().getFirst().getTopStroke();
                if (!(border instanceof Color color) || contrast(color, Color.WHITE) < 3
                        || contrast(color, Color.web("#FAFAF8")) < 3) {
                    throw new AssertionError(input.getClass().getSimpleName() + " must have a distinguishable unfocused boundary");
                }
            }
            FxSnapshot.save(root, "target/ui-polish/workspace-control-boundaries.png");
        } finally {
            stage.close();
        }
    }

    private static double contrast(Color first, Color second) {
        var firstLuminance = luminance(first);
        var secondLuminance = luminance(second);
        return (Math.max(firstLuminance, secondLuminance) + 0.05) / (Math.min(firstLuminance, secondLuminance) + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen()) + 0.0722 * linear(color.getBlue());
    }

    private static double linear(double channel) {
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static void checkFill(Region region, Color expected, String name) {
        if (region.getBackground() == null || region.getBackground().getFills().stream()
                .noneMatch(fill -> expected.equals(fill.getFill()))) {
            throw new AssertionError(name + " should use " + expected);
        }
    }

    private static void checkClear(javafx.scene.Node node, String name) {
        if (!(node instanceof Region region)) throw new AssertionError(name + " not found");
        if (region.getBackground() != null && region.getBackground().getFills().stream().anyMatch(fill ->
                !(fill.getFill() instanceof Color color) || color.getOpacity() > 0)) {
            throw new AssertionError(name + " paints a second internal surface");
        }
    }

    private static void checkFocusColor(Region region, Color expected, String name) {
        if (region.getBorder() == null || region.getBorder().getStrokes().stream().noneMatch(stroke ->
                expected.equals(stroke.getTopStroke()))) {
            throw new AssertionError(name + " has no distinct focus border " + expected);
        }
    }

    private static void checkFocusColors(Region region, String name) {
        checkFocusColor(region, Color.web("#173946"), name);
        checkFocusColor(region, Color.WHITE, name);
    }
}
