package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.presentation.FxSnapshot;
import it.uninafoodlab.application.catalog.BrowseCatalog;
import it.uninafoodlab.application.catalog.CatalogRepository;
import it.uninafoodlab.application.catalog.CategorySummary;
import it.uninafoodlab.application.course.BrowsePublishedCourses;
import it.uninafoodlab.application.course.CourseRepository;
import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.application.course.PublishedCourseSummary;
import it.uninafoodlab.application.course.PublishedSessionRecipeSummary;
import it.uninafoodlab.application.enrollment.BrowseEnrollments;
import it.uninafoodlab.application.enrollment.EnrollmentRepository;
import it.uninafoodlab.application.enrollment.EnrollmentSummary;
import it.uninafoodlab.application.enrollment.ManageEnrollment;
import it.uninafoodlab.application.enrollment.ManagePracticalAttendance;
import it.uninafoodlab.application.enrollment.PracticalAttendanceSummary;
import it.uninafoodlab.application.notification.BrowseNotifications;
import it.uninafoodlab.application.notification.ManageNotifications;
import it.uninafoodlab.application.notification.NotificationRepository;
import it.uninafoodlab.application.notification.ParticipantNotification;
import it.uninafoodlab.application.notification.NotificationReason;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;


public final class ParticipantWorkspaceUiSmoke {

    private ParticipantWorkspaceUiSmoke() {}

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var errorState = new FakeState();
            errorState.failCourseDetail.set(true);
            var errorController = errorState.controller();
            var errorScroll = new AtomicReference<javafx.scene.Parent>();
            var errorStage = fx(() -> {
                var loader = new FXMLLoader(ParticipantWorkspaceUiSmoke.class.getResource(
                        "participant-workspace.fxml"));
                loader.setController(errorController);
                var root = (javafx.scene.Parent) loader.load();
                errorScroll.set(root);
                var scene = new Scene(root, 1142, 633);
                scene.getStylesheets().add(ParticipantWorkspaceUiSmoke.class.getResource(
                        "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
                var stage = new Stage();
                stage.setScene(scene);
                stage.show();
                ((javafx.scene.control.ScrollPane) root.lookup("#rootScroll")).setVvalue(0.5);
                return stage;
            });
            await(() -> label(errorController, "feedbackLabel").getText()
                    .contains("Non è stato possibile caricare il programma"));
            fx(() -> {
                check(((javafx.scene.control.ScrollPane) errorScroll.get().lookup("#rootScroll")).getVvalue() <= 0.001,
                        "participant workspace returns to the top after an initial detail error");
                errorStage.close();
                return null;
            });

            var state = new FakeState();
            var controller = state.controller();
            var rootScroll = new AtomicReference<javafx.scene.Parent>();
            var stageRef = new AtomicReference<Stage>();
            fx(() -> {
                var loader = new FXMLLoader(ParticipantWorkspaceUiSmoke.class.getResource(
                        "participant-workspace.fxml"));
                loader.setController(controller);
                var root = (javafx.scene.Parent) loader.load();
                rootScroll.set(root);
                var scene = new Scene(root, 1142, 633);
                scene.getStylesheets().add(ParticipantWorkspaceUiSmoke.class.getResource(
                        "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
                var stage = new Stage();
                stage.setScene(scene);
                stage.show();
                stageRef.set(stage);
                return null;
            });
            await(() -> !controller.isBusy());
            verifyProfileMenu(controller, rootScroll.get());
            captureWidths(stageRef.get(), rootScroll.get(), "participant-discover");
            verifyTwoCourseCardsFillRow(stageRef.get(), rootScroll.get(), controller, state);
            fx(() -> {
                var workspace = (VBox) rootScroll.get();
                check(workspace.getBackground().getFills().stream().anyMatch(fill ->
                                javafx.scene.paint.Color.web("#FAFAF8").equals(fill.getFill())),
                        "Participant workspace uses the same sage backdrop behind white cards");
                var viewport = (javafx.scene.layout.Region) rootScroll.get().lookup(".viewport");
                check(viewport.getBackground().getFills().stream().anyMatch(fill ->
                                javafx.scene.paint.Color.web("#FAFAF8").equals(fill.getFill())),
                        "Participant viewport keeps the sage backdrop below short card lists");
                var account = (javafx.scene.control.MenuButton) rootScroll.get().lookup(".participant-topbar .profile-button");
                check(account != null && account.getText().equals("Partecipante demo")
                                && account.getItems().getFirst() instanceof javafx.scene.control.CustomMenuItem identity
                                && ((javafx.scene.control.Label) identity.getContent().lookup(".profile-menu-details"))
                                        .getText().equals("partecipante.smoke"),
                        "Participant profile shows the name with username inside its menu");
                check(((javafx.scene.control.ScrollPane) rootScroll.get().lookup("#rootScroll")).getVvalue() <= 0.001,
                        "participant workspace starts at the top of the catalog");
                check(rootScroll.get().lookup(".participant-page-heading").isManaged()
                                && ((javafx.scene.control.Label) rootScroll.get().lookup("#workspaceTitle")).getText().equals("Corsi disponibili"),
                        "Participant catalog exposes its page heading above the filters");
                check(list(controller, "courseList").getItems().size() == 2,
                        "two published courses load for the catalog layout");
                check(label(controller, "courseTitle").getText().contains("Pane"),
                        "selected course detail is visible");
                check(onlineLinkButton(controller) == null,
                        "online link stays hidden before enrolment");
                check(rootScroll.get().lookup("#courseGrid").lookupAll(".label").stream()
                                .map(javafx.scene.control.Label.class::cast)
                                .noneMatch(item -> item.getText().equals("Un laboratorio di impasti lenti.")),
                        "Participant catalog cards keep the full description in the course detail");
                FxSnapshot.save(rootScroll.get(), "target/ui-polish/participant-catalog.png");
                fireCard(rootScroll.get(), "#courseGrid", "Apri corso →");
                rootScroll.get().applyCss(); rootScroll.get().layout();
                check(((ButtonBase) ((VBox) rootScroll.get().lookup("#courseDetailPanel")).getChildren().getFirst()).getGraphic() != null,
                        "Course detail exposes a visible graphical back arrow");
                check(rootScroll.get().lookup(".participant-page-heading").isManaged(),
                        "Participant detail retains its full course heading");
                verifyLongDetailHeading(rootScroll.get());
                check(label(controller, "courseDescription").isVisible()
                                && label(controller, "courseDescription").isManaged()
                                && label(controller, "courseDescription").getText()
                                        .equals("Un laboratorio di impasti lenti."),
                        "Participant course detail shows the complete description after opening the card");
                FxSnapshot.save(rootScroll.get(), "target/ui-polish/participant-program.png");
                return null;
            });
            verifyProgramWidths(stageRef.get(), rootScroll.get(), controller, List.of("sessionList", "menuList"));
            fx(() -> {
                var root = rootScroll.get();
                var scroll = (javafx.scene.control.ScrollPane) root.lookup("#rootScroll");
                var topbar = root.lookup(".participant-topbar");
                var top = topbar.localToScene(topbar.getBoundsInLocal()).getMinY();
                scroll.setVvalue(1); root.layout();
                check(Math.abs(topbar.localToScene(topbar.getBoundsInLocal()).getMinY() - top) < 0.1,
                        "Main participant navigation stays fixed while the program scrolls");
                check(!scroll.getContent().lookupAll(".participant-topbar").contains(topbar),
                        "The participant navigation remains outside the program scroll content");
                FxSnapshot.save(root, "target/ui-polish/participant-fixed-navigation.png");
                scroll.setVvalue(0); return null;
            });
            verifySessionStatePresentation(controller);
            fx(() -> {
                button(controller, "enrollButton").fire();
                return null;
            });
            await(() -> !controller.isBusy());
            check(state.enrollment.get() != null, "enrolment write reaches repository");
            fx(() -> {
                var onlineLink = onlineLinkButton(controller);
                check(onlineLink != null, "authorised online link is visible after enrolment");
                check(!onlineLink.isDisabled(), "authorised online link must be enabled after refresh; busy=" + controller.isBusy());
                onlineLink.fire();
                check(state.onlineUrl.toString().equals(Clipboard.getSystemClipboard().getString()),
                        "authorised online link is copied to the native clipboard: " + Clipboard.getSystemClipboard().getString());
                button(controller, "mineTab").fire();
                return null;
            });
            await(() -> !controller.isBusy());
            captureWidths(stageRef.get(), rootScroll.get(), "participant-my-courses");
            fx(() -> {
                check(list(controller, "enrollmentList").getItems().size() == 1,
                        "own enrolments render");
                check(list(controller, "attendanceList").getItems().size() == 1,
                        "practical attendance renders");
                FxSnapshot.save(rootScroll.get(), "target/ui-polish/participant-courses.png");
                fireCard(rootScroll.get(), "#enrollmentGrid", "Apri corso →");
                return null;
            });
            await(() -> !controller.isBusy());
            verifyProgramWidths(stageRef.get(), rootScroll.get(), controller, List.of("sessionList", "menuList", "attendanceList"));
            fx(() -> {
                rootScroll.get().applyCss(); rootScroll.get().layout();
                FxSnapshot.save(rootScroll.get(), "target/ui-polish/participant-attendance.png");
                fireFirstAttendanceConfirmation(controller);
                return null;
            });
            await(() -> !controller.isBusy());
            check(state.attendance.get() == AttendanceStatus.CONFIRMED,
                    "attendance confirm reaches repository");
            fx(() -> {
                button(controller, "notificationsTab").fire();
                return null;
            });
            await(() -> !controller.isBusy());
            fx(() -> {
                check(list(controller, "notificationList").getItems().size() == 1,
                        "notification inbox renders");
                list(controller, "notificationList").getSelectionModel().selectFirst();
                FxSnapshot.save(rootScroll.get(), "target/ui-polish/participant-inbox.png");
                fireCard(rootScroll.get(), "#notificationCards", "Apri comunicazione →");
                check(((ButtonBase) ((VBox) rootScroll.get().lookup("#notificationDetailPanel")).getChildren().getFirst()).getGraphic() != null,
                        "Communication detail exposes a visible graphical back arrow");
                check(rootScroll.get().lookup("#notificationDetailPanel").isManaged(),
                        "Opening a communication reveals its complete detail");
                check(!rootScroll.get().lookup("#notificationCards").isManaged(),
                        "Communication rows give way to the readable message detail");
                FxSnapshot.save(rootScroll.get(), "target/ui-polish/participant-message-detail.png");
                button(controller, "markReadButton").fire();
                return null;
            });
            await(() -> !controller.isBusy());
            check(state.read.get(), "mark-read write reaches repository");
            fx(() -> {
                list(controller, "notificationList").getSelectionModel().selectFirst();
                check(button(controller, "openNotificationCourseButton").isVisible(),
                        "linked notification exposes the authorised course link");
                button(controller, "openNotificationCourseButton").fire();
                return null;
            });
            await(() -> !controller.isBusy());
            fx(() -> {
                check(((javafx.scene.control.ToggleButton) button(controller, "browseTab")).isSelected(),
                        "notification course link returns to the catalog");
                check(((PublishedCourseSummary) list(controller, "courseList").getSelectionModel()
                        .getSelectedItem()).id().equals(state.courseId),
                        "notification course link selects the exact course identity");
                return null;
            });
            verifyNotificationTargets(controller, rootScroll.get(), state);
            verifyMineWithdrawal(controller, rootScroll.get(), state);
            verifyPaginationAndReturnFocus();
            System.out.println("PASS: participant native shell, authorised online link, enrolment, own course, attendance, inbox and notification course navigation. "
                    + "renderScale=" + stageRef.get().getRenderScaleX() + "x" + stageRef.get().getRenderScaleY());
        } finally {
            Platform.exit();
        }
    }

    private static void verifyProfileMenu(ParticipantWorkspaceController controller, javafx.scene.Parent root) throws Exception {
        var passwordCalls = new java.util.concurrent.atomic.AtomicInteger();
        var logoutCalls = new java.util.concurrent.atomic.AtomicInteger();
        controller.setAccountAction(passwordCalls::incrementAndGet);
        controller.setLogoutAction(logoutCalls::incrementAndGet);
        var popup = fx(() -> {
            var account = (javafx.scene.control.MenuButton) root.lookup("#profileMenu");
            check(account.getItems().size() == 3
                            && account.getItems().getFirst() instanceof javafx.scene.control.CustomMenuItem,
                    "The profile presents a readable identity followed by password and logout actions");
            account.show();
            var menu = javafx.stage.Window.getWindows().stream()
                    .filter(javafx.scene.control.ContextMenu.class::isInstance)
                    .map(javafx.scene.control.ContextMenu.class::cast)
                    .filter(candidate -> candidate.isShowing() && candidate.getOwnerNode() == account)
                    .findFirst().orElseThrow();
            var content = (javafx.scene.layout.Region) menu.getSkin().getNode();
            content.applyCss(); content.layout();
            check(menu.getStyleClass().contains("profile-menu") && Math.abs(content.getWidth() - 224) <= 3
                            && Math.abs(content.getPadding().getLeft() - 8) < 0.1,
                    "The actual profile popup uses the compact 224px surface and 8px padding; width="
                            + content.getWidth() + ", padding=" + content.getPadding() + ", styles=" + menu.getStyleClass());
            var identity = (javafx.scene.control.CustomMenuItem) account.getItems().getFirst();
            var username = (javafx.scene.control.Label) identity.getContent().lookup(".profile-menu-details");
            check(username != null && "partecipante.smoke".equals(username.getText()),
                    "The profile identity displays the authenticated username");
            for (javafx.scene.Node node = username; node != null; node = node.getParent()) {
                check(node.getOpacity() >= 0.99, "The profile identity remains readable despite being informative");
            }
            var exit = content.lookupAll(".label").stream().map(javafx.scene.control.Label.class::cast)
                    .filter(label -> "Esci dall’account".equals(label.getText())).findFirst().orElseThrow();
            check(javafx.scene.paint.Color.web("#173946").equals(exit.getTextFill()),
                    "The initial logout command uses the same ink as the other menu actions");
            FxSnapshot.save(content, "target/ui-polish/participant-profile-menu.png");
            javafx.event.Event.fireEvent(menu.getSkin().getNode(), new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.ESCAPE,
                    false, false, false, false));
            return menu;
        });
        await(() -> !popup.isShowing());
        fx(() -> {
            var account = (javafx.scene.control.MenuButton) root.lookup("#profileMenu");
            check(!account.isShowing(), "Escape closes the participant profile popup");
            account.show(); account.getItems().get(1).fire();
            check(passwordCalls.get() == 1 && !account.isShowing(), "Password opens once and closes the profile popup");
            account.show(); account.getItems().get(2).fire();
            check(logoutCalls.get() == 1 && !account.isShowing(), "Logout invokes the existing action once and closes the popup");
            return null;
        });
    }

    private static void verifyNotificationTargets(ParticipantWorkspaceController controller,
            javafx.scene.Parent root, FakeState state) throws Exception {
        fx(() -> {
            ((javafx.scene.control.ComboBox<?>)field(controller,"categoryFilter")).getSelectionModel().select(1);
            return null;
        });
        await(() -> !controller.isBusy());
        fx(() -> {
            check(list(controller,"courseList").getItems().isEmpty(), "Category filter excludes the notified course");
            button(controller,"notificationsTab").fire(); return null;
        });
        await(() -> !controller.isBusy());
        fx(() -> {
            fireCard(root,"#notificationCards","Apri comunicazione →");
            button(controller,"openNotificationCourseButton").fire();return null;
        });
        await(() -> !controller.isBusy());
        fx(() -> {
            check(((PublishedCourseSummary)list(controller,"courseList").getSelectionModel().getSelectedItem()).id().equals(state.courseId),
                    "Notification target is resolved independently of the previous category filter");
            check(((javafx.scene.control.ToggleButton)button(controller,"browseTab")).isSelected(),"Successful target opens course route");
            state.hideCourse.set(true); button(controller,"notificationsTab").fire();return null;
        });
        await(() -> !controller.isBusy());
        fx(() -> {
            fireCard(root,"#notificationCards","Apri comunicazione →");
            button(controller,"openNotificationCourseButton").fire();return null;
        });
        await(() -> !controller.isBusy());
        fx(() -> {
            check(((javafx.scene.control.ToggleButton)button(controller,"notificationsTab")).isSelected(),
                    "Unavailable target keeps the inbox visible instead of showing stale course details");
            check(label(controller,"feedbackLabel").getText().contains("non è disponibile"), "Unavailable target provides useful feedback");
            return null;
        });
    }

    private static void verifyMineWithdrawal(ParticipantWorkspaceController controller,
            javafx.scene.Parent root, FakeState state) throws Exception {
        fx(() -> { state.hideCourse.set(false); button(controller, "mineTab").fire(); return null; });
        await(() -> !controller.isBusy());
        fx(() -> {
            root.lookupAll(".toggle-button").stream().filter(javafx.scene.control.ToggleButton.class::isInstance)
                    .map(javafx.scene.control.ToggleButton.class::cast).filter(button -> "Passati".equals(button.getText()))
                    .findFirst().orElseThrow().fire();
            return null;
        });
        await(() -> !controller.isBusy());
        fx(() -> {
            check(root.lookup("#enrollmentGrid").lookupAll(".label").stream()
                    .filter(javafx.scene.control.Label.class::isInstance).map(javafx.scene.control.Label.class::cast)
                    .anyMatch(label -> label.getText().contains("questa vista")),
                    "An empty history filter does not claim the participant has never enrolled");
            root.lookupAll(".toggle-button").stream().filter(javafx.scene.control.ToggleButton.class::isInstance)
                    .map(javafx.scene.control.ToggleButton.class::cast).filter(button -> "Attivi".equals(button.getText()))
                    .findFirst().orElseThrow().fire();
            return null;
        });
        await(() -> !controller.isBusy());
        fx(() -> {
            fireCard(root, "#enrollmentGrid", "Apri corso →"); return null;
        });
        await(() -> !controller.isBusy());
        verifyWithdrawalConfirmation(controller, root, state);
        await(() -> !controller.isBusy());
        fx(() -> {
            check(state.withdrawals.get() == 1, "A confirmed withdrawal writes exactly once");
            check(state.enrollment.get().status() == EnrollmentStatus.WITHDRAWN, "Withdrawal reaches repository");
            check(((EnrollmentSummary)field(controller, "selectedEnrollment")).status() == EnrollmentStatus.WITHDRAWN,
                    "Withdrawal refreshes the selected personal enrollment immediately");
            check(list(controller, "attendanceList").getItems().isEmpty(),
                    "Withdrawal removes stale practical attendance actions without leaving the page");
            check(((javafx.scene.control.ToggleButton)button(controller, "mineTab")).isSelected(),
                    "Withdrawal preserves the personal course route");
            ((ButtonBase) ((VBox) root.lookup("#attendancePanel")).getChildren().getFirst()).fire();
            return null;
        });
        await(() -> !controller.isBusy());
        fx(() -> {
            check(root.lookup("#enrollmentGrid").lookup(".participant-course-card") == null,
                    "A withdrawn enrollment leaves the active personal course list");
            root.lookup("#minePanel").lookupAll(".toggle-button").stream()
                    .map(javafx.scene.control.ToggleButton.class::cast).filter(button -> "Passati".equals(button.getText()))
                    .findFirst().orElseThrow().fire();
            return null;
        });
        await(() -> !controller.isBusy());
        fx(() -> {
            check(root.lookup("#enrollmentGrid").lookup(".participant-course-card") != null,
                    "Withdrawal remains visible in the past enrollment list");
            check(root.lookup("#enrollmentGrid").lookup(".participant-appointment") == null,
                    "Withdrawn enrollment cards do not advertise an upcoming appointment");
            return null;
        });
    }

    private static void verifyProgramWidths(Stage stage, javafx.scene.Parent root,
            ParticipantWorkspaceController controller, List<String> listNames) throws Exception {
        for (int width : new int[]{1000, 1536}) {
            fx(() -> {stage.setWidth(width + stage.getWidth() - stage.getScene().getWidth());return null;});
            await(() -> Math.abs(stage.getScene().getWidth() - width) < 2);
            fx(() -> {
                root.applyCss();root.layout();
                for (String name : listNames) {
                    check(!list(controller, name).isManaged(), name + " must not duplicate the visible timeline");
                }
                var timeline = timeline(controller); timeline.applyCss(); timeline.layout();
                for (var row : timeline.getChildren()) {
                    check(row.getBoundsInParent().getMaxX() <= timeline.getWidth() + 0.5,
                            "session content fits the available detail width at " + width + " px");
                }
                check(timeline.lookupAll(".scroll-pane").isEmpty() && timeline.lookupAll(".list-view").isEmpty(),
                        "the chronological program has no nested fixed-height scrolling containers");
                verifySessionModes(timeline);
                FxSnapshot.save(root,"target/ui-polish/participant-program-fit-" + width + ".png");
                return null;
            });
        }
        fx(() -> {stage.setWidth(1142 + stage.getWidth() - stage.getScene().getWidth());return null;});
        await(() -> Math.abs(stage.getScene().getWidth() - 1142) < 2);
    }

    private static void verifySessionStatePresentation(ParticipantWorkspaceController controller) throws Exception {
        fx(() -> {
            ListView<PublishedCourseSessionSummary> sessions = list(controller, "sessionList");
            var original = List.copyOf(sessions.getItems());
            var source = original.getFirst();
            try {
                sessions.getItems().setAll(new PublishedCourseSessionSummary(source.id(), source.sequenceNumber(),
                        source.scheduledAt(), SessionMode.ONLINE, source.durationMinutes(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), SessionStatus.CANCELLED));
                refreshTimeline(controller);
                var cancelled = timeline(controller).lookupAll(".participant-session-state").stream()
                        .map(javafx.scene.control.Label.class::cast).filter(label -> "Annullata".equals(label.getText()))
                        .findFirst().orElseThrow();
                check(javafx.scene.paint.Color.web("#A33421").equals(cancelled.getTextFill()) && !cancelled.isFocusTraversable(),
                        "Cancelled sessions expose an informational red state");
                check(onlineLinkButton(controller) == null, "An unavailable cancelled online link is not exposed");
                FxSnapshot.save(timeline(controller), "target/ui-polish/participant-session-cancelled.png");
                sessions.getItems().setAll(new PublishedCourseSessionSummary(source.id(), source.sequenceNumber(),
                        source.scheduledAt(), source.mode(), source.durationMinutes(), Optional.empty(),
                        source.venue(), source.capacity(), source.attendanceClosesAt(), SessionStatus.COMPLETED));
                refreshTimeline(controller);
                check(timeline(controller).lookupAll(".participant-session-state").stream().map(javafx.scene.control.Label.class::cast)
                                .anyMatch(label -> "Svolta".equals(label.getText())
                                        && !javafx.scene.paint.Color.web("#A33421").equals(label.getTextFill())),
                        "A recycled session cell resets the cancellation style for a completed session");
            } finally {
                sessions.getItems().setAll(original); refreshTimeline(controller);
            }
            return null;
        });
    }

    private static void verifySessionModes(javafx.scene.Parent list) {
        var modes = list.lookupAll(".participant-mode").stream()
                .filter(javafx.scene.control.Label.class::isInstance)
                .map(javafx.scene.control.Label.class::cast).toList();
        check(modes.stream().anyMatch(label -> label.getText().startsWith("Online") && label.getGraphic() != null)
                        && modes.stream().anyMatch(label -> label.getText().startsWith("In presenza") && label.getGraphic() != null),
                "The program shows online and in-person sessions as separate informative icon labels");
    }

    private static void verifyPaginationAndReturnFocus() throws Exception {
        var state = new FakeState();
        state.stress = true;
        var controller = state.controller();
        var rootRef = new AtomicReference<javafx.scene.Parent>();
        var stage = fx(() -> {
            var loader = new FXMLLoader(ParticipantWorkspaceUiSmoke.class.getResource("participant-workspace.fxml"));
            loader.setController(controller);
            var root = (javafx.scene.Parent) loader.load(); rootRef.set(root);
            var scene = new Scene(root, 1280, 800);
            scene.getStylesheets().add(ParticipantWorkspaceUiSmoke.class.getResource(
                    "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
            var shown = new Stage(); shown.setScene(scene); shown.show(); return shown;
        });
        await(() -> !controller.isBusy());
        var selected = new AtomicReference<Object>();
        fx(() -> {
            var root = rootRef.get(); root.applyCss(); root.layout();
            check(((FlowPane) root.lookup("#courseGrid")).getChildren().size() == 12,
                    "Available courses show twelve results from twenty-five");
            verifyFooterPagination(root.lookup("#browsePanel"), root.lookup("#courseGrid"));
            return null;
        });
        verifyPagerKeyboard(stage, rootRef.get().lookup("#browsePanel"));
        fx(() -> {
            var root = rootRef.get();
            var first = ((FlowPane) root.lookup("#courseGrid")).getChildren().getFirst();
            var open = (ButtonBase) first.lookup(".button"); selected.set(open.getUserData()); open.fire();
            return null;
        });
        await(() -> !controller.isBusy());
        fx(() -> { ((ButtonBase) ((VBox) rootRef.get().lookup("#courseDetailPanel")).getChildren().getFirst()).fire(); return null; });
        await(() -> !controller.isBusy());
        await(() -> selected.get().equals(stage.getScene().getFocusOwner().getUserData()));
        fx(() -> {
            var root = rootRef.get();
            check(hasPage(root.lookup("#browsePanel"), "Pagina 2 di 3"), "Returning from a course preserves the available page");
            ((javafx.scene.control.TextField) field(controller, "courseSearch")).setText("Corso aggiuntivo 24");
            return null;
        });
        await(() -> !controller.isBusy() && ((FlowPane) rootRef.get().lookup("#courseGrid")).getChildren().size() == 1);
        fx(() -> {
            var root = rootRef.get();
            check(((FlowPane) root.lookup("#courseGrid")).getChildren().size() == 1,
                    "A search resets pagination before showing its result");
            return null;
        });
        await(() -> !controller.isBusy());
        fx(() -> { button(controller, "clearFiltersButton").fire(); return null; });
        await(() -> !controller.isBusy());
        fx(() -> {
            check(hasPage(rootRef.get().lookup("#browsePanel"), "Pagina 1 di 3"), "Clearing filters starts at the first available page");
            button(controller, "mineTab").fire(); return null;
        });
        await(() -> !controller.isBusy());
        fx(() -> {
            var root = rootRef.get(); root.applyCss(); root.layout();
            check(((FlowPane) root.lookup("#enrollmentGrid")).getChildren().size() == 12,
                    "Personal courses also show twelve results");
            verifyFooterPagination(root.lookup("#minePanel"), root.lookup("#enrollmentGrid"));
            check(root.lookup("#enrollmentGrid").lookup(".participant-appointment-date") != null,
                    "Personal cards display a repository-backed next appointment");
            paginationNext(root.lookup("#minePanel")).fire();
            return null;
        });
        await(() -> !controller.isBusy());
        fx(() -> {
            var root = rootRef.get();
            var open = (ButtonBase) ((FlowPane) root.lookup("#enrollmentGrid")).getChildren().getFirst().lookup(".button");
            selected.set(open.getUserData()); open.fire(); return null;
        });
        await(() -> !controller.isBusy());
        fx(() -> { ((ButtonBase) ((VBox) rootRef.get().lookup("#attendancePanel")).getChildren().getFirst()).fire(); return null; });
        await(() -> !controller.isBusy());
        await(() -> selected.get().equals(stage.getScene().getFocusOwner().getUserData()));
        fx(() -> {
            check(hasPage(rootRef.get().lookup("#minePanel"), "Pagina 2 di 3"), "Returning from a personal course preserves its independent page");
            FxSnapshot.save(rootRef.get(), "target/ui-polish/participant-stress-page-2.png");
            rootRef.get().lookup("#minePanel").lookupAll(".toggle-button").stream()
                    .map(javafx.scene.control.ToggleButton.class::cast).filter(button -> "Tutti".equals(button.getText()))
                    .findFirst().orElseThrow().fire();
            return null;
        });
        await(() -> !controller.isBusy());
        fx(() -> {
            check(hasPage(rootRef.get().lookup("#minePanel"), "Pagina 1 di 3"), "Changing the personal history filter resets its page");
            stage.close(); controller.dispose(); return null;
        });
    }

    private static void verifyFooterPagination(javafx.scene.Node panel, javafx.scene.Node grid) {
        var controls = panel.lookupAll(".result-pager");
        check(controls.size() == 1, "A course list exposes one pagination control");
        var pager = controls.iterator().next();
        check(pager.localToScene(pager.getBoundsInLocal()).getMinY()
                        >= grid.localToScene(grid.getBoundsInLocal()).getMaxY(),
                "The pagination control follows the course results");
    }

    private static void verifyWithdrawalConfirmation(ParticipantWorkspaceController controller,
            javafx.scene.Parent root, FakeState state) throws Exception {
        scheduleWithdrawal(controller);
        await(() -> withdrawalDialog() != null || state.withdrawals.get() > 0);
        fx(() -> {
            var dialog = withdrawalDialog();
            check(dialog != null, "Withdrawal asks for confirmation before any repository write");
            check(state.withdrawals.get() == 0, "Opening withdrawal confirmation does not write");
            var keep = (javafx.scene.control.Button) dialog.lookupButton(dialog.getButtonTypes().stream()
                    .filter(type -> "Mantieni iscrizione".equals(type.getText())).findFirst().orElseThrow());
            var confirm = (javafx.scene.control.Button) dialog.lookupButton(dialog.getButtonTypes().stream()
                    .filter(type -> "Conferma ritiro".equals(type.getText())).findFirst().orElseThrow());
            check(keep.isDefaultButton() && !confirm.isDefaultButton(), "Keeping the enrollment is the safe default action");
            check(dialog.getAccessibleText().contains("ritiro"), "Withdrawal dialog exposes its purpose accessibly");
            check(dialog.lookupAll(".label").stream().map(javafx.scene.control.Label.class::cast)
                            .anyMatch(label -> label.getText().contains("adesioni confermate")),
                    "Withdrawal confirmation explains its consequences for attendance");
            dialog.applyCss(); dialog.layout();
            check(confirm.getBackground().getFills().stream().anyMatch(fill ->
                            javafx.scene.paint.Color.web("#A6382E").equals(fill.getFill()))
                            && javafx.scene.paint.Color.WHITE.equals(confirm.getTextFill()),
                    "The definitive withdrawal action uses a full red background and white text");
            FxSnapshot.save(dialog, "target/ui-polish/participant-withdrawal-confirmation.png");
            keep.fire(); return null;
        });
        await(() -> withdrawalDialog() == null && !controller.isBusy());
        fx(() -> {
            check(state.withdrawals.get() == 0 && state.enrollment.get().status() == EnrollmentStatus.ACTIVE,
                    "Cancelling withdrawal preserves enrollment and performs no write");
            check(root.getScene().getFocusOwner() == button(controller, "withdrawButton"),
                    "Cancelling withdrawal restores focus to the initiating action");
            return null;
        });
        scheduleWithdrawal(controller);
        await(() -> withdrawalDialog() != null);
        fx(() -> {
            var dialog = withdrawalDialog();
            dialog.lookupButton(dialog.getButtonTypes().stream()
                    .filter(type -> "Conferma ritiro".equals(type.getText())).findFirst().orElseThrow())
                    .fireEvent(new javafx.event.ActionEvent());
            return null;
        });
    }

    private static void scheduleWithdrawal(ParticipantWorkspaceController controller) throws Exception {
        fx(() -> {
            var action = button(controller, "withdrawButton"); action.requestFocus();
            Platform.runLater(action::fire); return null;
        });
    }

    private static javafx.scene.control.DialogPane withdrawalDialog() {
        return javafx.stage.Window.getWindows().stream()
                .filter(window -> window.isShowing() && window instanceof Stage stage
                        && "Ritira iscrizione".equals(stage.getTitle()))
                .map(window -> window.getScene().getRoot().lookup(".dialog-pane"))
                .filter(javafx.scene.control.DialogPane.class::isInstance)
                .map(javafx.scene.control.DialogPane.class::cast).findFirst().orElse(null);
    }

    private static void verifyPagerKeyboard(Stage stage, javafx.scene.Node panel) throws Exception {
        var next = fx(() -> {
            stage.toFront(); stage.requestFocus();
            var origin = paginationPrevious(panel);
            origin.requestFocusTraversal(javafx.scene.TraversalDirection.NEXT);
            return paginationNext(origin.getParent());
        });
        await(() -> stage.isFocused() && next.isFocused());
        fx(() -> { check(next.isFocusVisible(), "Keyboard traversal shows the next pager focus ring"); return null; });
        pressSpace(next);
        await(() -> hasPage(panel, "Pagina 2 di 3"));
        await(() -> stage.getScene().getFocusOwner() == next);
        fx(() -> { check(stage.getScene().getFocusOwner() == next,
                "Space advances the page and retains the same next control"); return null; });
        pressSpace(next);
        await(() -> hasPage(panel, "Pagina 3 di 3"));
        await(() -> stage.getScene().getFocusOwner() == paginationPrevious(next.getParent()));
        var previous = fx(() -> {
            var action = paginationPrevious(next.getParent());
            check(stage.getScene().getFocusOwner() == action,
                    "At the final page focus moves to the available previous control"); return action;
        });
        fx(() -> { check(previous.isFocusVisible(), "Final-page keyboard fallback retains the visible focus ring"); return null; });
        pressSpace(previous);
        await(() -> hasPage(panel, "Pagina 2 di 3"));
        await(() -> stage.getScene().getFocusOwner() == previous);
        fx(() -> { check(stage.getScene().getFocusOwner() == previous,
                "Space returns to the preceding page and preserves its control"); return null; });
    }

    private static void pressSpace(ButtonBase target) throws Exception {
        fx(() -> {
            target.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    " ", " ", javafx.scene.input.KeyCode.SPACE, false, false, false, false));
            check(target.isArmed(), "Native JavaFX Space handling arms the focused pager action");
            return null;
        });
        try {
            await(target::isArmed);
        } finally {
            fx(() -> {
                target.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_RELEASED,
                        " ", " ", javafx.scene.input.KeyCode.SPACE, false, false, false, false));
                return null;
            });
        }
    }

    private static ButtonBase paginationNext(javafx.scene.Node root) {
        return root.lookupAll(".result-pager .button").stream().map(ButtonBase.class::cast)
                .filter(button -> "Pagina successiva".equals(button.getAccessibleText()) && !button.isDisabled())
                .findFirst().orElseThrow();
    }

    private static ButtonBase paginationPrevious(javafx.scene.Node root) {
        return root.lookupAll(".result-pager .button").stream().map(ButtonBase.class::cast)
                .filter(button -> "Pagina precedente".equals(button.getAccessibleText()))
                .findFirst().orElseThrow();
    }

    private static boolean hasPage(javafx.scene.Node root, String expected) {
        return root.lookupAll(".result-page-position").stream().map(javafx.scene.control.Label.class::cast)
                .anyMatch(label -> expected.equals(label.getText()));
    }

    private static void verifyTwoCourseCardsFillRow(Stage stage, javafx.scene.Parent root,
            ParticipantWorkspaceController controller, FakeState state) throws Exception {
        fx(() -> {stage.setWidth(1440 + stage.getWidth() - stage.getScene().getWidth());return null;});
        await(() -> Math.abs(stage.getScene().getWidth() - 1440) < 2);
        fx(() -> {
            root.applyCss(); root.layout();
            var grid = (FlowPane) root.lookup("#courseGrid");
            grid.applyCss(); grid.layout();
            check(grid.getChildren().size() == 2, "two catalog cards are visible in the wide layout");
            check(grid.getWidth() > 1050, "catalog has enough width for three columns");
            var insets = grid.getInsets();
            double contentWidth = grid.getWidth() - insets.getLeft() - insets.getRight();
            double expectedCardWidth = (contentWidth - grid.getHgap()) / 2;
            for (var card : grid.getChildren()) {
                check(Math.abs(card.getBoundsInParent().getWidth() - expectedCardWidth) <= 2,
                        "two catalog cards use the available two-column width");
            }
            var lastCard = grid.getChildren().getLast();
            check(Math.abs(lastCard.getBoundsInParent().getMaxX() - (grid.getWidth() - insets.getRight())) <= 2,
                    "two catalog cards fill the row without an empty third-column gap: grid=" + grid.getWidth()
                            + ", cards=" + grid.getChildren().stream().map(javafx.scene.Node::getBoundsInParent).toList());
            var courseList = (ListView<PublishedCourseSummary>) root.lookup("#courseList");
            var second = courseList.getItems().get(1);
            courseList.getItems().remove(second);
            root.applyCss(); root.layout(); grid.applyCss(); grid.layout();
            check(grid.getChildren().size() == 1
                            && Math.abs(grid.getChildren().getFirst().getBoundsInParent().getWidth()
                                    - (grid.getWidth() - grid.getInsets().getLeft() - grid.getInsets().getRight())) <= 2,
                    "one catalog card expands to the available row");
            courseList.getItems().add(second);
            var third = new PublishedCourseSummary(UUID.randomUUID(), second.categoryId(), second.categoryName(),
                    second.chefName(), CourseTitle.of("W".repeat(30)),
                    "Descrizione sintetica del corso di prova.", second.startDate(), second.startTime(), 3);
            courseList.getItems().add(third);
            root.applyCss();
            root.layout();
            check(grid.getChildren().size() == 3
                            && grid.getChildren().getLast().getBoundsInParent().getMinY()
                                    == grid.getChildren().getFirst().getBoundsInParent().getMinY()
                            && grid.getChildren().getLast().getBoundsInParent().getMaxX() <= grid.getWidth(),
                    "three catalog cards, including a long title, stay on one row");
            var longTitle = (javafx.scene.control.Label) grid.getChildren().getLast().lookup(".participant-card-title");
            var rendered = (javafx.scene.text.Text) longTitle.lookup(".text");
            check(rendered != null && !longTitle.isWrapText()
                            && longTitle.getTooltip() != null
                            && longTitle.getTooltip().getText().equals(third.title().value()),
                    "Even wide-letter titles stay on one line with the complete title in a tooltip");
            courseList.getItems().remove(third);
            root.layout();
            var archived = new PublishedCourseSummary(UUID.randomUUID(), second.categoryId(), second.categoryName(),
                    second.chefName(), CourseTitle.of("Corso storico"), "Un corso concluso.",
                    LocalDate.of(2000, 10, 2), second.startTime(), 2);
            state.available.add(archived);
            root.lookup("#browsePanel").lookupAll(".toggle-button").stream()
                    .map(javafx.scene.control.ToggleButton.class::cast).filter(button -> "Tutti".equals(button.getText()))
                    .findFirst().orElseThrow().fire();
            return null;
        });
        await(() -> !controller.isBusy());
        fx(() -> {
            check(((FlowPane) root.lookup("#courseGrid")).getChildren().size() == 3,
                    "All courses retains the authorized historical course");
            root.lookup("#browsePanel").lookupAll(".toggle-button").stream()
                    .map(javafx.scene.control.ToggleButton.class::cast).filter(button -> "Disponibili".equals(button.getText()))
                    .findFirst().orElseThrow().fire();
            return null;
        });
        await(() -> !controller.isBusy());
        fx(() -> {
            check(((FlowPane) root.lookup("#courseGrid")).getChildren().size() == 2,
                    "Available courses exclude the authorized historical course");
            state.available.removeIf(course -> course.title().value().equals("Corso storico"));
            return null;
        });
        fx(() -> {stage.setWidth(1142 + stage.getWidth() - stage.getScene().getWidth());return null;});
        await(() -> Math.abs(stage.getScene().getWidth() - 1142) < 2);
    }

    private static void verifyLongDetailHeading(javafx.scene.Parent root) throws java.io.IOException {
        var heading = (javafx.scene.control.Label) root.lookup("#workspaceTitle");
        var original = heading.getText();
        var longTitle = "Dolci da forno per la colazione: impasti, consistenze e conservazione senza sprechi";
        heading.setText(longTitle);
        root.applyCss();
        root.layout();
        var rendered = (javafx.scene.text.Text) heading.lookup(".text");
        var expected = new javafx.scene.text.Text(longTitle);
        expected.setFont(heading.getFont());
        expected.setWrappingWidth(heading.getWidth() - heading.getInsets().getLeft()
                - heading.getInsets().getRight());
        check(heading.isWrapText() && rendered != null && rendered.getText().equals(longTitle)
                        && heading.getHeight() + 1 >= expected.getLayoutBounds().getHeight(),
                "Participant detail heading must show the full long course title at 1142px without ellipsis");
        FxSnapshot.save(root, "target/ui-polish/participant-long-detail-heading.png");
        heading.setText(original);
        root.layout();
    }

    private static void captureWidths(Stage stage, javafx.scene.Parent root, String name) throws Exception {
        for (int width : new int[]{1000, 1440}) {
            fx(() -> {stage.setWidth(width + stage.getWidth() - stage.getScene().getWidth());return null;});
            await(() -> Math.abs(stage.getScene().getWidth() - width) < 2);
            fx(() -> {
                root.applyCss(); root.layout();
                var scroll = (javafx.scene.control.ScrollPane) root.lookup("#rootScroll");
                FxSnapshot.save(root,"target/ui-polish/" + name + "-" + width + ".png");
                check(scroll.getContent().getBoundsInParent().getWidth() <= stage.getScene().getWidth() + 1,
                        "Participant content fits the actual scene at " + width + " px: content="
                                + scroll.getContent().getBoundsInParent() + " viewport=" + scroll.getViewportBounds()
                                + " min=" + ((javafx.scene.layout.Region) scroll.getContent()).minWidth(-1)
                                + " layout=" + scroll.getContent().getLayoutBounds()
                                + " header=" + root.lookup(".participant-topbar").getBoundsInParent());
                var profile = root.lookup("#profileMenu");
                check(profile.localToScene(profile.getBoundsInLocal()).getMaxX() <= stage.getScene().getWidth() + 1,
                        "Participant profile fits the scene at " + width + " px");
                for (String gridId : List.of("#courseGrid", "#enrollmentGrid")) {
                    var grid = root.lookup(gridId);
                    if (!grid.isVisible()) continue;
                    for (var action : grid.lookupAll(".button")) {
                        check(action.localToScene(action.getBoundsInLocal()).getMaxX() <= stage.getScene().getWidth() + 1,
                                "Participant card action fits the scene at " + width + " px");
                    }
                }
                FxSnapshot.save(root,"target/ui-polish/" + name + "-" + width + ".png"); return null;
            });
        }
        fx(() -> {stage.setWidth(1142 + stage.getWidth() - stage.getScene().getWidth());return null;});
        await(() -> Math.abs(stage.getScene().getWidth() - 1142) < 2);
    }

    private static void fireCard(javafx.scene.Parent root, String container, String text) {
        root.lookup(container).lookupAll(".button").stream().filter(ButtonBase.class::isInstance)
                .map(ButtonBase.class::cast).filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing visible card action: " + text)).fire();
    }

    private static ButtonBase button(Object controller, String field) throws Exception {
        return (ButtonBase) field(controller, field);
    }

    @SuppressWarnings("unchecked")
    private static <T> ListView<T> list(Object controller, String field) throws Exception {
        return (ListView<T>) field(controller, field);
    }

    private static javafx.scene.control.Label label(Object controller, String field) throws Exception {
        return (javafx.scene.control.Label) field(controller, field);
    }

    private static Object field(Object controller, String name) throws Exception {
        var field = controller.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(controller);
    }

    private static void fireFirstAttendanceConfirmation(Object controller) throws Exception {
        timeline(controller).lookupAll(".button").stream().filter(ButtonBase.class::isInstance)
                .map(ButtonBase.class::cast).filter(button -> "Confermo".equals(button.getText()))
                .findFirst().orElseThrow(() -> new AssertionError("attendance confirmation action is missing")).fire();
    }

    private static ButtonBase onlineLinkButton(Object controller) throws Exception {
        return timeline(controller).lookupAll(".button").stream()
                .filter(ButtonBase.class::isInstance)
                .map(ButtonBase.class::cast)
                .filter(ButtonBase::isVisible)
                .filter(button -> "Copia link".equals(button.getText()))
                .findFirst()
                .orElse(null);
    }

    private static VBox timeline(Object controller) throws Exception {
        return (VBox) ((javafx.scene.control.ScrollPane) field(controller, "rootScroll")).lookup("#sessionTimeline");
    }

    private static void refreshTimeline(Object controller) throws Exception {
        var view = field(controller, "view");
        var render = view.getClass().getDeclaredMethod("renderTimeline");
        render.setAccessible(true); render.invoke(view);
        timeline(controller).applyCss(); timeline(controller).layout();
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<T>(action);
        Platform.runLater(task);
        return task.get();
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        it.uninafoodlab.presentation.FxAwait.until(condition, 10);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FakeState {
        private boolean stress;
        private final List<PublishedCourseSummary> available = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final java.util.concurrent.atomic.AtomicInteger withdrawals = new java.util.concurrent.atomic.AtomicInteger();
        private final UUID courseId = UUID.randomUUID();
        private final UUID secondCourseId = UUID.randomUUID();
        private final UUID sessionId = UUID.randomUUID();
        private final UUID onlineSessionId = UUID.randomUUID();
        private final UUID notificationId = UUID.randomUUID();
        private final URI onlineUrl = URI.create("https://meet.example.test/pane");
        private final AtomicReference<EnrollmentSummary> enrollment = new AtomicReference<>();
        private final AtomicReference<Boolean> read = new AtomicReference<>(false);
        private final AtomicReference<AttendanceStatus> attendance = new AtomicReference<>();
        private final AtomicReference<Boolean> hideCourse = new AtomicReference<>(false);
        private final AtomicReference<Boolean> failCourseDetail = new AtomicReference<>(false);

        private ParticipantWorkspaceController controller() {
            var category = new CategorySummary(UUID.randomUUID(), "Panificazione", "Impasti");
            var otherCategory = new CategorySummary(UUID.randomUUID(), "Vegetariana", "Ortaggi");
            var course = new PublishedCourseSummary(courseId, category.id(), category.name(), "Chef Demo",
                    CourseTitle.of("Pane di casa"), "Un laboratorio di impasti lenti.",
                    LocalDate.of(2099, 10, 2), LocalTime.of(18, 0), 2);
            var secondCourse = new PublishedCourseSummary(secondCourseId, category.id(), category.name(), "Chef Demo",
                    CourseTitle.of("Focaccia ligure"), "Una focaccia soffice e profumata.",
                    LocalDate.of(2099, 10, 9), LocalTime.of(18, 0), 2);
            available.clear();
            available.addAll(List.of(course, secondCourse));
            if (stress) {
                for (int index = 3; index <= 25; index++) {
                    available.add(new PublishedCourseSummary(UUID.randomUUID(), category.id(), category.name(),
                            "Chef Demo", CourseTitle.of("Corso aggiuntivo " + index), "Laboratorio sintetico.",
                            LocalDate.of(2099, 10, index), LocalTime.of(18, 0), 2));
                }
            }
            var session = new PublishedCourseSessionSummary(sessionId, 1,
                    LocalDateTime.of(2099, 10, 2, 18, 0), SessionMode.PRACTICAL, 120,
                    Optional.empty(), Optional.of("Atelier 1"), Optional.of(6),
                    Optional.of(LocalDateTime.of(2099, 10, 1, 18, 0)), SessionStatus.SCHEDULED);
            var recipe = new PublishedSessionRecipeSummary(sessionId, UUID.randomUUID(), "Pane rustico",
                    "Impasto e cottura.", 1, java.math.BigDecimal.ONE);
            var notice = new ParticipantNotification(notificationId, "Benvenuto", "Apri corso.",
                    NotificationReason.GENERAL, Optional.of(course.title().value()), Instant.now(),
                    Optional.empty(), Optional.of(course.id()));
            var actor = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    Username.of("partecipante.smoke"), "Partecipante demo", Role.PARTICIPANT);
            CourseRepository courses = proxy(CourseRepository.class, (method, args) -> switch (method.getName()) {
                case "findPublished", "findPublishedByIds" -> ParticipantBrowseResponses.courses(args,
                        hideCourse.get() ? List.of() : List.copyOf(available));
                case "publishedSessions" -> {
                    if (failCourseDetail.compareAndSet(true, false)) {
                        throw new IllegalStateException("synthetic course detail failure");
                    }
                    yield ParticipantBrowseResponses.sessions(args[1], id -> List.of(session, onlineSession()));
                }
                case "publishedMenu" -> List.of(recipe);
                default -> null;
            });
            CatalogRepository catalog = proxy(CatalogRepository.class, (method, args) ->
                    method.getName().equals("categories") ? List.of(category, otherCategory) : List.of());
            EnrollmentRepository enrollments = proxy(EnrollmentRepository.class, (method, args) -> {
                return switch (method.getName()) {
                    case "findByParticipant" -> ParticipantBrowseResponses.enrollments(args, currentEnrollments());
                    case "commitments" -> ParticipantBrowseResponses.commitments(currentEnrollments(), id -> List.of(attendance(session)));
                    case "practicalSessions" -> enrollment.get() == null ? List.of() : List.of(attendance(session));
                    case "enroll" -> {
                        enrollment.set(new EnrollmentSummary(courseId, course.title(), category.name(),
                                course.chefName(), course.startDate(), EnrollmentStatus.ACTIVE, Instant.now()));
                        yield null;
                    }
                    case "withdraw" -> {
                        withdrawals.incrementAndGet();
                        enrollment.set(new EnrollmentSummary(courseId, course.title(), category.name(),
                                course.chefName(), course.startDate(), EnrollmentStatus.WITHDRAWN, Instant.now()));
                        yield null;
                    }
                    case "setAttendance" -> {
                        attendance.set((AttendanceStatus) args[2]);
                        yield null;
                    }
                    default -> null;
                };
            });
            NotificationRepository notifications = proxy(NotificationRepository.class, (method, args) -> switch (method.getName()) {
                case "findForParticipant" -> List.of(read.get()
                        ? new ParticipantNotification(notice.id(), notice.title(), notice.message(), notice.reason(),
                        notice.courseTitle(), notice.createdAt(), Optional.of(Instant.now()), notice.courseId()) : notice);
                case "markRead" -> { read.set(true); yield null; }
                default -> null;
            });
            return new ParticipantWorkspaceController(actor,
                    new BrowsePublishedCourses(courses), new BrowseCatalog(catalog),
                    new BrowseEnrollments(enrollments), new ManageEnrollment(enrollments),
                    new ManagePracticalAttendance(enrollments), new BrowseNotifications(notifications),
                    new ManageNotifications(notifications));
        }

        private List<EnrollmentSummary> currentEnrollments() {
            return stress ? available.stream().map(item -> new EnrollmentSummary(
                    item.id(), item.title(), item.categoryName(), item.chefName(), item.startDate(),
                    EnrollmentStatus.ACTIVE, Instant.now())).toList()
                    : enrollment.get() == null ? List.of() : List.of(enrollment.get());
        }

        private PracticalAttendanceSummary attendance(PublishedCourseSessionSummary session) {
            return new PracticalAttendanceSummary(session.id(), session.sequenceNumber(), session.scheduledAt(),
                    session.durationMinutes(), session.venue().orElse("Atelier 1"), 6,
                    attendance.get() == AttendanceStatus.CONFIRMED ? 1 : 0,
                    attendance.get() == AttendanceStatus.CONFIRMED ? 5 : 6,
                    session.attendanceClosesAt().orElseThrow(), Optional.ofNullable(attendance.get()), 1);
        }

        private PublishedCourseSessionSummary onlineSession() {
            return new PublishedCourseSessionSummary(onlineSessionId, 2,
                    LocalDateTime.of(2099, 10, 9, 18, 0), SessionMode.ONLINE, 90,
                    enrollment.get() == null ? Optional.empty() : Optional.of(onlineUrl),
                    Optional.empty(), Optional.empty(), Optional.empty(), SessionStatus.SCHEDULED);
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, ProxyResponse response) {
            return (T) java.lang.reflect.Proxy.newProxyInstance(
                    ParticipantWorkspaceUiSmoke.class.getClassLoader(), new Class<?>[]{type},
                    (proxy, method, args) -> response.apply(method, args));
        }

        @FunctionalInterface
        private interface ProxyResponse {
            Object apply(java.lang.reflect.Method method, Object[] args);
        }
    }
}
