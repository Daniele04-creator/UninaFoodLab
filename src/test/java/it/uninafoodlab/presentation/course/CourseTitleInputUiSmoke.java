package it.uninafoodlab.presentation.course;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.TextField;

public final class CourseTitleInputUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var verification = new FutureTask<>(() -> {
                var field = new TextField();
                var rejection = new AtomicReference<String>();
                CourseCreationForm.configureTitleField(field, rejection::set);
                field.setText("x".repeat(30));
                field.appendText("z");
                check(field.getText().equals("x".repeat(30)), "Typing cannot exceed thirty characters");
                check(rejection.get() != null && rejection.get().contains("30"), "Rejected typing explains the limit");
                field.setText("Pizza");
                field.selectAll();
                field.replaceSelection("x".repeat(31));
                check(field.getText().equals("Pizza"), "An oversized pasted replacement preserves the entire previous title");
                for (String lineBreak : new String[]{"\r\n", "\u0085", "\u2028", "\u2029"}) {
                    field.selectAll();
                    field.replaceSelection("Pane" + lineBreak + "pizza");
                    check(field.getText().equals("Pizza"), "A multiline pasted replacement is rejected as a whole");
                    check(rejection.get().contains("una sola riga"), "Multiline rejection explains the required single line");
                }
                field.selectAll();
                field.replaceSelection("Pane e pizza");
                check(field.getText().equals("Pane e pizza"), "A valid replacement remains complete");
                field.deleteText(0, 4);
                check(field.getText().equals(" e pizza"), "Deletion remains available while editing");
                return null;
            });
            Platform.runLater(verification);
            verification.get(10, TimeUnit.SECONDS);
            System.out.println("PASS CourseTitleInputUiSmoke: title typing, full replacement rejection, multiline rejection and recovery");
        } finally { Platform.exit(); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
