package it.uninafoodlab.presentation.notification;

import it.uninafoodlab.presentation.notification.NotificationWorkspaceController.CourseTarget;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class NotificationDrafts {
    private final Map<Destination, Draft> drafts = new HashMap<>();
    private CourseTarget currentTarget;

    CourseTarget currentTarget() {
        return currentTarget;
    }

    boolean hasUnsavedChanges(String title, String message) {
        if (!new Draft(title, message).empty()) return true;
        var destination = Destination.of(currentTarget);
        return drafts.entrySet().stream().anyMatch(entry ->
                !entry.getKey().equals(destination) && !entry.getValue().empty());
    }

    Draft changeTarget(CourseTarget target, String title, String message) {
        var previous = Destination.of(currentTarget);
        var destination = Destination.of(target);
        var current = new Draft(title, message);
        if (current.empty()) {
            drafts.remove(previous);
        } else {
            drafts.put(previous, current);
        }
        if (currentTarget == null && target != null && !current.empty()
                && drafts.putIfAbsent(destination, current) == null) {
            drafts.remove(previous);
        }
        currentTarget = target;
        return drafts.getOrDefault(destination, new Draft("", ""));
    }

    private record Destination(boolean selected, Optional<UUID> courseId) {
        static Destination of(CourseTarget target) {
            return new Destination(target != null, target == null ? Optional.empty() : target.courseId());
        }
    }

    record Draft(String title, String message) {
        boolean empty() {
            return title.isBlank() && message.isBlank();
        }
    }
}
