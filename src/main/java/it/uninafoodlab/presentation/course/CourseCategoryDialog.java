package it.uninafoodlab.presentation.course;

import static it.uninafoodlab.presentation.course.CoursePresentation.*;

import it.uninafoodlab.application.catalog.CreateCategory;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.common.FxAsyncExecutor;
import javafx.beans.binding.Bindings;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

final class CourseCategoryDialog {
    private final javafx.scene.Node owner;
    private final CreateCategory createCategory;
    private final AuthenticatedUser user;
    private final CourseWorkspaceActions actions;

    CourseCategoryDialog(javafx.scene.Node owner, CreateCategory createCategory,
            AuthenticatedUser user, CourseWorkspaceActions actions) {
        this.owner = owner;
        this.createCategory = createCategory;
        this.user = user;
        this.actions = actions;
    }

    void show() {
        if (actions.isBusy()) {
            return;
        }
        var dialog = new Dialog<ButtonType>();
        dialog.setTitle("Nuova categoria");
        dialog.setHeaderText("Nuova categoria");
        it.uninafoodlab.presentation.common.DialogTheme.apply(dialog,
                owner.getScene() == null ? null : owner.getScene().getWindow());
        dialog.getDialogPane().getStyleClass().addAll("app-dialog", "product-course-dialog");
        dialog.getDialogPane().setPrefWidth(700);
        var name = new TextField();
        var description = new TextArea();
        var error = new Label();
        dialog.getDialogPane().setContent(categoryForm(name, description, error));
        configureSubmission(dialog, name, description, error);
        dialog.setOnShown(event -> {
            dialog.getDialogPane().applyCss();
            if (dialog.getDialogPane().lookup(".button-bar") instanceof javafx.scene.control.ButtonBar bar) {
                bar.setButtonOrder("+CO");
            }
            name.requestFocus();
        });
        dialog.showAndWait();
    }

    private static VBox categoryForm(TextField name, TextArea description, Label error) {
        name.setPromptText("Es. Pasticceria");
        name.setAccessibleText("Nome della nuova categoria");
        it.uninafoodlab.presentation.common.TextAreaNavigation.install(description);
        description.setPromptText("Descrizione facoltativa");
        description.setAccessibleText("Descrizione della nuova categoria");
        description.setPrefRowCount(3);
        description.setWrapText(true);
        name.getStyleClass().add("course-field");
        description.getStyleClass().add("course-field");
        var nameLabel = new Label("Nome");
        nameLabel.getStyleClass().add("field-label");
        nameLabel.setLabelFor(name);
        var descriptionLabel = new Label("Descrizione, facoltativa");
        descriptionLabel.setLabelFor(description);
        descriptionLabel.getStyleClass().add("field-label");
        error.setWrapText(true);
        error.setManaged(false);
        error.setVisible(false);
        error.getStyleClass().add("course-feedback-error");
        error.setAccessibleText("Esito creazione categoria");
        var subtitle = readOnlyValue("Disponibile per tutti i corsi");
        var content = new VBox(12.0, subtitle, nameLabel, name, descriptionLabel, description, error);
        content.getStyleClass().add("app-dialog-form");
        return content;
    }

    private void configureSubmission(Dialog<ButtonType> dialog, TextField name, TextArea description, Label error) {
        var create = new ButtonType("Crea categoria", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        var close = new ButtonType("Annulla", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(close, create);
        var submit = dialog.getDialogPane().lookupButton(create);
        submit.getStyleClass().addAll("course-button", "button-primary");
        var cancel = dialog.getDialogPane().lookupButton(close);
        cancel.getStyleClass().addAll("course-button", "button-secondary");
        var submitting = new javafx.beans.property.SimpleBooleanProperty(false);
        submit.disableProperty().bind(submitting.or(Bindings.createBooleanBinding(
                () -> !CategoryDialogModel.isValid(name.getText(), description.getText()),
                name.textProperty(), description.textProperty())));
        cancel.disableProperty().bind(submitting);
        name.disableProperty().bind(submitting);
        description.disableProperty().bind(submitting);
        dialog.setOnCloseRequest(event -> { if (submitting.get()) event.consume(); });
        submit.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            if (submitting.get() || actions.isBusy()) return;
            CategoryDialogModel.toInput(name.getText(), description.getText()).ifPresent(input -> {
                error.setManaged(false);
                error.setVisible(false);
                submitting.set(true);
                actions.setBusy(true);
                FxAsyncExecutor.run("uninafoodlab-category-create",
                        () -> createCategory.create(user, input),
                        created -> {
                            submitting.set(false);
                            actions.setBusy(false);
                            dialog.close();
                            actions.refreshCategories(created);
                        }, failure -> {
                            submitting.set(false);
                            actions.setBusy(false);
                            error.setText("Categoria non creata. Se il nome esiste già, scegline uno diverso e riprova.");
                            error.setManaged(true);
                            error.setVisible(true);
                            name.requestFocus();
                        });
            });
        });
    }
}
