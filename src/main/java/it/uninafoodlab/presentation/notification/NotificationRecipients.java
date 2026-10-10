package it.uninafoodlab.presentation.notification;

import it.uninafoodlab.application.notification.ManageNotifications;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.common.FxAsyncExecutor;
import it.uninafoodlab.presentation.notification.NotificationWorkspaceController.CourseTarget;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

final class NotificationRecipients {
    private final AuthenticatedUser user;
    private final ManageNotifications manageNotifications;
    private final ComboBox<CourseTarget> courseTargetField;
    private final Button recipientRetryButton;
    private final Label recipientSummaryLabel;
    private final Runnable changed;
    private Long recipientCount;
    private long recipientRequest;

    NotificationRecipients(AuthenticatedUser user,
            ManageNotifications manageNotifications,
            ComboBox<CourseTarget> courseTargetField,
            Button recipientRetryButton,
            Label recipientSummaryLabel,
            Runnable changed) {
        this.user = user;
        this.manageNotifications = manageNotifications;
        this.courseTargetField = courseTargetField;
        this.recipientRetryButton = recipientRetryButton;
        this.recipientSummaryLabel = recipientSummaryLabel;
        this.changed = changed;
    }

    boolean hasRecipients() {
        return recipientCount != null && recipientCount > 0;
    }

    CourseTarget requiredTarget() {
        var target = courseTargetField.getValue();
        if (target == null) {
            throw new IllegalArgumentException("Seleziona i destinatari della notifica.");
        }
        if (!hasRecipients()) {
            throw new IllegalArgumentException("Attendi il conteggio e scegli un corso con destinatari.");
        }
        return target;
    }

    String countLabel() {
        if (recipientCount == null) {
            return "";
        }
        return recipientCount + (recipientCount == 1 ? " destinatario" : " destinatari");
    }

    void refresh() {
        recipientRetryButton.setVisible(false);
        recipientRetryButton.setManaged(false);
        var target = courseTargetField.getValue();
        var request = ++recipientRequest;
        recipientCount = null;
        changed.run();
        if (target == null) {
            recipientSummaryLabel.setText("Seleziona esplicitamente un corso oppure tutti i tuoi corsi.");
            return;
        }
        recipientSummaryLabel.setText("Calcolo dei destinatari…");
        FxAsyncExecutor.run("notification-recipient-count",
                () -> manageNotifications.recipientCount(user, target.courseId()),
                count -> {
                    if (request == recipientRequest) {
                        recipientCount = count;
                        recipientSummaryLabel.setText(recipientSummary(target, count));
                        changed.run();
                    }
                }, failure -> {
                    if (request == recipientRequest) {
                        recipientSummaryLabel.setText("Destinatari non disponibili. La bozza è conservata.");
                        recipientRetryButton.setVisible(true);
                        recipientRetryButton.setManaged(true);
                        changed.run();
                    }
                });
    }

    static String recipientSummary(CourseTarget target, long count) {
        var people = count == 1 ? "1 partecipante" : count + " partecipanti";
        var scope = target.courseId().isEmpty()
                ? " con iscrizione attiva in tutti i tuoi corsi, ciascuno una sola volta."
                : " con iscrizione attiva a «" + target.label() + "».";
        return people + scope + (count == 0 ? " Nessun destinatario disponibile." : " Il numero viene aggiornato all’invio.");
    }

}
