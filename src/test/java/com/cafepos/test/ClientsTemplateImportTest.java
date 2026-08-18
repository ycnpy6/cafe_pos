package com.cafepos.test;

import com.cafepos.util.CustomerImporter;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifie que le modele CSV distribue au client (installer/assets/clients_template.csv)
 * s'importe reellement sans erreur avec le format de colonnes documente dans
 * clients_template_LISEZMOI.txt. Protege contre une derive silencieuse entre
 * le document remis au client et le comportement reel de CustomerImporter.
 */
class ClientsTemplateImportTest {

    @BeforeAll
    static void initDb() throws Exception {
        TestDbHelper.initDatabase();
    }

    @BeforeEach
    void reset() throws Exception {
        TestDbHelper.resetData();
    }

    @Test
    void templateFileImportsCleanly() throws Exception {
        Path template = Paths.get("installer", "assets", "clients_template.csv");
        assertTrue(Files.exists(template), "Modele introuvable: " + template.toAbsolutePath());

        CustomerImporter.ImportResult result = CustomerImporter.importFromFile(template);

        assertEquals(2, result.inserted(), "les 2 clients d'exemple doivent s'importer: " + result.summary());
        assertEquals(0, result.failed(), "aucune erreur attendue: " + result.errors());
    }
}
