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

import java.net.URL;
import java.time.LocalDate;
import java.time.YearMonth;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.fx.ChartViewer;
import org.jfree.data.category.DefaultCategoryDataset;

public class ReportController {

    /* ====== Costanti UI/Styling ====== */
    private static final String FONT_FAMILY = "Arial";
    private static final int TITLE_FONT_SIZE = 18;
    private static final int AXIS_LABEL_FONT_SIZE = 14;
    private static final int TICK_FONT_SIZE = 12;
    private static final int LEGEND_FONT_SIZE = 14;
    private static final int VIEWER_W = 520;
    private static final int VIEWER_H = 320;

    // Colori scuro stile Nord (JFreeChart usa java.awt.Color)
    private static final java.awt.Color BG_DARK = new java.awt.Color(46, 52, 64);
    private static final java.awt.Color PLOT_DARK = new java.awt.Color(39, 43, 53);
    private static final java.awt.Color GRID_GRAY = java.awt.Color.GRAY;
    private static final java.awt.Color TEXT_WHITE = java.awt.Color.WHITE;
    private static final java.awt.Color BAR_BLUE = new java.awt.Color(66, 133, 244);
    private static final java.awt.Color BAR_YELLOW = new java.awt.Color(235, 203, 0);

    /* ====== Dipendenze ====== */
    private final ReportDao reportDao = new ReportDao();
    private final String cfChef;

    /* ====== Navigazione ====== */
    private Parent previousRoot; // root della schermata precedente

    /* ====== FXML ====== */
    @FXML private Button btnIndietro;
    @FXML private DatePicker dpMese;
    @FXML private Button btnGenera;
    @FXML private Label lblTitolo;
    @FXML private GridPane gridValori;
    @FXML private VBox rightCharts;

    /* ====== Costruttore ====== */
    public ReportController(String cfChef) {
        if (cfChef == null || cfChef.trim().isEmpty()) {
            throw new IllegalArgumentException("cfChef mancante per ReportController");
        }
        this.cfChef = cfChef.trim();
    }

    public void setPreviousRoot(Parent root) {
        this.previousRoot = root;
    }

    /* ====== Lifecycle ====== */
    @FXML
    private void initialize() {
        // Titolo più grande
        lblTitolo.setFont(Font.font(TITLE_FONT_SIZE));

        // Mese iniziale: oggi
        if (dpMese.getValue() == null) {
            dpMese.setValue(LocalDate.now());
        }
        dpMese.setShowWeekNumbers(false);
        dpMese.setEditable(false);

        // Wiring bottoni senza lambda “avanzate”
        btnGenera.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            @Override public void handle(javafx.event.ActionEvent e) { onGenera(); }
        });
        btnIndietro.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            @Override public void handle(javafx.event.ActionEvent e) { goBack(); }
        });

        // Carica subito il report corrente
        onGenera();
    }

    /* ====== Navigazione ====== */
    private void goBack() {
        if (previousRoot == null) return;
        Stage stage = (Stage) btnIndietro.getScene().getWindow();
        if (stage == null || stage.getScene() == null) return;
        stage.getScene().setRoot(previousRoot);
        stage.setTitle("Corsi");
    }

    /* ====== Azioni ====== */
    private void onGenera() {
        try {
            LocalDate chosen = (dpMese.getValue() != null) ? dpMese.getValue() : LocalDate.now();
            YearMonth ym = YearMonth.from(chosen);

            ReportMensile r = reportDao.getReportMensile(cfChef, ym);

            lblTitolo.setText("Report Mensile - " + ym);

            // ==== Pannello valori ====
            gridValori.getChildren().clear();
            int row = 0;
            addRow("Corsi totali:", safeNum(r.totaleCorsi()), row++);
            addRow("Sessioni online:", safeNum(r.totaleOnline()), row++);
            addRow("Sessioni pratiche:", safeNum(r.totalePratiche()), row++);
            addRow("Ricette - Media:", r.mediaRicettePratiche() == null ? "—" : format2(r.mediaRicettePratiche()), row++);
            addRow("Ricette - Max:", r.maxRicettePratiche() == null ? "—" : String.valueOf(r.maxRicettePratiche()), row++);
            addRow("Ricette - Min:", r.minRicettePratiche() == null ? "—" : String.valueOf(r.minRicettePratiche()), row++);

            // ==== Grafici ====
            rightCharts.getChildren().clear();

            // Conteggi (Corsi / Online / Pratiche)
            DefaultCategoryDataset ds1 = new DefaultCategoryDataset();
            ds1.addValue(r.totaleCorsi(), "Conteggi", "Corsi");
            ds1.addValue(r.totaleOnline(), "Conteggi", "Online");
            ds1.addValue(r.totalePratiche(), "Conteggi", "Pratiche");

            JFreeChart chart1 = ChartFactory.createBarChart(
                    "Corsi & Sessioni (" + ym + ")", "Categoria", "Numero", ds1
            );
            customizeBarChart(chart1, BAR_BLUE);
            ChartViewer viewer1 = new ChartViewer(chart1);
            viewer1.setPrefSize(VIEWER_W, VIEWER_H);
            rightCharts.getChildren().add(viewer1);

            animateDataset(ds1);

            // Ricette (Min/Media/Max)
            DefaultCategoryDataset ds2 = new DefaultCategoryDataset();
            ds2.addValue((r.minRicettePratiche() == null ? 0 : r.minRicettePratiche()), "Ricette", "Min");
            ds2.addValue((r.mediaRicettePratiche() == null ? 0 : r.mediaRicettePratiche()), "Ricette", "Media");
            ds2.addValue((r.maxRicettePratiche() == null ? 0 : r.maxRicettePratiche()), "Ricette", "Max");

            JFreeChart chart2 = ChartFactory.createBarChart(
                    "Ricette per Sessioni Pratiche (" + ym + ")", "Metrica", "N. ricette", ds2
            );
            customizeBarChart(chart2, BAR_YELLOW);
            ChartViewer viewer2 = new ChartViewer(chart2);
            viewer2.setPrefSize(VIEWER_W, VIEWER_H);
            rightCharts.getChildren().add(viewer2);

            animateDataset(ds2);



        } catch (Exception ex) {
            showError(ex);
        }
    }

    /* ====== Grafici/Animazione ====== */

    private void customizeBarChart(JFreeChart chart, java.awt.Color barColor) {
        chart.setBackgroundPaint(BG_DARK);
        if (chart.getTitle() != null) {
            chart.getTitle().setFont(new java.awt.Font(FONT_FAMILY, java.awt.Font.BOLD, TITLE_FONT_SIZE));
            chart.getTitle().setPaint(TEXT_WHITE);
        }

        org.jfree.chart.plot.CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(PLOT_DARK);
        plot.setRangeGridlinePaint(GRID_GRAY);

        org.jfree.chart.axis.CategoryAxis domainAxis = plot.getDomainAxis();
        org.jfree.chart.axis.NumberAxis rangeAxis = (org.jfree.chart.axis.NumberAxis) plot.getRangeAxis();

        domainAxis.setLabelFont(new java.awt.Font(FONT_FAMILY, java.awt.Font.BOLD, AXIS_LABEL_FONT_SIZE));
        domainAxis.setLabelPaint(TEXT_WHITE);
        rangeAxis.setLabelFont(new java.awt.Font(FONT_FAMILY, java.awt.Font.BOLD, AXIS_LABEL_FONT_SIZE));
        rangeAxis.setLabelPaint(TEXT_WHITE);

        domainAxis.setTickLabelFont(new java.awt.Font(FONT_FAMILY, java.awt.Font.PLAIN, TICK_FONT_SIZE));
        domainAxis.setTickLabelPaint(TEXT_WHITE);
        rangeAxis.setTickLabelFont(new java.awt.Font(FONT_FAMILY, java.awt.Font.PLAIN, TICK_FONT_SIZE));
        rangeAxis.setTickLabelPaint(TEXT_WHITE);

        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(new java.awt.Font(FONT_FAMILY, java.awt.Font.PLAIN, LEGEND_FONT_SIZE));
            chart.getLegend().setItemPaint(TEXT_WHITE);
            chart.getLegend().setBackgroundPaint(null);
            chart.getLegend().setFrame(org.jfree.chart.block.BlockBorder.NONE);
            chart.getLegend().setPosition(org.jfree.chart.ui.RectangleEdge.BOTTOM);
            chart.getLegend().setHorizontalAlignment(org.jfree.chart.ui.HorizontalAlignment.RIGHT);
        }

        org.jfree.chart.renderer.category.BarRenderer renderer =
                (org.jfree.chart.renderer.category.BarRenderer) plot.getRenderer();
        renderer.setDefaultItemLabelFont(new java.awt.Font(FONT_FAMILY, java.awt.Font.BOLD, 12));
        renderer.setDefaultItemLabelPaint(TEXT_WHITE);
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setSeriesPaint(0, barColor);
    }

    /** Animazione “from 0 to target” semplice, senza lambda. */
    private void animateDataset(DefaultCategoryDataset dataset) {
        javafx.animation.Timeline timeline = new javafx.animation.Timeline();
        final int steps = 30;              // fotogrammi
        final double frameMs = 20.0;       // durata frame

        // Mappa target
        java.util.Map<Comparable<?>, java.util.Map<Comparable<?>, Number>> targets =
                new java.util.HashMap<Comparable<?>, java.util.Map<Comparable<?>, Number>>();

        java.util.List<?> rowKeys = dataset.getRowKeys();
        java.util.List<?> colKeys = dataset.getColumnKeys();

        for (int i = 0; i < rowKeys.size(); i++) {
            Comparable<?> rowKey = (Comparable<?>) rowKeys.get(i);
            java.util.Map<Comparable<?>, Number> inner = new java.util.HashMap<Comparable<?>, Number>();
            targets.put(rowKey, inner);
            for (int j = 0; j < colKeys.size(); j++) {
                Comparable<?> colKey = (Comparable<?>) colKeys.get(j);
                Number value = dataset.getValue(rowKey, colKey);
                if (value == null) value = 0;
                inner.put(colKey, value);
                dataset.setValue(0, rowKey, colKey); // parti da 0
            }
        }

        for (int i = 1; i <= steps; i++) {
            final int step = i;
            javafx.animation.KeyFrame kf = new javafx.animation.KeyFrame(
                    javafx.util.Duration.millis(frameMs * step),
                    new javafx.event.EventHandler<javafx.event.ActionEvent>() {
                        @Override public void handle(javafx.event.ActionEvent e) {
                            for (java.util.Map.Entry<Comparable<?>, java.util.Map<Comparable<?>, Number>> rowEntry : targets.entrySet()) {
                                Comparable<?> row = rowEntry.getKey();
                                java.util.Map<Comparable<?>, Number> cols = rowEntry.getValue();
                                for (java.util.Map.Entry<Comparable<?>, Number> colEntry : cols.entrySet()) {
                                    Comparable<?> col = colEntry.getKey();
                                    Number target = colEntry.getValue();
                                    double val = target.doubleValue() * step / steps;
                                    dataset.setValue(val, row, col);
                                }
                            }
                        }
                    }
            );
            timeline.getKeyFrames().add(kf);
        }
        timeline.play();
    }

    /* ====== UI Helpers ====== */

    private void addRow(String key, String value, int row) {
        Label lblKey = new Label(key);
        lblKey.getStyleClass().add("card-text");
        Label lblValue = new Label(value);
        lblValue.getStyleClass().add("card-text");
        gridValori.add(lblKey, 0, row);
        gridValori.add(lblValue, 1, row);
    }

    private String safeNum(Integer n) {
        return (n == null) ? "0" : String.valueOf(n.intValue());
    }

    private String format2(Double d) {
        if (d == null) return "—";
        java.text.DecimalFormat df = new java.text.DecimalFormat("0.00");
        return df.format(d.doubleValue());
    }

    private void showError(Exception ex) {
        ex.printStackTrace();
        Alert a = new Alert(Alert.AlertType.ERROR, (ex.getMessage() == null ? "Errore sconosciuto" : ex.getMessage()), ButtonType.OK);
        a.setHeaderText("Errore generazione report");
       
        a.showAndWait();
    }
}
