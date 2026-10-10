package it.uninafoodlab.infrastructure.course;

import static org.assertj.core.api.Assertions.assertThat;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.application.catalog.RecipeFilter;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.application.course.CourseFilter;
import it.uninafoodlab.application.course.CoursePeriod;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.infrastructure.catalog.JdbcCatalogRepository;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import it.uninafoodlab.infrastructure.support.DemoDatabaseFixture;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class CatalogPagingPerformanceIT {

    private static final String MIGRATOR_PASSWORD = "paging-performance-migrator";
    private static final String APP_PASSWORD = "paging-performance-app";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");

    @Test
    void recordsPagingCostsWithTenTimesMoreCoursesAndRecipes() throws Exception {
        try (var runtime = startDatabase()) {
            var actor = runtime.authenticateUser().execute("marta.rossi", "MartaRossi!2026".toCharArray());
            var queries = new AtomicInteger();
            var source = countQueries(runtime.dataSource(), queries);
            var courses = new JdbcCourseRepository(source);
            var recipes = new JdbcCatalogRepository(source);
            var courseFilter = new CourseFilter(Optional.empty(), "Paging sample", CoursePeriod.ALL,
                    LocalDateTime.now(ZoneId.of("Europe/Rome")));
            var recipeFilter = new RecipeFilter(Optional.empty(), "Paging recipe");
            var page = new PageRequest(0, 12);
            insertFixtures(actor, 1, 10);
            var normalCourses = measure(() -> courses.findByChef(actor.sessionToken(), courseFilter, page), queries, 10);
            var normalRecipes = measure(() -> recipes.recipes(actor.sessionToken(), recipeFilter, page), queries, 10);
            insertFixtures(actor, 11, 100);
            var largeCourses = measure(() -> courses.findByChef(actor.sessionToken(), courseFilter, page), queries, 100);
            var largeRecipes = measure(() -> recipes.recipes(actor.sessionToken(), recipeFilter, page), queries, 100);
            writeReport(normalCourses, largeCourses, normalRecipes, largeRecipes);
            writePlans(actor, courseFilter.now());
        }
    }

    private static ApplicationRuntime startDatabase() throws Exception {
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(Path.of("database", "bootstrap", "001__create_roles.sql")),
                "/tmp/001__create_roles.sql");
        var bootstrap = POSTGRES.execInContainer("psql", "--username=" + POSTGRES.getUsername(),
                "--dbname=" + POSTGRES.getDatabaseName(), "--set=ON_ERROR_STOP=1",
                "--set=migrator_password=" + MIGRATOR_PASSWORD, "--set=app_password=" + APP_PASSWORD,
                "--file=/tmp/001__create_roles.sql");
        assertThat(bootstrap.getExitCode()).as(bootstrap.getStderr()).isZero();
        return DemoDatabaseFixture.start(new ApplicationConfig(POSTGRES.getJdbcUrl(), "uninafoodlab_app",
                APP_PASSWORD, "uninafoodlab_migrator", MIGRATOR_PASSWORD, "test"));
    }

    private static void insertFixtures(AuthenticatedUser actor, int first, int last) throws Exception {
        try (var connection = admin(); var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app.recipe(category_id, name, description, base_servings)
                    SELECT '30000000-0000-0000-0000-000000000001',
                           'Paging recipe ' || lpad(i::text, 3, '0'), 'Paging performance fixture', 1
                      FROM generate_series(%d, %d) i;
                    INSERT INTO app.course(chef_profile_id, category_id, title, description, start_date,
                        start_time, recurrence_days, session_count, status, ingredient_margin)
                    SELECT '%s', '30000000-0000-0000-0000-000000000001',
                           'Paging sample ' || lpad(i::text, 3, '0'), 'Paging performance fixture',
                           DATE '2049-01-01' + i, TIME '18:00', 1, 1, 'PUBLISHED', 0
                      FROM generate_series(%d, %d) i;
                    INSERT INTO app.course_session(course_id, sequence_number, scheduled_at, mode, status,
                        duration_minutes, online_url)
                    SELECT c.id, 1, c.start_date + c.start_time, 'ONLINE', 'SCHEDULED', 90, 'https://example.test/paging'
                      FROM app.course c WHERE c.title LIKE 'Paging sample %%'
                       AND NOT EXISTS (SELECT 1 FROM app.course_session s WHERE s.course_id = c.id);
                    ANALYZE app.recipe;
                    ANALYZE app.course;
                    ANALYZE app.course_session;
                    """.formatted(first, last, actor.profileId(), first, last));
        }
    }

    private static Measurement measure(Supplier<? extends Page<?>> operation, AtomicInteger queries, int count) {
        for (int index = 0; index < 20; index++) operation.get();
        var times = new double[100];
        queries.set(0);
        long heapBefore = usedHeap();
        for (int index = 0; index < times.length; index++) {
            long startedAt = System.nanoTime();
            var page = operation.get();
            times[index] = (System.nanoTime() - startedAt) / 1_000_000.0;
            assertThat(page.totalCount()).isEqualTo(count);
            assertThat(page.items()).hasSize(Math.min(count, 12));
        }
        assertThat(queries).hasValue(times.length);
        Arrays.sort(times);
        return new Measurement(count, Math.min(count, 12), times[49], times[94], heapBefore, usedHeap());
    }

    private static void writeReport(Measurement normalCourses, Measurement largeCourses,
            Measurement normalRecipes, Measurement largeRecipes) throws Exception {
        var report = """
                {
                  "database": "PostgreSQL 18.4 Testcontainers",
                  "java": "%s",
                  "os": "%s",
                  "samplesPerDataset": 100,
                  "warmupPerDataset": 20,
                  "queriesPerOperation": 1,
                  "memory": "Used heap before and after sampling; affected by GC, not retained-memory evidence",
                  "courses": {"normal": %s, "tenTimesLarger": %s},
                  "recipes": {"normal": %s, "tenTimesLarger": %s}
                }
                """.formatted(System.getProperty("java.version"), System.getProperty("os.name"),
                        normalCourses.json(), largeCourses.json(), normalRecipes.json(), largeRecipes.json());
        Files.writeString(Path.of("target", "catalog-paging-performance.json"), report);
    }

    private static void writePlans(AuthenticatedUser actor, LocalDateTime now) throws Exception {
        var parameters = Map.of("profile_id", "'" + actor.profileId() + "'::uuid",
                "p_category", "NULL::uuid", "p_ids", "NULL::uuid[]", "p_period", "'ALL'",
                "p_now", "TIMESTAMP '" + now + "'", "p_size", "12", "p_offset", "0",
                "p_menu", "false");
        try (var connection = admin(); var statement = connection.createStatement()) {
            for (var name : java.util.List.of("page_chef_courses", "page_catalog_recipes")) {
                String definition;
                try (var result = statement.executeQuery("SELECT pg_get_functiondef(oid) FROM pg_proc "
                        + "WHERE pronamespace='app'::regnamespace AND proname='" + name + "'")) {
                    assertThat(result.next()).isTrue();
                    definition = result.getString(1);
                }
                var query = definition.substring(definition.indexOf("RETURN QUERY") + 12, definition.lastIndexOf("END;"));
                for (var parameter : parameters.entrySet()) {
                    query = query.replaceAll("\\b" + parameter.getKey() + "\\b", parameter.getValue());
                }
                query = query.replaceAll("\\bp_search\\b", name.equals("page_chef_courses") ? "'Paging sample'" : "'Paging recipe'");
                try (var result = statement.executeQuery("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + query)) {
                    assertThat(result.next()).isTrue();
                    Files.writeString(Path.of("target", name + "-plan.json"), result.getString(1));
                }
            }
        }
    }

    private static long usedHeap() {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    private static Connection admin() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static DataSource countQueries(DataSource delegate, AtomicInteger count) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class},
                (proxy, method, arguments) -> {
                    var result = invoke(method, delegate, arguments);
                    if (!method.getName().equals("getConnection")) return result;
                    return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                            (connection, operation, parameters) -> {
                                if (operation.getName().equals("prepareStatement")) count.incrementAndGet();
                                return invoke(operation, result, parameters);
                            });
                });
    }

    private static Object invoke(Method method, Object target, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private record Measurement(int rows, int returnedRows, double p50Ms, double p95Ms, long heapBefore, long heapAfter) {
        String json() {
            return String.format(java.util.Locale.ROOT,
                    "{\"rows\": %d, \"returnedRows\": %d, \"p50Ms\": %.3f, \"p95Ms\": %.3f, \"heapBefore\": %d, \"heapAfter\": %d}",
                    rows, returnedRows, p50Ms, p95Ms, heapBefore, heapAfter);
        }
    }
}
