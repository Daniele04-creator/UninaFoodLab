package it.uninafoodlab.presentation.login;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class LoginResourcesTest {

    @Test
    void packagesAnAccessibleItalianLoginWithoutEmbeddedPasswords() throws IOException {
        try (var resource = getClass().getResourceAsStream(
                "/it/uninafoodlab/presentation/login/login.fxml")) {
            assertThat(resource).isNotNull();
            var fxml = new String(resource.readAllBytes(), UTF_8);
            assertThat(fxml)
                    .contains("Username", "Password", "Accedi")
                    .contains("accessibleText=")
                    .contains("onAction=\"#submit\"")
                    .doesNotContain("ChefDemo!2026", "ParticipantDemo!2026", "login-brand-accent");
        }
    }

    @Test
    void loginStylesIncludeLoadingErrorAndFocusStates() throws IOException {
        try (var resource = getClass().getResourceAsStream(
                "/it/uninafoodlab/presentation/styles/application.css")) {
            assertThat(resource).isNotNull();
            var css = new String(resource.readAllBytes(), UTF_8);
            assertThat(css)
                    .contains(".login-error")
                    .contains(".login-progress")
                    .contains(".login-field:focused")
                    .contains(".login-submit:focus-visible");
        }
    }

    @Test
    void loginProvidesAccessibleScrollableAuthentication() throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/login/login.fxml");
        var css = resource("/it/uninafoodlab/presentation/styles/application.css");

        assertThat(fxml)
                .contains("UninaFoodLab", "Accesso")
                .doesNotContain("Accesso Chef", "Per assistenza,", "styleClass=\"login-title\"")
                .contains("onAction=\"#togglePassword\"", "fx:id=\"visiblePasswordField\"")
                .contains("login-brand-title", "access-container", "access-scroll", "login-card")
                .contains("<center>")
                .doesNotContain("<left>", "ImageView", "lab-login-story")
                .doesNotContain("login-introduction", "login-editorial-title", "login-story-copy")
                .doesNotContain("Una regia chiara per corsi, persone e cucina.")
                .doesNotContain("Il tuo spazio di lavoro è pronto.")
                .doesNotContain("JAVA 25 · JAVAFX · POSTGRESQL 18", "documentati nel README");
        assertThat(css)
                .contains(".lab-login-main")
                .contains(".login-card", ".login-submit:hover")
                .doesNotContain(".login-brand-accent");
    }

    @Test
    void loginKeepsDecorativeAssetsSeparateFromAuthenticationInputs() throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/login/login.fxml");

        assertThat(fxml).doesNotContain("ImageView", "lab-workspace.png", "botanical-sprig.png");
    }

    @Test
    void loginDoesNotOfferAssistedPasswordRecovery() throws IOException {
        assertThat(resource("/it/uninafoodlab/presentation/login/login.fxml"))
                .doesNotContain("#resetPassword", "dimenticato", "recupero")
                .contains("#registerAccount", "#submit");
    }

    private static String resource(String path) throws IOException {
        try (var resource = LoginResourcesTest.class.getResourceAsStream(path)) {
            assertThat(resource).as(path).isNotNull();
            return new String(resource.readAllBytes(), UTF_8);
        }
    }
}
