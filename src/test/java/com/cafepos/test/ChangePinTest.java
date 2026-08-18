package com.cafepos.test;

import com.cafepos.dao.UserDAO;
import com.cafepos.model.User;
import com.cafepos.model.UserRole;
import com.cafepos.util.SecurityUtils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verrouille le mecanisme sous-jacent de Reglages > Utilisateurs > "Changer
 * PIN" (seul point d'entree pour changer le PIN du manager/admin) : le
 * nouveau hash remplace bien l'ancien, l'ancien PIN cesse immediatement de
 * fonctionner.
 */
class ChangePinTest {
    private final UserDAO userDAO = new UserDAO();

    @BeforeAll
    static void initDb() throws Exception {
        TestDbHelper.initDatabase();
    }

    @Test
    void changingPinReplacesTheOldOneEntirely() throws Exception {
        User manager = userDAO.findFirstByRole(UserRole.MANAGER);
        assertNotNull(manager, "aucun manager seede pour le test");

        String oldPinHash = manager.getPinHash();
        String newHash = SecurityUtils.sha256Hex("5678");

        userDAO.updatePin(manager.getId(), newHash);

        User byOldPin = userDAO.findByIdAndPin(manager.getId(), oldPinHash);
        assertNull(byOldPin, "l'ancien PIN doit cesser de fonctionner apres changement");

        User byNewPin = userDAO.findByIdAndPin(manager.getId(), newHash);
        assertNotNull(byNewPin, "le nouveau PIN doit fonctionner immediatement");

        // Remet un etat connu pour ne pas perturber d'autres tests partageant la meme base.
        userDAO.updatePin(manager.getId(), oldPinHash);
    }
}
