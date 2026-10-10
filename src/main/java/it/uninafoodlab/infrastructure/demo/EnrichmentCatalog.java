package it.uninafoodlab.infrastructure.demo;

import static it.uninafoodlab.infrastructure.demo.DemoSql.execute;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.BAKING;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.RECIPES;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.VEGETARIAN;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.id;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

final class EnrichmentCatalog {
    private EnrichmentCatalog() {}

    static void insert(Connection connection) throws SQLException {
        var farro = id("ingredient-farro");
        var miele = id("ingredient-miele");
        var sesamo = id("ingredient-sesamo");
        execute(connection, "INSERT INTO app.ingredient(id,name,unit) VALUES (?,'Farro perlato','GRAM')", farro);
        execute(connection, "INSERT INTO app.ingredient(id,name,unit) VALUES (?,'Miele millefiori','GRAM')", miele);
        execute(connection, "INSERT INTO app.ingredient(id,name,unit) VALUES (?,'Semi di sesamo','GRAM')", sesamo);
        insertRecipes(connection);
        insertFarroDoses(connection, farro, sesamo);
        insertBiscuitDoses(connection, miele);
    }

    private static void insertRecipes(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO app.recipe(id,category_id,name,description,base_servings)
                VALUES (?,?,'Insalata tiepida di farro, ceci e verdure',
                    'Un piatto completo per una persona, con cereali, legumi e ortaggi di stagione. Sciacquare il farro e lessarlo in acqua salata per circa 25 minuti. Tagliare le zucchine e rosolarle con metà olio. Unire i ceci scolati e i pomodorini divisi a metà; scaldare per cinque minuti. Scolare il farro, mescolarlo alle verdure e completare con l’olio rimasto e i semi di sesamo tostati. Servire tiepido.',1)
                """, RECIPES[0], VEGETARIAN);
        execute(connection, """
                INSERT INTO app.recipe(id,category_id,name,description,base_servings)
                VALUES (?,?,'Biscotti al limone, mandorle e miele',
                    'Biscotti friabili per una persona, con dosi misurate e cottura breve. Mescolare la farina con le mandorle, poi incorporare l’uovo battuto, il miele e il succo di limone. Lavorare solo fino a ottenere un impasto omogeneo, quindi lasciarlo riposare in frigorifero per 20 minuti. Formare piccoli dischi, sistemarli su carta da forno e cuocere a 180 °C per 12 minuti. Raffreddare completamente prima di conservare in un contenitore chiuso.',1)
                """, RECIPES[1], BAKING);
    }

    private static void insertFarroDoses(Connection connection, UUID farro, UUID sesamo) throws SQLException {
        dose(connection, RECIPES[0], farro, 75);
        dose(connection, RECIPES[0], UUID.fromString("a1000000-0000-4000-8000-000000000036"), 90);
        dose(connection, RECIPES[0], UUID.fromString("a1000000-0000-4000-8000-000000000034"), 80);
        dose(connection, RECIPES[0], UUID.fromString("a1000000-0000-4000-8000-000000000013"), 60);
        dose(connection, RECIPES[0], UUID.fromString("a1000000-0000-4000-8000-000000000007"), 10);
        dose(connection, RECIPES[0], sesamo, 5);
    }

    private static void insertBiscuitDoses(Connection connection, UUID miele) throws SQLException {
        dose(connection, RECIPES[1], UUID.fromString("a1000000-0000-4000-8000-000000000002"), 35);
        dose(connection, RECIPES[1], UUID.fromString("a1000000-0000-4000-8000-000000000024"), 25);
        dose(connection, RECIPES[1], UUID.fromString("a1000000-0000-4000-8000-000000000020"), 30);
        dose(connection, RECIPES[1], UUID.fromString("a1000000-0000-4000-8000-000000000021"), 10);
        dose(connection, RECIPES[1], miele, 15);
    }

    private static void dose(Connection connection, UUID recipe, UUID ingredient, int quantity) throws SQLException {
        execute(connection, "INSERT INTO app.recipe_ingredient(recipe_id,ingredient_id,quantity) VALUES (?,?,?)",
                recipe, ingredient, quantity);
    }
}
