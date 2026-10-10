package it.uninafoodlab.presentation.common;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

public class UserErrorMessagesTest {
    private static final String FALLBACK = "Operazione non completata. Controlla i dati e riprova.";

    @Test
    void translatesInputValidationWithoutChangingTheRule() {
        assertThat(inputError(() -> it.uninafoodlab.application.notification.NotificationDraft.of("", "Messaggio valido")))
                .isEqualTo("L’oggetto deve contenere da 3 a 120 caratteri.");
        assertThat(inputError(() -> it.uninafoodlab.application.notification.NotificationDraft.of("Oggetto valido", "")))
                .isEqualTo("Il messaggio deve contenere da 3 a 2000 caratteri.");
        assertThat(inputError(() -> it.uninafoodlab.domain.course.SessionDetails.online(60, java.net.URI.create("http://example.test/lesson"))))
                .isEqualTo("Inserisci un link HTTPS valido per la sessione online.");
    }

    @Test
    void retainsKnownItalianGuidanceAndSessionNumber() {
        assertThat(message("La data non può precedere oggi.")).isEqualTo("La data non può precedere oggi.");
        assertThat(message("Inserisci un URL HTTPS valido per la sessione 30"))
                .isEqualTo("Inserisci un URL HTTPS valido per la sessione 30");
    }

    @Test
    void replacesUnexpectedTechnicalOrEmptyErrorsWithItalianFeedback() {
        assertThat(message("ERROR: duplicate key value violates unique constraint account_username_key"))
                .isEqualTo(FALLBACK);
        assertThat(message("Errore SQL riservato: password=secret")).isEqualTo(FALLBACK);
        assertThat(message(null)).isEqualTo(FALLBACK);
        assertThat(message(" ")).isEqualTo(FALLBACK);
        assertThat(message("Inserisci un URL HTTPS valido per la sessione 30; secret"))
                .isEqualTo(FALLBACK);
    }

    private static String message(String message) {
        return UserErrorMessages.validation(new IllegalArgumentException(message), FALLBACK);
    }

    private static String inputError(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Invalid input must be rejected before translating its message");
        } catch (IllegalArgumentException failure) {
            return UserErrorMessages.validation(failure, FALLBACK);
        }
    }

    public static void main(String[] args) {
        var test = new UserErrorMessagesTest();
        test.translatesInputValidationWithoutChangingTheRule();
        test.retainsKnownItalianGuidanceAndSessionNumber();
        test.replacesUnexpectedTechnicalOrEmptyErrorsWithItalianFeedback();
    }
}
