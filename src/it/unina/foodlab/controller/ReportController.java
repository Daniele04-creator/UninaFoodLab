package it.unina.foodlab.controller;

import it.unina.foodlab.dao.ReportDao;
import it.unina.foodlab.util.ReportMensile;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.fx.ChartViewer;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.HorizontalAlignment;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;

public class ReportController {

    // === DAOs & state ===
    private final ReportDao reportDao = new ReportDao();
    private final String cfChef;
    private Parent previousRoot; // root della schermata precedente

    // === FX refs ===
    @FXML private Button btnIndietro;
    @FXML private DatePicker dpMese;
    @FXML private Button btnGenera;
    @FXML private Label  lblTitolo;
    @FXML private GridPane gridValori;
    @FXML private VBox rightCharts;

    // === Theme (FoodLab) ===
    private static final String BG_CARD   = "#20282b";
    private static final String BG_HDR    = "#242c2f";
    private static final String TXT_MAIN  = "#e9f5ec";
    private static final String TXT_MUTED = "#cfe5d9";
    private static final String BORDER_SOFT = "rgba(255,255,255,0.06)";
    private static final String ACCENT   = "#1fb57a";

    public ReportController(String cfChef) {
        if (cfChef == null || cfChef.isBlank()) throw new IllegalArgumentException("cfChef mancante");
        this.cfChef = cfChef.trim();
    }

    public void setPreviousRoot(Parent root) { this.prevousFix(root); }
    private void prevousFix(Parent root) { this.previousRoot = root; } // evita conflitto con FXML loader che talvolta scambia nomi

    @FXML
    private void initialize() {
        if (dpMese.getValue() == null) dpMese.setValue(LocalDate.now());

        // Theme tweaks for DatePicker (field + popup) & hide arrow button
        tintDatePickerDark(dpMese);
        hideDatePickerArrow(dpMese);
        openDatePickerOnClick(dpMese);

        btnGenera.setOnAction(e -> onGenera());
        btnIndietro.setOnAction(e -> goBack());

        onGenera(); // genera subito
    }

    private void goBack() {
        if (previousRoot == null) return;
        Stage stage = (Stage) btnIndietro.getScene().getWindow();
        stage.getScene().setRoot(previousRoot);
        stage.setTitle("Corsi");
    }

    private void onGenera() {
        try {
            LocalDate chosen = (dpMese.getValue() != null) ? dpMese.getValue() : LocalDate.now();
            YearMonth ym = YearMonth.from(chosen);

            ReportMensile r = reportDao.getReportMensile(cfChef, ym);
            lblTitolo.setText("Report Mensile - " + ym);

            // ======= valori testuali =======
            gridValori.getChildren().clear();
            addRow("Corsi totali:",          str(r.totaleCorsi()), 0);
            addRow("Sessioni online:",       str(r.totaleOnline()),1);
            addRow("Sessioni pratiche:",     str(r.totalePratiche()),2);
            addRow("Ricette - Media:",       r.mediaRicettePratiche()==null?"—":fmt(r.mediaRicettePratiche()),3);
            addRow("Ricette - Max:",         r.maxRicettePratiche()==null?"—":String.valueOf(r.maxRicettePratiche()),4);
            addRow("Ricette - Min:",         r.minRicettePratiche()==null?"—":String.valueOf(r.minRicettePratiche()),5);

            // ======= grafici =======
            rightCharts.getChildren().clear();

            // Chart 1: conteggi
            DefaultCategoryDataset ds1 = new DefaultCategoryDataset();
            ds1.addValue(r.totaleCorsi(),   "Conteggi", "Corsi");
            ds1.addValue(r.totaleOnline(),  "Conteggi", "Online");
            ds1.addValue(r.totalePratiche(),"Conteggi", "Pratiche");

            JFreeChart chart1 = ChartFactory.createBarChart("Corsi & Sessioni (" + ym + ")", "Categoria", "Numero", ds1);
            styleChart(chart1, new java.awt.Color(66,133,244)); // blu acceso
            ChartViewer v1 = new ChartViewer(chart1);
            v1.setPrefSize(520, 320);
            rightCharts.getChildren().add(wrapChart(v1));

            animateDataset(ds1);

            // Chart 2: ricette
            DefaultCategoryDataset ds2 = new DefaultCategoryDataset();
            ds2.addValue(zeroIfNull(r.minRicettePratiche()), "Ricette", "Min");
            ds2.addValue(zeroIfNull(r.mediaRicettePratiche()), "Ricette", "Media");
            ds2.addValue(zeroIfNull(r.maxRicettePratiche()), "Ricette", "Max");

            JFreeChart chart2 = ChartFactory.createBarChart("Ricette per Sessioni Pratiche (" + ym + ")", "Metrica", "N. ricette", ds2);
            styleChart(chart2, new java.awt.Color(250,195,0));  // giallo miele
            ChartViewer v2 = new ChartViewer(chart2);
            v2.setPrefSize(520, 320);
            rightCharts.getChildren().add(wrapChart(v2));

            animateDataset(ds2);

        } catch (Exception ex) {
            showError(ex);
        }
    }

    // ====== styling helpers ======

    private void tintDatePickerDark(DatePicker dp) {
        if (dp == null) return;
        dp.setEditable(false);
        dp.setStyle("-fx-background-color:#2b3438; -fx-control-inner-background:#2b3438;" +
                    "-fx-text-fill:" + TXT_MAIN + "; -fx-prompt-text-fill: rgba(233,245,236,0.70);" +
                    "-fx-background-radius:10; -fx-border-color:" + BORDER_SOFT + "; -fx-border-radius:10;");
        if (dp.getEditor() != null) {
            dp.getEditor().setStyle("-fx-background-color: transparent; -fx-text-fill:" + TXT_MAIN + ";");
        }
        dp.setOnShowing(ev -> Platform.runLater(() -> {
            Node popup = dp.lookup(".date-picker-popup");
            if (popup != null)
                popup.setStyle("-fx-background-color:" + BG_CARD + "; -fx-background-radius:10; -fx-border-color:" + BORDER_SOFT + "; -fx-border-radius:10;");
            Node header = dp.lookup(".month-year-pane");
            if (header != null)
                header.setStyle("-fx-background-color:" + BG_HDR + "; -fx-text-fill:" + TXT_MAIN + ";");
        }));
    }

    private void hideDatePickerArrow(DatePicker dp) {
        Platform.runLater(() -> {
            Node ab = dp.lookup(".arrow-button");
            if (ab != null) {
                ab.setVisible(false);
                ab.setManaged(false);
            }
        });
    }

    private void openDatePickerOnClick(DatePicker dp) {
        dp.setOnMouseClicked(e -> { if (!dp.isShowing()) dp.show(); });
        dp.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER: case SPACE: dp.show(); break;
                default: break;
            }
        });
    }

    private void styleChart(JFreeChart chart, java.awt.Color barColor) {
        chart.setBackgroundPaint(new java.awt.Color(39,43,53));                // intorno scuro
        chart.getTitle().setPaint(java.awt.Color.WHITE);
        chart.getTitle().setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 16));

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(new java.awt.Color(32,40,43));                 // fondo plot
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(new java.awt.Color(120,120,120));
        plot.setDomainGridlinesVisible(false);

        CategoryAxis x = plot.getDomainAxis();
        NumberAxis y   = (NumberAxis) plot.getRangeAxis();
        x.setLabel("Categoria");
        y.setLabel("Numero");
        x.setLabelFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));
        y.setLabelFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));
        x.setTickLabelPaint(java.awt.Color.WHITE);
        y.setTickLabelPaint(java.awt.Color.WHITE);
        x.setTickLabelFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        y.setTickLabelFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));

        if (chart.getLegend()!=null) {
            chart.getLegend().setItemPaint(java.awt.Color.WHITE);
            chart.getLegend().setBackgroundPaint(null);
            chart.getLegend().setFrame(BlockBorder.NONE);
            chart.getLegend().setPosition(RectangleEdge.BOTTOM);
            chart.getLegend().setHorizontalAlignment(HorizontalAlignment.RIGHT);
            chart.getLegend().setItemFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        }

        BarRenderer r = (BarRenderer) plot.getRenderer();
        r.setDefaultItemLabelsVisible(true);
        r.setDefaultItemLabelPaint(java.awt.Color.WHITE);
        r.setDefaultItemLabelFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        r.setSeriesPaint(0, barColor);
        r.setBarPainter(new org.jfree.chart.renderer.category.StandardBarPainter()); // colori pieni
    }

    private Node wrapChart(ChartViewer viewer) {
        VBox card = new VBox(viewer);
        card.setStyle("-fx-background-color: linear-gradient(to bottom,#242c2f,#20282b);" +
                      "-fx-background-radius:12; -fx-border-color:" + BORDER_SOFT + ";" +
                      "-fx-border-radius:12; -fx-padding:8;");
        return card;
    }

    // ====== dataset animation (bars grow from 0) ======
    private void animateDataset(final DefaultCategoryDataset ds) {
        final int steps = 30;
        final double frameMs = 18.0;

        // snapshot target values & reset to 0
        Map<Comparable<?>, Map<Comparable<?>, Number>> target = new HashMap<>();
        for (Object rkObj : ds.getRowKeys()) {
            Comparable<?> rk = (Comparable<?>) rkObj;
            Map<Comparable<?>, Number> row = new HashMap<>();
            for (Object ckObj : ds.getColumnKeys()) {
                Comparable<?> ck = (Comparable<?>) ckObj;
                Number v = ds.getValue(rk, ck);
                if (v == null) v = 0;
                row.put(ck, v);
                ds.setValue(0, rk, ck);
            }
            target.put(rk, row);
        }

        Timeline t = new Timeline();
        for (int i=1;i<=steps;i++) {
            final int step = i;
            t.getKeyFrames().add(new KeyFrame(Duration.millis(frameMs*step), ev -> {
                for (var rk : target.keySet()) {
                    for (var entry : target.get(rk).entrySet()) {
                        double to = entry.getValue().doubleValue();
                        ds.setValue(to * step / steps, rk, entry.getKey());
                    }
                }
            }));
        }
        t.play();
    }

    private void addRow(String key, String val, int row) {
        Label k = new Label(key);
        k.setStyle("-fx-text-fill:" + TXT_MUTED + "; -fx-font-weight:700;");
        Label v = new Label(val);
        v.setStyle("-fx-text-fill:" + TXT_MAIN + "; -fx-font-weight:700;");
        HBox h = new HBox(8, k, v);
        gridValori.add(h, 0, row);
    }

    private static String str(Number n) { return n==null ? "—" : String.valueOf(n); }
    private static String fmt(Double d) { return String.format(java.util.Locale.ITALY, "%.2f", d); }
    private static Number zeroIfNull(Number n){ return n==null?0:n; }

    private void showError(Exception ex) {
        ex.printStackTrace();
        Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
        a.setHeaderText("Errore generazione report");
        a.getDialogPane().setStyle("-fx-background-color: linear-gradient(to bottom,#242c2f,#20282b);" +
                                   "-fx-border-color:"+BORDER_SOFT+"; -fx-border-radius:12; -fx-background-radius:12;");
        a.showAndWait();
    }
}
