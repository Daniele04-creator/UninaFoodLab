package it.unina.foodlab.controller;

import it.unina.foodlab.dao.ReportDao;
import it.unina.foodlab.util.ReportMensile;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.ListCell;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.YearMonth;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.fx.ChartViewer;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;

import java.awt.Color;


public class ReportController {

  
    private final String cfChef;
    private Parent previousRoot; 
    public void setPreviousRoot(Parent n) { this.previousRoot = n; }

    
    private final ReportDao reportDao = new ReportDao();

   
    @FXML private BorderPane root;

    @FXML private Button btnIndietro;
    @FXML private ComboBox<String>  cbMese;
    @FXML private ComboBox<Integer> cbAnno;
    @FXML private Button btnCrea;

    @FXML private Label  lblTitolo;
    @FXML private VBox   cardValori;
    @FXML private GridPane gridValori;

    @FXML private ScrollPane spCharts;  
    @FXML private VBox   rightCharts;

    public ReportController(String cfChef) { this.cfChef = cfChef; }

    
    @FXML
    private void initialize() {
        initComboMeseAnno();
        if (btnCrea != null)     btnCrea.setOnAction(this::onGenera);
        if (btnIndietro != null) btnIndietro.setOnAction(e -> onIndietro());

        ensureGridColumns();
        fixChartsArea();

       
        YearMonth ym = getSelectedYearMonth();
        if (lblTitolo != null) lblTitolo.setText("Report Mensile - " + ym.getMonth() + " " + ym.getYear());

       
        Platform.runLater(() -> onGenera(null));
    }

  

    private void initComboMeseAnno() {
        if (cbMese != null) {
            cbMese.setItems(FXCollections.observableArrayList(
                "Gennaio","Febbraio","Marzo","Aprile","Maggio","Giugno",
                "Luglio","Agosto","Settembre","Ottobre","Novembre","Dicembre"
            ));
            cbMese.getSelectionModel().select(LocalDate.now().getMonthValue() - 1);
            styleReadableCombo(cbMese);
        }
        if (cbAnno != null) {
            int anno = LocalDate.now().getYear();
            ObservableList<Integer> anni = FXCollections.observableArrayList();
            for (int y = anno; y >= anno - 5; y--) anni.add(y);
            cbAnno.setItems(anni);
            cbAnno.getSelectionModel().selectFirst();
            styleReadableCombo(cbAnno);
        }
    }

   private <T> void styleReadableCombo(ComboBox<T> cb) {
    if (cb == null) return;

 
    cb.setStyle("-fx-background-color:#2e3845; -fx-background-radius:8; "
            + "-fx-border-color:#3a4657; -fx-border-radius:8; "
            + "-fx-padding:4 10; -fx-focus-color: transparent; "
            + "-fx-faint-focus-color: transparent; -fx-accent: transparent;");

 
    cb.setButtonCell(new ListCell<>() {
        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.toString());
            setStyle("-fx-text-fill:#e9f5ec; -fx-font-weight:700; -fx-background-color: transparent;");
        }
    });

    
    cb.setCellFactory(lv -> new ListCell<>() {
        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.toString());
            setStyle("-fx-text-fill:#e9f5ec; -fx-font-weight:700; -fx-background-color: transparent;");
        }
    });

    
    cb.showingProperty().addListener((obs, o, on) -> {
        if (!on) return;
        Scene sc = cb.getScene();
        if (sc != null) {
            for (Node n : sc.getRoot().lookupAll(".list-view")) {
                n.setStyle("-fx-background-color:#20282b; "
                        + "-fx-control-inner-background:#20282b; "
                        + "-fx-text-fill:#e9f5ec; "
                        + "-fx-focus-color: transparent; "
                        + "-fx-faint-focus-color: transparent; "
                        + "-fx-accent: transparent;");
            }
        }
    });
}


    private void ensureGridColumns() {
        if (gridValori != null && gridValori.getColumnConstraints().isEmpty()) {
            ColumnConstraints c1 = new ColumnConstraints();
            c1.setHalignment(HPos.LEFT);
            c1.setPercentWidth(45);

            ColumnConstraints c2 = new ColumnConstraints();
            c2.setHalignment(HPos.LEFT);
            c2.setPercentWidth(55);

            gridValori.getColumnConstraints().addAll(c1, c2);
        }
        if (cardValori != null) {
            cardValori.setStyle("-fx-background-color: linear-gradient(to bottom,#242c2f,#20282b); -fx-background-radius:12; -fx-border-color: rgba(255,255,255,0.06); -fx-border-radius:12; -fx-padding:16;");
        }
    }

    private void fixChartsArea() {
        if (spCharts != null) {
            spCharts.setFitToWidth(true);
            spCharts.setStyle("-fx-background-color: transparent; -fx-background-insets: 0;");
            Platform.runLater(() -> {
                Node vp = spCharts.lookup(".viewport");
                if (vp != null) vp.setStyle("-fx-background-color: transparent;");
            });
        }
        if (rightCharts != null) rightCharts.setFillWidth(true);
    }

    private YearMonth getSelectedYearMonth() {
        Integer a = (cbAnno != null) ? cbAnno.getValue() : null;
        int mIdx  = (cbMese != null) ? cbMese.getSelectionModel().getSelectedIndex() : -1;
        if (a != null && mIdx >= 0) return YearMonth.of(a, mIdx + 1);
        return YearMonth.from(LocalDate.now());
    }

   

    @FXML
    private void onGenera(ActionEvent e) {
        if (btnCrea != null) btnCrea.setDisable(true);
        try {
            YearMonth ym = getSelectedYearMonth();

            ReportMensile r = reportDao.getReportMensile(cfChef, ym);
            if (lblTitolo != null) lblTitolo.setText("Report Mensile - " + ym.getMonth() + " " + ym.getYear());

          
            ensureGridColumns();
            gridValori.getChildren().clear();
            addRow("Corsi totali:",      str(r.totaleCorsi()),                 0);
            addRow("Sessioni online:",   str(r.totaleOnline()),                1);
            addRow("Sessioni pratiche:", str(r.totalePratiche()),              2);
            addRow("Ricette - Media:",   (r.mediaRicettePratiche()==null ? "—" : fmt(r.mediaRicettePratiche())), 3);
            addRow("Ricette - Max:",     (r.maxRicettePratiche()==null   ? "—" : String.valueOf(r.maxRicettePratiche())), 4);
            addRow("Ricette - Min:",     (r.minRicettePratiche()==null   ? "—" : String.valueOf(r.minRicettePratiche())), 5);

           
            if (rightCharts != null) rightCharts.getChildren().clear();

          
            DefaultCategoryDataset ds1 = new DefaultCategoryDataset();
            ds1.addValue(r.totaleCorsi(),    "Conteggi", "Corsi");
            ds1.addValue(r.totaleOnline(),   "Conteggi", "Online");
            ds1.addValue(r.totalePratiche(), "Conteggi", "Pratiche");

            JFreeChart chart1 = ChartFactory.createBarChart(
                    "Corsi & Sessioni (" + ym.getMonth() + " " + ym.getYear() + ")",
                    "Categoria", "Numero", ds1);
            styleChart(chart1, new Color(66,133,244));
            ChartViewer v1 = new ChartViewer(chart1);
            v1.setStyle("-fx-background-color: transparent;");
            v1.setPrefSize(520, 320);
            if (rightCharts != null) rightCharts.getChildren().add(wrapChart(v1));
            animateDataset(ds1);

        
            DefaultCategoryDataset ds2 = new DefaultCategoryDataset();
            ds2.addValue(zeroIfNull(r.minRicettePratiche()),   "Ricette", "Min");
            ds2.addValue(zeroIfNull(r.mediaRicettePratiche()), "Ricette", "Media");
            ds2.addValue(zeroIfNull(r.maxRicettePratiche()),   "Ricette", "Max");

            JFreeChart chart2 = ChartFactory.createBarChart(
                    "Ricette per Sessioni Pratiche (" + ym.getMonth() + " " + ym.getYear() + ")",
                    "Metrica", "N. ricette", ds2);
            styleChart(chart2, new Color(250,195,0));
            ChartViewer v2 = new ChartViewer(chart2);
            v2.setStyle("-fx-background-color: transparent;");
            v2.setPrefSize(520, 320);
            if (rightCharts != null) rightCharts.getChildren().add(wrapChart(v2));
            animateDataset(ds2);
            
            chart1.getLegend().setVisible(false);
            chart2.getLegend().setVisible(false);


        } catch (Exception ex) {
            showError(ex);
        } finally {
            if (btnCrea != null) btnCrea.setDisable(false);
        }
    }

    @FXML
    private void onIndietro() {
        try {
            if (previousRoot != null && root != null) {
                root.getScene().setRoot(previousRoot);
                Stage st = (Stage) previousRoot.getScene().getWindow();
                if (st != null) st.setTitle("Corsi");
            }
        } catch (Exception ex) {
            showError(ex);
        }
    }

    

    private void addRow(String label, String value, int row) {
        Label l = new Label(label);
        l.setStyle("-fx-text-fill:#b7c5cf; -fx-font-weight:700;");
        Label v = new Label(value);
        v.setStyle("-fx-text-fill:#e9f5ec; -fx-font-weight:800;");
        GridPane.setMargin(l, new Insets(2, 0, 2, 0));
        GridPane.setMargin(v, new Insets(2, 0, 2, 0));
        gridValori.add(l, 0, row);
        gridValori.add(v, 1, row);
    }

    private String str(int n) { return String.valueOf(n); }

    private String fmt(Double val) {
        if (val == null) return "—";
        return new DecimalFormat("#,##0.00").format(val);
    }

    private double zeroIfNull(Number n) { return (n == null) ? 0.0 : n.doubleValue(); }

   

    private void styleChart(JFreeChart chart, Color accent) {
        chart.setBackgroundPaint(new Color(0x20,0x28,0x2b)); 
        chart.getTitle().setPaint(new Color(0xE9,0xF5,0xEC)); 

        if (chart.getPlot() instanceof CategoryPlot plot) {
            plot.setBackgroundPaint(new Color(0x24,0x2c,0x2f)); 
            plot.setDomainGridlinePaint(new Color(255,255,255,30));
            plot.setRangeGridlinePaint(new Color(255,255,255,30));

            if (plot.getRenderer() instanceof BarRenderer r) {
                r.setSeriesPaint(0, accent);
                r.setDrawBarOutline(false);
                r.setShadowVisible(false);
                r.setBarPainter(new org.jfree.chart.renderer.category.StandardBarPainter());
            }
            plot.getDomainAxis().setTickLabelPaint(new Color(0xCF,0xE5,0xD9));
            plot.getRangeAxis().setTickLabelPaint(new Color(0xCF,0xE5,0xD9));
            plot.getDomainAxis().setLabelPaint(new Color(0xE9,0xF5,0xEC));
            plot.getRangeAxis().setLabelPaint(new Color(0xE9,0xF5,0xEC));
        }
    }

    private Pane wrapChart(ChartViewer viewer) {
        StackPane card = new StackPane(viewer);
        card.setPadding(new Insets(10));
        card.setStyle(
            "-fx-background-color: linear-gradient(to bottom,#242c2f,#20282b);" +
            "-fx-background-radius:12; -fx-border-radius:12;" +
            "-fx-border-color: rgba(255,255,255,0.06);"
        );
        return card;
    }

    private void animateDataset(DefaultCategoryDataset ds) {
         
    }


    private void showError(Throwable ex) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Errore");
        a.setHeaderText("Operazione non riuscita");
        a.setContentText(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());

        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        TextArea ta = new TextArea(sw.toString());
        ta.setEditable(false);
        ta.setWrapText(false);
        ta.setMaxWidth(Double.MAX_VALUE);
        ta.setMaxHeight(Double.MAX_VALUE);

        GridPane gp = new GridPane();
        gp.setMaxWidth(Double.MAX_VALUE);
        gp.add(new Label("Dettagli:"), 0, 0);
        gp.add(ta, 0, 1);

        a.getDialogPane().setExpandableContent(gp);
        a.getDialogPane().setExpanded(false);
        a.showAndWait();
    }
}
