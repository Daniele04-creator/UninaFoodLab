package it.uninafoodlab.infrastructure.planning;

import it.uninafoodlab.domain.catalog.MeasurementUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class IngredientNameCorrectionsIT {
    @Container private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");
    private static ApplicationRuntime runtime;
    private static UUID token;
    private static final java.util.concurrent.atomic.AtomicInteger FIXTURE_DAY = new java.util.concurrent.atomic.AtomicInteger(200);

    @BeforeAll
    static void start() throws Exception {
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(
                Path.of("database/bootstrap/001__create_roles.sql")), "/tmp/roles.sql");
        var result = POSTGRES.execInContainer("psql", "--username=" + POSTGRES.getUsername(),
                "--dbname=" + POSTGRES.getDatabaseName(), "--set=ON_ERROR_STOP=1",
                "--set=migrator_password=lifecycle-migrator-only", "--set=app_password=lifecycle-app-only",
                "--file=/tmp/roles.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        runtime = it.uninafoodlab.infrastructure.support.DemoDatabaseFixture.start(new ApplicationConfig(POSTGRES.getJdbcUrl(), "uninafoodlab_app",
                "lifecycle-app-only", "uninafoodlab_migrator", "lifecycle-migrator-only", "test"));
        token = runtime.authenticateUser().execute("marta.rossi", "MartaRossi!2026".toCharArray()).sessionToken();
    }

    @AfterAll
    static void stop() { if (runtime != null) runtime.close(); }

    @Test
    void correctsUsedNamePreservingSnapshotsAndUnitIncludingSnapshotOnlyUsage() throws Exception {
        var data=fixture();
        var catalog=new it.uninafoodlab.infrastructure.catalog.JdbcRecipeCatalogManagementRepository(runtime.dataSource());
        var renamed=new it.uninafoodlab.application.catalog.IngredientInput("Corrected "+data.ingredient(),it.uninafoodlab.domain.catalog.MeasurementUnit.GRAM);
        catalog.saveIngredient(token,java.util.Optional.of(data.ingredient()),renamed);
        assertThat(catalog.ingredients(token)).filteredOn(i->i.id().equals(data.ingredient())).singleElement()
                .satisfies(i->{assertThat(i.locked()).isFalse();assertThat(i.unitLocked()).isTrue();});
        try(var c=admin();var st=c.prepareStatement("SELECT ingredients->0->>'ingredient_name' FROM app.session_menu_snapshot WHERE session_id=?")) {
            st.setObject(1,data.first());
            try(var rows=st.executeQuery()){int count=0;while(rows.next()){assertThat(rows.getString(1)).startsWith("Original ");count++;}assertThat(count).isEqualTo(2);}
            execute(c,"DELETE FROM app.recipe_ingredient WHERE ingredient_id=?",data.ingredient());
        }
        assertThat(catalog.ingredients(token)).filteredOn(i->i.id().equals(data.ingredient())).singleElement()
                .satisfies(i->assertThat(i.unitLocked()).isTrue());
        assertThatThrownBy(()->catalog.saveIngredient(token,java.util.Optional.of(data.ingredient()),
                new it.uninafoodlab.application.catalog.IngredientInput(renamed.name(),it.uninafoodlab.domain.catalog.MeasurementUnit.MILLILITER)))
                .isInstanceOf(it.uninafoodlab.infrastructure.catalog.CatalogRepositoryException.class);
    }

    @Test
    void sumsSameIdentityOnceAcrossDifferentlyNamedSnapshotRowsAndSessions() throws Exception {
        var data=fixture();
        try(var c=admin()) {
            execute(c,"UPDATE app.session_menu_snapshot SET ingredients=jsonb_set(ingredients,'{0,ingredient_name}',to_jsonb('Historical label'::text)) WHERE session_id=? AND recipe_id=?",data.first(),data.recipe());
            execute(c,"UPDATE app.session_menu_snapshot SET ingredients=jsonb_set(ingredients,'{0,ingredient_name}',to_jsonb('Other historical label'::text)) WHERE session_id=?",data.second());
        }
        var planning=new JdbcChefPlanningRepository(runtime.dataSource());
        assertThat(planning.requirements(token,data.first())).singleElement().satisfies(row->{
            assertThat(row.requiredQuantity()).isEqualByComparingTo("30");
            assertThat(row.ingredientName()).isEqualTo("Original "+data.ingredient());
        });
        assertThat(planning.sessionPreparation(token,data.first()).totals()).singleElement()
                .satisfies(row->assertThat(row.requiredQuantity()).isEqualByComparingTo("30"));
        var snapshots=planning.courseExport(token,data.course()).snapshots();
        assertThat(it.uninafoodlab.application.planning.BrowseChefPlanning.aggregateRequirements(snapshots))
                .singleElement().satisfies(row->assertThat(row.requiredQuantity()).isEqualByComparingTo("60"));
    }

    private static Data fixture() throws Exception {
        var ingredient=UUID.randomUUID();var recipe1=UUID.randomUUID();var recipe2=UUID.randomUUID();
        var course=UUID.randomUUID();var first=UUID.randomUUID();var second=UUID.randomUUID();var enrollment=UUID.randomUUID();
        int day=FIXTURE_DAY.addAndGet(5);
        try(var c=admin()) {
            execute(c,"INSERT INTO app.ingredient(id,name,unit) VALUES(?,?,'GRAM')",ingredient,"Original "+ingredient);
            for(var recipe:java.util.List.of(recipe1,recipe2)) {
                execute(c,"INSERT INTO app.recipe(id,category_id,name,description,base_servings) SELECT ?,id,?,'Preparation',1 FROM app.category ORDER BY id LIMIT 1",recipe,"Recipe "+recipe);
                execute(c,"INSERT INTO app.recipe_ingredient(recipe_id,ingredient_id,quantity) VALUES(?,?,?)",recipe,ingredient,recipe.equals(recipe1)?10:20);
            }
            execute(c,"INSERT INTO app.course(id,chef_profile_id,category_id,title,description,start_date,start_time,recurrence_days,session_count,status) SELECT ?,cp.id,c.id,?,'Test',current_date+20,'12:00',7,2,'PUBLISHED' FROM app.chef_profile cp JOIN app.account a ON a.id=cp.account_id CROSS JOIN app.category c WHERE a.username='marta.rossi' ORDER BY c.id LIMIT 1",course,"Course "+course.toString().substring(0, 16));
            int sequence=0;
            for(var session:java.util.List.of(first,second)) {
                sequence++;
                execute(c,"INSERT INTO app.course_session(id,course_id,sequence_number,scheduled_at,attendance_closes_at,mode,status,duration_minutes,venue,capacity) VALUES(?,?,?,(current_timestamp AT TIME ZONE 'Europe/Rome')+(?*interval '1 day'),(current_timestamp AT TIME ZONE 'Europe/Rome')+(?*interval '1 day'),'PRACTICAL','SCHEDULED',60,'Test lab',10)",session,course,sequence,day+sequence,day+sequence-1);
                for(var recipe:java.util.List.of(recipe1,recipe2))execute(c,"INSERT INTO app.session_recipe(session_id,recipe_id) VALUES(?,?)",session,recipe);
            }
            execute(c,"INSERT INTO app.enrollment(id,participant_profile_id,course_id,status) SELECT ?,p.id,?,'ACTIVE' FROM app.participant_profile p JOIN app.account a ON a.id=p.account_id WHERE a.username='luca.bianchi'",enrollment,course);
            for(var session:java.util.List.of(first,second))execute(c,"INSERT INTO app.practical_attendance(enrollment_id,session_id,status) VALUES(?,?,'CONFIRMED')",enrollment,session);
        }
        return new Data(ingredient,first,second,recipe2,course);
    }
    private static Connection admin() throws SQLException{return DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());}
    private static void execute(Connection c,String sql,Object... args) throws SQLException {
        try(var st=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)st.setObject(i+1,args[i]);st.execute();}
    }
    private record Data(UUID ingredient,UUID first,UUID second,UUID recipe,UUID course) {}
}
