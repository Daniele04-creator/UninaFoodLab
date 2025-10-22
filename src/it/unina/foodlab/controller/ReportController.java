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
import org.jfree.chart.*;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.block.*;
import org.jfree.chart.axis.*;
import org.jfree.chart.fx.ChartViewer;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.category.*;
import org.jfree.chart.ui.*;
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
        addRow("Ricette - Media:",
                r.mediaRicettePratiche() == null ? "—" : String.format("%.2f", r.mediaRicettePratiche()), row++);
        addRow("Ricette - Max:",
                r.maxRicettePratiche() == null ? "—" : r.maxRicettePratiche().toString(), row++);
        addRow("Ricette - Min:",
                r.minRicettePratiche() == null ? "—" : r.minRicettePratiche().toString(), row++);

        // ====== Grafici ======
        rightCharts.getChildren().clear();

        // --- Funzione helper per personalizzare i grafici ---
        java.util.function.BiConsumer<JFreeChart, java.awt.Color> customizeChart = (chart, barColor) -> {
            chart.setBackgroundPaint(new java.awt.Color(46, 52, 64));
            chart.getTitle().setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 18));
            chart.getTitle().setPaint(java.awt.Color.WHITE);

            CategoryPlot plot = chart.getCategoryPlot();
            plot.setBackgroundPaint(new java.awt.Color(39, 43, 53));
            plot.setRangeGridlinePaint(java.awt.Color.GRAY);

            CategoryAxis domainAxis = plot.getDomainAxis();
            NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();

            domainAxis.setLabelFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
            domainAxis.setLabelPaint(java.awt.Color.WHITE);
            rangeAxis.setLabelFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
            rangeAxis.setLabelPaint(java.awt.Color.WHITE);

            domainAxis.setTickLabelFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 12));
            domainAxis.setTickLabelPaint(java.awt.Color.WHITE);
            rangeAxis.setTickLabelFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 12));
            rangeAxis.setTickLabelPaint(java.awt.Color.WHITE);

            if (chart.getLegend() != null) {
                chart.getLegend().setItemFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 14));
                chart.getLegend().setItemPaint(java.awt.Color.WHITE);
                chart.getLegend().setBackgroundPaint(null);
                chart.getLegend().setFrame(BlockBorder.NONE);
                chart.getLegend().setPosition(RectangleEdge.BOTTOM);
                chart.getLegend().setHorizontalAlignment(HorizontalAlignment.RIGHT);
            }

            BarRenderer renderer =
                    (BarRenderer) plot.getRenderer();
            renderer.setDefaultItemLabelFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
            renderer.setDefaultItemLabelPaint(java.awt.Color.WHITE);
            renderer.setDefaultItemLabelsVisible(true);
            renderer.setSeriesPaint(0, barColor);
        };

        // --- Grafico 1: conteggi ---
        DefaultCategoryDataset ds1 = new DefaultCategoryDataset();
        ds1.addValue(r.totaleCorsi(), "Conteggi", "Corsi");
        ds1.addValue(r.totaleOnline(), "Conteggi", "Online");
        ds1.addValue(r.totalePratiche(), "Conteggi", "Pratiche");

        JFreeChart chart1 = ChartFactory.createBarChart(
                "Corsi & Sessioni (" + ym + ")", "Categoria", "Numero", ds1
        );
        customizeChart.accept(chart1, new java.awt.Color(66, 133, 244));
        ChartViewer viewer1 = new ChartViewer(chart1);
        viewer1.setPrefSize(520, 320);
        rightCharts.getChildren().add(viewer1);

        // Animazione grafico 1
        animateDataset(ds1);

        // --- Grafico 2: statistiche ricette ---
        DefaultCategoryDataset ds2 = new DefaultCategoryDataset();
        ds2.addValue(r.minRicettePratiche() != null ? r.minRicettePratiche() : 0, "Ricette", "Min");
        ds2.addValue(r.mediaRicettePratiche() != null ? r.mediaRicettePratiche() : 0, "Ricette", "Media");
        ds2.addValue(r.maxRicettePratiche() != null ? r.maxRicettePratiche() : 0, "Ricette", "Max");

        JFreeChart chart2 = ChartFactory.createBarChart(
                "Ricette per Sessioni Pratiche (" + ym + ")", "Metrica", "N. ricette", ds2
        );
        customizeChart.accept(chart2, new java.awt.Color(235, 203, 0));
        ChartViewer viewer2 = new ChartViewer(chart2);
        viewer2.setPrefSize(520, 320);
        rightCharts.getChildren().add(viewer2);

        // Animazione grafico 2
        animateDataset(ds2);

    } catch (Exception ex) {
        showError(ex);
    }
}


    private void animateDataset(DefaultCategoryDataset dataset) {
    javafx.animation.Timeline timeline = new javafx.animation.Timeline();
    int steps = 30; // numero di frame
    double frameDuration = 20; // millisecondi per frame

    // salva valori originali in mappa
    java.util.Map<Comparable<?>, java.util.Map<Comparable<?>, Number>> targets = new java.util.HashMap<>();

    for (Object rowObj : dataset.getRowKeys()) {
        Comparable<?> rowKey = (Comparable<?>) rowObj;
        targets.put(rowKey, new java.util.HashMap<>());
        for (Object colObj : dataset.getColumnKeys()) {
            Comparable<?> colKey = (Comparable<?>) colObj;
            Number value = dataset.getValue(rowKey, colKey);
            if (value == null) value = 0; // evita null
            targets.get(rowKey).put(colKey, value);
            dataset.setValue(0, rowKey, colKey); // parte da 0
        }
    }

    // timeline per animazione
    for (int i = 1; i <= steps; i++) {
        final int step = i;
        timeline.getKeyFrames().add(new javafx.animation.KeyFrame(
                javafx.util.Duration.millis(frameDuration * step),
                e -> {
                    for (Comparable<?> row : targets.keySet()) {
                        for (Comparable<?> col : targets.get(row).keySet()) {
                            Number target = targets.get(row).get(col);
                            dataset.setValue(target.doubleValue() * step / steps, row, col);
                        }
                    }
                }
        ));
    }

    timeline.play();
}




    private void addRow(String key, String value, int row) {
        Label lblKey = new Label(key);
        lblKey.getStyleClass().add("card-text");  // aggiungi la classe CSS
        Label lblValue = new Label(value);
        lblValue.getStyleClass().add("card-text");

        gridValori.add(lblKey, 0, row);
        gridValori.add(lblValue, 1, row);
    }




    private void showError(Exception ex) {
        ex.printStackTrace();
        Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
        a.setHeaderText("Errore generazione report");
        a.showAndWait();
    }
}