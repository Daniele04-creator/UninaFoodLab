package it.unina.foodlab.controller;

import it.unina.foodlab.dao.ReportDao;
import it.unina.foodlab.util.ReportMensile;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
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
	@FXML private ComboBox<String> cbMese;
	@FXML private ComboBox<Integer> cbAnno;
	@FXML private Button btnCrea;
	@FXML private Label lblTitolo;
	@FXML private VBox cardValori;
	@FXML private GridPane gridValori;
	@FXML private ScrollPane spCharts;
	@FXML private VBox rightCharts;

	public ReportController(String cfChef) {
		this.cfChef = cfChef;
	}

	@FXML
	private void initialize() {
	    initComboMeseAnno();
	    YearMonth ym = getSelectedYearMonth();
	    lblTitolo.setText("Report Mensile - " + ym.getMonth() + " " + ym.getYear());
	    Platform.runLater(() -> onGenera(null));
	}

	private void initComboMeseAnno() {
	    int meseCorrente = LocalDate.now().getMonthValue();
	    int annoCorrente = LocalDate.now().getYear();

	    cbMese.setItems(FXCollections.observableArrayList(
	            "Gennaio","Febbraio","Marzo","Aprile","Maggio","Giugno",
	            "Luglio","Agosto","Settembre","Ottobre","Novembre","Dicembre"
	    ));
	    cbMese.getSelectionModel().select(meseCorrente - 1);

	    ObservableList<Integer> anni = FXCollections.observableArrayList();
	    for (int y = annoCorrente; y >= annoCorrente - 5; y--) {
	        anni.add(y);
	    }
	    cbAnno.setItems(anni);
	    cbAnno.getSelectionModel().selectFirst();
	}


	private YearMonth getSelectedYearMonth() {
	    Integer anno = cbAnno.getValue();
	    int meseIndex = cbMese.getSelectionModel().getSelectedIndex();

	    if (anno != null && meseIndex >= 0) {
	        return YearMonth.of(anno, meseIndex + 1);
	    }
	    return YearMonth.from(LocalDate.now());
	}


	@FXML
private void onGenera(ActionEvent e) {
    btnCrea.setDisable(true);
    try {
        YearMonth ym = getSelectedYearMonth();
        ReportMensile r = reportDao.getReportMensile(cfChef, ym);

        lblTitolo.setText("Report Mensile - " + ym.getMonth() + " " + ym.getYear());

        gridValori.getChildren().clear();

        addRow("Corsi totali:",       r.totaleCorsi() + "",      0);
        addRow("Sessioni online:",    r.totaleOnline() + "",     1);
        addRow("Sessioni pratiche:",  r.totalePratiche() + "",   2);
        addRow("Ricette - Media:",    r.mediaRicettePratiche() == null ? "—" : fmt(r.mediaRicettePratiche()), 3);
        addRow("Ricette - Max:",      r.maxRicettePratiche()   == null ? "—" : String.valueOf(r.maxRicettePratiche()), 4);
        addRow("Ricette - Min:",      r.minRicettePratiche()   == null ? "—" : String.valueOf(r.minRicettePratiche()), 5);

        rightCharts.getChildren().clear();

        DefaultCategoryDataset ds1 = new DefaultCategoryDataset();
        ds1.addValue(r.totaleCorsi(),   "Conteggi", "Corsi");
        ds1.addValue(r.totaleOnline(),  "Conteggi", "Online");
        ds1.addValue(r.totalePratiche(),"Conteggi", "Pratiche");

        JFreeChart chart1 = ChartFactory.createBarChart(
                "Corsi & Sessioni (" + ym.getMonth() + " " + ym.getYear() + ")",
                "Categoria", "Numero", ds1);
        styleChart(chart1, new Color(66, 133, 244));
        ChartViewer v1 = new ChartViewer(chart1);
        v1.setPrefSize(520, 320);
        rightCharts.getChildren().add(wrapChart(v1));

        DefaultCategoryDataset ds2 = new DefaultCategoryDataset();
        ds2.addValue(zeroIfNull(r.minRicettePratiche()),   "Ricette", "Min");
        ds2.addValue(zeroIfNull(r.mediaRicettePratiche()), "Ricette", "Media");
        ds2.addValue(zeroIfNull(r.maxRicettePratiche()),   "Ricette", "Max");

        JFreeChart chart2 = ChartFactory.createBarChart(
                "Ricette per Sessioni Pratiche (" + ym.getMonth() + " " + ym.getYear() + ")",
                "Metrica", "N. ricette", ds2);
        styleChart(chart2, new Color(250, 195, 0));
        ChartViewer v2 = new ChartViewer(chart2);
        v2.setPrefSize(520, 320);
        rightCharts.getChildren().add(wrapChart(v2));

        chart1.getLegend().setVisible(false);
        chart2.getLegend().setVisible(false);

    } catch (Exception ex) {
        ex.printStackTrace();
        showError("Errore durante la generazione del report.");
    } finally {
        btnCrea.setDisable(false);
    }
}

	@FXML
private void onIndietro() {
    try {
        root.getScene().setRoot(previousRoot);
        Stage st = (Stage) previousRoot.getScene().getWindow();
        st.setTitle("Corsi");
    } catch (Exception ex) {
        ex.printStackTrace();
        showError("Errore nel ritorno alla schermata dei corsi.");
    }
}


	private void addRow(String label, String value, int row) {
		Label l = new Label(label);
		Label v = new Label(value);
		GridPane.setMargin(l, new Insets(2, 0, 2, 0));
		GridPane.setMargin(v, new Insets(2, 0, 2, 0));
		gridValori.add(l, 0, row);
		gridValori.add(v, 1, row);
	}


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
		card.getStyleClass().add("chart-card");
		return card;
	}


	private void showError(String message) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle("Errore");
    alert.setHeaderText("Operazione non riuscita");
    alert.setContentText(message);

    DialogPane pane = alert.getDialogPane();
    pane.getStylesheets().add(
            getClass().getResource("/it/unina/foodlab/util/dark-theme.css").toExternalForm()
    );
    pane.getStyleClass().add("dark-alert");

    alert.showAndWait();
}

}
