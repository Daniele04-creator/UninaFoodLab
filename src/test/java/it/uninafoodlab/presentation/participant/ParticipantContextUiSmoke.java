package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.catalog.*;
import it.uninafoodlab.application.course.*;
import it.uninafoodlab.application.enrollment.*;
import it.uninafoodlab.application.notification.*;
import it.uninafoodlab.domain.catalog.MeasurementUnit;
import it.uninafoodlab.domain.course.*;
import it.uninafoodlab.domain.enrollment.*;
import it.uninafoodlab.domain.identity.*;
import it.uninafoodlab.presentation.FxSnapshot;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;


public final class ParticipantContextUiSmoke {
    private ParticipantContextUiSmoke() {}

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            if (args.length == 0 || args[0].equals("context")) verifyContexts();
            if (args.length == 0 || args[0].equals("feedback")) verifyWriteFeedback();
            if (args.length == 0 || args[0].equals("empty")) verifyEmptyContexts();
            if (args.length == 0 || args[0].equals("reopen")) verifyAttendanceOnReopen();
            if (args.length == 0 || args[0].equals("timeline")) verifyUnifiedTimeline();
            if (args.length == 0 || args[0].equals("completed")) verifyCompletedTimeline();
            if (args.length == 0 || args[0].equals("refresh")) verifyExternalAttendanceChanges();
            if (args.length == 0 || args[0].equals("pending-write")) verifyPendingWrites();
            System.out.println("PASS: participant course isolation, obsolete responses, contextual retry and durable write confirmations.");
        } finally {
            Platform.exit();
        }
    }

    private static void verifyPendingWrites() throws Exception {
        try (var ui = new Fixture(false)) {
            ui.open("#courseGrid", "Corso A"); ui.ready();
            var write = ui.block("enroll-write", ui.a, false);
            ui.fire("enrollButton"); write.awaitEntered();
            ui.back("#courseDetailPanel");
            ui.assertBlockedCourse("#courseGrid", "Corso B");
            check(fx(ui.controller::isBusy) && ui.enrollWrites.get() == 1,
                    "pending enrollment must remain busy and must not be submitted twice");
            write.release.countDown(); ui.ready();
            ui.assertConfirmation("Iscrizione registrata.", false);
            check(ui.enrollWrites.get() == 1, "navigation must not repeat enrollment");
            ui.open("#courseGrid", "Corso B"); ui.ready(); ui.assertProgram(ui.b);
        }
        try (var ui = new Fixture(true)) {
            ui.fire("mineTab"); ui.ready(); ui.open("#enrollmentGrid", "Corso A"); ui.ready();
            var write = ui.block("attendance-write", ui.sa, false);
            ui.attendance(AttendanceStatus.CONFIRMED); write.awaitEntered();
            ui.back("#attendancePanel");
            ui.assertBlockedCourse("#enrollmentGrid", "Corso B");
            check(fx(ui.controller::isBusy) && ui.attendanceWrites.get() == 1,
                    "pending attendance must retain its context and prevent another write");
            write.release.countDown(); ui.ready();
            ui.assertConfirmation("Presenza confermata.", false);
            ui.assertAttendance(ui.a, AttendanceStatus.CONFIRMED);
            check(ui.attendanceWrites.get() == 1, "navigation must not repeat attendance");
            ui.open("#enrollmentGrid", "Corso B"); ui.ready(); ui.assertProgram(ui.b);
        }
    }

    private static void verifyExternalAttendanceChanges() throws Exception {
        verifyExternalAttendanceChanges("browseTab", "#courseGrid");
        verifyExternalAttendanceChanges("mineTab", "#enrollmentGrid");
    }

    private static void verifyExternalAttendanceChanges(String tab, String grid) throws Exception {
        try (var ui = new Fixture(true)) {
            ui.attendance.put(ui.sb, AttendanceStatus.CONFIRMED);
            ui.fire(tab); ui.ready(); ui.open(grid, "Corso A"); ui.ready();
            check(fx(() -> ui.label("attendanceWarning").getText().isEmpty()),
                    "separate appointments must initially have no conflict");
            int reads = ui.attendanceReads.get();
            var original = ui.session(ui.a);
            ui.changedSessions.put(ui.a, new PublishedCourseSessionSummary(original.id(), 1,
                    original.scheduledAt(), SessionMode.PRACTICAL, 60, Optional.empty(),
                    Optional.of("Nuova sede A"), Optional.of(4), original.attendanceClosesAt(), SessionStatus.SCHEDULED));
            var other = ui.session(ui.b);
            ui.changedSessions.put(ui.b, new PublishedCourseSessionSummary(other.id(), 1,
                    original.scheduledAt(), SessionMode.PRACTICAL, 60, Optional.empty(),
                    other.venue(), other.capacity(), other.attendanceClosesAt(), SessionStatus.SCHEDULED));
            ui.fire(tab); ui.ready(); ui.open(grid, "Corso A"); ui.ready();
            fx(() -> {
                var session = ui.<PracticalAttendanceSummary>list("attendanceList").getItems().getFirst();
                check(session.venue().equals("Nuova sede A") && session.capacity() == 4,
                        "successful list refresh must reload externally changed attendance details");
                check(ui.label("attendanceWarning").getText().contains("Corso B"),
                        "successful list refresh must reload externally rescheduled commitments");
                return null;
            });
            check(ui.attendanceReads.get() > reads && ui.attendanceWrites.get() == 0,
                    "refresh must obtain current attendance before any participant write");
        }
    }

    private static void verifyUnifiedTimeline() throws Exception {
        try (var ui = new Fixture(true)) {
            ui.open("#courseGrid", "Corso A"); ui.ready();
            ui.assertUnifiedTimeline(ui.a, "#courseDetailPanel");
            fx(() -> {
                var timeline = ui.root.lookup("#sessionTimeline");
                var confirm = timeline.lookupAll(".button").stream().filter(Button.class::isInstance)
                        .map(Button.class::cast).filter(button -> button.getText().equals("Confermo")).findFirst().orElseThrow();
                check(!confirm.isDisabled(), "enrolled browse course must expose current attendance action");
                confirm.requestFocus();
                confirm.fire(); return null;
            });
            ui.ready(); ui.assertAttendance(ui.a, AttendanceStatus.CONFIRMED);
            fx(() -> {
                var row = ui.root.lookup("#sessionTimeline").lookupAll(".participant-session-row").stream()
                        .filter(node -> ui.session(ui.a).id().equals(node.getUserData())).findFirst().orElseThrow();
                check(row.lookupAll(".participant-session-feedback").stream().anyMatch(node ->
                        node.lookupAll(".label").stream().map(Label.class::cast).anyMatch(label ->
                                label.isVisible() && label.getText().equals("Presenza confermata."))),
                        "attendance confirmation must remain next to the affected session after refresh");
                return null;
            });
            check(ui.attendanceWrites.get() == 1, "visible timeline must save exactly one confirmation");
            it.uninafoodlab.presentation.FxAwait.until(() -> ui.stage.getScene().getFocusOwner() instanceof Label label
                    && label.getText().equals("Presenza confermata."), 10);
            ui.fire("mineTab"); ui.ready(); ui.open("#enrollmentGrid", "Corso A"); ui.ready();
            ui.assertUnifiedTimeline(ui.a, "#attendancePanel");
            ui.snapshot("unified-course-program");
        }
    }

    private static void verifyCompletedTimeline() throws Exception {
        try (var ui = new Fixture(true)) {
            ui.open("#courseGrid", "Corso A"); ui.ready();
            fx(() -> {
                var source = ui.session(ui.a);
                var scheduled = LocalDateTime.of(2000, 10, 2, 18, 0);
                var completed = new PublishedCourseSessionSummary(source.id(), source.sequenceNumber(), scheduled,
                        source.mode(), source.durationMinutes(), source.onlineUrl(), source.venue(), source.capacity(),
                        Optional.of(scheduled.minusDays(1)), SessionStatus.COMPLETED);
                var view = (ParticipantWorkspaceView) ui.field("view");
                view.renderAttendance(List.of());
                view.renderCourseDetail(new ParticipantWorkspaceData.CourseDetailSnapshot(
                        List.of(completed), List.of(ui.recipe(ui.a))));
                var timeline = ui.root.lookup("#sessionTimeline");
                check(timeline.lookupAll(".label").stream().map(Label.class::cast)
                                .noneMatch(label -> label.getText().equals("Adesioni non ancora caricate.")),
                        "completed session without an authorized attendance item must not show a permanent loading message");
                check(timeline.lookupAll(".button").stream().map(Button.class::cast)
                                .anyMatch(button -> button.getAccessibleText().contains(ui.recipe(ui.a).name())),
                        "completed sessions must retain their authorized historical recipe snapshot");
                var historical = new PracticalAttendanceSummary(source.id(), source.sequenceNumber(), scheduled,
                        source.durationMinutes(), source.venue().orElseThrow(), 8, 1, 7, scheduled.minusDays(1),
                        Optional.of(AttendanceStatus.CONFIRMED), 1);
                view.renderAttendance(List.of(historical));
                check(timeline.lookupAll(".label").stream().map(Label.class::cast)
                                .anyMatch(label -> label.getText().equals("Presenza confermata")),
                        "authorized historic attendance must remain readable when the repository returns it");
                check(timeline.lookupAll(".button").stream().map(Button.class::cast)
                                .noneMatch(button -> button.getText().equals("Confermo") || button.getText().equals("Non partecipo")),
                        "historical attendance must remain read only");
                return null;
            });
        }
    }

    private static void verifyAttendanceOnReopen() throws Exception {
        try (var ui = new Fixture(true)) {
            ui.failAttendanceRefresh();
            var mine = ui.block("catalog", null, false);
            ui.back("#attendancePanel"); mine.awaitEntered();

            ui.open("#enrollmentGrid", "Corso A"); ui.ready();
            ui.assertProgram(ui.a);
            ui.assertAttendance(ui.a, AttendanceStatus.CONFIRMED);
            mine.release.countDown();
            check(mine.finished.await(10, TimeUnit.SECONDS), "previous mine reading must finish");
            Thread.sleep(100); fx(() -> null);
            ui.assertAttendance(ui.a, AttendanceStatus.CONFIRMED);
            check(ui.attendanceWrites.get() == 1, "reopening must not repeat the successful attendance write");
            ui.snapshot("reopen-after-refresh-error");
        }
        try (var ui = new Fixture(true)) {
            ui.failAttendanceRefresh();
            var mine = ui.block("catalog", null, true);
            ui.back("#attendancePanel"); mine.awaitEntered(); mine.release.countDown(); ui.ready();
            var program = ui.block("menu", ui.a, true);
            ui.open("#enrollmentGrid", "Corso A"); program.awaitEntered(); program.release.countDown(); ui.ready();
            check(fx(() -> ui.button("retryButton").isVisible()), "reopened program failure must offer retry");
            ui.retryProgram(ui.a);
            ui.assertAttendance(ui.a, AttendanceStatus.CONFIRMED);
            check(ui.attendanceWrites.get() == 1, "program retry and subsequent attendance reading must not rewrite attendance");
        }
        try (var ui = new Fixture(true)) {
            ui.failAttendanceRefresh();
            var mine = ui.block("catalog", null, true);
            ui.back("#attendancePanel"); mine.awaitEntered(); mine.release.countDown(); ui.ready();
            var attendance = ui.block("attendance", ui.a, false);
            ui.open("#enrollmentGrid", "Corso A"); attendance.awaitEntered();

            ui.back("#attendancePanel");
            ui.open("#enrollmentGrid", "Corso B"); ui.ready();
            ui.assertProgram(ui.b); ui.assertAttendance(ui.b, null);
            attendance.release.countDown();
            check(attendance.finished.await(10, TimeUnit.SECONDS), "obsolete A attendance must finish");
            Thread.sleep(100); fx(() -> null);
            ui.assertProgram(ui.b); ui.assertAttendance(ui.b, null);
            check(!fx(() -> ui.button("retryButton").isVisible()), "obsolete A attendance must not expose retry on B");
        }
    }

    private static void verifyEmptyContexts() throws Exception {
        try (var ui = new Fixture(false)) {
            ui.open("#courseGrid", "Corso A"); ui.ready();
            var gate = ui.block("catalog", null, false);
            ui.fire("enrollButton"); gate.awaitEntered();
            ui.hiddenCourses.addAll(List.of(ui.a, ui.b));
            gate.release.countDown(); ui.ready();
            ui.assertConfirmation("Iscrizione registrata.", false);
            check(!ui.feedback().contains("Aggiornamento"), "empty catalog must finalize write confirmation");
            check(!fx(() -> ui.button("retryButton").isVisible()), "empty successful catalog must not offer retry");
        }
        try (var ui = new Fixture(true)) {
            ui.hiddenCourses.add(ui.b);
            ui.fire("mineTab"); ui.ready();
            ui.open("#enrollmentGrid", "Corso A"); ui.ready();
            ui.back("#attendancePanel");
            ui.open("#enrollmentGrid", "Corso B"); ui.ready();
            fx(() -> {
                check(ui.list("sessionList").getItems().isEmpty() && ui.list("menuList").getItems().isEmpty(),
                        "enrollment without published summary must not retain the old program");
                check(!ui.root.lookup("#courseDetailPanel").isVisible(), "unavailable program must be hidden");
                return null;
            });
        }
        try (var ui = new Fixture(true)) {
            ui.fire("mineTab"); ui.ready();
            ui.open("#enrollmentGrid", "Corso A"); ui.ready();
            var gate = ui.block("attendance", ui.a, true);
            ui.attendance(AttendanceStatus.CONFIRMED); gate.awaitEntered(); gate.release.countDown(); ui.ready();
            ui.assertRefreshFailure("Presenza confermata.");
            fx(() -> {
                var list = ui.<EnrollmentSummary>list("enrollmentList");
                list.getSelectionModel().select(list.getItems().stream().filter(e -> e.courseId().equals(ui.b)).findFirst().orElseThrow());
                return null;
            });
            ui.ready();
            check(!fx(() -> ui.button("retryButton").isVisible()), "cached enrollment must invalidate old attendance retry");
            check(!ui.feedback().contains("Presenza confermata"), "new enrollment must not inherit previous confirmation");
            fx(() -> { ui.list("enrollmentList").getSelectionModel().clearSelection(); return null; });
            check(!fx(() -> ui.button("retryButton").isVisible()), "empty enrollment selection must invalidate retry");
        }
    }

    private static void verifyContexts() throws Exception {
        try (var ui = new Fixture(true)) {
            ui.open("#courseGrid", "Corso A");
            ui.ready();
            ui.assertProgram(ui.a);
            ui.back("#courseDetailPanel");
            var gate = ui.block("menu", ui.b, true);
            ui.open("#courseGrid", "Corso B");
            gate.awaitEntered();
            ui.assertEmptyProgram("BROWSE: B loading must not expose A's session or recipe");
            ui.snapshot("browse-b-loading");
            gate.release.countDown();
            ui.ready();
            ui.assertEmptyProgram("BROWSE: failed B must not expose A's session or recipe");
            ui.retryProgram(ui.b);

            ui.fire("mineTab");
            ui.ready();
            ui.open("#enrollmentGrid", "Corso A");
            ui.ready();
            ui.assertProgram(ui.a);
            ui.back("#attendancePanel");
            gate = ui.block("menu", ui.b, true);
            ui.open("#enrollmentGrid", "Corso B");
            gate.awaitEntered();
            ui.assertEmptyProgram("MINE: B loading must not expose A's session or recipe");
            ui.snapshot("mine-b-loading");
            gate.release.countDown();
            ui.ready();
            ui.assertEmptyProgram("MINE: failed B must keep its program empty");
            ui.snapshot("mine-b-error");
            var failedRetry = ui.block("menu", ui.b, true);
            int failedRetryMenus = ui.menuReads.get(), failedRetryCatalogs = ui.catalogReads.get();
            ui.fire("retryButton");
            failedRetry.awaitEntered();
            failedRetry.release.countDown();
            ui.ready();
            ui.assertEmptyProgram("a second B failure must keep B empty and retryable");
            check(ui.menuReads.get() == failedRetryMenus + 1 && ui.catalogReads.get() == failedRetryCatalogs,
                    "failed retry must reread only B's program");
            check(fx(() -> ui.button("retryButton").isVisible()), "second failure must still offer retry");
            ui.retryProgram(ui.b);
            ui.snapshot("mine-b-retry");


            for (boolean failure : List.of(false, true)) {
                ui.selectCourse(ui.a);
                ui.ready();
                gate = ui.block("menu", ui.b, failure);
                ui.selectCourse(ui.b);
                gate.awaitEntered();
                ui.selectCourse(ui.a);
                ui.ready();
                ui.assertProgram(ui.a);
                var feedback = ui.feedback();
                gate.release.countDown();
                check(gate.finished.await(10, TimeUnit.SECONDS), "obsolete read must finish");

                Thread.sleep(100);
                fx(() -> null);
                ui.assertProgram(ui.a);
                check(ui.feedback().equals(feedback), "obsolete success/failure must not change current feedback");
                check(!fx(ui.controller::isBusy), "obsolete callback must leave current busy state alone");
                check(!fx(() -> ui.button("retryButton").isVisible()), "obsolete failure must not offer retry");
            }
        }
    }

    private static void verifyWriteFeedback() throws Exception {
        try (var ui = new Fixture(false)) {
            ui.open("#courseGrid", "Corso A");
            ui.ready();
            var gate = ui.block("catalog", null, false);
            ui.fire("enrollButton");
            gate.awaitEntered();
            ui.assertConfirmation("Iscrizione registrata.", true);
            ui.snapshot("enroll-refresh-loading");
            gate.release.countDown();
            ui.ready();
            ui.assertConfirmation("Iscrizione registrata.", false);
            check(ui.enrollWrites.get() == 1, "enrollment must be written once");

            ui.fire("mineTab");
            ui.ready();
            ui.open("#enrollmentGrid", "Corso A");
            ui.ready();
            for (var status : List.of(AttendanceStatus.CONFIRMED, AttendanceStatus.DECLINED)) {
                String confirmation = status == AttendanceStatus.CONFIRMED
                        ? "Presenza confermata." : "Rinuncia registrata.";
                gate = ui.block("attendance", ui.a, true);
                ui.attendance(status);
                gate.awaitEntered();
                ui.assertConfirmation(confirmation, true);
                gate.release.countDown();
                ui.ready();
                ui.assertRefreshFailure(confirmation);
                ui.snapshot(status == AttendanceStatus.CONFIRMED ? "attendance-refresh-error" : "decline-refresh-error");
                int reads = ui.attendanceReads.get();
                int writes = ui.attendanceWrites.get();
                ui.fire("retryButton");
                ui.ready();
                ui.assertConfirmation(confirmation, false);
                check(ui.attendanceReads.get() == reads + 1, "attendance retry must repeat the failed reading");
                check(ui.attendanceWrites.get() == writes, "attendance retry must not repeat the write");
                fx(() -> {
                    PracticalAttendanceSummary attendance = ui.<PracticalAttendanceSummary>list("attendanceList").getItems().getFirst();
                    check(attendance.attendanceStatus().orElseThrow() == status, "retry must expose the persisted attendance");
                    return null;
                });
            }

            gate = ui.block("catalog", null, true);
            ui.withdraw();
            gate.awaitEntered();
            ui.assertConfirmation("Ritiro registrato.", true);
            gate.release.countDown();
            ui.ready();
            ui.assertRefreshFailure("Ritiro registrato.");
            ui.snapshot("withdraw-refresh-error");
            int writes = ui.withdrawWrites.get();
            ui.fire("retryButton");
            ui.ready();
            ui.assertConfirmation("Ritiro registrato.", false);
            check(ui.withdrawWrites.get() == writes && writes == 1, "withdrawal retry must never repeat withdrawal");
            check(fx(() -> ui.label("attendanceWarning").getText().contains("Ti sei ritirato")),
                    "withdrawal retry must update the enrollment context");
            ui.snapshot("withdraw-refresh-retry");
        }
        try (var ui = new Fixture(false)) {
            ui.open("#courseGrid", "Corso B");
            ui.ready();
            var gate = ui.block("menu", ui.b, true);
            ui.fire("enrollButton");
            gate.awaitEntered();
            ui.assertConfirmation("Iscrizione registrata.", true);
            gate.release.countDown();
            ui.ready();
            ui.assertRefreshFailure("Iscrizione registrata.");
            int reads = ui.menuReads.get();
            ui.fire("retryButton");
            ui.ready();
            ui.assertConfirmation("Iscrizione registrata.", false);
            ui.assertProgram(ui.b);
            check(ui.menuReads.get() == reads + 1 && ui.enrollWrites.get() == 1,
                    "nested program retry after enrollment must reload only program, not enrollment");

            var obsolete = ui.block("menu", ui.b, true);

            ui.selectCourse(ui.a); ui.ready();
            ui.selectCourse(ui.b); obsolete.awaitEntered();
            ui.selectCourse(ui.a); ui.ready();
            check(!ui.feedback().contains("Iscrizione registrata"), "a new course must not inherit B's confirmation");
            obsolete.release.countDown();
            check(obsolete.finished.await(10, TimeUnit.SECONDS), "old B reading must finish");
            Thread.sleep(100); fx(() -> null);
            ui.assertProgram(ui.a);
            check(!fx(() -> ui.button("retryButton").isVisible()), "old B error cannot expose retry on A");

            ui.selectCourse(ui.b); ui.ready();
            var failed = ui.block("menu", ui.a, true);
            ui.selectCourse(ui.a); failed.awaitEntered(); failed.release.countDown(); ui.ready();
            check(fx(() -> ui.button("retryButton").isVisible()), "A failure must offer its retry");
            ui.selectCourse(ui.b); ui.ready();
            int previousReads = ui.menuReads.get();
            fx(() -> { ui.button("retryButton").fire(); return null; });
            ui.ready(); ui.assertProgram(ui.b);
            check(ui.menuReads.get() == previousReads, "new B selection must invalidate A's hidden retry");
        }
    }

    private static final class ReadGate {
        final String kind;
        final UUID course;
        final boolean fail;
        final AtomicBoolean claimed = new AtomicBoolean();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        ReadGate(String kind, UUID course, boolean fail) { this.kind = kind; this.course = course; this.fail = fail; }
        void awaitEntered() throws Exception { check(entered.await(10, TimeUnit.SECONDS), kind + " reading did not start"); }
        void read(String reading, UUID id) throws Exception {
            if (!kind.equals(reading) || !Objects.equals(course, id) || !claimed.compareAndSet(false, true)) return;
            entered.countDown();
            try {
                check(release.await(10, TimeUnit.SECONDS), "blocked reading timed out");
                if (fail) throw new IllegalStateException("Synthetic " + kind + " read failure");
            } finally { finished.countDown(); }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        final UUID sa = UUID.randomUUID(), sb = UUID.randomUUID();
        final Map<UUID, EnrollmentStatus> enrollments = new ConcurrentHashMap<>();
        final Map<UUID, AttendanceStatus> attendance = new ConcurrentHashMap<>();
        final Map<UUID, PublishedCourseSessionSummary> changedSessions = new ConcurrentHashMap<>();
        final Set<UUID> hiddenCourses = ConcurrentHashMap.newKeySet();
        final AtomicInteger enrollWrites = new AtomicInteger(), withdrawWrites = new AtomicInteger();
        final AtomicInteger attendanceWrites = new AtomicInteger(), attendanceReads = new AtomicInteger();
        final AtomicInteger menuReads = new AtomicInteger(), catalogReads = new AtomicInteger();
        final List<PublishedCourseSummary> courses;
        final ParticipantWorkspaceController controller;
        final VBox root;
        final Stage stage;
        volatile ReadGate gate;

        Fixture(boolean enrolled) throws Exception {
            var category = new CategorySummary(UUID.randomUUID(), "Cucina sintetica", "Descrizione");
            courses = List.of(course(a, category, "Corso A", 2), course(b, category, "Corso B", 3));
            if (enrolled) { enrollments.put(a, EnrollmentStatus.ACTIVE); enrollments.put(b, EnrollmentStatus.ACTIVE); }
            var actor = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    Username.of("context.smoke"), "Partecipante sintetico", Role.PARTICIPANT);
            CourseRepository courseRepository = proxy(CourseRepository.class, (p, m, args) -> switch (m.getName()) {
                case "findPublished", "findPublishedByIds" -> {
                    catalogReads.incrementAndGet(); read("catalog", null);
                    yield ParticipantBrowseResponses.courses(args, courses.stream().filter(c -> !hiddenCourses.contains(c.id())).toList());
                }
                case "publishedSessions" -> ParticipantBrowseResponses.sessions(args[1], id -> List.of(session(id)));
                case "publishedMenu" -> { menuReads.incrementAndGet(); UUID id = (UUID) args[1]; read("menu", id); yield List.of(recipe(id)); }
                default -> null;
            });
            CatalogRepository catalog = proxy(CatalogRepository.class, (p, m, args) -> List.of(category));
            EnrollmentRepository enrollment = proxy(EnrollmentRepository.class, (p, m, args) -> switch (m.getName()) {
                case "findByParticipant" -> ParticipantBrowseResponses.enrollments(args, currentEnrollments());
                case "commitments" -> ParticipantBrowseResponses.commitments(currentEnrollments(), id -> List.of(practicalSession(id)));
                case "practicalSessions" -> {
                    attendanceReads.incrementAndGet(); UUID id = (UUID) args[1]; read("attendance", id);
                    yield List.of(practicalSession(id));
                }
                case "enroll" -> { enrollWrites.incrementAndGet(); read("enroll-write", (UUID) args[1]); enrollments.put((UUID) args[1], EnrollmentStatus.ACTIVE); yield null; }
                case "withdraw" -> { withdrawWrites.incrementAndGet(); enrollments.put((UUID) args[1], EnrollmentStatus.WITHDRAWN); yield null; }
                case "setAttendance" -> { attendanceWrites.incrementAndGet(); read("attendance-write", (UUID) args[1]); attendance.put((UUID) args[1], (AttendanceStatus) args[2]); yield null; }
                default -> null;
            });
            NotificationRepository notifications = proxy(NotificationRepository.class, (p, m, args) -> List.of());
            controller = new ParticipantWorkspaceController(actor, new BrowsePublishedCourses(courseRepository),
                    new BrowseCatalog(catalog), new BrowseEnrollments(enrollment), new ManageEnrollment(enrollment),
                    new ManagePracticalAttendance(enrollment), new BrowseNotifications(notifications), new ManageNotifications(notifications));
            root = fx(() -> {
                var loader = new FXMLLoader(ParticipantContextUiSmoke.class.getResource("participant-workspace.fxml"));
                loader.setController(controller);
                return loader.load();
            });
            stage = fx(() -> {
                var scene = new Scene(root, 1142, 800);
                scene.getStylesheets().add(ParticipantContextUiSmoke.class.getResource("/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
                var window = new Stage(); window.setScene(scene); window.show(); return window;
            });
            ready();
        }

        private List<EnrollmentSummary> currentEnrollments() {
            return courses.stream().filter(course -> enrollments.containsKey(course.id()))
                    .map(course -> new EnrollmentSummary(course.id(), course.title(), course.categoryName(), course.chefName(),
                            course.startDate(), enrollments.get(course.id()), Instant.now())).toList();
        }

        private PracticalAttendanceSummary practicalSession(UUID courseId) {
            var session = session(courseId);
            return new PracticalAttendanceSummary(session.id(), 1, session.scheduledAt(), 60,
                    session.venue().orElseThrow(), session.capacity().orElseThrow(), 0,
                    session.capacity().orElseThrow(), session.attendanceClosesAt().orElseThrow(),
                    Optional.ofNullable(attendance.get(session.id())), 1);
        }

        ReadGate block(String kind, UUID id, boolean fail) { return gate = new ReadGate(kind, id, fail); }
        void read(String kind, UUID id) throws Exception { var current = gate; if (current != null) current.read(kind, id); }
        PublishedCourseSummary course(UUID id, CategorySummary category, String title, int day) {
            return new PublishedCourseSummary(id, category.id(), category.name(), "Chef sintetico", CourseTitle.of(title),
                    "Descrizione " + title, LocalDate.of(2099, 10, day), LocalTime.of(18, 0), 1);
        }
        PublishedCourseSessionSummary session(UUID id) {
            if (changedSessions.containsKey(id)) return changedSessions.get(id);
            return new PublishedCourseSessionSummary(id.equals(a) ? sa : sb, 1, LocalDateTime.of(2099, 10, id.equals(a) ? 2 : 3, 18, 0),
                    SessionMode.PRACTICAL, 60, Optional.empty(), Optional.of("Sede " + (id.equals(a) ? "A" : "B")),
                    Optional.of(8), Optional.of(LocalDateTime.of(2099, 10, 1, 18, 0)), SessionStatus.SCHEDULED);
        }
        PublishedSessionRecipeSummary recipe(UUID id) {
            return new PublishedSessionRecipeSummary(id.equals(a) ? sa : sb, id, "Ricetta " + (id.equals(a) ? "A" : "B"),
                    "Preparazione sintetica", 1, BigDecimal.ONE, List.of(new PublishedSessionRecipeSummary.IngredientDose(
                            id.equals(a) ? "Farina A" : "Pomodoro B", BigDecimal.TEN, MeasurementUnit.GRAM)));
        }
        void ready() throws Exception { it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy(), 10); }
        Object field(String name) throws Exception { var f = controller.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(controller); }
        ButtonBase button(String name) throws Exception {
            if (name.equals("retryButton")) {
                var local = root.lookup("#sessionTimeline").lookup(".participant-session-feedback .button");
                if (local instanceof ButtonBase button && button.isVisible()) return button;
            }
            return (ButtonBase) field(name);
        }
        Label label(String name) throws Exception {
            if (name.equals("feedbackLabel")) {
                var local = root.lookup("#sessionTimeline").lookup(".participant-session-feedback .label");
                if (local instanceof Label label && label.isVisible()) return label;
            }
            return (Label) field(name);
        }
        @SuppressWarnings("unchecked") <T> ListView<T> list(String name) throws Exception { return (ListView<T>) field(name); }
        void fire(String name) throws Exception { fx(() -> { check(!button(name).isDisabled(), name + " must be enabled"); button(name).fire(); return null; }); }
        void back(String panel) throws Exception { fx(() -> { ((Button) ((VBox) root.lookup(panel)).getChildren().getFirst()).fire(); return null; }); }
        void open(String grid, String title) throws Exception {
            fx(() -> {
                var pane = (FlowPane) root.lookup(grid);
                var card = pane.getChildren().stream().filter(n -> n instanceof VBox && n.lookupAll(".label").stream()
                        .anyMatch(l -> ((Label) l).getText().equals(title))).findFirst().orElseThrow();
                var button = card.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(c -> c.getText().startsWith("Apri corso")).findFirst().orElseThrow();
                check(!button.isDisabled(), "course card must be enabled"); button.fire(); return null;
            });
        }
        void selectCourse(UUID id) throws Exception {
            fx(() -> { list("courseList").getSelectionModel().select(courses.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow()); return null; });
        }
        void assertBlockedCourse(String grid, String title) throws Exception {
            fx(() -> {
                var card = root.lookup(grid).lookupAll(".participant-course-card").stream()
                        .filter(node -> node.lookupAll(".label").stream().map(Label.class::cast)
                                .anyMatch(label -> label.getText().equals(title))).findFirst().orElseThrow();
                var open = ParticipantCourseCards.action((VBox) card);
                check(open.isDisabled(), "opening another course must be disabled while a write is pending");
                open.fire();
                check(label("courseTitle").getText().equals("Corso A"),
                        "disabled navigation must retain the pending write context");
                return null;
            });
        }
        void assertEmptyProgram(String message) throws Exception {
            fx(() -> {
                check(label("courseTitle").getText().equals("Corso B"), "current title must be B");
                check(list("sessionList").getItems().isEmpty() && list("menuList").getItems().isEmpty(), message);
                check(list("sessionList").getSelectionModel().getSelectedItem() == null
                        && list("menuList").getSelectionModel().getSelectedItem() == null, "old selection must be cleared");
                return null;
            });
        }
        void assertProgram(UUID id) throws Exception {
            fx(() -> {
                check(list("sessionList").getItems().equals(List.of(session(id))), "program session must belong to current course");
                check(list("menuList").getItems().equals(List.of(recipe(id))), "menu and ingredients must belong to current course");
                return null;
            });
        }
        void assertUnifiedTimeline(UUID course, String panel) throws Exception {
            fx(() -> {
                var timeline = (VBox) root.lookup("#sessionTimeline");
                check(timeline != null && timeline.isVisible() && timeline.isManaged(),
                        "detail must render a single visible timeline");
                var sessionRows = timeline.getChildren().stream()
                        .filter(node -> node.getStyleClass().contains("participant-session-row")).toList();
                check(sessionRows.size() == 1
                                && Objects.equals(sessionRows.getFirst().getUserData(), course.equals(a) ? sa : sb),
                        "session and attendance must share a single row identified by the session");
                check(timeline.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                                .anyMatch(button -> button.getAccessibleText().contains(recipe(course).name())),
                        "the historical recipe must be accessible inside its session");
                check(!list("sessionList").isManaged() && !list("menuList").isManaged()
                                && !list("attendanceList").isManaged(),
                        "program, menu and attendance backing lists must not create duplicate visible content");
                check(((VBox) root.lookup(panel)).getChildren().getFirst().isFocused(),
                        "opening course details must focus their back control");
                return null;
            });
        }
        void assertAttendance(UUID id, AttendanceStatus status) throws Exception {
            fx(() -> {
                var rows = this.<PracticalAttendanceSummary>list("attendanceList").getItems();
                check(rows.size() == 1 && rows.getFirst().sessionId().equals(id.equals(a) ? sa : sb)
                                && rows.getFirst().attendanceStatus().equals(Optional.ofNullable(status)),
                        "attendance must load for the current enrollment alongside the program");
                return null;
            });
        }
        void failAttendanceRefresh() throws Exception {
            fire("mineTab"); ready(); open("#enrollmentGrid", "Corso A"); ready();
            var failed = block("attendance", a, true);
            attendance(AttendanceStatus.CONFIRMED); failed.awaitEntered(); failed.release.countDown(); ready();
            assertRefreshFailure("Presenza confermata.");
        }
        void retryProgram(UUID id) throws Exception {
            int menus = menuReads.get(), catalogs = catalogReads.get();
            fire("retryButton"); ready(); assertProgram(id);
            check(menuReads.get() == menus + 1, "retry must reread the failed program");
            check(catalogReads.get() == catalogs, "program retry must not reread catalog or mine section");
            check(!fx(() -> button("retryButton").isVisible()), "successful retry must hide retry");
        }
        String feedback() throws Exception { return fx(() -> label("feedbackLabel").getText()); }
        void assertConfirmation(String prefix, boolean busy) throws Exception {
            fx(() -> {
                check(label("feedbackLabel").isVisible() && label("feedbackLabel").getText().startsWith(prefix),
                        "successful write confirmation must remain visible: " + prefix + " actual=" + label("feedbackLabel").getText());
                check(controller.isBusy() == busy, "expected refresh busy=" + busy);
                return null;
            });
        }
        void assertRefreshFailure(String prefix) throws Exception {
            assertConfirmation(prefix, false);
            check(feedback().contains("dati non sono stati aggiornati"), "refresh error must explicitly distinguish saved write from stale data");
            check(fx(() -> button("retryButton").isVisible()), "failed refresh must offer read retry");
        }
        void attendance(AttendanceStatus status) throws Exception {
            fx(() -> {
                var method = controller.getClass().getDeclaredMethod("changeAttendance", PracticalAttendanceSummary.class, AttendanceStatus.class);
                method.setAccessible(true); method.invoke(controller, this.<PracticalAttendanceSummary>list("attendanceList").getItems().getFirst(), status);
                return null;
            });
        }
        void withdraw() throws Exception {
            fx(() -> {
                Platform.runLater(() -> {
                    var window = Window.getWindows().stream().filter(w -> w != stage && w.isShowing()).findFirst().orElseThrow();
                    var pane = (DialogPane) window.getScene().getRoot();
                    var type = pane.getButtonTypes().stream().filter(t -> t.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
                    ((Button) pane.lookupButton(type)).fire();
                });
                button("withdrawButton").fire(); return null;
            });
        }
        void snapshot(String name) throws Exception { fx(() -> { FxSnapshot.save(root, "tmp/participant-context-fix/" + name + ".png"); return null; }); }
        public void close() throws Exception { if (gate != null) gate.release.countDown(); fx(() -> { controller.dispose(); stage.close(); return null; }); }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(ParticipantContextUiSmoke.class.getClassLoader(), new Class<?>[]{type}, handler);
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<T>(action); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
