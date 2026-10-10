package it.uninafoodlab.infrastructure.course;

import it.uninafoodlab.infrastructure.support.DatabaseActors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class CourseDatabaseApiIT {

    private static final String MIGRATOR_PASSWORD = "course-api-migrator-only";
    private static final String APP_PASSWORD = "course-api-app-only";
    private static UUID CHEF_ID;
    private static UUID SECOND_CHEF_SESSION_TOKEN;
    private static UUID PARTICIPANT_SESSION_TOKEN;
    private static final UUID CAMPANIA_CATEGORY =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PASTRY_CATEGORY =
            UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID MARGHERITA_RECIPE =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID SEED_CHEF_PROFILE_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");

    private static ApplicationRuntime runtime;

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
        createSecondChef();
        CHEF_ID = runtime.authenticateUser()
                .execute("marta.rossi", "MartaRossi!2026".toCharArray())
                .sessionToken();
        SECOND_CHEF_SESSION_TOKEN = runtime.authenticateUser()
                .execute("chef.second", "ChefDemo!2026".toCharArray())
                .sessionToken();
        PARTICIPANT_SESSION_TOKEN = DatabaseActors.authenticate(runtime, "luca.bianchi", "LucaBianchi!2026".toCharArray())
                .sessionToken();
    }

    @AfterAll
    static void stopDatabase() {
        runtime.close();
    }

    @Test
    void courseRosterIncludesOnlineEnrollmentsAndProtectsChefOwnership() throws SQLException {
        UUID courseId;
        try (var connection = runtime.dataSource().getConnection()) {
            courseId = createCourse(connection, CAMPANIA_CATEGORY,
                    "Iscritti corso online", LocalDate.now().plusMonths(3),
                    LocalTime.of(18, 0), 7, "ONLINE", "ONLINE");
            publish(connection, CHEF_ID, courseId);
            connection.commit();
        }
        var repository = new it.uninafoodlab.infrastructure.planning.JdbcChefPlanningRepository(
                runtime.dataSource());
        assertThat(repository.courseParticipants(CHEF_ID, courseId)).isEmpty();
        var enrollmentRepository = new it.uninafoodlab.infrastructure.enrollment.JdbcEnrollmentRepository(
                runtime.dataSource());
        enrollmentRepository.enroll(PARTICIPANT_SESSION_TOKEN, courseId);
        assertThat(repository.courseParticipants(CHEF_ID, courseId))
                .containsExactly(new it.uninafoodlab.application.planning.CourseParticipant(
                        "Luca Bianchi", it.uninafoodlab.domain.enrollment.EnrollmentStatus.ACTIVE));
        enrollmentRepository.withdraw(PARTICIPANT_SESSION_TOKEN, courseId);
        assertThat(repository.courseParticipants(CHEF_ID, courseId))
                .singleElement().satisfies(participant -> assertThat(participant.enrollmentStatus())
                        .isEqualTo(it.uninafoodlab.domain.enrollment.EnrollmentStatus.WITHDRAWN));
        for (var deniedToken : List.of(SECOND_CHEF_SESSION_TOKEN,
                PARTICIPANT_SESSION_TOKEN, UUID.randomUUID())) {
            assertThatThrownBy(() -> repository.courseParticipants(deniedToken, courseId))
                    .isInstanceOf(it.uninafoodlab.infrastructure.planning.ChefPlanningRepositoryException.class)
                    .hasRootCauseInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error.getCause()).getSQLState())
                            .isEqualTo("42501"));
        }
        assertThatThrownBy(() -> repository.courseParticipants(CHEF_ID, UUID.randomUUID()))
                .hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    void createsACourseAndGeneratesAllSessionsAtomically() throws SQLException {
        try (var connection = runtime.dataSource().getConnection()) {
            var courseId = createCourse(
                    connection,
                    CAMPANIA_CATEGORY,
                    "Tecniche pizza napoletana",
                    LocalDate.of(2026, 9, 1),
                    LocalTime.of(18, 30),
                    7,
                    "ONLINE",
                    "PRACTICAL",
                    "ONLINE");

            assertThat(listSessions(connection, courseId))
                    .containsExactly(
                            new SessionRow(1, LocalDateTime.of(2026, 9, 1, 18, 30), "ONLINE"),
                            new SessionRow(
                                    2,
                                    LocalDateTime.of(2026, 9, 8, 18, 30),
                                    "PRACTICAL"),
                            new SessionRow(3, LocalDateTime.of(2026, 9, 15, 18, 30), "ONLINE"));
        }
    }

    @Test
    void createsACompleteCourseWithModeSpecificFieldsAndZeroIngredientMargin()
            throws SQLException {
        try (var connection = runtime.dataSource().getConnection()) {
            var courseId = createDetailedCourse(connection, "Corso completo pubblicabile");

            try (var statement = connection.prepareStatement(
                    "select ingredient_margin from app.list_chef_courses(?, null, null) "
                            + "where course_id = ?")) {
                statement.setObject(1, CHEF_ID);
                statement.setObject(2, courseId);
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBigDecimal("ingredient_margin"))
                            .isEqualByComparingTo("0");
                }
            }

            try (var statement = connection.prepareStatement(
                    "select sequence_number, duration_minutes, online_url, venue, capacity "
                            + "from app.list_course_sessions(?, ?) order by sequence_number")) {
                statement.setObject(1, CHEF_ID);
                statement.setObject(2, courseId);
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt("duration_minutes")).isEqualTo(90);
                    assertThat(result.getString("online_url"))
                            .isEqualTo("https://meet.uninafoodlab.local/complete");
                    assertThat(result.getString("venue")).isNull();
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt("duration_minutes")).isEqualTo(120);
                    assertThat(result.getString("online_url")).isNull();
                    assertThat(result.getString("venue")).isEqualTo("Laboratorio Vesuvio");
                    assertThat(result.getInt("capacity")).isEqualTo(14);
                    assertThat(result.next()).isFalse();
                }
            }
        }
    }

    @Test
    void databaseConstraintRejectsNullRequiredModeSpecificFields() throws SQLException {
        UUID courseId;
        try (var connection = runtime.dataSource().getConnection()) {
            courseId = createDetailedCourse(connection, "Corso con dettagli protetti");
            connection.commit();
        }

        assertModeDetailCannotBeCleared(courseId, "ONLINE", "online_url");
        assertModeDetailCannotBeCleared(courseId, "PRACTICAL", "venue");
        assertModeDetailCannotBeCleared(courseId, "PRACTICAL", "capacity");
    }

    @Test
    void rejectsOnlineUrlsWithoutAValidHttpsHostAtBothDatabaseBoundaries()
            throws SQLException {
        try (var connection = runtime.dataSource().getConnection()) {
            assertThatThrownBy(() -> createOnlineCourseWithUrl(connection, "https://?query"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("22023"));
            connection.rollback();
        }

        UUID courseId;
        try (var connection = runtime.dataSource().getConnection()) {
            courseId = createDetailedCourse(connection, "Corso con URL protetto");
            connection.commit();
        }
        try (var connection = migratorConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "update app.course_session set online_url = 'https://?query' "
                            + "where course_id = ? and mode = 'ONLINE'")) {
                statement.setObject(1, courseId);
                assertThatThrownBy(statement::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                                .isEqualTo("23514"));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void activatesOwnedCoursesEvenWhenRecipesWillBeAssignedLater()
            throws SQLException {
        try (var connection = runtime.dataSource().getConnection()) {
            var courseId = createDetailedCourse(connection, "Corso pronto a pubblicare");
            connection.commit();

            publish(connection, CHEF_ID, courseId);
            publish(connection, CHEF_ID, courseId);
            connection.commit();

            assertThat(publishedCourseIds(connection)).contains(courseId);
            assertThatThrownBy(() -> publish(connection, UUID.randomUUID(), courseId))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("42501"));
            connection.rollback();
        }
    }

    @Test
    void filtersOwnedCoursesByCategory() throws SQLException {
        try (var connection = runtime.dataSource().getConnection()) {
            var expectedTitle = "Frolle e creme moderne";
            createCourse(
                    connection,
                    PASTRY_CATEGORY,
                    expectedTitle,
                    LocalDate.of(2026, 10, 2),
                    LocalTime.of(17, 0),
                    2,
                    "PRACTICAL", "PRACTICAL");
            createCourse(
                    connection,
                    CAMPANIA_CATEGORY,
                    "Primi piatti campani",
                    LocalDate.of(2026, 10, 1),
                    LocalTime.of(18, 0),
                    7,
                    "ONLINE", "ONLINE");

            try (var statement = connection.prepareStatement(
                    "select title, category_id from app.list_chef_courses(?, ?, null)")) {
                statement.setObject(1, CHEF_ID);
                statement.setObject(2, PASTRY_CATEGORY);
                try (var result = statement.executeQuery()) {
                    var titles = new ArrayList<String>();
                    while (result.next()) {
                        assertThat(result.getObject("category_id", UUID.class))
                                .isEqualTo(PASTRY_CATEGORY);
                        titles.add(result.getString("title"));
                    }
                    assertThat(titles).contains(expectedTitle);
                }
            }
        }
    }

    @Test
    void rejectsInvalidCourseRequestsWithoutPartialWrites() throws SQLException {
        try (var connection = runtime.dataSource().getConnection()) {
            var before = countCourses(connection);

            assertThatThrownBy(() -> createCourse(
                            connection,
                            CAMPANIA_CATEGORY,
                            "Corso non valido",
                            LocalDate.of(2026, 11, 1),
                            LocalTime.NOON,
                            7,
                            "ONLINE",
                            "UNKNOWN"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("22023"));
            connection.rollback();
            assertThatThrownBy(() -> createCourse(
                            connection,
                            UUID.randomUUID(),
                            "Categoria inesistente",
                            LocalDate.of(2026, 11, 1),
                            LocalTime.NOON,
                            7,
                            "ONLINE", "ONLINE"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("23503"));
            connection.rollback();

            assertThat(countCourses(connection)).isEqualTo(before);
        }
    }

    @Test
    void applicationRoleCannotWriteCourseTablesDirectly() throws SQLException {
        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute(
                            "insert into app.course(chef_profile_id, category_id, title, "
                                    + "description, start_date, start_time, recurrence_days, "
                                    + "session_count, status) values ('"
                                    + CHEF_ID
                                    + "', '"
                                    + CAMPANIA_CATEGORY
                                    + "', 'Vietato', 'Scrittura diretta', current_date, "
                                    + "time '18:00', 7, 1, 'DRAFT')"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("42501"));
        }
    }

    @Test
    void applicationRoleCannotReadCourseTablesDirectly() throws SQLException {
        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeQuery("select * from app.course"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("42501"));
        }
    }

    @Test
    void databaseSessionsPreventCrossRoleAndCrossChefImpersonation() throws SQLException {
        try (var connection = runtime.dataSource().getConnection()) {
            var courseId = createDetailedCourse(connection, "Corso con identità vincolata");
            connection.commit();

            assertThat(listSessionIds(connection, SECOND_CHEF_SESSION_TOKEN, courseId)).isEmpty();
            assertThatThrownBy(() -> publish(connection, SECOND_CHEF_SESSION_TOKEN, courseId))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("42501"));
            connection.rollback();

            assertThatThrownBy(() -> listSessionIds(
                            connection, PARTICIPANT_SESSION_TOKEN, courseId))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("42501"));
            connection.rollback();

            try (var statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.executeQuery("select * from app.account"))
                        .isInstanceOf(SQLException.class)
                        .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                                .isEqualTo("42501"));
            }
            connection.rollback();

            try (var statement = connection.prepareStatement(
                    "select app.authenticated_profile_id(?, ?)")) {
                statement.setObject(1, SEED_CHEF_PROFILE_ID);
                statement.setString(2, "CHEF");
                assertThatThrownBy(statement::executeQuery)
                        .isInstanceOf(SQLException.class)
                        .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                                .isEqualTo("42501"));
            }
        }
    }

    @Test
    void legacyIncompleteCourseCreationIsNotExecutableByTheApplicationRole()
            throws SQLException {
        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select app.create_course(?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, CHEF_ID);
            statement.setObject(2, CAMPANIA_CATEGORY);
            statement.setString(3, "Creazione incompleta vietata");
            statement.setString(4, "La vecchia API non deve essere accessibile");
            statement.setObject(5, LocalDate.of(2027, 6, 1));
            statement.setObject(6, LocalTime.NOON);
            statement.setInt(7, 7);
            statement.setArray(8, connection.createArrayOf("text", new String[] {"ONLINE"}));

            assertThatThrownBy(statement::executeQuery)
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("42501"));
        }
    }

    @Test
    void profileRoleTriggersRejectMismatchedProfiles() throws SQLException {
        var participantAccount = UUID.randomUUID();
        try (var connection = migratorConnection()) {
            connection.setAutoCommit(false);
            try (var account = connection.prepareStatement(
                            "insert into app.account(id, username, password_hash, role) "
                                    + "values (?, ?, ?, 'PARTICIPANT')");
                    var chefProfile = connection.prepareStatement(
                            "insert into app.chef_profile(account_id, display_name) values (?, ?)")) {
                account.setObject(1, participantAccount);
                account.setString(2, "mismatch." + participantAccount.toString().substring(0, 8));
                account.setString(
                        3,
                        "$pbkdf2-sha256$600000$AAAAAAAAAAAAAAAAAAAAAA$"
                                + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
                account.executeUpdate();

                chefProfile.setObject(1, participantAccount);
                chefProfile.setString(2, "Profilo non coerente");
                assertThatThrownBy(chefProfile::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                                .isEqualTo("23514"));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void databaseTriggerRejectsDirectRecipesOnOnlineSessions() throws SQLException {
        UUID courseId;
        try (var connection = runtime.dataSource().getConnection()) {
            courseId = createCourse(
                    connection,
                    CAMPANIA_CATEGORY,
                    "Corso online protetto",
                    LocalDate.of(2027, 4, 1),
                    LocalTime.of(19, 0),
                    7,
                    "ONLINE", "ONLINE");
            connection.commit();
        }

        try (var connection = migratorConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "insert into app.session_recipe(session_id, recipe_id) "
                            + "select id, ? from app.course_session where course_id = ?")) {
                statement.setObject(1, MARGHERITA_RECIPE);
                statement.setObject(2, courseId);
                assertThatThrownBy(statement::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                                .isEqualTo("23514"));
            } finally {
                connection.rollback();
            }
        }
    }

    private static UUID createCourse(
            Connection connection,
            UUID categoryId,
            String title,
            LocalDate startDate,
            LocalTime startTime,
            int recurrenceDays,
            String... modes)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "select app.create_course(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, CHEF_ID);
            statement.setObject(2, categoryId);
            statement.setString(3, title);
            statement.setString(4, "Descrizione completa del corso");
            statement.setObject(5, startDate);
            statement.setObject(6, startTime);
            statement.setInt(7, recurrenceDays);
            statement.setBigDecimal(8, BigDecimal.ZERO);
            statement.setArray(9, connection.createArrayOf("text", modes));
            var durations = new Integer[modes.length];
            var onlineUrls = new String[modes.length];
            var venues = new String[modes.length];
            var capacities = new Integer[modes.length];
            for (var index = 0; index < modes.length; index++) {
                durations[index] = 120;
                if ("ONLINE".equals(modes[index])) {
                    onlineUrls[index] =
                            "https://meet.uninafoodlab.local/api-" + (index + 1);
                } else if ("PRACTICAL".equals(modes[index])) {
                    venues[index] = "Laboratorio API";
                    capacities[index] = 12;
                }
            }
            statement.setArray(10, connection.createArrayOf("integer", durations));
            statement.setArray(11, connection.createArrayOf("text", onlineUrls));
            statement.setArray(12, connection.createArrayOf("text", venues));
            statement.setArray(13, connection.createArrayOf("integer", capacities));
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getObject(1, UUID.class);
            }
        }
    }

    private static UUID createDetailedCourse(Connection connection, String title)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "select app.create_course(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, CHEF_ID);
            statement.setObject(2, CAMPANIA_CATEGORY);
            statement.setString(3, title);
            statement.setString(4, "Descrizione completa del corso");
            statement.setObject(5, uniqueFutureDate(title));
            statement.setObject(6, LocalTime.of(18, 30));
            statement.setInt(7, 7);
            statement.setBigDecimal(8, BigDecimal.ZERO);
            statement.setArray(9, connection.createArrayOf(
                    "text", new String[] {"ONLINE", "PRACTICAL"}));
            statement.setArray(10, connection.createArrayOf(
                    "integer", new Integer[] {90, 120}));
            statement.setArray(11, connection.createArrayOf(
                    "text", new String[] {
                        "https://meet.uninafoodlab.local/complete", null
                    }));
            statement.setArray(12, connection.createArrayOf(
                    "text", new String[] {null, "Laboratorio Vesuvio"}));
            statement.setArray(13, connection.createArrayOf(
                    "integer", new Integer[] {null, 14}));
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getObject(1, UUID.class);
            }
        }
    }

    private static UUID createOnlineCourseWithUrl(Connection connection, String onlineUrl)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "select app.create_course(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, CHEF_ID);
            statement.setObject(2, CAMPANIA_CATEGORY);
            statement.setString(3, "Corso URL non valido");
            statement.setString(4, "Verifica coerente del vincolo HTTPS");
            statement.setObject(5, LocalDate.of(2027, 7, 1));
            statement.setObject(6, LocalTime.of(18, 30));
            statement.setInt(7, 7);
            statement.setBigDecimal(8, BigDecimal.ZERO);
            statement.setArray(9, connection.createArrayOf("text", new String[] {"ONLINE", "ONLINE"}));
            statement.setArray(10, connection.createArrayOf("integer", new Integer[] {90, 90}));
            statement.setArray(11, connection.createArrayOf("text", new String[] {onlineUrl, onlineUrl}));
            statement.setArray(12, connection.createArrayOf("text", new String[] {null, null}));
            statement.setArray(13, connection.createArrayOf("integer", new Integer[] {null, null}));
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getObject(1, UUID.class);
            }
        }
    }

    private static void publish(Connection connection, UUID chefId, UUID courseId)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "select app.publish_course(?, ?)")) {
            statement.setObject(1, chefId);
            statement.setObject(2, courseId);
            statement.executeQuery();
        }
    }

    private static LocalDate uniqueFutureDate(String title) {
        return LocalDate.of(2035, 1, 1).plusDays(Math.floorMod(title.hashCode(), 10_000));
    }

    private static List<UUID> publishedCourseIds(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                        "select course_id from app.list_published_courses(?, null)")) {
            statement.setObject(1, PARTICIPANT_SESSION_TOKEN);
            try (var result = statement.executeQuery()) {
                var ids = new ArrayList<UUID>();
                while (result.next()) {
                    ids.add(result.getObject(1, UUID.class));
                }
                return List.copyOf(ids);
            }
        }
    }

    private static List<SessionRow> listSessions(Connection connection, UUID courseId)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "select sequence_number, scheduled_at, mode "
                        + "from app.list_course_sessions(?, ?) order by sequence_number")) {
            statement.setObject(1, CHEF_ID);
            statement.setObject(2, courseId);
            try (var result = statement.executeQuery()) {
                var sessions = new ArrayList<SessionRow>();
                while (result.next()) {
                    sessions.add(new SessionRow(
                            result.getInt("sequence_number"),
                            result.getObject("scheduled_at", LocalDateTime.class),
                            result.getString("mode")));
                }
                return List.copyOf(sessions);
            }
        }
    }

    private static int countCourses(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                "select count(*) from app.list_chef_courses(?, null, null)")) {
            statement.setObject(1, CHEF_ID);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private static Connection migratorConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), "uninafoodlab_migrator", MIGRATOR_PASSWORD);
    }

    private static List<UUID> listSessionIds(
            Connection connection, UUID sessionToken, UUID courseId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "select session_id from app.list_course_sessions(?, ?)")) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, courseId);
            try (var result = statement.executeQuery()) {
                var ids = new ArrayList<UUID>();
                while (result.next()) {
                    ids.add(result.getObject(1, UUID.class));
                }
                return List.copyOf(ids);
            }
        }
    }

    private static void createSecondChef() throws SQLException {
        var accountId = UUID.fromString("10000000-0000-0000-0000-000000000003");
        try (var connection = migratorConnection()) {
            connection.setAutoCommit(false);
            try (var account = connection.prepareStatement(
                            "insert into app.account(id, username, password_hash, role) "
                                    + "values (?, 'chef.second', ?, 'CHEF')");
                    var profile = connection.prepareStatement(
                            "insert into app.chef_profile(account_id, display_name) values (?, ?)")) {
                account.setObject(1, accountId);
                account.setString(2,
                        "$pbkdf2-sha256$600000$jbDTsbcntZ7JDhcGHbwZ5Q$"
                                + "caxX34l2hBbPZM9Km+VzQVpA52LuXw9f1Mj0LFjbxEc");
                account.executeUpdate();
                profile.setObject(1, accountId);
                profile.setString(2, "Chef Secondo");
                profile.executeUpdate();
                connection.commit();
            }
        }
    }

    private static void assertModeDetailCannotBeCleared(
            UUID courseId, String mode, String column) throws SQLException {
        try (var connection = migratorConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "update app.course_session set " + column + " = null "
                            + "where course_id = ? and mode = ?")) {
                statement.setObject(1, courseId);
                statement.setString(2, mode);
                assertThatThrownBy(statement::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                                .isEqualTo("23514"));
            } finally {
                connection.rollback();
            }
        }
    }

    private record SessionRow(int sequenceNumber, LocalDateTime scheduledAt, String mode) {}
}
