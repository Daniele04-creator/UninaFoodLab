package it.uninafoodlab.presentation.common;

import java.util.Map;
import java.util.Set;

public final class UserErrorMessages {
    private static final Map<String, String> TRANSLATIONS = Map.ofEntries(
            Map.entry("Notification title must contain 3 to 120 characters", "L’oggetto deve contenere da 3 a 120 caratteri."),
            Map.entry("Notification message must contain 3 to 2000 characters", "Il messaggio deve contenere da 3 a 2000 caratteri."),
            Map.entry("course title must contain 3 to 120 characters", "Il titolo del corso deve contenere da 3 a 120 caratteri."),
            Map.entry("course title must contain 3 to 30 characters", "Il titolo del corso deve contenere da 3 a 30 caratteri."),
            Map.entry("course title must be on a single line", "Il titolo del corso deve essere su una sola riga."),
            Map.entry("course description must not be blank", "Inserisci la descrizione del corso."),
            Map.entry("frequency must contain 1 to 31 days", "La frequenza deve essere compresa tra 1 e 31 giorni."),
            Map.entry("session duration must contain 1 to 720 minutes", "La durata deve essere compresa tra 1 e 720 minuti."),
            Map.entry("online session requires an HTTPS URL", "Inserisci un link HTTPS valido per la sessione online."),
            Map.entry("practical session venue must not be blank", "Inserisci la sede o il link alla posizione della sessione."),
            Map.entry("practical session capacity must be positive", "La capienza deve essere maggiore di zero."),
            Map.entry("practical attendance cutoff must precede the session", "La chiusura adesioni deve precedere la sessione."),
            Map.entry("online sessions must not have an attendance cutoff", "Le sessioni online non prevedono una chiusura adesioni."),
            Map.entry("Username must contain 3 to 50 lowercase letters, numbers, dots, underscores or hyphens",
                    "Scegli uno username di 3–50 caratteri: lettere minuscole, numeri, . _ -"));

    private static final Set<String> ITALIAN_VALIDATION = Set.of(
            "La data non può precedere oggi.",
            "Il numero di sessioni deve essere compreso tra 2 e 30",
            "Seleziona una categoria", "Inserisci il titolo del corso", "Inserisci la descrizione del corso",
            "Il titolo del corso deve contenere da 3 a 30 caratteri.",
            "Il titolo del corso deve essere su una sola riga.",
            "Seleziona la data di inizio", "La durata deve essere compresa tra 1 e 720 minuti",
            "Inserisci un orario nel formato HH:mm", "La frequenza deve essere compresa tra 1 e 31 giorni",
            "La chiusura adesioni deve precedere la sessione.", "La sede può contenere al massimo 180 caratteri.",
            "Seleziona la data della sessione.", "Seleziona la data di chiusura delle adesioni.",
            "Indica il link HTTPS o la sede della sessione.",
            "Il titolo della comunicazione deve contenere da 3 a 120 caratteri.",
            "Il messaggio deve contenere da 3 a 2000 caratteri.",
            "Seleziona i destinatari della notifica.",
            "Attendi il conteggio e scegli un corso con destinatari.",
            "Lo Chef ha già una sessione programmata in questo intervallo.",
            "Inserisci una quantità positiva con al massimo 3 decimali per una persona.",
            "La quantità deve essere maggiore di zero.",
            "La quantità per una persona deve essere positiva, con al massimo 3 decimali, e inferiore a 1000000000000.",
            "Il nome della ricetta deve contenere da 1 a 120 caratteri.",
            "Inserisci una descrizione fino a 4000 caratteri.",
            "La ricetta deve indicare le dosi per una persona.",
            "Inserisci almeno un ingrediente, senza duplicati.",
            "Il nome dell'ingrediente deve contenere da 1 a 100 caratteri.",
            "Usa grammi o millilitri per indicare dosi per una persona.",
            "Il nome della categoria deve contenere da 3 a 80 caratteri.",
            "La descrizione della categoria non può superare 500 caratteri.",
            "Scegli il tuo ruolo.", "Scegli uno username di 3–50 caratteri: lettere, numeri, . _ -",
            "Inserisci un nome da mostrare valido (massimo 100 caratteri).",
            "La password deve contenere almeno 12 caratteri.", "Le password non coincidono.",
            "Inserisci la password attuale.", "La password attuale deve contenere almeno 12 caratteri.",
            "Inserisci la nuova password.", "La nuova password deve contenere almeno 12 caratteri.",
            "Ripeti la nuova password.", "Scegli uno username valido.",
            "Inserisci il nome da mostrare.", "Inserisci un nome da mostrare valido.",
            "La password attuale non è corretta o la sessione è scaduta.",
            "Impossibile completare la registrazione.");

    private UserErrorMessages() {}

    public static String validation(Throwable failure, String fallback) {
        var message = failure.getMessage();
        if (message == null) return fallback;
        var translated = TRANSLATIONS.get(message);
        if (translated != null) return translated;
        if (ITALIAN_VALIDATION.contains(message) || message.matches(
                "Inserisci (un URL HTTPS valido per la|la sede della|una capienza valida per la) sessione ([1-9]|[12][0-9]|30)")) {
            return message;
        }
        return fallback;
    }
}
