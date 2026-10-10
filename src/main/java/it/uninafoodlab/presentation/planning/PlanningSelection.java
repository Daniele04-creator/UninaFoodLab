package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.application.course.BrowseCourses;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.common.FxAsyncExecutor;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.EnumMap;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

import javafx.util.StringConverter;

import static it.uninafoodlab.presentation.planning.PlanningLabels.planningSessionLabel;

final class PlanningSelection {
    private static final String CHOOSE_COURSE_PROMPT = "Prima scegli un corso";
    private final AuthenticatedUser user;
    private final BrowseCourses browseCourses;
    private final Host host;
    private boolean embedded;
    private boolean shoppingOnly = true;
    private boolean courseRequirements;
    private UUID embeddedCourseId;
    private UUID embeddedSessionId;
    private boolean updatingSelectors;
    private boolean loadingCourses;
    private boolean loadingSessions;
    private long courseLoadVersion;
    private UUID requestedCourseId;
    private UUID requestedSessionId;
    private boolean requestedCourseDeterminesScope = true;
    private int sessionLoadVersion;
    private List<CourseSessionSummary> planningSessions = List.of();
    private PlanningPresentation.CourseScope currentScope = PlanningPresentation.CourseScope.OPEN;
    private final EnumMap<PlanningPresentation.CourseScope, CourseSelection> scopeSelections =
            new EnumMap<>(PlanningPresentation.CourseScope.class);
    private final ComboBox<PlanningPresentation.CourseScope> scopeField;
    private final ComboBox<PlanningFilterChoice<CourseSummary>> courseField;
    private final ComboBox<PlanningFilterChoice<CourseSessionSummary>> sessionField;
    private final Label courseScopeDescription;
    PlanningSelection(
            AuthenticatedUser user,
            BrowseCourses browseCourses,
            Host host,
            ComboBox<PlanningPresentation.CourseScope> scopeField,
            ComboBox<PlanningFilterChoice<CourseSummary>> courseField,
            ComboBox<PlanningFilterChoice<CourseSessionSummary>> sessionField,
            Label courseScopeDescription) {
        this.user = user;
        this.browseCourses = browseCourses;
        this.host = host;
        this.scopeField = scopeField;
        this.courseField = courseField;
        this.sessionField = sessionField;
        this.courseScopeDescription = courseScopeDescription;
    }

    interface Host {
        void invalidatePlanning();
        void useSingleSession();
        boolean isBusy();
        void setEmbedded(boolean value);
        void showPlanning();
        void showPanels(boolean requirements, boolean participants, boolean report);
        void clearSessionPlanning(String summary);
        void fail(String message);
        void fail(String message, Runnable retry);
        void setBusy(boolean value);
        void loadCourseParticipants(UUID courseId);
        void loadSessionPlanning();
        void loadCourseRequirements();
    }

    void refreshForNavigation() {
        if (loadingCourses || loadingSessions) return;

        if (requestedCourseId == null) {
            requestedCourseDeterminesScope = true;
            requestedCourseId = selectedValue(courseField).map(CourseSummary::id).orElse(null);
            requestedSessionId = selectedValue(sessionField).map(CourseSessionSummary::id).orElse(null);
        }
        sessionLoadVersion++;
        host.invalidatePlanning();
        loadCourses();
    }

    void showSessionPlanning(UUID courseId, UUID sessionId) {
        rememberScopeSelection();
        requestedCourseDeterminesScope = true;
        sessionLoadVersion++;
        loadingSessions = false;
        host.invalidatePlanning();
        requestedCourseId = Objects.requireNonNull(courseId, "courseId");
        requestedSessionId = Objects.requireNonNull(sessionId, "sessionId");
        host.useSingleSession();
        host.showPlanning();
        if (!loadingCourses) loadCourses();
    }

    void showCourseRequirements(UUID courseId) {
        courseRequirements = true;
        requestedCourseDeterminesScope = true;
        requestedCourseId = Objects.requireNonNull(courseId, "courseId");
        requestedSessionId = null;
        sessionLoadVersion++;
        loadingSessions = false;
        planningSessions = List.of();
        host.invalidatePlanning();
        host.clearSessionPlanning("Caricamento delle sessioni in corso.");
        if (!loadingCourses) loadCourses();
    }

    void showEmbeddedSession(UUID courseId, UUID sessionId, boolean participants) {
        Objects.requireNonNull(courseId, "courseId");
        var sameContext = embedded && courseId.equals(embeddedCourseId)
                && Objects.equals(sessionId, embeddedSessionId);
        embeddedCourseId = courseId;
        embeddedSessionId = sessionId;
        embedded = true;
        shoppingOnly = false;
        host.setEmbedded(true);
        host.useSingleSession();
        host.showPanels(!participants, participants, false);
        if (sameContext) {
            if (!host.isBusy()) {

                requestedCourseId = courseId;
                requestedSessionId = sessionId;
                refreshForNavigation();
            }
            return;
        }
        requestedCourseDeterminesScope = true;
        requestedCourseId = courseId;
        requestedSessionId = sessionId;
        sessionLoadVersion++;
        host.invalidatePlanning();
        loadingSessions = false;
        host.clearSessionPlanning("Caricamento in corso.");
        if (!loadingCourses) loadCourses();
    }

    private void selectRequestedCourse() {
        var choice = courseField.getItems().stream()
                .filter(item -> item.value().map(course -> course.id().equals(requestedCourseId)).orElse(false))
                .findFirst();
        if (choice.isEmpty()) {
            requestedCourseId = null;
            requestedSessionId = null;
            host.clearSessionPlanning("Seleziona un corso disponibile.");
            host.fail("Il corso richiesto non è più disponibile per la pianificazione.");
            return;
        }
        updatingSelectors = true;
        try { courseField.setValue(choice.orElseThrow()); }
        finally { updatingSelectors = false; }
        if (embedded && embeddedSessionId == null) {
            requestedCourseId = null;
            requestedSessionId = null;
            host.loadCourseParticipants(embeddedCourseId);
        } else loadSessions(choice.orElseThrow().value().orElseThrow());
    }

    void loadSelectedCourseSessions() {
        if (updatingSelectors) return;
        requestedCourseId = null;
        requestedSessionId = null;
        sessionLoadVersion++;
        loadingSessions = false;
        host.invalidatePlanning();
        updatingSelectors = true;
        try {
            sessionField.getItems().setAll(PlanningFilterChoice.all(CHOOSE_COURSE_PROMPT));
            sessionField.getSelectionModel().selectFirst();
        } finally { updatingSelectors = false; }
        host.clearSessionPlanning("Caricamento delle sessioni in corso.");
        var course = selectedValue(courseField);
        if (course.isEmpty()) {
            planningSessions = List.of();
            sessionField.getItems().setAll(PlanningFilterChoice.all(CHOOSE_COURSE_PROMPT));
            sessionField.getSelectionModel().selectFirst();
            host.clearSessionPlanning(selectedValue(courseField).isEmpty() ? "Scegli un corso." : "Scegli una sessione pratica.");
            return;
        }
        loadSessions(course.orElseThrow());
    }

    void configureSelectors() {
        updatingSelectors = true;
        try {
            scopeField.getItems().setAll(PlanningPresentation.CourseScope.values());
            scopeField.setValue(currentScope);
        } finally { updatingSelectors = false; }
        courseField.setConverter(new StringConverter<>() {
            @Override
            public String toString(PlanningFilterChoice<CourseSummary> choice) {
                return choice == null ? "—" : choice.label();
            }

            @Override
            public PlanningFilterChoice<CourseSummary> fromString(String value) {
                return null;
            }
        });
        sessionField.setConverter(new StringConverter<>() {
            @Override
            public String toString(PlanningFilterChoice<CourseSessionSummary> choice) {
                return choice == null ? "—" : choice.label();
            }

            @Override
            public PlanningFilterChoice<CourseSessionSummary> fromString(String value) {
                return null;
            }
        });
    }

    private void rememberScopeSelection() {
        var courseId = requestedCourseId != null ? requestedCourseId
                : selectedValue(courseField).map(CourseSummary::id).orElse(null);
        var sessionId = requestedCourseId != null ? requestedSessionId
                : selectedValue(sessionField).map(CourseSessionSummary::id).orElse(null);
        scopeSelections.put(currentScope, new CourseSelection(courseId, sessionId));
    }

    void changeCourseScope() {
        if (updatingSelectors || scopeField.getValue() == null || scopeField.getValue() == currentScope) return;
        rememberScopeSelection();
        currentScope = scopeField.getValue();
        host.useSingleSession();
        var remembered = scopeSelections.get(currentScope);
        requestedCourseDeterminesScope = false;
        requestedCourseId = remembered == null ? null : remembered.courseId();
        requestedSessionId = remembered == null ? null : remembered.sessionId();
        sessionLoadVersion++;
        host.invalidatePlanning();
        loadingSessions = false;
        planningSessions = List.of();
        updatingSelectors = true;
        try {
            courseField.getItems().setAll(PlanningFilterChoice.none());
            courseField.getSelectionModel().selectFirst();
            sessionField.getItems().setAll(PlanningFilterChoice.all(CHOOSE_COURSE_PROMPT));
            sessionField.getSelectionModel().selectFirst();
        } finally { updatingSelectors = false; }
        host.clearSessionPlanning("Caricamento dei corsi in corso.");
        loadCourses();
    }

    void loadCourses() {
        var version = ++courseLoadVersion;
        loadingCourses = true;
        host.setBusy(true);
        FxAsyncExecutor.run(
                "planning-course-load",
                () -> browseCourses.courses(user, Optional.empty(), Optional.empty()),
                courses -> {
                    if (version != courseLoadVersion) return;
                    acceptCourses(courses);
                },
                failure -> {
                    if (version == courseLoadVersion) {
                        loadingCourses = false;
                        host.fail("Non è stato possibile caricare i corsi.", this::refreshForNavigation);
                    }
                });
    }

    private void loadSessions(CourseSummary course) {
        var version = ++sessionLoadVersion;
        loadingSessions = true;
        host.invalidatePlanning();
        host.setBusy(true);
        FxAsyncExecutor.run(
                "planning-session-load",
                () -> browseCourses.sessions(user, course.id()),
                availableSessions -> {
                    if (version != sessionLoadVersion) return;
                    acceptSessions(course, availableSessions);
                },
                failure -> {
                    if (version == sessionLoadVersion) {
                        loadingSessions = false;
                        host.fail("Non è stato possibile caricare le sessioni pratiche.", () -> loadSessions(course));
                    }
                });
    }

    private void acceptCourses(List<CourseSummary> courses) {
        loadingCourses = false;
        var now = LocalDateTime.now(ZoneId.of("Europe/Rome"));
        selectDestinationScope(courses, now);
        var visibleCourses = PlanningPresentation.visibleCourses(courses, currentScope, now);

        if (!requestedCourseDeterminesScope && visibleCourses.stream()
                .noneMatch(course -> course.id().equals(requestedCourseId))) {
            requestedCourseId = null;
            requestedSessionId = null;
        }
        requestedCourseDeterminesScope = true;
        var choices = new ArrayList<PlanningFilterChoice<CourseSummary>>();
        choices.add(PlanningFilterChoice.all(visibleCourses.isEmpty() ? "Nessun corso disponibile" : "Scegli un corso"));
        visibleCourses.stream()
                .map(course -> PlanningFilterChoice.of(course, course.title().value()))
                .forEach(choices::add);
        updatingSelectors = true;
        try {
            scopeField.setValue(currentScope);
            courseField.getItems().setAll(choices);
            courseField.getSelectionModel().selectFirst();
            sessionField.getItems().setAll(PlanningFilterChoice.all(CHOOSE_COURSE_PROMPT));
            sessionField.getSelectionModel().selectFirst();
        } finally { updatingSelectors = false; }
        courseScopeDescription.setText(currentScope == PlanningPresentation.CourseScope.OPEN
                ? "Corsi in programma e in svolgimento"
                : "Corsi terminati o annullati, sola consultazione");
        host.setBusy(false);
        if (requestedCourseId != null) {
            selectRequestedCourse();
        } else {
            planningSessions = List.of();
            var prompt = "Seleziona un corso e una sessione pratica.";
            if (visibleCourses.isEmpty()) {
                prompt = currentScope == PlanningPresentation.CourseScope.OPEN
                        ? "Nessun corso attivo."
                        : "Nessun corso passato.";
            }
            host.clearSessionPlanning(prompt);
        }
    }

    private void selectDestinationScope(List<CourseSummary> courses, LocalDateTime now) {
        if (shoppingOnly) {
            currentScope = PlanningPresentation.CourseScope.OPEN;
        } else if (requestedCourseDeterminesScope) {
            currentScope = PlanningPresentation.destinationScope(courses, requestedCourseId, currentScope, now);
        }
    }

    private void acceptSessions(CourseSummary course, List<CourseSessionSummary> availableSessions) {
        loadingSessions = false;
        if (isEmbeddedOnlineSession(availableSessions)) {
            requestedCourseId = null;
            requestedSessionId = null;
            host.loadCourseParticipants(course.id());
            return;
        }
        var sessions = shoppingOnly
                ? PlanningPresentation.futureRequirementsSessions(availableSessions, LocalDateTime.now(ZoneId.of("Europe/Rome")))
                : PlanningPresentation.consultableSessions(availableSessions);
        planningSessions = List.copyOf(sessions);
        var choices = new ArrayList<PlanningFilterChoice<CourseSessionSummary>>();
        choices.add(PlanningFilterChoice.all(sessions.isEmpty()
                ? "Nessuna sessione pratica" : "Scegli una sessione pratica"));
        sessions.stream()
                .map(session -> PlanningFilterChoice.of(
                        session, planningSessionLabel(session)))
                .forEach(choices::add);
        var requested = requestedCourseId != null && requestedCourseId.equals(course.id())
                ? requestedSessionId : null;
        var desired = choices.stream().filter(choice -> choice.value()
                .map(session -> session.id().equals(requested)).orElse(false)).findFirst();
        updatingSelectors = true;
        try {
            sessionField.getItems().setAll(choices);
            sessionField.setValue(desired.orElse(choices.getFirst()));
        } finally { updatingSelectors = false; }
        requestedCourseId = null;
        requestedSessionId = null;
        host.setBusy(false);
        host.clearSessionPlanning(sessions.isEmpty()
                ? "Nessuna sessione pratica per il corso selezionato."
                : "Scegli una sessione pratica.");
        loadAcceptedSelection(requested, desired.isPresent());
    }

    private void loadAcceptedSelection(UUID requested, boolean selectedSession) {
        if (requested != null && !selectedSession) {
            host.fail("La sessione richiesta non è più disponibile per la pianificazione.");
        } else if (selectedSession || courseRequirements) {
            host.loadSessionPlanning();
        }
    }

    private boolean isEmbeddedOnlineSession(List<CourseSessionSummary> availableSessions) {
        if (!embedded || embeddedSessionId == null) return false;
        return availableSessions.stream().anyMatch(item -> item.id().equals(embeddedSessionId)
                && item.mode() == it.uninafoodlab.domain.course.SessionMode.ONLINE);
    }

    static <T> Optional<T> selectedValue(ComboBox<PlanningFilterChoice<T>> field) {
        var choice = field.getValue();
        return choice == null ? Optional.empty() : choice.value();
    }
    void loadUnselectedSession(boolean requirementsVisible) {
        var choice = sessionField.getValue();
        if (requirementsVisible && choice != null && choice.label().equals("Fabbisogni delle sessioni future")
                && !planningSessions.isEmpty()) {
            host.loadCourseRequirements();
        } else {
            host.clearSessionPlanning(selectedValue(courseField).isEmpty() ? "Scegli un corso." : "Scegli una sessione pratica.");
        }
    }
    boolean isUpdating() { return updatingSelectors; }
    void loadAvailableSession() {
        if (!host.isBusy() && !planningSessions.isEmpty() && selectedValue(sessionField).isPresent()) {
            host.loadSessionPlanning();
        }
    }
    void setShoppingOnly(boolean value) {
        if (shoppingOnly == value) return;
        shoppingOnly = value;
        sessionLoadVersion++;
        loadingSessions = false;
        host.invalidatePlanning();
        if (!loadingCourses) loadCourses();
    }
    void leaveEmbedded() { embedded = false; courseRequirements = false; }
    List<CourseSessionSummary> sessions() { return planningSessions; }
    PlanningPresentation.CourseScope scope() { return currentScope; }
    private record CourseSelection(UUID courseId, UUID sessionId) {}
}
