package it.uninafoodlab.presentation;

import it.uninafoodlab.application.course.BrowseCourses;
import it.uninafoodlab.application.course.CourseRepository;
import it.uninafoodlab.application.notification.*;
import it.uninafoodlab.application.planning.*;
import it.uninafoodlab.domain.identity.*;
import it.uninafoodlab.presentation.notification.NotificationWorkspaceController;
import it.uninafoodlab.presentation.planning.PlanningWorkspaceController;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;





public final class PlanningNotificationUiSmoke {
    private static final AuthenticatedUser CHEF = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), Username.of("smoke.chef"), "Chef sintetico", Role.CHEF);

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            reportLoadingFailureAndRetry();
            notificationRetryAndCreatedSelection();
            System.out.println("PASS: native report loading/failure/retry and notification draft/filter/selection flows");
        } finally { Platform.exit(); }
    }

    private static BrowseCourses emptyCourses() {
        return new BrowseCourses((CourseRepository) Proxy.newProxyInstance(CourseRepository.class.getClassLoader(),
                new Class<?>[]{CourseRepository.class}, (proxy, method, args) -> List.of()));
    }

    private static void reportLoadingFailureAndRetry() throws Exception {
        var repository = new ReportRepository();
        var controller = new PlanningWorkspaceController(CHEF, emptyCourses(), new BrowseChefPlanning(repository));
        var root = fx(() -> load("planning/planning-workspace.fxml", controller));
        await(() -> !flag(field(controller, "feedback"), "busy"));
        fx(() -> { controller.showMonthlyReport(); return null; });
        await(() -> !flag(field(controller, "feedback"), "busy"));
        check(fx(() -> node(controller, "reportMetrics").isVisible()), "Initial report must render");
        check(fx(() -> label(controller, "pageTitle").isManaged()
                && label(controller, "reportScopeLabel").getText().startsWith("Tutti i tuoi corsi")
                && !label(controller, "pageDescription").isManaged()
                && root.lookup(".mock-eyebrow") == null),
                "Report heading remains visible above the selected period and course scope");
        check(fx(() -> node(controller, "reportEmptyLabel").isVisible()
                && node(controller, "recipeEmptyLabel").isVisible()), "Empty report must explain both missing series");
        repository.hasSessions = true;
        fx(() -> { invoke(field(controller, "reportPresenter"), "load"); return null; });
        await(() -> !flag(field(controller, "feedback"), "busy"));
        check(fx(() -> {
            var sessions = (javafx.scene.chart.BarChart<Number, String>) field(controller, "sessionChart");
            var recipes = (javafx.scene.chart.BarChart<Number, String>) field(controller, "recipeChart");
            return sessions.isVisible() && recipes.isVisible()
                    && sessions.getData().getFirst().getData().stream().map(value -> value.getXValue().intValue()).toList().equals(List.of(4, 2))
                    && recipes.getData().getFirst().getData().stream().map(value -> value.getXValue().doubleValue()).toList().equals(List.of(4.0, 1.25, 1.0))
                    && recipes.getData().getFirst().getData().stream()
                            .map(value -> ((Label) value.getNode().lookup(".ops-bar-value")).getText())
                            .toList().equals(List.of("4", "1,3", "1"))
                    && reportAverageValue(controller).equals("1,3")
                    && ((javafx.scene.chart.NumberAxis) recipes.getXAxis()).getTickLabelFormatter()
                            .toString(new java.math.BigDecimal("1.25")).equals("1,3");
        }), "Compact native charts must retain session counts and recipe minimum, mean, maximum");
        check(fx(() -> {
            var chart = (javafx.scene.chart.BarChart<Number, String>) field(controller, "recipeChart");
            var axis = (javafx.scene.chart.NumberAxis) chart.getXAxis();
            return axis.getLowerBound() == 0 && axis.getUpperBound() == 5 && axis.getTickUnit() == 1;
        }), "Four recipes use an integer scale from zero to five while the fractional mean stays unchanged");
        repository.maximumRecipes = 2;
        fx(() -> { invoke(field(controller, "reportPresenter"), "load"); return null; });
        await(() -> !flag(field(controller, "feedback"), "busy"));
        check(fx(() -> {
            var chart = (javafx.scene.chart.BarChart<Number, String>) field(controller, "recipeChart");
            var axis = (javafx.scene.chart.NumberAxis) chart.getXAxis();
            return axis.getUpperBound() == 3 && axis.getTickUnit() == 1
                    && chart.getData().getFirst().getData().get(1).getXValue().doubleValue() == 1.25
                    && reportAverageValue(controller).equals("1,3");
        }), "Two recipes end the scale at three, replacing 2.4 without rounding the underlying mean");
        check(fx(() -> ((javafx.scene.chart.BarChart<Number, String>) field(controller, "sessionChart"))
                        .getData().getFirst().getData().getFirst().getYValue().equals("In presenza")),
                "Practical session terminology agrees with the agenda and course details");
        check(fx(() -> ((javafx.scene.layout.FlowPane) field(controller, "reportMetrics")).getChildren().stream()
                        .filter(javafx.scene.layout.VBox.class::isInstance).map(javafx.scene.layout.VBox.class::cast)
                        .flatMap(box -> box.getChildren().stream()).filter(Label.class::isInstance)
                        .map(Label.class::cast).anyMatch(label -> label.getText().equals("Media ricette per sessione pratica"))),
                "The recipe summary explicitly names the mean and its practical-session denominator");
        verifyReportDetailsNavigation(root, controller);
        repository.fail = true;
        repository.gate = new CountDownLatch(1);
        fx(() -> {
            set(field(controller, "reportPeriod"), "updatingPeriod", true);
            combo(controller, "periodField").setValue(Month.OCTOBER);
            combo(controller, "reportYearField").setValue(2026);
            set(field(controller, "reportPeriod"), "updatingPeriod", false);
            invoke(field(controller, "reportPresenter"), "load");
            return null;
        });
        check(fx(() -> !node(controller, "reportMetrics").isVisible()
                && !node(controller, "reportCharts").isVisible()
                && button(controller, "reportDetailsButton").isDisabled()), "Old results and detail actions stay unavailable during load");
        check(fx(() -> label(controller, "reportScopeLabel").getText().contains("Ottobre 2026")), "Loading context must identify requested month");
        repository.gate.countDown();
        await(() -> !flag(field(controller, "feedback"), "busy"));
        check(fx(() -> !node(controller, "reportMetrics").isVisible()
                && button(controller, "retryButton").isVisible()), "Failure must leave results hidden with retry");
        check(fx(() -> label(controller, "feedbackLabel").getText().contains("Ottobre 2026")), "Failure must name the month");
        repository.fail = false;
        fx(() -> { button(controller, "retryButton").fire(); return null; });
        await(() -> !flag(field(controller, "feedback"), "busy"));
        check(fx(() -> node(controller, "reportMetrics").isVisible()
                && label(controller, "totalCoursesLabel").getText().equals("3")
                && label(controller, "reportScopeLabel").getText().contains("ottobre 2026")), "Retry must render requested month only");
    }

    private static void verifyReportDetailsNavigation(javafx.scene.Parent root,
            PlanningWorkspaceController controller) throws Exception {
        var period = fx(() -> combo(controller, "periodField").getValue());
        var year = fx(() -> combo(controller, "reportYearField").getValue());
        var lifecycleRepository = (SessionLifecycleRepository) Proxy.newProxyInstance(
                SessionLifecycleRepository.class.getClassLoader(), new Class<?>[]{SessionLifecycleRepository.class},
                (proxy, method, arguments) -> method.getName().equals("report") ? List.of(
                        new SessionLifecycleRepository.ReportRow(UUID.randomUUID(), "Cucina italiana: preparazione degli impasti "
                                + "e tecniche di lavorazione per pane, pizza e focaccia con lievitazione naturale",
                                ((YearMonth) arguments[1]).atDay(18).atTime(10, 0), "PRACTICAL", "COMPLETED", 2),
                        new SessionLifecycleRepository.ReportRow(UUID.randomUUID(), "Panificazione",
                                ((YearMonth) arguments[1]).atDay(22).atTime(10, 0), "ONLINE", "CANCELLED", null)) : null);
        var stage = fx(() -> {
            controller.setSessionLifecycle(new ManageSessionLifecycle(lifecycleRepository));
            var window = new javafx.stage.Stage();
            var scene = new javafx.scene.Scene(root, 1100, 800);
            scene.getStylesheets().add(PlanningNotificationUiSmoke.class.getResource(
                    "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
            window.setScene(scene); window.show();
            button(controller, "reportDetailsButton").fire();
            return window;
        });
        try {
            await(() -> root.lookup("#reportDetailsList") != null);
            fx(() -> {
                root.applyCss(); root.layout();
                check(node(controller, "reportDetailsPanel").isManaged()
                                && !node(controller, "reportPanel").isManaged(),
                        "Monthly sessions open on a separate page");
                check(button(controller, "reportDetailsBack").getGraphic() instanceof javafx.scene.image.ImageView,
                        "Report detail return has a visible left arrow");
                var list = (javafx.scene.layout.VBox) root.lookup("#reportDetailsList");
                check(list.lookupAll(".report-session-row").size() == 1, "Completed sessions appear in counted details");
                check(list.lookupAll(".report-session-title").stream().map(Label.class::cast)
                                .anyMatch(label -> label.isWrapText()
                                        && label.getText().endsWith("con lievitazione naturale")),
                        "Report session rows retain complete long course titles");
                check(list.lookupAll(".report-session-mode").stream().map(Label.class::cast)
                                .anyMatch(label -> label.getText().equals("In presenza")
                                        && label.getGraphic() instanceof javafx.scene.image.ImageView),
                        "Practical report sessions have an informative icon and readable In presenza label");
                root.lookupAll(".toggle-button").stream().map(ToggleButton.class::cast)
                        .filter(button -> button.getText().startsWith("Escluse (")).findFirst().orElseThrow().fire();
                check(list.lookupAll(".report-session-row").size() == 1, "Excluded details retain cancelled sessions");
                root.applyCss(); root.layout();
                check(list.lookupAll(".report-session-mode").stream().map(Label.class::cast)
                                .anyMatch(label -> label.getText().equals("Online")
                                        && label.getGraphic() instanceof javafx.scene.image.ImageView),
                        "Online report sessions retain a video icon and readable mode label");
                it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/report-details-1100.png");
                button(controller, "reportDetailsBack").fire();
                check(node(controller, "reportPanel").isManaged()
                                && !node(controller, "reportDetailsPanel").isManaged()
                                && combo(controller, "periodField").getValue().equals(period)
                                && combo(controller, "reportYearField").getValue().equals(year),
                        "Returning to the monthly report keeps the selected period and rendered metrics");
                return null;
            });
        } finally { fx(() -> { stage.close(); return null; }); }
    }

    private static void notificationRetryAndCreatedSelection() throws Exception {
        var repository = new NoticeRepository();
        var controller = new NotificationWorkspaceController(CHEF, new BrowseNotifications(repository),
                new ManageNotifications(repository), emptyCourses());
        try {
            var root = fx(() -> load("notification/notification-workspace.fxml", controller));
            fx(() -> { controller.activate(); return null; });
            await(() -> !flag(controller, "busy"));
            assertNotificationLayout(root, controller);
            repository.recipientFailure = true;
            fx(() -> {
                combo(controller, "historyCourseField").getSelectionModel().select(1);
                text(controller, "historySearchField").setText("precedente");
                button(controller, "composeButton").fire();
                combo(controller, "courseTargetField").getSelectionModel().selectFirst();
                text(controller, "titleField").setText("Materiali aggiornati");
                text(controller, "messageField").setText("Portare grembiule e quaderno alla prossima sessione.");
                return null;
            });
            await(() -> node(controller, "recipientRetryButton").isVisible());
            check(fx(() -> button(controller, "sendButton").isDisabled()),
                    "Unavailable recipients keep sending disabled even with a complete draft");
            assertDraft(controller);
            repository.recipientFailure = false;
            fx(() -> { button(controller, "recipientRetryButton").fire(); return null; });
            await(() -> !button(controller, "sendButton").isDisabled());
            assertDraft(controller);
            verifySendReadiness(controller, repository);
            repository.historyFailure = true;
            fx(() -> { invoke(controller, "refreshHistory"); return null; });
            await(() -> button(controller, "historyRetryButton").isVisible());
            assertDraft(controller);
            repository.historyFailure = false;
            fx(() -> { button(controller, "historyRetryButton").fire(); return null; });
            await(() -> !flag(controller, "refreshing"));
            assertDraft(controller);
            check(fx(() -> combo(controller, "historyCourseField").getValue().toString().equals("Altro corso, 14/09/2026")
                    && text(controller, "historySearchField").getText().equals("precedente")), "Retry must preserve history filters");
            repository.sendGate = new CountDownLatch(1);
            fx(() -> { button(controller, "sendButton").fire(); return null; });
            await(() -> flag(controller, "busy"));
            check(fx(() -> button(controller, "sendButton").isDisabled()
                            && text(controller, "titleField").isDisabled()
                            && text(controller, "messageField").isDisabled()),
                    "Pending submission disables sending and draft editing");
            repository.sendGate.countDown();
            await(() -> !flag(controller, "busy") && !flag(controller, "refreshing")
                    && !node(controller, "composeCard").isVisible());
            check(fx(() -> {
                var list = (ListView<?>) field(controller, "notificationList");
                var selected = (NotificationWorkspaceController.NotificationView) list.getSelectionModel().getSelectedItem();
                return selected != null && selected.id().equals(repository.createdId);
            }), "Successful registration must select the new message despite incompatible filters");
            check(fx(() -> combo(controller, "historyCourseField").getValue().toString().equals("Tutti i corsi")
                    && text(controller, "historySearchField").getText().isEmpty()
                    && label(controller, "feedbackLabel").getText().startsWith("Comunicazione inviata.")
                    && label(controller, "feedbackLabel").getText().contains("Filtri incompatibili aggiornati")), "Only incompatible filters must reset with sending feedback");
        } finally {
            if (repository.sendGate != null) repository.sendGate.countDown();
            fx(() -> { controller.dispose(); return null; });
        }
    }

    private static void verifySendReadiness(Object controller, NoticeRepository repository) throws Exception {
        fx(() -> {
            var title = text(controller, "titleField");
            var message = text(controller, "messageField");
            title.clear();
            check(button(controller, "sendButton").isDisabled(), "An empty subject disables sending");
            title.setText(" \t ");
            check(button(controller, "sendButton").isDisabled(), "Whitespace is not a subject");
            title.setText("Materiali aggiornati");
            check(!button(controller, "sendButton").isDisabled(), "Restoring a subject re-enables the complete draft");
            message.setText(" \n\t ");
            check(button(controller, "sendButton").isDisabled(), "Whitespace is not a message");
            message.setText("Portare grembiule e quaderno alla prossima sessione.");
            check(!button(controller, "sendButton").isDisabled(), "Restoring a message re-enables the complete draft");
            title.setText("ab");
            check(button(controller, "sendButton").isDisabled(), "A subject below the minimum length disables sending");
            title.setText("abc");
            message.setText("abc");
            check(!button(controller, "sendButton").isDisabled(), "Both minimum lengths permit sending");
            title.setText("t".repeat(120));
            message.setText("m".repeat(2000));
            check(!button(controller, "sendButton").isDisabled(), "Both maximum lengths permit sending");
            title.appendText("t");
            check(button(controller, "sendButton").isDisabled(), "A subject above the maximum length disables sending");
            title.setText("Materiali aggiornati");
            message.appendText("m");
            check(button(controller, "sendButton").isDisabled(), "A message above the maximum length disables sending");
            message.setText("ab");
            check(button(controller, "sendButton").isDisabled(), "A message below the minimum length disables sending");
            message.setText("Portare grembiule e quaderno alla prossima sessione.");
            check(!button(controller, "sendButton").isDisabled(), "Correcting all lengths restores sending");
            return null;
        });
        repository.recipientCount = 0;
        fx(() -> { invoke(controller, "refreshRecipientCount"); return null; });
        await(() -> label(controller, "recipientFooterLabel").getText().equals("0 destinatari"));
        check(fx(() -> button(controller, "sendButton").isDisabled()), "An empty recipient set disables sending");
        repository.recipientCount = 1;
        fx(() -> { invoke(controller, "refreshRecipientCount"); return null; });
        await(() -> !button(controller, "sendButton").isDisabled());
        assertDraft(controller);
    }

    private static void assertDraft(Object controller) throws Exception {
        check(fx(() -> text(controller, "titleField").getText().equals("Materiali aggiornati")
                && text(controller, "messageField").getText().equals("Portare grembiule e quaderno alla prossima sessione.")),
                "Failure/retry must preserve exact draft text");
    }

    private static void assertNotificationLayout(javafx.scene.Parent root, Object controller) throws Exception {
        var stage = fx(() -> {
            var scene = new javafx.scene.Scene(root, 1440, 900);
            scene.getStylesheets().add(PlanningNotificationUiSmoke.class.getResource(
                    "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
            var window = new javafx.stage.Stage();
            window.setScene(scene); window.show();
            return window;
        });
        for (int width : new int[]{1440, 1000}) {
            fx(() -> {
                stage.setWidth(width + stage.getWidth() - stage.getScene().getWidth());
                return null;
            });
            await(() -> Math.abs(root.getLayoutBounds().getWidth() - width) < 2);
            fx(() -> {
                root.applyCss(); root.layout();
                var archive = (javafx.scene.layout.Region) field(controller, "notificationArchive");
                check(label(controller, "workspaceTitleLabel").isManaged(),
                        "Communication archive has its own visible heading beside the new action");
                check(archive.getWidth() >= width - 90, "Message cards use the available page width");
                var open = root.lookupAll(".notification-open").stream().filter(javafx.scene.Node::isVisible)
                        .map(Button.class::cast).findFirst().orElseThrow(
                                () -> new AssertionError("Each communication needs an explicit open action"));
                check(open.getWidth() >= open.prefWidth(-1) - 1,
                        "Communication opening action stays readable at compact width");
                check(root.lookupAll(".notification-card-message").stream()
                                .map(Label.class::cast).allMatch(label -> label.getHeight() <= 48),
                        "The archive keeps a two-line preview while the detail retains complete text");
                var query = text(controller, "historySearchField").getText();
                var course = combo(controller, "historyCourseField").getValue();
                open.fire();
                root.applyCss(); root.layout();
                check(root.lookup(".notification-detail").isManaged()
                                && !archive.isManaged() && button(controller, "backButton").isVisible(),
                        "Opening a communication shows its complete detail on a separate page");
                check(button(controller, "backButton").getGraphic() instanceof javafx.scene.image.ImageView
                                && label(controller, "workspaceTitleLabel").isManaged()
                                && label(controller, "workspaceTitleLabel").getText().equals("Dettaglio comunicazione"),
                        "Communication detail has a page heading and visible left return arrow");
                check(!label(controller, "detailMessageLabel").getText().isBlank(),
                        "Detail keeps the complete communication text");
                check(!label(controller, "detailDateLabel").getText().contains("·")
                                && !label(controller, "detailStatusLabel").getText().contains("·")
                                && label(controller, "sentBadge").getText().equals("Inviata"),
                        "Date and recipient status remain readable without dot separators");
                var detailBorder = ((javafx.scene.layout.Region) root.lookup(".notification-detail")).getBorder();
                check(detailBorder == null || detailBorder.getStrokes().stream()
                                .allMatch(stroke -> stroke.getWidths().getTop() == 0),
                        "Communication detail has no decorative coloured top rule");
                it.uninafoodlab.presentation.FxSnapshot.save(root,
                        "target/native-match/message-detail-" + width + ".png");
                button(controller, "backButton").fire();
                root.applyCss(); root.layout();
                check(archive.isManaged() && !root.lookup(".notification-detail").isManaged()
                                && text(controller, "historySearchField").getText().equals(query)
                                && java.util.Objects.equals(combo(controller, "historyCourseField").getValue(), course),
                        "Returning restores the filtered communication list");
                check(!root.lookup(".notification-detail").isManaged(),
                        "Separate message detail does not reserve a second column");
                return null;
            });
            fx(() -> {
                it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/messages-" + width + ".png");
                return null;
            });
        }

        fx(() -> { stage.setHeight(460 + stage.getHeight() - stage.getScene().getHeight()); return null; });
        await(() -> Math.abs(root.getLayoutBounds().getHeight() - 460) < 2);
        fx(() -> {
            root.applyCss(); root.layout();
            var scroll = (ScrollPane) root.lookup(".workspace-scroll");
            var list = (ListView<NotificationWorkspaceController.NotificationView>) field(controller, "notificationList");
            var originalItems = List.copyOf(list.getItems());
            var sample = originalItems.getFirst();
            list.getItems().setAll(java.util.stream.IntStream.rangeClosed(1, 16)
                    .mapToObj(index -> new NotificationWorkspaceController.NotificationView(UUID.randomUUID(),
                            sample.title() + " " + index, sample.message(), sample.courseTitle(), sample.createdAt(),
                            sample.status(), sample.courseId(), sample.courseStartDate())).toList());
            root.applyCss(); root.layout();
            var listBounds = list.localToScene(list.getBoundsInLocal());
            check(listBounds.getMaxY() <= root.getScene().getHeight() - 20,
                    "Compact archive keeps the notification list inside the visible page: " + listBounds);
            check(scroll.getContent().getBoundsInLocal().getHeight() <= scroll.getViewportBounds().getHeight() + 1,
                    "The archive scrolls only its list, without an outer page scrollbar");
            check(list.lookupAll(".scroll-bar").stream().map(ScrollBar.class::cast)
                            .anyMatch(bar -> bar.isVisible() && bar.getOrientation() == javafx.geometry.Orientation.VERTICAL),
                    "An overflowing archive retains its native list scrollbar");
            it.uninafoodlab.presentation.FxSnapshot.save(root,
                    "target/native-match/notification-archive-short.png");
            list.getItems().setAll(originalItems);
            root.applyCss(); root.layout();
            root.lookupAll(".notification-open").stream().filter(Node::isVisible)
                    .map(Button.class::cast).findFirst().orElseThrow().fire();
            label(controller, "detailMessageLabel").setText("Messaggio completo della comunicazione. ".repeat(50));
            root.applyCss(); root.layout();
            check(scroll.getContent().getBoundsInLocal().getHeight() > scroll.getViewportBounds().getHeight(),
                    "Long communication details remain scrollable at compact height");
            scroll.setVvalue(scroll.getVmax()); root.layout();
            check(label(controller, "detailMessageLabel").localToScene(
                            label(controller, "detailMessageLabel").getBoundsInLocal()).getMaxY()
                            <= root.getScene().getHeight(),
                    "Scrolling a long detail reaches the final line of the message");
            it.uninafoodlab.presentation.FxSnapshot.save(root,
                    "target/native-match/notification-detail-end-short.png");
            button(controller, "backButton").fire();
            scroll.setVvalue(scroll.getVmin());
            button(controller, "composeButton").fire();
            root.applyCss();
            root.layout();

            root.layout();
            var composer = node(controller, "composeCard");
            check(((TextArea) field(controller, "messageField")).getHeight() >= 150,
                    "Compact composer preserves a multiline message editor instead of shrinking to one row");
            check(label(controller, "workspaceTitleLabel").isManaged()
                            && label(controller, "workspaceTitleLabel").getText().equals("Nuova comunicazione"),
                    "Composer retains a specific operation heading");
            var save = button(controller, "sendButton");
            check(save.getText().equals("Invia") && save.getAccessibleText().contains("Invia"),
                    "The composer exposes sending as its clear keyboard and visible action");
            check(save.localToScene(save.getBoundsInLocal()).getMaxX()
                    <= composer.localToScene(composer.getBoundsInLocal()).getMaxX(),
                    "Composer action must stay within its surface at compact width");
            check(save.localToScene(save.getBoundsInLocal()).getMaxY() <= root.getScene().getHeight(),
                    "Sending stays visible below the scrollable form at compact height");
            var sendPosition = save.localToScene(save.getBoundsInLocal());
            scroll.setVvalue(scroll.getVmax());
            root.layout();
            check(Math.abs(save.localToScene(save.getBoundsInLocal()).getMinY() - sendPosition.getMinY()) < 1,
                    "Scrolling the form leaves its send action and recipient count stationary: "
                            + sendPosition + " -> " + save.localToScene(save.getBoundsInLocal()));
            it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/new-message-1000.png");
            it.uninafoodlab.presentation.FxSnapshot.save(root,
                    "target/native-match/notification-composer-short.png");
            button(controller, "backButton").fire();
            check(!node(controller, "composerActionBar").isVisible()
                            && !node(controller, "composerActionBar").isManaged(),
                    "Returning to the archive hides the compose-only action bar");
            return null;
        });
    }

    private static javafx.scene.Parent load(String resource, Object controller) throws Exception {
        var loader = new FXMLLoader(PlanningNotificationUiSmoke.class.getResource("/it/uninafoodlab/presentation/" + resource));
        loader.setController(controller);
        return loader.load();
    }

    private static final class ReportRepository implements ChefPlanningRepository {
        volatile boolean fail;
        volatile boolean hasSessions;
        volatile int maximumRecipes = 4;
        volatile CountDownLatch gate = new CountDownLatch(0);
        public List<IngredientRequirement> requirements(UUID token, UUID session) { return List.of(); }
        public List<SessionParticipant> participants(UUID token, UUID session) { return List.of(); }
        public List<CourseParticipant> courseParticipants(UUID token, UUID course) { return List.of(); }
        public MonthlyReport monthlyReport(UUID token, YearMonth month) {
            try { if (!gate.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test gate timed out"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            if (fail) throw new IllegalStateException("Synthetic report failure");
            return hasSessions
                    ? new MonthlyReport(month, 3, 2, 4, Optional.of(new java.math.BigDecimal("1.25")), Optional.of(1), Optional.of(maximumRecipes))
                    : new MonthlyReport(month, 3, 0, 0, Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    private static final class NoticeRepository implements NotificationRepository {
        volatile boolean recipientFailure;
        volatile boolean historyFailure;
        volatile long recipientCount = 1;
        volatile CountDownLatch sendGate;
        final UUID createdId = UUID.randomUUID();
        volatile List<ChefNotification> history = List.of(new ChefNotification(UUID.randomUUID(), "Comunicazione precedente",
                "Per la prossima sessione pratica portare un grembiule e un contenitore pulito. "
                        + "Prepariamo insieme il menu e controlliamo gli ingredienti prima di iniziare. "
                        + "L'appuntamento resta alle 10:00 nel laboratorio indicato nel calendario. "
                        + "Per consultare le dosi aprire le ricette della sessione: le quantità sono riferite a una persona. "
                        + "Ricordare di confermare l'adesione entro la scadenza, così possiamo organizzare le quantità senza sprechi.", NotificationReason.GENERAL, Optional.of("Altro corso"), Instant.now(), 1, Optional.of(UUID.randomUUID()), Optional.of(LocalDate.of(2026, 9, 14))));
        public long countRecipients(UUID token, Optional<UUID> course) {
            if (recipientFailure) throw new IllegalStateException("Synthetic recipient failure");
            return recipientCount;
        }
        public UUID send(UUID token, Optional<UUID> course, NotificationDraft draft) {
            if (sendGate != null) {
                try { sendGate.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
            }
            history = List.of(new ChefNotification(createdId, draft.title(), draft.message(), NotificationReason.GENERAL,
                    Optional.empty(), Instant.now(), 1), history.getFirst());
            return createdId;
        }
        public void markRead(UUID token, UUID notification) { throw new UnsupportedOperationException(); }
        public List<ParticipantNotification> findForParticipant(UUID token) { return List.of(); }
        public List<ChefNotification> findForChef(UUID token) {
            if (historyFailure) throw new IllegalStateException("Synthetic history failure");
            return history;
        }
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static void await(BooleanSupplier condition) throws Exception {
        it.uninafoodlab.presentation.FxAwait.until(condition::getAsBoolean, 10);
    }

    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target);
    }
    private static Node node(Object target, String name) { return (Node) field(target, name); }
    private static Label label(Object target, String name) { return (Label) field(target, name); }
    private static String reportAverageValue(Object controller) {
        var reportView = field(field(controller, "reportPresenter"), "monthlyReportView");
        return label(reportView, "averageRecipesValue").getText();
    }
    private static Button button(Object target, String name) { return (Button) field(target, name); }
    private static TextInputControl text(Object target, String name) { return (TextInputControl) field(target, name); }
    @SuppressWarnings("unchecked")
    private static ComboBox<Object> combo(Object target, String name) { return (ComboBox<Object>) field(target, name); }
    private static boolean flag(Object target, String name) { return (boolean) field(target, name); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
