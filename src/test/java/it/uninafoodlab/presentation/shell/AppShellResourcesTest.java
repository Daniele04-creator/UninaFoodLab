package it.uninafoodlab.presentation.shell;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AppShellResourcesTest {

    @Test
    void packagesTheApplicationShellAndStylesheet() {
        assertThat(getClass().getResource("/it/uninafoodlab/presentation/shell/app-shell.fxml"))
                .isNotNull();
        assertThat(getClass().getResource("/it/uninafoodlab/presentation/assets/brand/uninafoodlab-logo-orange.png"))
                .isNotNull();
        assertThat(getClass().getResource("/it/uninafoodlab/presentation/styles/application.css"))
                .isNotNull();
    }

    @Test
    void shellExposesItalianNavigationAndAccessibleLabels() throws IOException {
        var resource = getClass().getResourceAsStream(
                "/it/uninafoodlab/presentation/shell/app-shell.fxml");

        assertThat(resource).isNotNull();
        try (resource) {
            var fxml = new String(resource.readAllBytes(), UTF_8);
            assertThat(fxml)
                    .contains("Agenda", "Corsi", "Comunicazioni",
                            "Ricettario", "Report")
                    .contains("workbench-navigation", "fx:id=\"contentHost\"")
                    .doesNotContain("shell-page-header", "fx:id=\"pageTitle\"", "fx:id=\"pageDescription\"")
                    .contains("accessibleText=")
                    .doesNotContain("reportsButton", "text=\"Fabbisogni\"");
        }
    }

    @Test
    void shellBrandIsStaticAndDoesNotAddATabStop() throws IOException {
        var fxml = stylesheetResource("/it/uninafoodlab/presentation/shell/app-shell.fxml");

        assertThat(fxml)
                .contains("<Label fx:id=\"brandTitle\"")
                .doesNotContain("brand-home");
    }

    @Test
    void stylesheetDefinesAVisibleKeyboardFocus() throws IOException {
        var resource = getClass().getResourceAsStream(
                "/it/uninafoodlab/presentation/styles/application.css");

        assertThat(resource).isNotNull();
        try (resource) {
            var css = new String(resource.readAllBytes(), UTF_8);
            assertThat(css)
                    .contains("-fx-focus-color")
                    .contains(".logout-button:focus-visible")
                    .contains(".button:focus-visible")
                    .doesNotContain(".button:focused", ".navigation-button:focused")
                    .contains(".text-field:focused")
                    .contains("-fx-font-size: 14px");
        }
    }

    @Test
    void secondaryProfileTextMeetsNormalTextContrast() throws IOException {
        var css = stylesheet();
        var foreground = colorProperty(css, ".lab-profile .profile-role", "-fx-text-fill");
        var background = colorProperty(css, ".shell-header", "-fx-background-color");

        assertThat(contrastRatio(foreground, background)).isGreaterThanOrEqualTo(4.5);
    }

    @Test
    void disabledActionLabelsRemainReadableWithTheStudioPalette() throws IOException {
        var css = stylesheet();
        var selector = ".button:disabled, .toggle-button:disabled";
        var foreground = colorProperty(css, selector, "-fx-text-fill");
        var background = colorProperty(css, selector, "-fx-background-color");

        assertThat(contrastRatio(foreground, background)).isGreaterThanOrEqualTo(4.5);
    }

    @Test
    void shellKeepsBrandAndTaskNavigationWithoutTechnicalDiagnostics() throws IOException {
        var fxml = stylesheetResource("/it/uninafoodlab/presentation/shell/app-shell.fxml");
        var css = stylesheet();

        assertThat(fxml)
                .contains("shell-header", "workbench-navigation", "lab-navigation", "lab-brand-title", "lab-profile")
                .doesNotContain("<left>", "lab-sidebar", "environment-card", "SPAZIO DI LAVORO", "OPERATIVITÀ");
        assertThat(css)
                .contains(".workbench-navigation", ".navigation-button-selected", ".workspace-hero")
                .contains(".shell-header", ".surface-card")
                .doesNotContain(".environment-card");
    }

    @Test
    void shellStartsMaximizedWithoutShrinkingTheJavaFxLogicalViewport() throws IOException {
        var navigator = Files.readString(Path.of(
                "src/main/java/it/uninafoodlab/presentation/navigation/SceneNavigator.java"), UTF_8);

        assertThat(navigator)
                .contains("show(load(loader), 1120, 720, false)")
                .contains("show(root, SHELL_WIDTH, SHELL_HEIGHT, true)")
                .contains("stage.setMaximized(maximized)");
    }

    @Test
    void courseNavigationKeepsSpecificSessionEntryPoints() throws ReflectiveOperationException {
        var controller = it.uninafoodlab.presentation.course.CourseWorkspaceController.class;
        for (var action : java.util.List.of("showSessionActions", "showSessionParticipants")) {
            var method = controller.getMethod(action, java.util.UUID.class, java.util.UUID.class);
            assertThat(method.getReturnType()).isEqualTo(void.class);
        }
        assertThat(controller.getDeclaredMethods()).extracting(java.lang.reflect.Method::getName)
                .doesNotContain("showAttendanceRegister", "showCourseOverview");
        var menuEntry = AppShellController.class.getDeclaredMethod(
                "showRecipeAssignment", java.util.UUID.class, java.util.UUID.class);
        assertThat(menuEntry.getReturnType()).isEqualTo(void.class);
    }

    @Test
    void shellOwnsTheNotificationRefreshLifecycle() throws IOException, ReflectiveOperationException {
        var shell = Files.readString(Path.of(
                "src/main/java/it/uninafoodlab/presentation/shell/AppShellController.java"), UTF_8);

        assertThat(shell)
                .contains("private NotificationWorkspaceController notificationWorkspaceController;")
                .contains("notificationWorkspaceController = new NotificationWorkspaceController(")
                .contains("notificationWorkspaceController.activate()")
                .contains("notificationWorkspaceController.deactivate()");
        assertThat(it.uninafoodlab.presentation.common.WorkspaceState.class)
                .isAssignableFrom(it.uninafoodlab.presentation.notification.NotificationWorkspaceController.class);
        assertThat(it.uninafoodlab.presentation.notification.NotificationWorkspaceController.class
                .getDeclaredMethod("dispose").getReturnType()).isEqualTo(void.class);
        var navigator = Files.readString(Path.of(
                "src/main/java/it/uninafoodlab/presentation/navigation/SceneNavigator.java"), UTF_8);
        assertThat(navigator).contains("WorkspaceState::dispose", "disposeWorkspace.run()");
    }

    private static String stylesheet() throws IOException {
        return stylesheetResource("/it/uninafoodlab/presentation/styles/application.css");
    }

    private static String stylesheetResource(String path) throws IOException {
        try (var resource = AppShellResourcesTest.class.getResourceAsStream(path)) {
            assertThat(resource).isNotNull();
            return new String(resource.readAllBytes(), UTF_8);
        }
    }

    private static String colorProperty(String css, String selector, String property) {
        var rule = Pattern.compile(
                        "(?m)^\\s*" + Pattern.quote(selector) + "\\s*\\{([^}]*)}", Pattern.DOTALL)
                .matcher(css);
        assertThat(rule.find()).as("CSS selector %s", selector).isTrue();

        var color = Pattern.compile(Pattern.quote(property)
                        + "\\s*:\\s*(#[0-9a-fA-F]{6}|-(?:atelier|studio)-[a-z-]+)")
                .matcher(rule.group(1));
        assertThat(color.find()).as("CSS property %s in %s", property, selector).isTrue();
        var value = color.group(1);
        return value.startsWith("#") ? value : colorProperty(css, ".root", value);
    }

    private static double contrastRatio(String first, String second) {
        var firstLuminance = relativeLuminance(first);
        var secondLuminance = relativeLuminance(second);
        var lighter = Math.max(firstLuminance, secondLuminance);
        var darker = Math.min(firstLuminance, secondLuminance);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(String color) {
        var red = linearChannel(Integer.parseInt(color.substring(1, 3), 16) / 255.0);
        var green = linearChannel(Integer.parseInt(color.substring(3, 5), 16) / 255.0);
        var blue = linearChannel(Integer.parseInt(color.substring(5, 7), 16) / 255.0);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linearChannel(double channel) {
        return channel <= 0.04045
                ? channel / 12.92
                : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
