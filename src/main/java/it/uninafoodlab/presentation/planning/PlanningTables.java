package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.application.planning.IngredientRequirement;
import it.uninafoodlab.application.planning.SessionParticipant;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Label;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;

import static it.uninafoodlab.presentation.planning.PlanningLabels.quantityLabel;
import static it.uninafoodlab.presentation.planning.PlanningLabels.initials;

final class PlanningTables {
    private final TableView<IngredientRequirement> requirementTable;
    private final TableView<SessionParticipant> participantTable;
    private final TableColumn<IngredientRequirement, String> ingredientColumn;
    private final TableColumn<IngredientRequirement, String> confirmedParticipantsColumn;
    private final TableColumn<IngredientRequirement, String> quantityColumn;
    private final TableColumn<SessionParticipant, String> participantColumn;
    private final TableColumn<SessionParticipant, String> enrollmentColumn;
    private final TableColumn<SessionParticipant, String> attendanceColumn;
    PlanningTables(
            TableView<IngredientRequirement> requirementTable,
            TableView<SessionParticipant> participantTable,
            TableColumn<IngredientRequirement, String> ingredientColumn,
            TableColumn<IngredientRequirement, String> confirmedParticipantsColumn,
            TableColumn<IngredientRequirement, String> quantityColumn,
            TableColumn<SessionParticipant, String> participantColumn,
            TableColumn<SessionParticipant, String> enrollmentColumn,
            TableColumn<SessionParticipant, String> attendanceColumn) {
        this.requirementTable = requirementTable;
        this.participantTable = participantTable;
        this.ingredientColumn = ingredientColumn;
        this.confirmedParticipantsColumn = confirmedParticipantsColumn;
        this.quantityColumn = quantityColumn;
        this.participantColumn = participantColumn;
        this.enrollmentColumn = enrollmentColumn;
        this.attendanceColumn = attendanceColumn;
    }

    void configure() {
        requirementTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        participantTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        fitTableToRows(requirementTable, 46);
        fitTableToRows(participantTable, 46);
        ingredientColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().ingredientName()));
        confirmedParticipantsColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                Integer.toString(cell.getValue().confirmedParticipants())));
        quantityColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                quantityLabel(cell.getValue())));
        quantityColumn.setCellFactory(ignored -> new TableCell<>() {
            { setAlignment(javafx.geometry.Pos.CENTER_RIGHT); }
            @Override protected void updateItem(String quantity, boolean empty) {
                super.updateItem(quantity, empty);
                setText(empty ? null : quantity);
            }
        });
        participantColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().displayName()));
        participantColumn.setCellFactory(ignored -> new TableCell<SessionParticipant, String>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                setText(null);
                if (empty || name == null) {
                    setGraphic(null);
                    return;
                }
                var avatar = new Label(initials(name));
                avatar.getStyleClass().add("participant-avatar");
                var label = new Label(name);
                label.getStyleClass().add("participant-name");
                setGraphic(new HBox(16.0, avatar, label));
            }
        });
        enrollmentColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().enrollmentStatus().name().equals("ACTIVE") ? "Attiva" : "Ritirata"));
        attendanceColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().attendanceStatus()
                        .map(status -> status.name().equals("CONFIRMED") ? "Confermata" : "Revocata")
                        .orElse("Non comunicata")));
        enrollmentColumn.setCellFactory(ignored -> statusCell("Attiva", "Ritirata"));
        attendanceColumn.setCellFactory(ignored -> statusCell("Confermata", "Revocata"));
    }

    private static void fitTableToRows(TableView<?> table, double headerHeight) {

        table.prefHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> table.getItems().isEmpty() ? 180.0
                        : headerHeight + table.getItems().size() * table.getFixedCellSize() + 2,
                table.getItems(), table.fixedCellSizeProperty()));
        table.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        table.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
    }

    private static TableCell<SessionParticipant, String> statusCell(String positive, String negative) {
        return new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                setText(null);
                setGraphic(null);
                if (empty || status == null) return;
                var symbol = "−";
                var style = "ops-status-neutral";
                if (status.equals(positive)) {
                    symbol = "✓";
                    style = "ops-status-positive";
                } else if (status.equals(negative)) {
                    symbol = "×";
                    style = "ops-status-negative";
                }
                var icon = new Label(symbol);
                icon.getStyleClass().addAll("ops-status-icon", style);
                var row = new HBox(12, icon, new Label(status));
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                setGraphic(row);
            }
        };
    }
}
