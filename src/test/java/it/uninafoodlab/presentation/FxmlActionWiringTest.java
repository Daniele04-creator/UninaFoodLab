package it.uninafoodlab.presentation;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

class FxmlActionWiringTest {

    private static final Map<String, Path> CONTROLLERS = Map.of(
            "catalog-workspace.fxml", javaSource("catalog", "CatalogWorkspaceController.java"),
            "login.fxml", javaSource("login", "LoginController.java"),
            "app-shell.fxml", javaSource("shell", "AppShellController.java"),
            "dashboard-workspace.fxml", javaSource("dashboard", "DashboardWorkspaceController.java"),
            "notification-workspace.fxml", javaSource("notification", "NotificationWorkspaceController.java"),
            "course-workspace.fxml", javaSource("course", "CourseWorkspaceController.java"),
            "planning-workspace.fxml", javaSource("planning", "PlanningWorkspaceController.java"));

    @Test
    void everyDeclaredButtonInvokesAnExistingControllerAction() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);

        for (var entry : CONTROLLERS.entrySet()) {
            var fxml = fxml(entry.getKey());
            var document = factory.newDocumentBuilder().parse(fxml.toFile());
            var controller = Files.readString(entry.getValue(), UTF_8);

            assertControlsHaveHandlers(document.getElementsByTagName("Button"), fxml, controller);
            assertControlsHaveHandlers(document.getElementsByTagName("ToggleButton"), fxml, controller);
        }
    }

    private static void assertControlsHaveHandlers(
            org.w3c.dom.NodeList controls, Path fxml, String controller) {
        for (var index = 0; index < controls.getLength(); index++) {
            var control = (Element) controls.item(index);
            var action = control.getAttribute("onAction");
            assertThat(action)
                    .as("%s button '%s'", fxml.getFileName(), control.getAttribute("text"))
                    .startsWith("#");
            var method = action.substring(1);
            assertThat(controller)
                    .as("%s handler #%s", fxml.getFileName(), method)
                    .containsPattern(Pattern.compile("\\bvoid\\s+" + Pattern.quote(method) + "\\s*\\("));
        }
    }

    private static Path fxml(String name) throws IOException {
        try (var paths = Files.walk(Path.of("src/main/resources/it/uninafoodlab/presentation"))) {
            return paths.filter(path -> path.getFileName().toString().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Missing FXML " + name));
        }
    }

    private static Path javaSource(String feature, String fileName) {
        return Path.of("src/main/java/it/uninafoodlab/presentation", feature, fileName);
    }
}
