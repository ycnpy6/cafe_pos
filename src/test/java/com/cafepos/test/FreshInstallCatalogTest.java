package com.cafepos.test;

import com.cafepos.db.DatabaseManager;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verrouille le comportement de seed sur une installation reellement neuve
 * (base vide, comme sur la machine du client). Regression trouvee lors d'une
 * simulation de charge : le bloc "nouveaux produits" en fin de schema.sql
 * s'executait avant seed.sql (categories pas encore creees), ce qui faisait
 * sauter tout seed.sql (le catalogue reel, ~90 produits avec leurs vrais
 * prix) et laissait la quasi-totalite du menu a 0 DA sur chaque nouvelle
 * installation.
 *
 * Reproduit la sequence d'init de DatabaseManager.initialize() sur une
 * connexion isolee (via reflexion sur ses methodes privees) plutot que de
 * piloter le singleton DatabaseManager lui-meme : ce dernier est partage par
 * tous les tests de la JVM (via TestDbHelper), le reinitialiser ici
 * polluerait les autres classes de test qui s'executent dans le meme fork
 * Surefire.
 */
class FreshInstallCatalogTest {

    @Test
    void freshInstallSeedsRealPricesWithoutDuplicates() throws Exception {
        Path tempDir = Files.createTempDirectory("cafepos-fresh-install-test");
        Path dbPath = tempDir.resolve("cafepos.db");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            // Reproduit uniquement le chemin categories/produits de
            // DatabaseManager.initialize()+seedIfEmpty() ; on evite
            // deliberement seedIfEmpty() dans son ensemble, qui declenche
            // aussi l'import clients via CustomerImporter — celui-ci passe
            // par le pool singleton partage de DatabaseManager (pas par
            // cette connexion isolee), ce qui ecrirait dans la base d'un
            // AUTRE test s'executant dans le meme fork Surefire.
            invokeStatic("applyPragmas", conn);
            invokeStatic("runSchema", conn);
            invokeStatic("normalizeCategories", conn);

            boolean categoriesEmpty = (boolean) invokeStaticReturning("isTableEmpty", conn, "categories");
            if (categoriesEmpty) {
                String seedSql = (String) invokeStaticReturning("readResourceText", "/db/seed.sql");
                invokeStatic("executeScript", conn, seedSql);
            }
            invokeStatic("ensureRequiredCategories", conn);
            invokeStatic("ensureRequiredProducts", conn);
            invokeStatic("cleanupOrphanReferences", conn);

            assertKnownPricesAreSet(conn);
            assertNoDuplicateProductNames(conn);
            assertCategoriesAreAssigned(conn);
        }
    }

    private static void invokeStatic(String methodName, Connection conn) throws Exception {
        invokeStaticReturning(methodName, conn);
    }

    private static void invokeStatic(String methodName, Connection conn, String extra) throws Exception {
        invokeStaticReturning(methodName, conn, extra);
    }

    private static Object invokeStaticReturning(String methodName, Object... args) throws Exception {
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i] instanceof Connection ? Connection.class : String.class;
        }
        Method method = DatabaseManager.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception e) {
                throw e;
            }
            throw ex;
        }
    }

    private void assertKnownPricesAreSet(Connection conn) throws Exception {
        String[] namesWithExpectedNonZeroPrice = {"espresso", "cappuccino", "latte", "americano", "bottle of water"};
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT price FROM products WHERE LOWER(name) = ?")) {
            for (String name : namesWithExpectedNonZeroPrice) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "produit manquant sur installation neuve: " + name);
                    double price = rs.getDouble(1);
                    assertTrue(price > 0, "prix incorrect (0 DA) pour '" + name + "' sur installation neuve");
                }
            }
        }
    }

    private void assertNoDuplicateProductNames(Connection conn) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT LOWER(name), COUNT(*) c FROM products GROUP BY LOWER(name) HAVING c > 1")) {
            StringBuilder duplicates = new StringBuilder();
            while (rs.next()) {
                duplicates.append(rs.getString(1)).append(" (x").append(rs.getInt(2)).append(") ");
            }
            assertEquals("", duplicates.toString(), "produits en double sur installation neuve: " + duplicates);
        }
    }

    private void assertCategoriesAreAssigned(Connection conn) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM products WHERE category_id IS NULL")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "des produits ont perdu leur categorie sur installation neuve");
        }
    }

    /**
     * Le client importe sa propre liste de clients (Clients > Importer CSV) ;
     * l'installeur ne doit plus embarquer de liste de clients par defaut
     * (l'ancien fichier contenait de vraies coordonnees clients d'un
     * deploiement precedent). seedCustomersIfEmpty() attrape deja
     * IllegalArgumentException quand la ressource est absente (voir
     * DatabaseManager), donc l'absence du fichier suffit a garantir une base
     * clients vide sur une installation neuve.
     */
    @Test
    void noBundledCustomerSeedFileShips() {
        assertTrue(DatabaseManager.class.getResource("/db/customers_seed.csv") == null,
                "un fichier de seed clients est encore embarque dans l'installeur");
    }
}
