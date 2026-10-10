package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.application.planning.IngredientRequirement;
import it.uninafoodlab.domain.catalog.MeasurementUnit;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import javafx.application.Platform;
import javafx.print.Printer;
import javafx.print.PrinterJob;

public final class PlanningPrintUiSmoke {
    private PlanningPrintUiSmoke() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Specify a new PDF output path");
        var output = Path.of(args[0]).toAbsolutePath();
        if (Files.exists(output)) throw new IllegalArgumentException("Output must not already exist");
        Files.createDirectories(output.getParent());
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var printed = new java.util.concurrent.CompletableFuture<PrinterJob>();
            var pulseDuringPrint = new java.util.concurrent.CompletableFuture<Void>();
            var task = new FutureTask<Void>(() -> {
                var printer = Printer.getAllPrinters().stream()
                        .filter(candidate -> candidate.getName().equals("Microsoft Print to PDF"))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Microsoft Print to PDF unavailable"));
                var job = PrinterJob.createPrinterJob(printer);
                if (job == null) throw new IllegalStateException("PDF print job unavailable");
                job.getJobSettings().setOutputFile(output.toString());
                var rows = new java.util.ArrayList<>(IntStream.rangeClosed(1, 100).mapToObj(index ->
                        new IngredientRequirement("Ingrediente sintetico " + index, MeasurementUnit.GRAM,
                                9, BigDecimal.valueOf(index * 9L))).toList());
                boolean rejected = false;
                try {
                    ShoppingListPrinter.printJob(job, "Guard verification", rows);
                } catch (IllegalStateException failure) {
                    rejected = failure.getMessage().equals("Printing must run outside the JavaFX thread");
                }
                if (!rejected) throw new AssertionError("Printing must reject execution on the JavaFX thread");
                ShoppingListPrinter.submit(job, "Collaudo PDF, 02/10/2026, 9 confermati", rows,
                        new ShoppingListPrinter.PrintingProgress() {
                            public void printingStarted() {
                                checkFxCallback();
                                Platform.runLater(() -> {
                                    if (!printed.isDone()) pulseDuringPrint.complete(null);
                                });
                            }
                            public void printingCompleted() {
                                checkFxCallback();
                                if (job.getJobStatus() != PrinterJob.JobStatus.DONE) {
                                    printed.completeExceptionally(new AssertionError("PDF print job was not completed"));
                                } else printed.complete(job);
                            }
                            public void printingCancelled() {
                                printed.completeExceptionally(new AssertionError("PDF print was unexpectedly cancelled"));
                            }
                            public void printingFailed(String message) {
                                printed.completeExceptionally(new AssertionError(message));
                            }
                        });
                rows.clear();
                return null;
            });
            Platform.runLater(task);
            task.get(10, TimeUnit.SECONDS);
            pulseDuringPrint.get(10, TimeUnit.SECONDS);
            printed.get(45, TimeUnit.SECONDS);
            verifyCancellation();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!completePdf(output)) {
                if (System.nanoTime() >= deadline) throw new IllegalStateException("PDF spool output incomplete");
                Thread.sleep(100);
            }
            System.out.println("PlanningPrintUiSmoke: PASS (worker printing, responsive JavaFX, copied rows, UI callbacks, cancellation; inspect PDF for all 100 rows)");
        } finally {
            Platform.exit();
        }
    }

    private static void checkFxCallback() {
        if (!Platform.isFxApplicationThread()) throw new AssertionError("Printing feedback must run on JavaFX");
    }

    private static void verifyCancellation() throws Exception {
        var cancelled = new java.util.concurrent.CompletableFuture<Void>();
        Platform.runLater(() -> {
            var printer = Printer.getAllPrinters().stream()
                    .filter(candidate -> candidate.getName().equals("Microsoft Print to PDF")).findFirst().orElseThrow();
            var job = PrinterJob.createPrinterJob(printer);
            if (job == null) throw new IllegalStateException("PDF print job unavailable");
            job.cancelJob();
            ShoppingListPrinter.submit(job, "Cancelled print", java.util.List.of(), new ShoppingListPrinter.PrintingProgress() {
                public void printingStarted() { checkFxCallback(); }
                public void printingCompleted() { cancelled.completeExceptionally(new AssertionError("Cancelled job reported success")); }
                public void printingCancelled() { checkFxCallback(); cancelled.complete(null); }
                public void printingFailed(String message) { cancelled.completeExceptionally(new AssertionError("Cancellation reported as failure: " + message)); }
            });
        });
        cancelled.get(10, TimeUnit.SECONDS);
    }

    private static boolean completePdf(Path output) throws java.io.IOException {
        if (!Files.exists(output) || Files.size(output) == 0) return false;
        var content = Files.readString(output, StandardCharsets.ISO_8859_1);
        return content.startsWith("%PDF-") && content.stripTrailing().endsWith("%%EOF");
    }
}
