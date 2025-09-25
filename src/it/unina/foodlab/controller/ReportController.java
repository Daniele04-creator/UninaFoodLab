package it.unina.foodlab.controller;

import it.unina.foodlab.dao.ReportDao;
import it.unina.foodlab.util.ReportMensile;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.YearMonth;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.fx.ChartViewer;
import org.jfree.data.category.DefaultCategoryDataset;

public class ReportController {

    private final ReportDao reportDao = new ReportDao();
    private final String cfChef;

    private Parent previousRoot; // root della schermata precedente

    @FXML private Button btnIndietro;
    @FXML private DatePicker dpMese;
    @FXML private Button btnGenera;
    @FXML private Label lblTitolo;
    @FXML private GridPane gridValori;
    @FXML private VBox rightCharts;

    // Costruttore con parametro: verrà usato dalla controllerFactory
    public ReportController(String cfChef) {
        if (cfChef == null || cfChef.isBlank()) {
            throw new IllegalArgumentException("cfChef mancante per ReportController");
        }
        this.cfChef = cfChef.trim();
    }

    // Metodo per passare la root precedente
    public void setPreviousRoot(Parent root) {
        this.previousRoot = root;
    }

    @FXML
    private void initialize() {
        // setup iniziale UI
        lblTitolo.setFont(Font.font(18));
        if (dpMese.getValue() == null) {
            dpMese.setValue(LocalDate.now());
        }

        btnGenera.setOnAction(e -> onGenera());

        btnIndietro.setOnAction(e -> goBack());

        onGenera(); // genera subito per il mese corrente
    }

    private void goBack() {
        if (previousRoot == null) return;
        Stage stage = (Stage) btnIndietro.getScene().getWindow();
        stage.getScene().setRoot(previousRoot);
        stage.setTitle("Corsi"); // ripristina titolo della schermata principale
    }

    private void onGenera() {
        try {
            LocalDate chosen = (dpMese.getValue() != null) ? dpMese.getValue() : LocalDate.now();
            YearMonth ym = YearMonth.from(chosen);

            ReportMensile r = reportDao.getReportMensile(cfChef, ym);

            lblTitolo.setText("Report Mensile - " + ym);

            // ====== Valori testuali ======
            gridValori.getChildren().clear();
            int row = 0;
            addRow("Corsi totali:", String.valueOf(r.totaleCorsi()), row++);
            addRow("Sessioni online:", String.valueOf(r.totaleOnline()), row++);
            addRow("Sessioni pratiche:", String.valueOf(r.totalePratiche()), row++);
            addRow("Ricette (pratiche) - Media:",
                    r.mediaRicettePratiche() == null ? "—" : String.format("%.2f", r.mediaRicettePratiche()), row++);
            addRow("Ricette (pratiche) - Max:",
                    r.maxRicettePratiche() == null ? "—" : r.maxRicettePratiche().toString(), row++);
            addRow("Ricette (pratiche) - Min:",
                    r.minRicettePratiche() == null ? "—" : r.minRicettePratiche().toString(), row++);

            // ====== Grafici ======
            rightCharts.getChildren().clear();

            // Grafico 1: conteggi
            DefaultCategoryDataset ds1 = new DefaultCategoryDataset();
            ds1.addValue(r.totaleCorsi(),    "Conteggi", "Corsi");
            ds1.addValue(r.totaleOnline(),   "Conteggi", "Online");
            ds1.addValue(r.totalePratiche(), "Conteggi", "Pratiche");

            JFreeChart chart1 = ChartFactory.createBarChart(
                    "Corsi & Sessioni (" + ym + ")", "Categoria", "Numero", ds1
            );
            ChartViewer viewer1 = new ChartViewer(chart1);
            viewer1.setPrefSize(520, 320);
            viewer1.setMinSize(520, 320);
            viewer1.setMaxWidth(Double.MAX_VALUE);
            rightCharts.getChildren().add(viewer1);

            // Grafico 2: statistiche ricette sulle pratiche
            if (r.totalePratiche() > 0 && r.mediaRicettePratiche() != null) {
                DefaultCategoryDataset ds2 = new DefaultCategoryDataset();
                ds2.addValue(r.minRicettePratiche(),   "Ricette", "Min");
                ds2.addValue(r.mediaRicettePratiche(), "Ricette", "Media");
                ds2.addValue(r.maxRicettePratiche(),   "Ricette", "Max");

                JFreeChart chart2 = ChartFactory.createBarChart(
                        "Ricette per Sessioni Pratiche (" + ym + ")", "Metrica", "N. ricette", ds2
                );
                ChartViewer viewer2 = new ChartViewer(chart2);
                viewer2.setPrefSize(520, 320);
                viewer2.setMinSize(520, 320);
                viewer2.setMaxWidth(Double.MAX_VALUE);
                rightCharts.getChildren().add(viewer2);
            }

        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void addRow(String label, String value, int row) {
        Label l = new Label(label);
        Label v = new Label(value);
        gridValori.add(l, 0, row);
        gridValori.add(v, 1, row);
    }

    private void showError(Exception ex) {
        ex.printStackTrace();
        Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
        a.setHeaderText("Errore generazione report");
        a.showAndWait();
    }
}