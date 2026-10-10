package it.uninafoodlab.infrastructure.course;

import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.application.course.CourseFilter;
import it.uninafoodlab.application.course.CoursePeriod;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.application.course.PublishedCourseSummary;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

final class CoursePageQueries {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private final DataSource dataSource;

    CoursePageQueries(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    Page<CourseSummary> byChef(UUID sessionToken, CourseFilter filter, PageRequest page, List<UUID> courseIds) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT * FROM app.page_chef_courses(?, ?, ?, ?, ?, ?, ?, ?)")) {
            var ids = bind(statement, sessionToken, filter, page, courseIds);
            try {
                try (var result = statement.executeQuery()) {
                    var courses = new ArrayList<CourseSummary>();
                    long total = 0;
                    while (result.next()) {
                        total = result.getLong("total_count");
                        if (result.getObject("course_id", UUID.class) != null) courses.add(ChefCourseQueries.mapCourse(result));
                    }
                    return new Page<>(courses, total);
                }
            } finally {
                if (ids != null) ids.free();
            }
        } catch (SQLException exception) {
            throw new CourseRepositoryException("Unable to load course page", exception);
        }
    }

    Page<PublishedCourseSummary> published(UUID sessionToken, CourseFilter filter, PageRequest page, List<UUID> courseIds) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT * FROM app.page_published_courses(?, ?, ?, ?, ?, ?, ?, ?)")) {
            var ids = bind(statement, sessionToken, filter, page, courseIds);
            try {
                try (var result = statement.executeQuery()) {
                    var courses = new ArrayList<PublishedCourseSummary>();
                    long total = 0;
                    while (result.next()) {
                        total = result.getLong("total_count");
                        if (result.getObject("course_id", UUID.class) != null) courses.add(PublishedCourseQueries.mapPublishedCourse(result));
                    }
                    return new Page<>(courses, total);
                }
            } finally {
                if (ids != null) ids.free();
            }
        } catch (SQLException exception) {
            throw new CourseRepositoryException("Unable to load published course page", exception);
        }
    }

    Optional<CourseSummary> byChef(UUID sessionToken, UUID courseId) {
        Objects.requireNonNull(courseId, "courseId");
        return byChef(sessionToken, allCourses(), new PageRequest(0, 1), List.of(courseId)).items().stream().findFirst();
    }

    List<PublishedCourseSummary> published(UUID sessionToken, List<UUID> courseIds) {
        var ids = List.copyOf(courseIds).stream().distinct().toList();
        if (ids.size() > 100) throw new IllegalArgumentException("At most one hundred course IDs required");
        return published(sessionToken, allCourses(), new PageRequest(0, 100), ids).items();
    }

    private static CourseFilter allCourses() {
        return new CourseFilter(Optional.empty(), "", CoursePeriod.ALL, LocalDateTime.now(ROME));
    }

    private static java.sql.Array bind(PreparedStatement statement, UUID sessionToken,
            CourseFilter filter, PageRequest page, List<UUID> courseIds) throws SQLException {
        Objects.requireNonNull(sessionToken, "sessionToken");
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(page, "page");
        statement.setObject(1, sessionToken);
        statement.setObject(2, filter.categoryId().orElse(null), Types.OTHER);
        statement.setString(3, filter.search());
        statement.setString(4, filter.period().name());
        statement.setObject(5, filter.now());
        statement.setInt(6, page.size());
        statement.setInt(7, page.offset());
        if (courseIds == null) {
            statement.setNull(8, Types.ARRAY);
            return null;
        }
        var ids = statement.getConnection().createArrayOf("uuid", courseIds.toArray(UUID[]::new));
        statement.setArray(8, ids);
        return ids;
    }
}
