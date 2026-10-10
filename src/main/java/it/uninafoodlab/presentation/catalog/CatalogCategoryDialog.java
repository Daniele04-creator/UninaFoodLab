package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.presentation.common.UserErrorMessages;

import it.uninafoodlab.application.catalog.CategorySummary;
import it.uninafoodlab.application.catalog.CreateCategory;
import it.uninafoodlab.application.catalog.NewCategoryInput;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.common.DialogTheme;
import it.uninafoodlab.presentation.common.FxAsyncExecutor;
import java.util.function.Consumer;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;


final class CatalogCategoryDialog {
    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final TextField name = new TextField();
    private final TextArea description = new TextArea();
    private final Label error = new Label();
    private final SimpleBooleanProperty pending = new SimpleBooleanProperty();
    private final CreateCategory createCategory;
    private final AuthenticatedUser user;
    private final Consumer<CategorySummary> onCreated;

    CatalogCategoryDialog(Node owner, CreateCategory createCategory, AuthenticatedUser user,
            Consumer<CategorySummary> onCreated) {
        this.createCategory = createCategory;
        this.user = user;
        this.onCreated = onCreated;
        dialog.setTitle("Nuova categoria");
        dialog.setHeaderText("Organizza ricette e corsi");
        DialogTheme.apply(dialog, owner.getScene().getWindow());
        dialog.getDialogPane().setPrefWidth(580);
        name.setId("newCategoryName");
        name.setPromptText("Es. Pasticceria");
        description.setId("newCategoryDescription");
        it.uninafoodlab.presentation.common.TextAreaNavigation.install(description);
        description.setPromptText("Descrizione facoltativa");
        description.setPrefRowCount(3);
        description.setWrapText(true);
        error.setId("categoryCreateError");
        error.setWrapText(true);
        error.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        error.getStyleClass().add("course-feedback-error");
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());
        var nameLabel = new Label("Nome, da 3 a 80 caratteri");
        nameLabel.setLabelFor(name);
        var descriptionLabel = new Label("Descrizione facoltativa");
        descriptionLabel.setLabelFor(description);
        var content = new VBox(12, nameLabel, name, descriptionLabel, description, error);
        content.getStyleClass().add("app-dialog-form");
        dialog.getDialogPane().setContent(content);
    }

    static void enable(Button button) {
        if (button != null) button.setDisable(false);
    }

    static java.util.UUID selectedCategoryId(ComboBox<CategorySummary> choices) {
        return choices.getValue() == null ? null : choices.getValue().id();
    }

    static void open(Button button, java.util.function.Supplier<CreateCategory> creator,
            AuthenticatedUser user, ComboBox<CategorySummary> choices, Label categoryError,
            CatalogOperations operations) {
        if (operations.isLoading() || creator.get() == null) return;
        new CatalogCategoryDialog(button, creator.get(), user, created -> {
            choices.getItems().add(created);
            choices.getItems().sort(java.util.Comparator.comparing(CategorySummary::name, String.CASE_INSENSITIVE_ORDER));
            choices.setValue(created);
            categoryError.setText("");
            operations.setFeedback("Categoria creata e selezionata. La bozza della ricetta è conservata.",
                    CatalogOperations.FeedbackType.SUCCESS);
        }).show();
    }

    void show() {
        var create = new ButtonType("Crea categoria", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, create);
        var submit = dialog.getDialogPane().lookupButton(create);
        submit.setId("createCategorySubmit");
        submit.getStyleClass().add("button-primary");
        submit.disableProperty().bind(pending);
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).disableProperty().bind(pending);
        name.disableProperty().bind(pending);
        description.disableProperty().bind(pending);
        submit.addEventFilter(javafx.event.ActionEvent.ACTION, event -> { event.consume(); submit(); });
        dialog.setOnCloseRequest(event -> { if (pending.get()) event.consume(); });
        dialog.setOnShown(event -> name.requestFocus());
        dialog.showAndWait();
    }

    private void submit() {
        if (pending.get()) return;
        final NewCategoryInput input;
        try {
            input = new NewCategoryInput(name.getText(), description.getText());
        } catch (IllegalArgumentException exception) {
            showError(UserErrorMessages.validation(exception, "Controlla nome e descrizione della categoria."));
            name.requestFocus();
            return;
        }
        error.setText("");
        pending.set(true);
        FxAsyncExecutor.run("catalog-category-create", () -> createCategory.create(user, input), created -> {
            pending.set(false);
            onCreated.accept(created);
            dialog.close();
        }, failure -> {
            pending.set(false);
            showError("Categoria non creata. Controlla la connessione; se il nome esiste già, scegline uno diverso.");
            name.requestFocus();
        });
    }

    private void showError(String message) {
        error.setText(message);
        dialog.getDialogPane().getScene().getWindow().sizeToScene();
    }
}
