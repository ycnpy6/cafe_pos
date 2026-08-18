package com.cafepos.test;

import com.cafepos.dao.SettingsDAO;
import com.cafepos.model.AppAction;
import com.cafepos.model.UserRole;
import com.cafepos.util.ActionAccessManager;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verrouille la logique lue par la table "Actions et acces" des reglages et
 * par ActionAccessManager : sans override en base, chaque action doit
 * refleter son defaut declare dans AppAction ; un override explicite doit
 * etre respecte dans les deux sens (true -> false et false -> true).
 *
 * Design courant (voir AppAction) : le PIN n'est PLUS force par defaut sur
 * les actions manager — un manager deja authentifie (session normale ou
 * fenetre de grace de 5 min via AdminSessionManager apres une etape PIN)
 * n'a pas a resaisir son code a chaque action. Seul le role protege encore
 * ces actions par defaut ; le PIN redevient obligatoire uniquement si
 * explicitement reactive depuis Reglages pour une action donnee. Le stock
 * (consultation + ajustements simples) est accessible au barista sans PIN ;
 * prix/couts/recettes/achats restent geres par des actions MANAGER
 * separees, chacune verifiee a son propre point d'entree.
 *
 * Ajoute suite a un incident ou des lignes "action.pin.*" figees a "false"
 * (contamination hors du code applicatif, aucun chemin de code actuel
 * n'ecrit ces cles en masse) masquaient les defauts pour toutes les actions,
 * faisant apparaitre la table comme entierement decochee.
 */
class ActionAccessPolicyTest {
    private final ActionAccessManager accessManager = new ActionAccessManager();
    private final SettingsDAO settingsDAO = new SettingsDAO();

    @BeforeAll
    static void initDb() throws Exception {
        TestDbHelper.initDatabase();
    }

    @BeforeEach
    void reset() throws Exception {
        TestDbHelper.resetData();
        for (AppAction action : AppAction.values()) {
            settingsDAO.setValue("action.pin." + action.getKey(), "");
            settingsDAO.setValue("action.role." + action.getKey(), "");
        }
    }

    @Test
    void withoutOverrideEachActionReflectsItsDeclaredDefault() throws Exception {
        for (AppAction action : AppAction.values()) {
            assertEquals(action.isDefaultPinRequired(), accessManager.resolvePinRequired(action),
                    "PIN requis par defaut incorrect pour " + action.getKey());
            assertEquals(action.getDefaultRole(), accessManager.resolveRequiredRole(action),
                    "Role par defaut incorrect pour " + action.getKey());
        }
    }

    @Test
    void noActionForcesPinByDefault() {
        long pinRequiredCount = java.util.Arrays.stream(AppAction.values())
                .filter(AppAction::isDefaultPinRequired)
                .count();
        // Le role (deja verifie par ailleurs) protege les actions manager ;
        // le PIN par defaut ne doit plus etre force pour respecter la
        // fenetre de grace de AdminSessionManager. Un retour a "PIN toujours
        // requis" doit etre un choix explicite (case a cocher Reglages), pas
        // le comportement par defaut.
        assertEquals(0, pinRequiredCount, "Une action force le PIN par defaut alors que la fenetre de grace doit s'appliquer");
    }

    @Test
    void staffFacingStockActionsDoNotRequireManagerRole() {
        // Consultation du stock + ajustements simples : le barista doit
        // pouvoir les faire seul, sans manager ni PIN.
        assertEquals(UserRole.BARISTA, AppAction.OPEN_STOCK.getDefaultRole());
        assertFalse(AppAction.OPEN_STOCK.isDefaultPinRequired());
        assertEquals(UserRole.BARISTA, AppAction.ADJUST_STOCK.getDefaultRole());
        assertFalse(AppAction.ADJUST_STOCK.isDefaultPinRequired());
    }

    @Test
    void sensitiveStockActionsStayManagerOnly() {
        // Prix, couts, recettes et achats restent geres par le manager,
        // meme si l'ecran Stock lui-meme est ouvert au barista.
        for (AppAction action : new AppAction[]{
                AppAction.EDIT_PRODUCT_PRICE, AppAction.EDIT_PRODUCT_COST, AppAction.EDIT_RECIPE,
                AppAction.EDIT_INGREDIENTS, AppAction.PURCHASE_INGREDIENT, AppAction.MANAGE_PRODUCTS,
        }) {
            assertEquals(UserRole.MANAGER, action.getDefaultRole(), action.getKey() + " doit rester reserve au manager");
        }
    }

    @Test
    void explicitOverrideCanTurnPinRequirementOff() throws Exception {
        AppAction action = AppAction.EDIT_INGREDIENTS;

        settingsDAO.setValue("action.pin." + action.getKey(), "false");

        assertFalse(accessManager.resolvePinRequired(action),
                "l'override explicite 'false' doit rester respecte (meme si c'est deja le defaut)");
    }

    @Test
    void explicitOverrideCanTurnPinRequirementOn() throws Exception {
        AppAction action = AppAction.EDIT_INGREDIENTS;
        assertFalse(action.isDefaultPinRequired(), "precondition: EDIT_INGREDIENTS n'est plus pin-required par defaut");

        settingsDAO.setValue("action.pin." + action.getKey(), "true");

        assertTrue(accessManager.resolvePinRequired(action),
                "l'override explicite 'true' doit activer le PIN meme si le defaut est false"
                        + " (ex: manager veut reconfirmer avant un achat d'ingredient)");
    }

    @Test
    void explicitRoleOverrideIsRespected() throws Exception {
        AppAction action = AppAction.OPEN_CLIENTS;
        assertEquals(UserRole.BARISTA, action.getDefaultRole());

        settingsDAO.setValue("action.role." + action.getKey(), "MANAGER");

        assertEquals(UserRole.MANAGER, accessManager.resolveRequiredRole(action));
    }
}
