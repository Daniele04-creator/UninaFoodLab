package it.uninafoodlab.application.planning;

import it.uninafoodlab.application.course.CourseAuthorizationException;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ManageSessionLifecycle {
    private final SessionLifecycleRepository repository;

    public ManageSessionLifecycle(SessionLifecycleRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public SessionLifecycleRepository.State state(AuthenticatedUser user, UUID sessionId) {
        return repository.state(chefSessionToken(user), Objects.requireNonNull(sessionId));
    }

    public List<SessionLifecycleRepository.SavedRecipe> menu(AuthenticatedUser user, UUID sessionId) {
        return List.copyOf(repository.menu(chefSessionToken(user), Objects.requireNonNull(sessionId)));
    }

    public List<SessionLifecycleRepository.ReportRow> report(AuthenticatedUser user, YearMonth month) {
        return List.copyOf(repository.report(chefSessionToken(user), Objects.requireNonNull(month)));
    }

    public void refresh(AuthenticatedUser user, UUID sessionId) {
        repository.refresh(chefSessionToken(user), Objects.requireNonNull(sessionId));
    }

    private static UUID chefSessionToken(AuthenticatedUser user) {
        if (Objects.requireNonNull(user).role() != Role.CHEF) throw new CourseAuthorizationException();
        return user.sessionToken();
    }
}
