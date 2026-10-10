package it.uninafoodlab.presentation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;


public final class DesignAccessibilityUiSmoke {
    private static final double TEXT_MIN = 4.5;
    private static final double EDGE_MIN = 3;
    private static final double TARGET_MIN = 32;
    private static final List<String> failures = new ArrayList<>();
    private static final List<String> evidence = new ArrayList<>();
    private static int textChecks, edgeChecks, targetChecks, fontChecks;

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var task = new FutureTask<Void>(() -> {
                inspect("shared", "workspace-page", null,
                        new String[] {"page-title", "page-description", "form-helper", "course-feedback-info",
                                "course-feedback-success", "course-feedback-error", "result-page-range"},
                        new String[] {"button-primary", "button-secondary", "button-danger", "button-quiet", "navigation-button"});
                inspect("course", "course-chef-workspace", "course/course-workspace.css",
                        new String[] {"course-card-title", "course-card-category", "course-card-facts", "course-card-mode",
                                "course-card-badge course-card-planned", "course-card-badge course-card-active", "course-card-badge course-card-concluded",
                                "course-card-badge course-card-cancelled", "course-session-state session-status-pending",
                                "course-session-state session-status-confirmed", "course-session-state session-status-cancelled",
                                "course-session-menu-count recipe-assignment-missing"},
                        new String[] {"course-catalog-tab", "course-section-tab", "course-add-session-button", "course-create-action button-secondary"});
                inspect("catalog", "catalog-workspace", "catalog/catalog-workspace.css",
                        new String[] {"catalog-recipe-title", "catalog-recipe-meta", "cookbook-entry-description",
                                "cookbook-entry-count", "catalog-preparation", "catalog-dose-table-heading"},
                        new String[] {"catalog-tab", "cookbook-category-action", "cookbook-open-action", "button-secondary catalog-ingredient-action"});
                inspect("notification", "notification-workspace", "notification/notification-workspace.css",
                        new String[] {"notification-row-title", "notification-row-course", "notification-row-date",
                                "notification-card-message", "notification-detail-title", "notification-detail-message", "notification-field-error"},
                        new String[] {"notification-open", "notification-back", "button-primary"});
                inspect("participant", "participant-shell", "participant/participant.css",
                        new String[] {"participant-card-title", "participant-card-meta", "participant-category", "participant-mode",
                                "participant-enrollment-state participant-enrollment-planned", "participant-enrollment-state participant-enrollment-active",
                                "participant-enrollment-state participant-enrollment-concluded", "participant-session-state participant-session-cancelled",
                                "participant-response-status participant-response-confirmed", "participant-message-state"},
                        new String[] {"course-catalog-tab", "button-primary", "button-secondary", "button-danger"});
                inspect("report", "mock-planning", "planning/planning-workspace.css",
                        new String[] {"report-summary-value", "ops-muted", "report-session-title", "report-session-mode",
                                "report-session-state report-session-neutral", "report-session-state report-session-cancelled", "ops-bar-value"},
                        new String[] {"ops-scope-tab", "ops-segment", "lab-text-action report-month-arrow", "button-secondary"});
                inspect("dialog", "studio-dialog", "styles/dialogs.css",
                        new String[] {"recipe-dialog-heading", "recipe-dialog-preparation", "account-dialog-validation"},
                        new String[] {"button-primary", "button-secondary", "button-danger"});
                Files.createDirectories(Path.of("target/ui-quality"));
                String summary = "text=" + textChecks + "; edges=" + edgeChecks + "; targets=" + targetChecks
                        + "; fonts=" + fontChecks + "; failures=" + failures.size();
                evidence.add(summary);
                Files.write(Path.of("target/ui-quality/design-accessibility.txt"), evidence);
                System.out.println(summary);
                if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
                return null;
            });
            Platform.runLater(task);
            task.get(60, TimeUnit.SECONDS);
            System.out.println("PASS: real CSS text >=4.5, active edges/focus >=3, targets >=32, local typefaces");
        } finally { Platform.exit(); }
    }

    private static void inspect(String family, String rootClass, String css, String[] labels, String[] buttons) throws Exception {
        var root = new VBox(12);
        root.getStyleClass().addAll("workspace-page", rootClass);
        root.setStyle("-fx-padding: 24;");
        var heading = new Label("UninaFoodLab — " + family);
        heading.getStyleClass().add("page-title");
        var textSamples = new FlowPane(14, 10);
        if (family.equals("participant")) textSamples.getStyleClass().add("participant-course-card");
        var actions = new FlowPane(14, 10);
        root.getChildren().addAll(heading, textSamples, actions);
        var sampleLabels = new ArrayList<Labeled>();
        sampleLabels.add(heading);
        for (String classes : labels) {
            var sample = new Label(labelText(classes));
            if (classes.equals("catalog-dose-table-heading")) {
                var tableHeading = new HBox(sample);
                tableHeading.getStyleClass().add(classes);
                textSamples.getChildren().add(tableHeading);
            } else if (family.equals("course") && classes.startsWith("course-card-")) {
                sample.getStyleClass().addAll(classes.split(" "));
                var card = new VBox(sample); card.getStyleClass().add("course-catalog-card");
                textSamples.getChildren().add(card);
            } else {
                sample.getStyleClass().addAll(classes.split(" "));
                textSamples.getChildren().add(sample);
            }
            sampleLabels.add(sample);
        }
        var controls = new ArrayList<Control>();
        for (String classes : buttons) {
            boolean toggle = classes.contains("tab") || classes.contains("segment") || classes.contains("category-action");
            Labeled button = toggle ? new ToggleButton("Corsi e ricette") : new Button("Salva ricetta");
            button.getStyleClass().addAll(classes.split(" "));
            if (classes.equals("cookbook-category-action")) {
                button.setText("");
                var title = new Label("Cucina mediterranea"); title.getStyleClass().add("cookbook-category-name");
                button.setGraphic(new HBox(8, title, new Label("12")));
            }
            if (family.equals("participant") && classes.equals("course-catalog-tab")) {
                var topbar = new HBox(button); topbar.getStyleClass().add("participant-topbar"); actions.getChildren().add(topbar);
            } else if (classes.equals("navigation-button")) {
                var navigation = new HBox(button); navigation.getStyleClass().add("workbench-navigation"); actions.getChildren().add(navigation);
            } else actions.getChildren().add(button);
            controls.add(button);
        }
        var field = new TextField("Pomodori maturi");
        var choices = new ComboBox<String>(); choices.getItems().add("Cucina mediterranea"); choices.getSelectionModel().selectFirst();
        var date = new DatePicker(java.time.LocalDate.of(2026, 10, 2));
        var quantity = new Spinner<Integer>(1, 30, 2);
        var inputs = new FlowPane(14, 10, field, choices, date, quantity);
        root.getChildren().add(inputs);
        controls.addAll(List.of(field, choices, date, quantity));
        var scene = new Scene(root, 1150, 650);
        scene.getStylesheets().add(resource("styles/application.css"));
        if (css != null) scene.getStylesheets().add(resource(css));
        var stage = new Stage(); stage.setTitle("Design gate — " + family); stage.setScene(scene); stage.show();
        try {
            root.applyCss(); root.layout();
            for (var sample : sampleLabels) checkText(sample, family + "/" + sample.getStyleClass(), "normal");
            for (var control : controls) {
                String name = family + "/" + control.getStyleClass();
                checkTarget(control, name);
                for (String state : List.of("normal", "hover", "pressed", "selected", "disabled")) {
                    boolean navigation = control.getStyleClass().contains("navigation-button");
                    if (state.equals("selected") && !(control instanceof ToggleButton) && !navigation) continue;
                    if (navigation) {
                        control.getStyleClass().remove("navigation-button-selected");
                        if (state.equals("selected")) control.getStyleClass().add("navigation-button-selected");
                    }
                    control.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), state.equals("hover"));
                    control.pseudoClassStateChanged(PseudoClass.getPseudoClass("pressed"), state.equals("pressed"));
                    if (control instanceof ToggleButton toggle) toggle.setSelected(state.equals("selected"));
                    control.setDisable(state.equals("disabled"));
                    root.applyCss(); root.layout();
                    if (control instanceof Labeled labeled) {
                        if (!labeled.getText().isEmpty()) checkText(labeled, name, state);
                        if (labeled.getGraphic() instanceof HBox graphic) for (Node child : graphic.getChildren())
                            if (child instanceof Label label) checkText(label, name + "/graphic", state);
                    }
                    else if (control instanceof TextField input) checkInput(input, name, state);
                    else if (control.lookup(".text-field") instanceof TextField input) checkInput(input, name, state);
                    else if (control instanceof ComboBox<?>) {
                        var display = control.getChildrenUnmodifiable().stream()
                                .filter(Labeled.class::isInstance).map(Labeled.class::cast)
                                .filter(n -> n.getStyleClass().contains("list-cell")).findFirst()
                                .orElseThrow(() -> new AssertionError(name + " display cell missing"));
                        checkText(display, name, state);
                    }
                    if (family.equals("shared") && control == choices && state.equals("disabled"))
                        FxSnapshot.save(root, "target/ui-quality/design-combo-disabled.png");
                    if (control.getStyleClass().contains("cookbook-category-action") && state.equals("disabled"))
                        FxSnapshot.save(root, "target/ui-quality/design-category-disabled.png");
                    if (state.equals("selected")) {
                        control.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
                        control.pseudoClassStateChanged(PseudoClass.getPseudoClass("focus-visible"), true);
                        root.applyCss(); root.layout();
                        checkFocus(control, name + "/selected");
                        if (family.equals("report") || control.getStyleClass().contains("cookbook-category-action"))
                            FxSnapshot.save(root, "target/ui-quality/design-" + family + "-selected-focus.png");
                        control.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), false);
                        control.pseudoClassStateChanged(PseudoClass.getPseudoClass("focus-visible"), false);
                        root.applyCss(); root.layout();
                    }
                    if (!control.isDisabled() && !quiet(control) && !(control instanceof ToggleButton)) checkBoundary(control, name, state);
                }
                control.setDisable(false);
                control.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), false);
                control.pseudoClassStateChanged(PseudoClass.getPseudoClass("pressed"), false);
                control.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
                control.pseudoClassStateChanged(PseudoClass.getPseudoClass("focus-visible"), true);
                root.applyCss(); root.layout();
                checkFocus(control, name);
                if (family.equals("catalog") && control.getStyleClass().contains("catalog-ingredient-action"))
                    FxSnapshot.save(root, "target/ui-quality/design-ingredient-focus.png");
                control.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), false);
                control.pseudoClassStateChanged(PseudoClass.getPseudoClass("focus-visible"), false);
            }
            root.applyCss(); root.layout();
            FxSnapshot.save(root, "target/ui-quality/design-" + family + ".png");
        } finally { stage.close(); }
    }

    private static String resource(String path) {
        var url = DesignAccessibilityUiSmoke.class.getResource(path);
        if (url == null) throw new AssertionError("Missing local resource " + path);
        return url.toExternalForm();
    }

    private static String labelText(String classes) {
        if (classes.contains("planned") || classes.contains("pending")) return "In programma";
        if (classes.contains("active")) return "In corso";
        if (classes.contains("cancelled")) return "Annullata";
        if (classes.contains("confirmed") || classes.contains("concluded") || classes.equals("course-card-badge")) return "Concluso";
        if (classes.contains("error") || classes.contains("validation")) return "Inserisci una quantità valida";
        if (classes.contains("success")) return "Ricetta salvata";
        return "Cucina mediterranea: ingredienti e dosi";
    }

    private static boolean quiet(Control node) {
        return node.getStyleClass().stream().anyMatch(c -> c.contains("quiet") || c.contains("navigation")
                || c.contains("open") || c.contains("back") || c.contains("ingredient-action") || c.contains("month-arrow"));
    }

    private static void checkText(Labeled node, String name, String state) {
        String expected = node.getStyleClass().contains("catalog-recipe-title") ? "Georgia" : "Manrope";
        checkColor(node, node.getTextFill(), name, state);
        fontChecks++;
        if (!node.getFont().getFamily().equals(expected)) fail(name + " font=" + node.getFont().getFamily() + ", expected=" + expected);
    }

    private static void checkInput(TextField input, String name, String state) {
        var text = input.lookupAll(".text").stream().filter(javafx.scene.text.Text.class::isInstance)
                .map(javafx.scene.text.Text.class::cast).filter(t -> !t.getText().isBlank()).findFirst()
                .orElseThrow(() -> new AssertionError(name + " has no rendered input text"));
        checkColor(input, text.getFill(), name, state);
        fontChecks++;
        if (!input.getFont().getFamily().equals("Manrope")) fail(name + " font=" + input.getFont().getFamily());
    }

    private static void checkColor(Region node, Paint paint, String name, String state) {
        Color bg = background(node);
        if (!(paint instanceof Color foreground)) { fail(name + " non-solid text paint"); return; }
        double opacity = 1;
        for (Node current = node; current != null; current = current.getParent()) opacity *= current.getOpacity();
        Color effectiveText = composite(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), foreground.getOpacity() * opacity), bg);
        double ratio = contrast(effectiveText, bg);
        if (ratio < TEXT_MIN) {
            System.err.println("  paint=" + paint + " background=" + bg + " opacity=" + opacity);
            for (Node current = node; current != null; current = current.getParent())
                System.err.println("  " + current.getClass().getSimpleName() + " " + current.getStyleClass() + " opacity=" + current.getOpacity());
        }
        textChecks++;
        record(name + "/" + state + " text", ratio, TEXT_MIN);
    }

    private static void checkTarget(Control node, String name) {
        targetChecks++;
        if (node.getHeight() < TARGET_MIN) fail(name + " height=" + node.getHeight() + " < " + TARGET_MIN);
    }

    private static void checkBoundary(Region node, String name, String state) {
        Color outside = background(node.getParent());
        double strongest = 0;
        if (node.getBorder() != null) for (var stroke : node.getBorder().getStrokes()) {
            if (stroke.getWidths().getTop() > 0 && stroke.getTopStroke() instanceof Color color)
                strongest = Math.max(strongest, contrast(composite(color, outside), outside));
        }

        strongest = Math.max(strongest, contrast(background(node), outside));
        edgeChecks++;
        record(name + "/" + state + " boundary", strongest, EDGE_MIN);
    }

    private static void checkFocus(Region node, String name) {
        double strongest = 0;
        if (node.getBorder() != null) for (var stroke : node.getBorder().getStrokes()) {
            if (stroke.getWidths().getTop() >= 2 && stroke.getTopStroke() instanceof Color color) {
                Color adjacent = stroke.getInsets().getTop() >= 3 ? background(node) : background(node.getParent());
                strongest = Math.max(strongest, contrast(composite(color, adjacent), adjacent));
            }
        }
        edgeChecks++;
        record(name + "/focus-visible", strongest, EDGE_MIN);
    }

    private static Color background(Node node) {
        if (node == null) return Color.WHITE;
        Color result = background(node.getParent());
        if (node instanceof Region region && region.getBackground() != null)
            for (var fill : region.getBackground().getFills()) if (fill.getFill() instanceof Color color) result = composite(color, result);
        return result;
    }

    private static Color composite(Color front, Color back) {
        double alpha = front.getOpacity();
        return new Color(front.getRed() * alpha + back.getRed() * (1 - alpha),
                front.getGreen() * alpha + back.getGreen() * (1 - alpha),
                front.getBlue() * alpha + back.getBlue() * (1 - alpha), 1);
    }

    private static double contrast(Color a, Color b) {
        double first = luminance(a), second = luminance(b);
        return (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
    }

    private static double luminance(Color c) { return .2126 * linear(c.getRed()) + .7152 * linear(c.getGreen()) + .0722 * linear(c.getBlue()); }
    private static double linear(double c) { return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4); }
    private static void record(String name, double ratio, double min) {
        evidence.add(String.format(Locale.ROOT, "%s=%.3f (min %.1f)", name, ratio, min));
        if (ratio < min) fail(String.format(Locale.ROOT, "%s contrast %.3f < %.1f", name, ratio, min));
    }
    private static void fail(String message) { failures.add(message); System.err.println("FAIL: " + message); }
}
