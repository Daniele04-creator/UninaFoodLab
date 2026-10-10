package it.uninafoodlab.application.planning;

import it.uninafoodlab.domain.catalog.MeasurementUnit;

import it.uninafoodlab.application.course.CourseAuthorizationException;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import java.time.YearMonth;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;

public final class BrowseChefPlanning {

    private static final String SESSION_ID = "sessionId";

    private final ChefPlanningRepository repository;

    public BrowseChefPlanning(ChefPlanningRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public SessionPreparation sessionPreparation(AuthenticatedUser actor, UUID sessionId) {
        requireChef(actor);
        return Objects.requireNonNull(repository.sessionPreparation(actor.sessionToken(),
                Objects.requireNonNull(sessionId, SESSION_ID)), "sessionPreparation");
    }

    public List<IngredientRequirement> requirements(
            AuthenticatedUser actor, UUID sessionId) {
        requireChef(actor);
        return List.copyOf(repository.requirements(
                actor.sessionToken(), Objects.requireNonNull(sessionId, SESSION_ID)));
    }

    public List<IngredientRequirement> requirements(
            AuthenticatedUser actor, List<UUID> sessionIds) {
        return aggregateRequirements(exportSnapshots(actor, sessionIds));
    }

    public List<SessionPlanningSnapshot> exportSnapshots(
            AuthenticatedUser actor, List<UUID> sessionIds) {
        requireChef(actor);
        var requested = List.copyOf(Objects.requireNonNull(sessionIds, "sessionIds"));
        var snapshots = List.copyOf(repository.exportSnapshots(actor.sessionToken(), requested));
        if (!snapshots.stream().map(SessionPlanningSnapshot::sessionId).toList().equals(requested)) {
            throw new IllegalStateException("Planning export snapshot does not match requested sessions");
        }
        return snapshots;
    }

    public static List<IngredientRequirement> aggregateRequirements(List<SessionPlanningSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots");
        var totals = new LinkedHashMap<RequirementKey, IngredientRequirement>();
        for (var snapshot : snapshots) {
            for (var requirement : snapshot.requirements()) {
                var key = new RequirementKey(requirement.ingredientName(), requirement.unit());
                totals.merge(key, requirement, (left, right) -> new IngredientRequirement(
                        left.ingredientName(), left.unit(),
                        left.confirmedParticipants() + right.confirmedParticipants(),
                        left.requiredQuantity().add(right.requiredQuantity())));
            }
        }
        return List.copyOf(totals.values());
    }

    public CoursePlanningExport courseExport(AuthenticatedUser actor, UUID courseId) {
        requireChef(actor);
        return Objects.requireNonNull(repository.courseExport(actor.sessionToken(),
                Objects.requireNonNull(courseId, "courseId")), "courseExport");
    }

    public List<SessionParticipant> participants(
            AuthenticatedUser actor, UUID sessionId) {
        requireChef(actor);
        return List.copyOf(repository.participants(
                actor.sessionToken(), Objects.requireNonNull(sessionId, SESSION_ID)));
    }

    public List<CourseParticipant> courseParticipants(AuthenticatedUser actor, UUID courseId) {
        requireChef(actor);
        return List.copyOf(repository.courseParticipants(
                actor.sessionToken(), Objects.requireNonNull(courseId, "courseId")));
    }

    public MonthlyReport monthlyReport(AuthenticatedUser actor, YearMonth month) {
        requireChef(actor);
        return repository.monthlyReport(
                actor.sessionToken(), Objects.requireNonNull(month, "month"));
    }

    private static void requireChef(AuthenticatedUser actor) {
        Objects.requireNonNull(actor, "actor");
        if (actor.role() != Role.CHEF) {
            throw new CourseAuthorizationException();
        }
    }

    private record RequirementKey(String ingredientName, MeasurementUnit unit) {}
}
