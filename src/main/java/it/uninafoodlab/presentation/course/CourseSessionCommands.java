package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.planning.ManageSessionLifecycle;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import java.time.Clock;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;

final class CourseSessionCommands {
    record Fields(Button refreshButton, ComboBox<CourseSessionSummary> recipeSession) {}

    private final Fields fields;
    private final Clock clock;
    private final AuthenticatedUser user;
    private final CourseWorkspaceActions actions;
    private final BooleanSupplier archive;
    private final Supplier<ManageSessionLifecycle> lifecycle;
    private final Runnable reload;

    CourseSessionCommands(Fields fields, Clock clock, AuthenticatedUser user,
            CourseWorkspaceActions actions, BooleanSupplier archive,
            Supplier<ManageSessionLifecycle> lifecycle, Runnable reload) {
        this.fields = fields;
        this.clock = clock;
        this.user = user;
        this.actions = actions;
        this.archive = archive;
        this.lifecycle = lifecycle;
        this.reload = reload;
    }

    void update() {
        CoursePresentation.setEditorVisible(fields.refreshButton(), canRefresh(fields.recipeSession().getValue()));
        fields.refreshButton().setDisable(actions.isBusy());
    }

    void refreshRecipes() {
        var session = fields.recipeSession().getValue();
        if (!canRefresh(session)) return;
        actions.runAsync("refresh-session-recipes", () -> {
            lifecycle.get().refresh(user, session.id());
            return true;
        }, ignored -> reload.run(), "Impossibile aggiornare le ricette della sessione.");
    }

    private boolean canRefresh(CourseSessionSummary session) {
        return lifecycle.get() != null && !archive.getAsBoolean()
                && CourseSessionPolicy.isRecipeMutable(session, clock) && !session.recipeIds().isEmpty();
    }
}
