package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.notification.ParticipantNotification;
import it.uninafoodlab.application.course.CourseFilter;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;


final class ParticipantNotificationNavigation {
    private ParticipantNotificationNavigation() {}

    static void open(ParticipantNotification notification, boolean busy, ParticipantWorkspaceData data, CourseFilter filter,
            Loader loader, Consumer<ParticipantWorkspaceData.NotificationCourseSnapshot> success,
            Runnable unavailable) {
        if (notification == null || busy || notification.courseId().isEmpty()) return;
        var courseId = notification.courseId().orElseThrow();
        loader.load(() -> data.notificationCourse(courseId, filter), target -> target.ifPresentOrElse(success, unavailable));
    }

    @FunctionalInterface
    interface Loader {
        void load(Callable<Optional<ParticipantWorkspaceData.NotificationCourseSnapshot>> task,
                Consumer<Optional<ParticipantWorkspaceData.NotificationCourseSnapshot>> result);
    }
}
