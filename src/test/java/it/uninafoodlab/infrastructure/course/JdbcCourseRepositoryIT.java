package it.uninafoodlab.infrastructure.course;

import it.uninafoodlab.domain.catalog.MeasurementUnit;

import it.uninafoodlab.infrastructure.support.DatabaseActors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.application.course.CourseCreation;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.application.course.CourseFilter;
import it.uninafoodlab.application.course.CoursePeriod;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.domain.course.CourseSchedule;
import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.IngredientMargin;
import it.uninafoodlab.domain.course.SessionDetails;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import java.net.URI;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class JdbcCourseRepositoryIT {

    private static final String MIGRATOR_PASSWORD = "course-repository-migrator-only";
    private static final String APP_PASSWORD = "course-repository-app-only";
    private static UUID chefSessionToken;
    private static UUID participantSessionToken;
    private static final UUID ITALIAN_CATEGORY_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PASTRY_CATEGORY_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID MARGHERITA_RECIPE_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID GNOCCHI_RECIPE_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000002");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");

    private static ApplicationRuntime runtime;
    private static JdbcCourseRepository repository;

    @BeforeAll
    static void startDatabase() throws Exception {
        POSTGRES.copyFileToContainer(
                MountableFile.forHostPath(
                        Path.of("database", "bootstrap", "001__create_roles.sql")),
                "/tmp/001__create_roles.sql");
        var result = POSTGRES.execInContainer(
                "psql",
                "--username=" + POSTGRES.getUsername(),
                "--dbname=" + POSTGRES.getDatabaseName(),
                "--set=ON_ERROR_STOP=1",
                "--set=migrator_password=" + MIGRATOR_PASSWORD,
                "--set=app_password=" + APP_PASSWORD,
                "--file=/tmp/001__create_roles.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();

        runtime = it.uninafoodlab.infrastructure.support.DemoDatabaseFixture.start(new ApplicationConfig(
                POSTGRES.getJdbcUrl(),
                "uninafoodlab_app",
                APP_PASSWORD,
                "uninafoodlab_migrator",
                MIGRATOR_PASSWORD,
                "test"));
        repository = new JdbcCourseRepository(runtime.dataSource());
        chefSessionToken = runtime.authenticateUser()
                .execute("marta.rossi", "MartaRossi!2026".toCharArray())
                .sessionToken();
        participantSessionToken = DatabaseActors.authenticate(runtime, "luca.bianchi", "LucaBianchi!2026".toCharArray())
                .sessionToken();
    }

    @AfterAll
    static void stopDatabase() {
        runtime.close();
    }

    @Test
    void databaseEnforcesShortSingleLineCourseTitles() throws SQLException {
        try (var admin = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            admin.setAutoCommit(false);
            try (var insert = admin.prepareStatement("""
                    INSERT INTO app.course(id,chef_profile_id,category_id,title,description,
                        start_date,start_time,recurrence_days,session_count)
                    VALUES (?,?,?,?,'Title limit test',DATE '2049-06-02',TIME '18:00',7,1)
                    """)) {
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, runtime.authenticateUser()
                        .execute("marta.rossi", "MartaRossi!2026".toCharArray()).profileId());
                insert.setObject(3, ITALIAN_CATEGORY_ID);
                for (String invalid : List.of("W".repeat(31), "Pane\nrustico", "Pane\rrustico",
                        "Pane\u000Brustico", "Pane\frustico", "Pane\u0085rustico",
                        "Pane\u2028rustico", "Pane\u2029rustico")) {
                    var savepoint = admin.setSavepoint();
                    insert.setString(4, invalid);
                    assertThatThrownBy(insert::executeUpdate).isInstanceOf(SQLException.class)
                            .extracting(failure -> ((SQLException) failure).getSQLState()).isEqualTo("23514");
                    admin.rollback(savepoint);
                }
                insert.setString(4, "W".repeat(30));
                assertThat(insert.executeUpdate()).isEqualTo(1);
            } finally {
                admin.rollback();
            }
        }
    }

    @Test
    void pagesAuthorizedCoursesWithCountsAndHistoricalFilters() {
        var now = LocalDateTime.now(ZoneId.of("Europe/Rome"));
        var all = new CourseFilter(Optional.empty(), "", CoursePeriod.ALL, now);
        var first = repository.findByChef(chefSessionToken, all, new PageRequest(0, 1));
        var second = repository.findByChef(chefSessionToken, all, new PageRequest(1, 1));
        assertThat(first.items()).hasSize(1);
        assertThat(second.items()).hasSize(1).doesNotContainAnyElementsOf(first.items());
        assertThat(first.totalCount()).isEqualTo(repository.findByChef(chefSessionToken, Optional.empty()).size());
        assertThat(repository.findByChef(chefSessionToken, all, new PageRequest(1000, 1)).items()).isEmpty();
        var course = first.items().getFirst();
        var search = new CourseFilter(Optional.of(course.categoryId()),
                course.title().value().toUpperCase(java.util.Locale.ROOT) + " " + course.categoryName(),
                CoursePeriod.ALL, now);
        assertThat(repository.findByChef(chefSessionToken, search, new PageRequest(0, 1)).items()).containsExactly(course);
        var past = repository.findByChef(chefSessionToken,
                new CourseFilter(Optional.empty(), "", CoursePeriod.PAST, now), new PageRequest(0, 100));
        assertThat(past.items()).allSatisfy(item -> assertThat(item.status() == CourseStatus.COMPLETED
                || item.status() == CourseStatus.CANCELLED
                || item.status() != CourseStatus.DRAFT && item.lastSessionEndsAt().map(end -> !end.isAfter(now)).orElse(true))
                .isTrue());
        var published = repository.findPublished(participantSessionToken, all, new PageRequest(0, 1));
        assertThat(published.totalCount()).isEqualTo(publishedCourses().size());
        assertThatThrownBy(() -> repository.findByChef(participantSessionToken, all, new PageRequest(0, 1)))
                .isInstanceOf(CourseRepositoryException.class);
        assertThatThrownBy(() -> repository.findPublished(chefSessionToken, all, new PageRequest(0, 1)))
                .isInstanceOf(CourseRepositoryException.class);
    }

    @Test
    void readsRequestedCourseMetadataWithoutLoadingTheWholeCatalog() {
        var course = repository.findByChef(chefSessionToken, Optional.empty()).getFirst();
        assertThat(repository.findByChef(chefSessionToken, course.id())).contains(course);
        assertThat(repository.findByChef(chefSessionToken, UUID.randomUUID())).isEmpty();
        var published = publishedCourses().getFirst();
        assertThat(repository.findPublishedByIds(participantSessionToken, List.of(published.id())))
                .containsExactly(published);
        var sessions = repository.publishedSessions(participantSessionToken, List.of(published.id()));
        assertThat(sessions).containsKey(published.id());
        assertThat(sessions.get(published.id())).containsExactlyElementsOf(
                repository.publishedSessions(participantSessionToken, published.id()));
    }

    @Test
    void batchesChefSessionsAndPreservesEmptyCourseResults() {
        var courses = repository.findByChef(chefSessionToken, Optional.empty());
        var first = courses.getFirst().id();
        var second = courses.get(1).id();
        var unknown = UUID.randomUUID();
        var sessions = repository.sessions(chefSessionToken, List.of(first, first, second, unknown));
        assertThat(sessions).containsOnlyKeys(first, second, unknown);
        assertThat(sessions.get(first)).containsExactlyElementsOf(repository.sessions(chefSessionToken, first));
        assertThat(sessions.get(second)).containsExactlyElementsOf(repository.sessions(chefSessionToken, second));
        assertThat(sessions.get(unknown)).isEmpty();
        assertThatThrownBy(() -> repository.sessions(participantSessionToken, List.of(first)))
                .isInstanceOf(CourseRepositoryException.class);
        assertThatThrownBy(() -> repository.sessions(participantSessionToken, List.<UUID>of()))
                .isInstanceOf(CourseRepositoryException.class);
        assertThatThrownBy(() -> repository.publishedSessions(chefSessionToken, List.<UUID>of()))
                .isInstanceOf(CourseRepositoryException.class);
    }

    @Test
    void creationRollsBackTheCourseAndSessionsWhenPublicationFails() throws SQLException {
        var actor = runtime.authenticateUser()
                .execute("marta.rossi", "MartaRossi!2026".toCharArray());
        try (var admin = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = admin.createStatement()) {
            statement.execute("""
                    CREATE FUNCTION app.reject_test_publication() RETURNS trigger
                    LANGUAGE plpgsql AS $$ BEGIN
                        IF NEW.title = 'Atomic publication regression' AND NEW.status = 'PUBLISHED' THEN
                            RAISE EXCEPTION 'Simulated publication failure';
                        END IF;
                        RETURN NEW;
                    END $$;
                    CREATE TRIGGER reject_test_publication BEFORE UPDATE ON app.course
                    FOR EACH ROW EXECUTE FUNCTION app.reject_test_publication();
                    """);
            try {
                var createCourse = runtime.createCourse();
                var input = new it.uninafoodlab.application.course.NewCourseInput(
                        ITALIAN_CATEGORY_ID, "Atomic publication regression", "Test atomic creation",
                        LocalDate.of(2047, 6, 2), LocalTime.of(18, 30), 7,
                        java.math.BigDecimal.ZERO,
                        List.of(details(SessionMode.ONLINE), details(SessionMode.ONLINE)));
                assertThatThrownBy(() -> createCourse.execute(actor, input))
                        .isInstanceOf(CourseRepositoryException.class);
                try (var result = statement.executeQuery("""
                        SELECT count(*) FROM app.course WHERE title = 'Atomic publication regression'
                        """)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getLong(1)).as("failed creation must leave no draft course").isZero();
                }
            } finally {
                statement.execute("DROP TRIGGER reject_test_publication ON app.course");
                statement.execute("DROP FUNCTION app.reject_test_publication()");
            }
        }
    }

    @Test
    void creationPublishesTheCourseWithAllItsSessions() {
        var actor = runtime.authenticateUser()
                .execute("marta.rossi", "MartaRossi!2026".toCharArray());
        var id = runtime.createCourse().execute(actor,
                new it.uninafoodlab.application.course.NewCourseInput(
                        ITALIAN_CATEGORY_ID, "Atomic publication success", "Test atomic creation",
                        LocalDate.of(2047, 7, 2), LocalTime.of(18, 30), 7,
                        java.math.BigDecimal.ZERO,
                        List.of(details(SessionMode.ONLINE), details(SessionMode.PRACTICAL))));
        assertThat(repository.findByChef(actor.sessionToken(), Optional.empty()))
                .filteredOn(course -> course.id().equals(id))
                .singleElement().satisfies(course -> assertThat(course.status()).isEqualTo(CourseStatus.PUBLISHED));
        assertThat(repository.sessions(actor.sessionToken(), id)).hasSize(2);
    }

    @Test
    void publishedIngredientDosesUseSessionSnapshotAndKeepMenuAuthorization() throws SQLException {
        var course = repository.create(creation(ITALIAN_CATEGORY_ID, "Dosi pubblicate della sessione",
                LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(160),
                List.of(SessionMode.PRACTICAL, SessionMode.PRACTICAL)));
        var session = repository.sessions(chefSessionToken, course).getFirst().id();
        repository.assignRecipe(chefSessionToken, session, MARGHERITA_RECIPE_ID);
        assertThat(repository.publishedMenu(participantSessionToken, course)).isEmpty();
        repository.publish(chefSessionToken, course);
        var menu = repository.publishedMenu(participantSessionToken, course);
        assertThat(menu).hasSize(1);
        assertThat(menu.getFirst().ingredients()).isNotEmpty().allSatisfy(dose -> {
            assertThat(dose.name()).isNotBlank();
            assertThat(dose.quantity()).isPositive();
            assertThat(dose.unit()).isNotNull();
        });
        try (var admin = POSTGRES.createConnection("")) {
            admin.setAutoCommit(false);
            try (var update = admin.prepareStatement(
                    "update app.recipe_ingredient set quantity=quantity+1 where recipe_id=?")) {
                update.setObject(1, MARGHERITA_RECIPE_ID);
                update.executeUpdate();
                try (var read = admin.prepareStatement(
                        "select ingredient_name, quantity, unit from app.list_published_session_menu_doses(?,?)")) {
                    read.setObject(1, participantSessionToken);
                    read.setObject(2, course);
                    try (var rows = read.executeQuery()) {
                        var actual = new java.util.ArrayList<it.uninafoodlab.application.course.PublishedSessionRecipeSummary.IngredientDose>();
                        while (rows.next()) {
                            actual.add(new it.uninafoodlab.application.course.PublishedSessionRecipeSummary.IngredientDose(
                                    rows.getString(1), rows.getBigDecimal(2),
                                    it.uninafoodlab.domain.catalog.MeasurementUnit.valueOf(rows.getString(3))));
                        }
                        assertThat(actual).containsExactlyElementsOf(menu.getFirst().ingredients());
                    }
                }
            } finally {
                admin.rollback();
            }
        }
        assertThatThrownBy(() -> repository.publishedMenu(chefSessionToken, course))
                .isInstanceOf(CourseRepositoryException.class);
        assertThatThrownBy(() -> repository.publishedMenu(UUID.randomUUID(), course))
                .isInstanceOf(CourseRepositoryException.class);
    }

    @Test
    void publishedMenuShowsRecipeDetailsWithoutDisclosingDraftMenusOrRequiringEnrollment() throws SQLException {
        var course = repository.create(creation(ITALIAN_CATEGORY_ID, "Menu consultabile",
                LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(60),
                List.of(SessionMode.PRACTICAL, SessionMode.PRACTICAL)));
        var session = repository.sessions(chefSessionToken, course).getFirst().id();
        repository.assignRecipe(chefSessionToken, session, MARGHERITA_RECIPE_ID);
        try (var connection = runtime.dataSource().getConnection(); var statement = connection.prepareStatement(
                "select * from app.list_published_session_menu(?,?)")) {
            statement.setObject(1, participantSessionToken);
            statement.setObject(2, course);
            try (var result = statement.executeQuery()) { assertThat(result.next()).isFalse(); }
            connection.commit();
            repository.publish(chefSessionToken, course);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject("session_id", UUID.class)).isEqualTo(session);
                assertThat(result.getObject("recipe_id", UUID.class)).isEqualTo(MARGHERITA_RECIPE_ID);
                assertThat(result.getString("name")).isNotBlank();
                assertThat(result.getString("description")).isNotBlank();
                assertThat(result.getInt("base_servings")).isPositive();
                assertThat(result.getBigDecimal("portions_per_participant")).isEqualByComparingTo("1");
                assertThat(result.next()).isFalse();
            }
            connection.commit();
            statement.setObject(1, chefSessionToken);
            assertThatThrownBy(statement::executeQuery).isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("42501"));
        }
    }

    @Test
    void catalogPreservesOwnPastCourseAndUsesLastSessionEndInsteadOfStartDate() throws SQLException {
        var course = repository.create(creation(ITALIAN_CATEGORY_ID, "Storico personale",
                LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(70),
                List.of(SessionMode.ONLINE, SessionMode.ONLINE)));
        repository.publish(chefSessionToken, course);
        var unrelated = repository.create(creation(ITALIAN_CATEGORY_ID, "Storico non personale",
                LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(71),
                List.of(SessionMode.ONLINE, SessionMode.ONLINE)));
        repository.publish(chefSessionToken, unrelated);
        try (var connection = runtime.dataSource().getConnection(); var enroll = connection.prepareStatement(
                "select app.enroll_in_course(?,?)")) {
            enroll.setObject(1, participantSessionToken);
            enroll.setObject(2, course);
            enroll.execute();
            connection.commit();
        }
        var pastEnd = LocalDateTime.of(2020, 1, 10, 11, 30);
        try (var connection = POSTGRES.createConnection(""); var statement = connection.prepareStatement(
                "update app.course_session set scheduled_at=? + ((sequence_number - 1) * interval '2 hours'), "
                        + "duration_minutes=90 where course_id=?")) {
            statement.setObject(1, pastEnd.minusMinutes(210));
            statement.setObject(2, course);
            statement.executeUpdate();
            statement.setObject(1, pastEnd.plusHours(3));
            statement.setObject(2, unrelated);
            statement.executeUpdate();
        }
        try (var connection = runtime.dataSource().getConnection(); var statement = connection.prepareStatement(
                "select last_session_ends_at from app.list_published_courses(?,?) where course_id=?")) {
            statement.setObject(1, participantSessionToken);
            statement.setObject(2, ITALIAN_CATEGORY_ID);
            statement.setObject(3, course);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1, LocalDateTime.class)).isEqualTo(pastEnd);
            }
        }
        assertThat(publishedCourses())
                .extracting(value -> value.id()).contains(course).doesNotContain(unrelated);
        try (var connection = POSTGRES.createConnection(""); var statement = connection.prepareStatement(
                "update app.course set status='COMPLETED' where id in (?,?)")) {
            statement.setObject(1, course);
            statement.setObject(2, unrelated);
            statement.executeUpdate();
        }
        assertThat(publishedCourses())
                .extracting(value -> value.id()).contains(course).doesNotContain(unrelated);
        assertThat(repository.publishedSessions(participantSessionToken, course)).hasSize(2);
        assertThat(repository.publishedSessions(participantSessionToken, unrelated)).isEmpty();
    }

    @Test
    void pastPublishedDetailsRequireOwnEnrollmentEvenWhenCourseIdentifiersAreKnown() throws SQLException {
        var own = repository.create(creation(ITALIAN_CATEGORY_ID, "Programma storico personale",
                LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(80),
                List.of(SessionMode.PRACTICAL, SessionMode.PRACTICAL)));
        var unrelated = repository.create(creation(ITALIAN_CATEGORY_ID, "Programma storico altrui",
                LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(81),
                List.of(SessionMode.PRACTICAL, SessionMode.PRACTICAL)));
        for (var course : List.of(own, unrelated)) {
            repository.assignRecipe(chefSessionToken, repository.sessions(chefSessionToken, course).getFirst().id(),
                    MARGHERITA_RECIPE_ID);
            repository.publish(chefSessionToken, course);
            assertThat(repository.publishedSessions(participantSessionToken, course)).hasSize(2);
            assertThat(repository.publishedMenu(participantSessionToken, course)).hasSize(1);
        }
        executeEnrollmentChange("select app.enroll_in_course(?, ?)", own);
        executeEnrollmentChange("select app.withdraw_from_course(?, ?)", own);
        try (var connection = POSTGRES.createConnection(""); var statement = connection.prepareStatement(
                "update app.course_session set scheduled_at=? + ((sequence_number - 1) * interval '2 hours'), "
                        + "attendance_closes_at=? where course_id=?")) {
            statement.setObject(1, LocalDateTime.of(2002, 1, 10, 10, 0));
            statement.setObject(2, LocalDateTime.of(2002, 1, 9, 10, 0));
            statement.setObject(3, own);
            statement.executeUpdate();
            statement.setObject(1, LocalDateTime.of(2002, 1, 10, 15, 0));
            statement.setObject(2, LocalDateTime.of(2002, 1, 9, 15, 0));
            statement.setObject(3, unrelated);
            statement.executeUpdate();
        }

        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(publishedCourses())
                    .extracting(value -> value.id()).contains(own).doesNotContain(unrelated);
            softly.assertThat(repository.publishedSessions(participantSessionToken, unrelated))
                    .as("Past published sessions without own enrollment").isEmpty();
            softly.assertThat(repository.publishedMenu(participantSessionToken, unrelated))
                    .as("Past published menu without own enrollment").isEmpty();
            softly.assertThat(repository.publishedSessions(participantSessionToken, own))
                    .as("Own withdrawn enrollment retains historical sessions").hasSize(2);
            softly.assertThat(repository.publishedMenu(participantSessionToken, own))
                    .as("Own withdrawn enrollment retains historical menu").hasSize(1);
        });
    }

    @Test
    void createsCommitsAndMapsCoursesAndSessions() {
        var creation = creation(
                ITALIAN_CATEGORY_ID,
                "Pizza e forno a legna",
                LocalDate.of(2026, 12, 1),
                List.of(SessionMode.ONLINE, SessionMode.PRACTICAL));

        var courseId = repository.create(creation);

        assertThat(repository.findByChef(chefSessionToken, Optional.of(ITALIAN_CATEGORY_ID)))
                .filteredOn(course -> course.id().equals(courseId))
                .singleElement()
                .satisfies(course -> {
                    assertThat(course.categoryName()).isEqualTo("Cucina italiana");
                    assertThat(course.title()).isEqualTo(CourseTitle.of("Pizza e forno a legna"));
                    assertThat(course.recurrenceDays()).isEqualTo(7);
                    assertThat(course.sessionCount()).isEqualTo(2);
                    assertThat(course.lastSessionEndsAt())
                            .contains(LocalDateTime.of(2026, 12, 8, 20, 30));
                    assertThat(course.status()).isEqualTo(CourseStatus.DRAFT);
                });
        assertThat(repository.sessions(chefSessionToken, courseId))
                .extracting(
                        session -> session.sequenceNumber(),
                        session -> session.scheduledAt(),
                        session -> session.mode(),
                        session -> session.recipeCount())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                1,
                                LocalDateTime.of(2026, 12, 1, 18, 30),
                                SessionMode.ONLINE,
                                0),
                        org.assertj.core.groups.Tuple.tuple(
                                2,
                                LocalDateTime.of(2026, 12, 8, 18, 30),
                                SessionMode.PRACTICAL,
                                0));
    }

    @Test
    void filtersByCategoryAndReturnsNoSessionsForAnotherOwner() {
        var courseId = repository.create(creation(
                PASTRY_CATEGORY_ID,
                "Dessert al cucchiaio",
                LocalDate.of(2027, 1, 10),
                List.of(SessionMode.PRACTICAL, SessionMode.PRACTICAL)));

        assertThat(repository.findByChef(chefSessionToken, Optional.of(PASTRY_CATEGORY_ID)))
                .extracting(CourseSummary::id)
                .contains(courseId);
        assertThat(repository.findByChef(chefSessionToken, Optional.of(ITALIAN_CATEGORY_ID)))
                .extracting(CourseSummary::id)
                .doesNotContain(courseId);
        var invalidSessionToken = UUID.randomUUID();
        assertThatThrownBy(() -> repository.sessions(invalidSessionToken, courseId))
                .isInstanceOf(CourseRepositoryException.class)
                .satisfies(error -> assertThat(((SQLException) error.getCause()).getSQLState())
                        .isEqualTo("42501"));
    }

    @Test
    void rollsBackAFailedCreationBeforeReturningTheConnection() {
        var duplicate = creation(
                ITALIAN_CATEGORY_ID,
                "Corso con chiave naturale",
                LocalDate.of(2027, 2, 1),
                List.of(SessionMode.ONLINE, SessionMode.ONLINE));
        repository.create(duplicate);

        assertThatThrownBy(() -> repository.create(duplicate))
                .isInstanceOf(CourseRepositoryException.class)
                .hasMessage("Unable to create course")
                .hasMessageNotContaining("Corso con chiave naturale");

        var recoveredId = repository.create(creation(
                ITALIAN_CATEGORY_ID,
                "Corso successivo al rollback",
                LocalDate.of(2027, 2, 2),
                List.of(SessionMode.ONLINE, SessionMode.ONLINE)));
        assertThat(repository.sessions(chefSessionToken, recoveredId)).hasSize(2);
    }

    @Test
    void addsMultipleRecipesToAFuturePublishedPracticalSession() throws SQLException {
        var courseId = repository.create(creation(
                ITALIAN_CATEGORY_ID,
                "Laboratorio pizza pratica",
                LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(30),
                List.of(SessionMode.PRACTICAL, SessionMode.PRACTICAL)));
        var sessionId = repository.sessions(chefSessionToken, courseId).getFirst().id();

        repository.assignRecipe(chefSessionToken, sessionId, MARGHERITA_RECIPE_ID);
        repository.publish(chefSessionToken, courseId);
        repository.assignRecipe(chefSessionToken, sessionId, GNOCCHI_RECIPE_ID);

        assertThat(repository.sessions(chefSessionToken, courseId))
                .first()
                .satisfies(session -> {
                    assertThat(session.recipeCount()).isEqualTo(2);

                });

        try (var connection = POSTGRES.createConnection("");
                var duplicate = connection.prepareStatement(
                        "insert into app.session_recipe(session_id, recipe_id) values (?, ?)")) {
            duplicate.setObject(1, sessionId);
            duplicate.setObject(2, MARGHERITA_RECIPE_ID);
            assertThatThrownBy(duplicate::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("23505"));
        }
    }

    @Test
    void rejectsRecipeChangesOnceThePracticalSessionHasStarted() {
        var courseId = repository.create(creation(
                ITALIAN_CATEGORY_ID,
                "Laboratorio già iniziato",
                LocalDate.now(ZoneId.of("Europe/Rome")).minusDays(1),
                List.of(SessionMode.PRACTICAL, SessionMode.PRACTICAL)));
        var sessionId = repository.sessions(chefSessionToken, courseId).getFirst().id();

        assertThatThrownBy(() -> repository.assignRecipe(chefSessionToken, sessionId, MARGHERITA_RECIPE_ID))
                .isInstanceOf(CourseRepositoryException.class)
                .hasMessage("Unable to assign recipe")
                .satisfies(error -> assertThat(((SQLException) error.getCause()).getSQLState())
                        .isEqualTo("55000"));
    }

    @Test
    void menuAdditionIsIdempotentAndRemovalPreservesTheOtherRecipes() {
        var course = repository.create(creation(ITALIAN_CATEGORY_ID, "Menu completo",
                LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(45),
                List.of(SessionMode.PRACTICAL, SessionMode.PRACTICAL)));
        var session = repository.sessions(chefSessionToken, course).getFirst().id();
        repository.assignRecipe(chefSessionToken, session, MARGHERITA_RECIPE_ID);
        repository.assignRecipe(chefSessionToken, session, GNOCCHI_RECIPE_ID);
        repository.assignRecipe(chefSessionToken, session, GNOCCHI_RECIPE_ID);
        assertThat(repository.sessions(chefSessionToken, course).getFirst().recipeIds())
                .containsExactlyInAnyOrder(MARGHERITA_RECIPE_ID, GNOCCHI_RECIPE_ID);
        repository.removeRecipe(chefSessionToken, session, MARGHERITA_RECIPE_ID);
        assertThat(repository.sessions(chefSessionToken, course).getFirst().recipeIds()).containsExactly(GNOCCHI_RECIPE_ID);
        assertThatThrownBy(() -> repository.removeRecipe(participantSessionToken, session, GNOCCHI_RECIPE_ID))
                .isInstanceOf(CourseRepositoryException.class);
    }

    @Test
    void closedAttendanceAlsoFreezesTheMenuBeforeTheLessonStarts() throws SQLException {
        var course = repository.create(creation(ITALIAN_CATEGORY_ID, "Acquisti definitivi",
                LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(45),
                List.of(SessionMode.PRACTICAL, SessionMode.PRACTICAL)));
        var session = repository.sessions(chefSessionToken, course).getFirst().id();
        repository.assignRecipe(chefSessionToken, session, MARGHERITA_RECIPE_ID);
        try (var connection = POSTGRES.createConnection(""); var statement = connection.prepareStatement(
                "update app.course_session set attendance_closes_at = localtimestamp - interval '1 day' where id=?")) {
            statement.setObject(1, session);
            statement.executeUpdate();
        }
        assertThatThrownBy(() -> repository.assignRecipe(chefSessionToken, session, GNOCCHI_RECIPE_ID))
                .isInstanceOf(CourseRepositoryException.class);
        assertThatThrownBy(() -> repository.removeRecipe(chefSessionToken, session, MARGHERITA_RECIPE_ID))
                .isInstanceOf(CourseRepositoryException.class);
        assertThat(repository.sessions(chefSessionToken, course).getFirst().recipeIds()).containsExactly(MARGHERITA_RECIPE_ID);
    }

    @Test
    void ingredientLockWaitCannotAllowRecipeAssignmentAfterTheCutoff() throws Exception {
        var course = repository.create(creation(ITALIAN_CATEGORY_ID, "Attesa ingredienti",
                LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(45),
                List.of(SessionMode.PRACTICAL, SessionMode.PRACTICAL)));
        var session = repository.sessions(chefSessionToken, course).getFirst().id();
        try (var blocker = POSTGRES.createConnection("");
                var observer = POSTGRES.createConnection("");
                var assignment = runtime.dataSource().getConnection()) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.createStatement()) {
                lock.executeQuery("select id from app.ingredient "
                        + "where id='40000000-0000-0000-0000-000000000001' for update");
            }
            try (var cutoff = observer.prepareStatement("update app.course_session "
                    + "set attendance_closes_at = (clock_timestamp() at time zone 'Europe/Rome') "
                    + "+ interval '4 seconds' where id=?")) {
                cutoff.setObject(1, session);
                cutoff.executeUpdate();
            }
            int assignmentPid;
            try (var pid = assignment.createStatement(); var result = pid.executeQuery("select pg_backend_pid()")) {
                assertThat(result.next()).isTrue();
                assignmentPid = result.getInt(1);
            }
            try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                try {
                    var outcome = executor.submit(() -> {
                        try (var statement = assignment.prepareStatement("select app.assign_recipe_to_session(?,?,?)")) {
                            statement.setObject(1, chefSessionToken);
                            statement.setObject(2, session);
                            statement.setObject(3, MARGHERITA_RECIPE_ID);
                            statement.setQueryTimeout(15);
                            statement.execute();
                            assignment.commit();
                            return "SUCCESS";
                        } catch (SQLException failure) {
                            assignment.rollback();
                            return failure.getSQLState();
                        }
                    });
                    var deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                    boolean waiting = false;
                    try (var activity = observer.prepareStatement(
                            "select wait_event_type='Lock' from pg_stat_activity where pid=?")) {
                        activity.setInt(1, assignmentPid);
                        while (!waiting && System.nanoTime() < deadline) {
                            try (var result = activity.executeQuery()) {
                                waiting = result.next() && result.getBoolean(1);
                            }
                            if (!waiting) {
                                Thread.sleep(20);
                            }
                        }
                    }
                    assertThat(waiting).as("assignment must wait on the held ingredient row").isTrue();
                    try (var clock = observer.prepareStatement("select attendance_closes_at <= "
                            + "clock_timestamp() at time zone 'Europe/Rome' from app.course_session where id=?")) {
                        clock.setObject(1, session);
                        boolean closed = false;
                        while (!closed && System.nanoTime() < deadline) {
                            try (var result = clock.executeQuery()) {
                                assertThat(result.next()).isTrue();
                                closed = result.getBoolean(1);
                            }
                            if (!closed) {
                                Thread.sleep(20);
                            }
                        }
                        assertThat(closed).as("database clock passed the cutoff while assignment waited").isTrue();
                    }
                    blocker.rollback();
                    assertThat(outcome.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("55000");
                } finally {
                    blocker.rollback();
                }
            }
        }
        assertThat(repository.sessions(chefSessionToken, course).getFirst().recipeIds()).isEmpty();
    }

    @Test
    void monthlyReportAutomaticallyCountsAssignedRecipesInTheScheduledMonth() throws SQLException {
        var course = repository.create(creation(ITALIAN_CATEGORY_ID, "Menu del report",
                LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(50),
                List.of(SessionMode.PRACTICAL, SessionMode.PRACTICAL, SessionMode.PRACTICAL)));
        var sessions = repository.sessions(chefSessionToken, course);
        repository.assignRecipe(chefSessionToken, sessions.get(0).id(), MARGHERITA_RECIPE_ID);
        repository.assignRecipe(chefSessionToken, sessions.get(0).id(), GNOCCHI_RECIPE_ID);
        repository.assignRecipe(chefSessionToken, sessions.get(1).id(), MARGHERITA_RECIPE_ID);
        try (var connection = POSTGRES.createConnection(""); var statement = connection.prepareStatement(
                "update app.course_session set scheduled_at = case when sequence_number = 3 "
                        + "then timestamp '2001-03-01 10:00' "
                        + "else timestamp '2001-02-05 10:00' + ((sequence_number - 1) * interval '2 hours') end, "
                        + "attendance_closes_at=timestamp '2001-02-03 10:00' where course_id=?")) {
            statement.setObject(1, course);
            statement.executeUpdate();
        }
        try (var connection = runtime.dataSource().getConnection(); var statement = connection.prepareStatement(
                "select * from app.chef_monthly_report(?,2001,2)")) {
            statement.setObject(1, chefSessionToken);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("total_courses")).isEqualTo(1);
                assertThat(result.getInt("practical_sessions")).isEqualTo(2);
                assertThat(result.getInt("minimum_recipes")).isEqualTo(1);
                assertThat(result.getInt("maximum_recipes")).isEqualTo(2);
                assertThat(result.getBigDecimal("average_recipes")).isEqualByComparingTo("1.5");
            }
        }
    }

    @Test
    void rejectsRecipesForOnlineSessionsAndSessionsOwnedByAnotherChef() {
        var onlineCourseId = repository.create(creation(
                ITALIAN_CATEGORY_ID,
                "Corso solo online",
                LocalDate.of(2027, 3, 10),
                List.of(SessionMode.ONLINE, SessionMode.ONLINE)));
        var onlineSessionId = repository.sessions(chefSessionToken, onlineCourseId).getFirst().id();

        assertThatThrownBy(
                        () -> repository.assignRecipe(chefSessionToken, onlineSessionId, MARGHERITA_RECIPE_ID))
                .isInstanceOf(CourseRepositoryException.class)
                .hasMessage("Unable to assign recipe")
                .satisfies(error -> assertThat(((SQLException) error.getCause()).getSQLState())
                        .isEqualTo("23514"));
        var invalidSessionToken = UUID.randomUUID();
        assertThatThrownBy(() -> repository.assignRecipe(
                        invalidSessionToken, onlineSessionId, MARGHERITA_RECIPE_ID))
                .isInstanceOf(CourseRepositoryException.class)
                .hasMessage("Unable to assign recipe")
                .satisfies(error -> assertThat(((SQLException) error.getCause()).getSQLState())
                        .isEqualTo("42501"));

        var recoveredId = repository.create(creation(
                PASTRY_CATEGORY_ID,
                "Corso dopo errore assegnazione",
                LocalDate.of(2027, 3, 11),
                List.of(SessionMode.PRACTICAL, SessionMode.PRACTICAL)));
        var practicalSessionId = repository.sessions(chefSessionToken, recoveredId).getFirst().id();
        var missingRecipeId = UUID.randomUUID();
        assertThatThrownBy(
                        () -> repository.assignRecipe(chefSessionToken, practicalSessionId, missingRecipeId))
                .isInstanceOf(CourseRepositoryException.class)
                .hasMessage("Unable to assign recipe")
                .satisfies(error -> assertThat(((SQLException) error.getCause()).getSQLState())
                        .isEqualTo("23503"));

        var finalCourseId = repository.create(creation(
                ITALIAN_CATEGORY_ID,
                "Corso dopo ricetta inesistente",
                LocalDate.of(2027, 3, 12),
                List.of(SessionMode.ONLINE, SessionMode.ONLINE)));
        assertThat(repository.sessions(chefSessionToken, finalCourseId)).hasSize(2);
    }

    @Test
    void activatesCoursesBeforeRecipeAssignmentAndMapsTheParticipantCatalog() {
        var incompleteId = repository.create(creation(
                ITALIAN_CATEGORY_ID,
                "Corso da completare",
                LocalDate.of(2027, 4, 1),
                List.of(SessionMode.PRACTICAL, SessionMode.PRACTICAL)));
        repository.publish(chefSessionToken, incompleteId);

        var courseId = repository.create(creation(
                ITALIAN_CATEGORY_ID,
                "Corso pubblicato nel catalogo",
                LocalDate.of(2027, 4, 2),
                List.of(SessionMode.ONLINE, SessionMode.PRACTICAL)));
        var practicalSession = repository.sessions(chefSessionToken, courseId).stream()
                .filter(session -> session.mode() == SessionMode.PRACTICAL)
                .findFirst()
                .orElseThrow();
        repository.assignRecipe(chefSessionToken, practicalSession.id(), MARGHERITA_RECIPE_ID);

        repository.publish(chefSessionToken, courseId);
        repository.publish(chefSessionToken, courseId);

        assertThat(repository.findByChef(
                        chefSessionToken,
                        Optional.empty(),
                        Optional.of(CourseStatus.PUBLISHED)))
                .extracting(CourseSummary::id)
                .contains(courseId, incompleteId);
        assertThat(publishedCourses(Optional.of(ITALIAN_CATEGORY_ID)))
                .filteredOn(course -> course.id().equals(courseId))
                .singleElement()
                .satisfies(course -> {
                    assertThat(course.chefName()).isEqualTo("Marta Rossi");
                    assertThat(course.description()).isEqualTo("Descrizione JDBC completa");
                });
        assertThat(repository.publishedSessions(participantSessionToken, courseId))
                .hasSize(2)
                .last()
                .satisfies(session -> {
                    assertThat(session.mode()).isEqualTo(SessionMode.PRACTICAL);
                    assertThat(session.venue()).contains("Laboratorio JDBC");
                    assertThat(session.capacity()).contains(12);
                });
        assertThat(repository.publishedSessions(participantSessionToken, incompleteId))
                .hasSize(2)
                .allSatisfy(session -> assertThat(session.mode()).isEqualTo(SessionMode.PRACTICAL));
    }

    @Test
    void revealsOnlineAccessOnlyWhileTheParticipantEnrollmentIsActive() throws SQLException {
        var courseId = repository.create(creation(
                ITALIAN_CATEGORY_ID,
                "Accesso online protetto",
                LocalDate.of(2027, 4, 20),
                List.of(SessionMode.ONLINE, SessionMode.ONLINE)));
        repository.publish(chefSessionToken, courseId);
        var expectedAccess = URI.create("https://meet.uninafoodlab.local/jdbc");

        assertThat(repository.publishedSessions(participantSessionToken, courseId))
                .hasSize(2)
                .allSatisfy(session -> assertThat(session.onlineUrl()).isEqualTo(Optional.empty()));

        executeEnrollmentChange("select app.enroll_in_course(?, ?)", courseId);
        assertThat(repository.publishedSessions(participantSessionToken, courseId))
                .hasSize(2)
                .allSatisfy(session -> assertThat(session.onlineUrl()).isEqualTo(Optional.of(expectedAccess)));

        executeEnrollmentChange("select app.withdraw_from_course(?, ?)", courseId);
        assertThat(repository.publishedSessions(participantSessionToken, courseId))
                .hasSize(2)
                .allSatisfy(session -> assertThat(session.onlineUrl()).isEqualTo(Optional.empty()));
    }

    private static void executeEnrollmentChange(String sql, UUID courseId) throws SQLException {
        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, participantSessionToken);
            statement.setObject(2, courseId);
            statement.execute();
            connection.commit();
        }
    }

    private static CourseCreation creation(
            UUID categoryId, String title, LocalDate startDate, List<SessionMode> modes) {
        return new CourseCreation(
                chefSessionToken,
                categoryId,
                CourseTitle.of(title),
                "Descrizione JDBC completa",
                IngredientMargin.zero(),
                CourseSchedule.of(
                        fixtureStartDate(title, startDate),
                        LocalTime.of(18, 30),
                        7,
                        modes.stream().map(JdbcCourseRepositoryIT::details).toList()));
    }

    private static LocalDate fixtureStartDate(String title, LocalDate requestedDate) {
        if (requestedDate.isBefore(LocalDate.now(ZoneId.of("Europe/Rome")))
                || title.equals("Pizza e forno a legna")) {
            return requestedDate;
        }
        return LocalDate.of(2040, 1, 1).plusDays(Math.floorMod(title.hashCode(), 10_000));
    }

    private static SessionDetails details(SessionMode mode) {
        return mode == SessionMode.ONLINE
                ? SessionDetails.online(
                        120, URI.create("https://meet.uninafoodlab.local/jdbc"))
                : SessionDetails.practical(120, "Laboratorio JDBC", 12);
    }

    private static List<it.uninafoodlab.application.course.PublishedCourseSummary> publishedCourses() {
        return publishedCourses(Optional.empty());
    }

    private static List<it.uninafoodlab.application.course.PublishedCourseSummary> publishedCourses(Optional<UUID> categoryId) {
        var filter = new CourseFilter(categoryId, "", CoursePeriod.ALL, LocalDateTime.now(ZoneId.of("Europe/Rome")));
        return repository.findPublished(participantSessionToken, filter, new PageRequest(0, 100)).items();
    }
}
