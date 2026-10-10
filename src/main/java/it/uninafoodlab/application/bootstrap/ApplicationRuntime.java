package it.uninafoodlab.application.bootstrap;

import com.zaxxer.hikari.HikariDataSource;
import it.uninafoodlab.application.auth.AuthenticateUser;
import it.uninafoodlab.application.catalog.BrowseCatalog;
import it.uninafoodlab.application.catalog.CreateCategory;
import it.uninafoodlab.application.catalog.ManageRecipeCatalog;
import it.uninafoodlab.infrastructure.catalog.JdbcRecipeCatalogManagementRepository;
import it.uninafoodlab.application.course.AssignRecipeToSession;
import it.uninafoodlab.application.course.BrowsePublishedCourses;
import it.uninafoodlab.application.course.BrowseCourses;
import it.uninafoodlab.application.course.CreateCourse;
import it.uninafoodlab.application.course.PublishCourse;
import it.uninafoodlab.application.enrollment.BrowseEnrollments;
import it.uninafoodlab.application.enrollment.ManageEnrollment;
import it.uninafoodlab.application.enrollment.ManagePracticalAttendance;
import it.uninafoodlab.application.notification.BrowseNotifications;
import it.uninafoodlab.application.notification.ManageNotifications;
import it.uninafoodlab.application.planning.BrowseChefPlanning;
import it.uninafoodlab.application.schedule.ManageCourseSchedule;
import it.uninafoodlab.infrastructure.auth.JdbcAccountRepository;
import it.uninafoodlab.infrastructure.catalog.JdbcCatalogRepository;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import it.uninafoodlab.infrastructure.course.JdbcCourseRepository;
import it.uninafoodlab.infrastructure.database.DataSourceFactory;
import it.uninafoodlab.infrastructure.database.DatabaseMigrator;
import it.uninafoodlab.infrastructure.enrollment.JdbcEnrollmentRepository;
import it.uninafoodlab.infrastructure.notification.JdbcNotificationRepository;
import it.uninafoodlab.infrastructure.planning.JdbcChefPlanningRepository;
import it.uninafoodlab.infrastructure.schedule.JdbcScheduleRepository;
import it.uninafoodlab.infrastructure.security.Pbkdf2PasswordHasher;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;

public final class ApplicationRuntime implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final AuthenticateUser authenticateUser;
    private final BrowseCatalog browseCatalog;
    private final CreateCategory createCategory;
    private final ManageRecipeCatalog manageRecipeCatalog;
    private final CreateCourse createCourse;
    private final BrowseCourses browseCourses;
    private final AssignRecipeToSession assignRecipeToSession;
    private final PublishCourse publishCourse;
    private final BrowsePublishedCourses browsePublishedCourses;
    private final ManageEnrollment manageEnrollment;
    private final ManagePracticalAttendance managePracticalAttendance;
    private final BrowseEnrollments browseEnrollments;
    private final BrowseNotifications browseNotifications;
    private final ManageNotifications manageNotifications;
    private final ManageCourseSchedule manageCourseSchedule;
    private final BrowseChefPlanning browseChefPlanning;
    private final it.uninafoodlab.application.planning.ManageSessionLifecycle sessionLifecycle;
    private final it.uninafoodlab.application.account.AccountLifecycle accountLifecycle;

    private ApplicationRuntime(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        authenticateUser = new AuthenticateUser(
                new JdbcAccountRepository(dataSource, new Pbkdf2PasswordHasher()));
        var catalogRepository = new JdbcCatalogRepository(dataSource);
        browseCatalog = new BrowseCatalog(catalogRepository);
        createCategory = new CreateCategory(catalogRepository);
        manageRecipeCatalog = new ManageRecipeCatalog(new JdbcRecipeCatalogManagementRepository(dataSource));
        var courseRepository = new JdbcCourseRepository(dataSource);
        createCourse = new CreateCourse(courseRepository);
        browseCourses = new BrowseCourses(courseRepository);
        assignRecipeToSession = new AssignRecipeToSession(courseRepository);
        publishCourse = new PublishCourse(courseRepository);
        browsePublishedCourses = new BrowsePublishedCourses(courseRepository);
        var enrollmentRepository = new JdbcEnrollmentRepository(dataSource);
        manageEnrollment = new ManageEnrollment(enrollmentRepository);
        managePracticalAttendance = new ManagePracticalAttendance(enrollmentRepository);
        browseEnrollments = new BrowseEnrollments(enrollmentRepository);
        var notificationRepository = new JdbcNotificationRepository(dataSource);
        browseNotifications = new BrowseNotifications(notificationRepository);
        manageNotifications = new ManageNotifications(notificationRepository);
        manageCourseSchedule = new ManageCourseSchedule(new JdbcScheduleRepository(dataSource));
        browseChefPlanning = new BrowseChefPlanning(new JdbcChefPlanningRepository(dataSource));
        sessionLifecycle = new it.uninafoodlab.application.planning.ManageSessionLifecycle(
                new it.uninafoodlab.infrastructure.planning.JdbcSessionLifecycleRepository(dataSource));
        accountLifecycle = new it.uninafoodlab.application.account.AccountLifecycle(
                new it.uninafoodlab.infrastructure.account.JdbcAccountLifecycleRepository(dataSource, new Pbkdf2PasswordHasher()),
                new Pbkdf2PasswordHasher());
    }

    public static ApplicationRuntime start(ApplicationConfig applicationConfig) {
        return start(
                applicationConfig,
                () -> DatabaseMigrator.migrate(applicationConfig),
                () -> DataSourceFactory.create(applicationConfig));
    }

    static ApplicationRuntime start(
            ApplicationConfig applicationConfig,
            Runnable migrateDatabase,
            Supplier<HikariDataSource> dataSourceSupplier) {
        Objects.requireNonNull(applicationConfig, "applicationConfig");
        Objects.requireNonNull(migrateDatabase, "migrateDatabase");
        Objects.requireNonNull(dataSourceSupplier, "dataSourceSupplier");
        migrateDatabase.run();

        var dataSource = Objects.requireNonNull(dataSourceSupplier.get(), "dataSource");
        var started = false;
        try {
            try (var connection = dataSource.getConnection()) {
                if (!connection.isValid(5)) {
                    throw new ApplicationStartupException("Database connection validation failed");
                }
            }
            started = true;
            return new ApplicationRuntime(dataSource);
        } catch (SQLException exception) {
            throw new ApplicationStartupException("Unable to initialize the application database", exception);
        } finally {
            if (!started) {
                dataSource.close();
            }
        }
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public AuthenticateUser authenticateUser() {
        return authenticateUser;
    }

    public BrowseCatalog browseCatalog() {
        return browseCatalog;
    }

    public CreateCategory createCategory() {
        return createCategory;
    }

    public ManageRecipeCatalog manageRecipeCatalog() {
        return manageRecipeCatalog;
    }

    public CreateCourse createCourse() {
        return createCourse;
    }

    public BrowseCourses browseCourses() {
        return browseCourses;
    }

    public AssignRecipeToSession assignRecipeToSession() {
        return assignRecipeToSession;
    }

    public PublishCourse publishCourse() {
        return publishCourse;
    }

    public BrowsePublishedCourses browsePublishedCourses() {
        return browsePublishedCourses;
    }

    public ManageEnrollment manageEnrollment() {
        return manageEnrollment;
    }

    public ManagePracticalAttendance managePracticalAttendance() {
        return managePracticalAttendance;
    }

    public BrowseEnrollments browseEnrollments() {
        return browseEnrollments;
    }

    public BrowseNotifications browseNotifications() {
        return browseNotifications;
    }

    public ManageNotifications manageNotifications() {
        return manageNotifications;
    }

    public ManageCourseSchedule manageCourseSchedule() {
        return manageCourseSchedule;
    }

    public BrowseChefPlanning browseChefPlanning() {
        return browseChefPlanning;
    }

    public it.uninafoodlab.application.planning.ManageSessionLifecycle sessionLifecycle() { return sessionLifecycle; }

    public it.uninafoodlab.application.account.AccountLifecycle accountLifecycle() { return accountLifecycle; }

    @Override
    public void close() {
        dataSource.close();
    }
}
