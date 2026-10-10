package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.application.planning.IngredientRequirement;
import it.uninafoodlab.presentation.common.FxAsyncExecutor;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.List;
import javafx.print.PrinterJob;
import javafx.application.Platform;
import java.util.concurrent.CancellationException;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class ShoppingListPrinter {
    private ShoppingListPrinter() {}

    public static void print(Window owner, String context, List<IngredientRequirement> rows, PrintingProgress progress) {
        var job = PrinterJob.createPrinterJob();
        if (job == null) {
            progress.printingFailed("Nessuna stampante disponibile. Configura una stampante o una stampante PDF.");
            return;
        }
        if (!job.showPrintDialog(owner)) return;
        submit(job, context, rows, progress);
    }

    public interface PrintingProgress {
        void printingStarted();
        void printingCompleted();
        void printingCancelled();
        void printingFailed(String message);
    }

    static void submit(PrinterJob job, String context, List<IngredientRequirement> rows, PrintingProgress progress) {
        var snapshot = List.copyOf(rows);
        progress.printingStarted();
        FxAsyncExecutor.run("shopping-list-print", () -> {
            try {
                printJob(job, context, snapshot);
                return true;
            } catch (RuntimeException failure) {
                job.cancelJob();
                throw failure;
            }
        }, ignored -> progress.printingCompleted(), failure -> {
            if (failure instanceof CancellationException) progress.printingCancelled();
            else progress.printingFailed("Stampa non completata. Controlla la stampante e riprova.");
        });
    }

    static void printJob(PrinterJob job, String context, List<IngredientRequirement> rows) {
        if (Platform.isFxApplicationThread()) throw new IllegalStateException("Printing must run outside the JavaFX thread");
        var layout = job.getJobSettings().getPageLayout();
        int columns = Math.max(20, (int) (layout.getPrintableWidth() / 6.2));
        var lines = printLines(context, rows, columns);
        int pageSize = Math.max(1, (int) ((layout.getPrintableHeight() - 40) / 15));
        int pages = Math.max(1, (lines.size() + pageSize - 1) / pageSize);
        for (int page = 0; page < pages; page++) {
            var content = new VBox(0);
            content.setPrefWidth(layout.getPrintableWidth());
            var title = new Label("UninaFoodLab, Lista della spesa, pagina " + (page + 1) + " / " + pages);
            title.setMinHeight(35);
            content.getChildren().add(title);
            for (var line : lines.subList(page * pageSize, Math.min(lines.size(), (page + 1) * pageSize))) {
                var label = new Label(line);
                label.setFont(javafx.scene.text.Font.font("Monospaced", 10));
                label.setMinHeight(15);
                content.getChildren().add(label);
            }
            if (!job.printPage(layout, content)) throw printFailure(job);
        }
        if (!job.endJob()) throw printFailure(job);
    }

    private static RuntimeException printFailure(PrinterJob job) {
        return job.getJobStatus() == PrinterJob.JobStatus.CANCELED
                ? new CancellationException("Print job cancelled") : new IllegalStateException("Print job failed");
    }

    static List<String> printLines(String context, List<IngredientRequirement> rows, int columns) {
        var lines = new java.util.ArrayList<String>();
        wrap(stamped(context), columns, lines);
        lines.add("");
        for (var row : rows) wrap(row.ingredientName() + " — " + row.requiredQuantity().toPlainString()
                + " " + row.unit().symbol(), columns, lines);
        return List.copyOf(lines);
    }

    private static String stamped(String context) {
        return context + ", Stampa " + ZonedDateTime.now(ZoneId.of("Europe/Rome"))
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm"));
    }

    private static void wrap(String text, int columns, java.util.List<String> lines) {
        for (String paragraph : text.split("\\R", -1)) {
            for (int start = 0; start < paragraph.length(); start += columns) {
                lines.add(paragraph.substring(start, Math.min(start + columns, paragraph.length())));
            }
        }
    }
}
