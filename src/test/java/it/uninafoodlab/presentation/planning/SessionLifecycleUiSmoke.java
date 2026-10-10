package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.application.planning.ManageSessionLifecycle;
import it.uninafoodlab.application.planning.SessionLifecycleRepository;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import it.uninafoodlab.presentation.FxAwait;
import it.uninafoodlab.presentation.FxSnapshot;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class SessionLifecycleUiSmoke {
    private SessionLifecycleUiSmoke() {}

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        var sessionId = UUID.randomUUID();
        var repository = (SessionLifecycleRepository) Proxy.newProxyInstance(
                SessionLifecycleRepository.class.getClassLoader(), new Class<?>[] {SessionLifecycleRepository.class},
                (proxy, method, values) -> switch (method.getName()) {
                    case "report" -> List.of(new SessionLifecycleRepository.ReportRow(sessionId,
                            "Sapori di casa", LocalDateTime.of(2026, 9, 18, 15, 0), "PRACTICAL", "COMPLETED", 1));
                    default -> throw new AssertionError("Operazione non prevista: " + method.getName());
                });
        var lifecycle = new ManageSessionLifecycle(repository);
        var user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Username.of("smoke.chef"), "Chef dimostrativo", Role.CHEF);
        try {
            var owner = fx(() -> {
                var stage = new Stage();
                stage.setScene(new Scene(new VBox(), 1000, 650));
                stage.show();
                return stage;
            });
            verifyInlineReport(owner, user, lifecycle);
            System.out.println("PASS: automatic monthly report and inline details rendered");
        } finally {
            fx(() -> { List.copyOf(Window.getWindows()).forEach(Window::hide); return null; });
            Platform.exit();
        }
    }

    private static void verifyInlineReport(Stage owner, AuthenticatedUser user, ManageSessionLifecycle lifecycle) throws Exception {
        var content = new VBox(16);
        var details = fx(() -> {
            var view = new ReportDetailsView(content, user, () -> lifecycle, () -> YearMonth.of(2026, 9));
            owner.getScene().setRoot(content);
            view.show();
            return view;
        });
        FxAwait.until(() -> content.lookup("#reportDetailsList") != null, 10);
        fx(() -> {
            var list = (VBox) content.lookup("#reportDetailsList");
            if (list.lookupAll(".report-session-row").size() != 1) throw new AssertionError("Il dettaglio deve mostrare le sessioni svolte");
            FxSnapshot.save(content, "target/ui-polish/report.png");
            content.lookupAll(".toggle-button").stream().filter(javafx.scene.control.ToggleButton.class::isInstance)
                    .map(javafx.scene.control.ToggleButton.class::cast).filter(button -> button.getText().startsWith("Escluse ("))
                    .findFirst().orElseThrow().fire();
            if (!list.lookupAll(".report-session-row").isEmpty()) throw new AssertionError("Le sessioni svolte non devono apparire fra le escluse");
            details.reset();
            if (!content.getChildren().isEmpty()) {
                throw new AssertionError("Il cambio mese deve svuotare i dettagli precedenti");
            }
            return null;
        });
    }

    private static <T> T fx(Callable<T> task) throws Exception {
        var future = new FutureTask<>(task);
        Platform.runLater(future);
        return future.get(15, java.util.concurrent.TimeUnit.SECONDS);
    }
}
