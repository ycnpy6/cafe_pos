package com.cafepos.test;

import com.cafepos.dao.WaitingOrderDAO;
import com.cafepos.db.DatabaseManager;
import com.cafepos.model.Order;
import com.cafepos.model.OrderLine;
import com.cafepos.model.Product;
import com.cafepos.model.Tag;
import com.cafepos.model.WaitingOrderSummary;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verrouille le detail affiche dans le panneau "Commandes en attente" : le
 * barista doit voir les articles (nom, quantite, options) sans avoir a
 * reprendre la commande d'abord. Ajoute suite a une demande explicite pour
 * que la liste d'attente soit exploitable au premier coup d'oeil.
 */
class WaitingOrderDetailTest {
    private final WaitingOrderDAO waitingOrderDAO = new WaitingOrderDAO();

    @BeforeAll
    static void initDb() throws Exception {
        TestDbHelper.initDatabase();
    }

    @BeforeEach
    void reset() throws Exception {
        TestDbHelper.resetData();
    }

    @Test
    void findAllIncludesItemNamesQuantitiesAndOptions() throws Exception {
        int productId = insertProduct("Latte Test", 400);
        int tagGroupId = insertTagGroup("Options Test");
        int tagId = insertTag(tagGroupId, "Sans sucre", 0);

        Product product = new Product(productId, "Latte Test", 400, 0, 1, 0, true);
        Tag tag = new Tag(tagId, tagGroupId, "Sans sucre", 0);

        Order order = new Order();
        order.addLine(new OrderLine(product, 2, List.of(tag)));

        int waitingId = waitingOrderDAO.save(order);
        assertTrue(waitingId > 0, "la commande en attente doit s'enregistrer");

        List<WaitingOrderSummary> summaries = waitingOrderDAO.findAll();
        WaitingOrderSummary found = summaries.stream()
                .filter(s -> s.id() == waitingId)
                .findFirst()
                .orElse(null);

        assertTrue(found != null, "la commande en attente doit apparaitre dans findAll()");
        assertEquals(1, found.lineCount());
        assertTrue(found.itemsSummary().contains("2x Latte Test"),
                "le detail doit montrer la quantite et le nom du produit: " + found.itemsSummary());
        assertTrue(found.itemsSummary().contains("Sans sucre"),
                "le detail doit montrer les options choisies: " + found.itemsSummary());
    }

    @Test
    void findAllHandlesOrderWithoutTagsGracefully() throws Exception {
        int productId = insertProduct("Croissant Test", 150);
        Product product = new Product(productId, "Croissant Test", 150, 0, 1, 0, true);

        Order order = new Order();
        order.addLine(new OrderLine(product, 1, List.of()));

        int waitingId = waitingOrderDAO.save(order);
        List<WaitingOrderSummary> summaries = waitingOrderDAO.findAll();
        WaitingOrderSummary found = summaries.stream()
                .filter(s -> s.id() == waitingId)
                .findFirst()
                .orElse(null);

        assertTrue(found != null);
        assertEquals("1x Croissant Test", found.itemsSummary());
    }

    private int insertProduct(String name, double price) throws Exception {
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO products (name, price, cost, category_id, stock, active, is_prepared) "
                             + "VALUES (?, ?, 0, 1, 0, 1, 0)",
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setDouble(2, price);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int insertTagGroup(String name) throws Exception {
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO tag_groups (name, multi_select) VALUES (?, 0)",
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int insertTag(int groupId, String name, double priceModifier) throws Exception {
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO tags (group_id, name, price_modifier) VALUES (?, ?, ?)",
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, groupId);
            ps.setString(2, name);
            ps.setDouble(3, priceModifier);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
