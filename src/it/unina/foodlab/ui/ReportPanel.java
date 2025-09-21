package it.unina.foodlab.ui;

import it.unina.foodlab.dao.ReportDao;
import it.unina.foodlab.util.ReportMensile;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;

import java.time.LocalDate;
import java.time.YearMonth;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.fx.ChartViewer;
import org.jfree.data.category.DefaultCategoryDataset;

public class ReportPanel extends BorderPane {

    private final ReportDao reportDao;
    private final String cfChef; // lo passi dal login

    private final DatePicker dpMese = new DatePicker(LocalDate.now());
    private final Button btnGenera = new Button("Genera Report");
    private final Label lblTitolo = new Label("Report Mensile");
    private final GridPane gridValori = new GridPane();
    private final VBox rightCharts = new VBox(12);

    public ReportPanel(String cfChef) {
        if (cfChef == null || cfChef.isBlank())
            throw new IllegalArgumentException("cfChef mancante per ReportPanel");
        this.cfChef = cfChef.trim();
        this.reportDao = new ReportDao();

        setPadding(new Insets(12));
        setTop(buildToolbar());
        setCenter(buildCenter());
        setRight(buildRight());

        btnGenera.setOnAction(e -> onGenera());
        onGenera(); // genera subito per il mese corrente
    }

    private Node buildToolbar() {
        HBox bar = new HBox(8, new Label("Scegli una data del mese:"), dpMese, btnGenera);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8));
        return bar;
    }

    private Node buildCenter() {
        lblTitolo.setFont(Font.font(18));

        gridValori.setHgap(12);
        gridValori.setVgap(8);
        gridValori.setPadding(new Insets(8));

        VBox box = new VBox(8, lblTitolo, new Separator(), gridValori);
        box.setPadding(new Insets(8));
        return box;
    }

    private Node buildRight() {
        rightCharts.setPadding(new Insets(8));
        rightCharts.setSpacing(12);
        rightCharts.setFillWidth(true);

        ScrollPane sp = new ScrollPane(rightCharts);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setPrefWidth(540); // larghezza comoda per i grafici

        return sp;
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

            // Grafico 2: statistiche ricette sulle pratiche (se esistono pratiche)
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