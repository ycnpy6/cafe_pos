package com.cafepos.service;

import com.cafepos.dao.AccountTransactionDAO;
import com.cafepos.dao.CustomerDAO;
import com.cafepos.dao.IngredientDAO;
import com.cafepos.dao.IngredientMovementDAO;
import com.cafepos.dao.OrderDAO;
import com.cafepos.dao.ProductIngredientDAO;
import com.cafepos.dao.ProductDAO;
import com.cafepos.dao.StockMovementDAO;
import com.cafepos.db.DatabaseManager;
import com.cafepos.model.Customer;
import com.cafepos.model.Ingredient;
import com.cafepos.model.Order;
import com.cafepos.model.OrderLine;
import com.cafepos.model.PaymentType;
import com.cafepos.model.ProductIngredientUsage;
import com.cafepos.model.RefundLineSelection;
import com.cafepos.model.RefundableOrderLine;
import com.cafepos.model.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderService {
    private final OrderDAO orderDAO = new OrderDAO();
    private final StockMovementDAO stockMovementDAO = new StockMovementDAO();
    private final AccountTransactionDAO accountTransactionDAO = new AccountTransactionDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final IngredientDAO ingredientDAO = new IngredientDAO();
    private final ProductIngredientDAO productIngredientDAO = new ProductIngredientDAO();
    private final IngredientMovementDAO ingredientMovementDAO = new IngredientMovementDAO();
    private final PrintQueueService printQueueService = PrintQueueService.getInstance();

    public int saveOrder(Order order, PaymentType paymentType) throws Exception {
        User user = SessionManager.getCurrentUser();
        Integer actorUserId = user == null ? null : user.getId();
        int userId = actorUserId == null ? 0 : actorUserId;
        Integer workPeriodId = SessionManager.getCurrentWorkPeriodId();
        order.setPaymentType(paymentType);

        double remainingBalance = 0;

        switch (paymentType) {
            case ESPECES -> {
                order.setCashAmount(order.getTotal());
                order.setPrepaidAmount(0);
            }
            case PREPAYE -> {
                order.setCashAmount(0);
                if (order.getPrepaidAmount() <= 0) {
                    order.setPrepaidAmount(order.getTotal());
                }
            }
            case MIXTE -> {
                if (order.getCashAmount() <= 0 || order.getPrepaidAmount() <= 0) {
                    throw new IllegalStateException("Montants mixte invalides");
                }
                double totalPaid = order.getCashAmount() + order.getPrepaidAmount();
                if (totalPaid + 0.001 < order.getTotal()) {
                    throw new IllegalStateException("Montant insuffisant");
                }
            }
        }

        if (paymentType == PaymentType.PREPAYE || paymentType == PaymentType.MIXTE) {
            Customer customer = order.getCustomer();
            if (customer == null) {
                throw new IllegalStateException("Client non charge");
            }
        }

        try (java.sql.Connection conn = DatabaseManager.openConnection()) {
            conn.setAutoCommit(false);

            Map<Integer, Double> requiredIngredients = new HashMap<>();
            Map<Integer, Ingredient> ingredientById = new HashMap<>();

            for (OrderLine line : order.getLines()) {
                int available = productDAO.getStockById(conn, line.getProduct().getId());
                if (available < line.getQuantity()) {
                    conn.rollback();
                    throw new IllegalStateException("Stock insuffisant pour " + line.getProduct().getName());
                }

                List<ProductIngredientUsage> recipeLines =
                        productIngredientDAO.findRecipeByProduct(conn, line.getProduct().getId());
                for (ProductIngredientUsage usage : recipeLines) {
                    double required = usage.quantityPerProduct() * line.getQuantity();
                    if (required <= 0) {
                        continue;
                    }
                    requiredIngredients.merge(usage.ingredientId(), required, Double::sum);
                }
            }

            for (Map.Entry<Integer, Double> entry : requiredIngredients.entrySet()) {
                Ingredient ingredient = ingredientDAO.findById(conn, entry.getKey());
                if (ingredient == null || !ingredient.isActive()) {
                    conn.rollback();
                    throw new IllegalStateException("Ingredient indisponible: #" + entry.getKey());
                }
                ingredientById.put(entry.getKey(), ingredient);
                if (ingredient.getStockQuantity() + 0.0001 < entry.getValue()) {
                    conn.rollback();
                    throw new IllegalStateException("Stock ingredient insuffisant pour " + ingredient.getName());
                }
            }

            int orderId = orderDAO.insertOrder(conn, order, userId, workPeriodId);
            for (OrderLine line : order.getLines()) {
                productDAO.decrementStock(conn, line.getProduct().getId(), line.getQuantity());
                stockMovementDAO.insertMovement(conn, line.getProduct().getId(), -line.getQuantity(), "Vente");
            }

            for (Map.Entry<Integer, Double> entry : requiredIngredients.entrySet()) {
                int ingredientId = entry.getKey();
                double usedQuantity = entry.getValue();
                Ingredient ingredient = ingredientById.get(ingredientId);
                double unitCost = ingredient == null ? 0 : ingredient.getUnitCost();

                ingredientDAO.adjustStock(conn, ingredientId, -usedQuantity);
                ingredientMovementDAO.insertMovement(
                        conn,
                        ingredientId,
                        -usedQuantity,
                        "SALE",
                        unitCost,
                        usedQuantity * unitCost,
                        workPeriodId,
                        orderId,
                        actorUserId
                );
            }

            if (paymentType == PaymentType.PREPAYE || paymentType == PaymentType.MIXTE) {
                Customer customer = customerDAO.findByCardUid(order.getCustomer().getCardUid());
                if (customer == null) {
                    conn.rollback();
                    throw new IllegalStateException("Client introuvable");
                }
                double prepaid = paymentType == PaymentType.MIXTE ? order.getPrepaidAmount() : order.getTotal();
                if (prepaid <= 0) {
                    conn.rollback();
                    throw new IllegalStateException("Montant prepaid invalide");
                }
                if (customer.getBalance() < prepaid) {
                    conn.rollback();
                    throw new IllegalStateException("Solde insuffisant");
                }
                double newBalance = customer.getBalance() - prepaid;
                customerDAO.updateBalance(conn, customer.getId(), newBalance);
                accountTransactionDAO.insertTransaction(conn, customer.getId(), -prepaid, "Vente POS", userId,
                    newBalance, orderId);
                remainingBalance = newBalance;
            }

            // Ajout file impression dans la meme transaction.
            printQueueService.enqueueReceipt(conn, order, orderId, remainingBalance);
            conn.commit();
            return orderId;
        } catch (Exception ex) {
            throw ex;
        }
    }

    public void refundOrder(int orderId, List<RefundLineSelection> lines, boolean toRfid, String reason)
            throws Exception {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalStateException("Aucune ligne selectionnee");
        }

        User user = SessionManager.getCurrentUser();
        Integer userId = user == null ? null : user.getId();
        int userIdForLog = userId == null ? 0 : userId;
        Integer workPeriodId = SessionManager.getCurrentWorkPeriodId();

        try (java.sql.Connection conn = DatabaseManager.openConnection()) {
            conn.setAutoCommit(false);

            List<RefundableOrderLine> refundable = orderDAO.findRefundableLines(conn, orderId);
            Map<Integer, RefundableOrderLine> refundableByLineId = new HashMap<>();
            for (RefundableOrderLine line : refundable) {
                refundableByLineId.put(line.orderLineId(), line);
            }

            double totalRefund = 0;
            for (RefundLineSelection selection : lines) {
                RefundableOrderLine available = refundableByLineId.get(selection.orderLineId());
                if (available == null) {
                    conn.rollback();
                    throw new IllegalStateException("Ligne introuvable");
                }
                if (selection.quantity() <= 0 || selection.quantity() > available.refundableQuantity()) {
                    conn.rollback();
                    throw new IllegalStateException("Quantite de remboursement invalide");
                }
                if (selection.productId() != available.productId()) {
                    conn.rollback();
                    throw new IllegalStateException("Produit de ligne invalide");
                }
                totalRefund += available.unitPrice() * selection.quantity();
            }

            if (totalRefund <= 0) {
                conn.rollback();
                throw new IllegalStateException("Montant de remboursement invalide");
            }

            String method = toRfid ? "RFID" : "ESPECES";
            int refundId = orderDAO.insertRefund(conn, orderId,
                    reason == null || reason.isBlank() ? null : reason.trim(),
                    method,
                    totalRefund,
                    userId);

            if (refundId <= 0) {
                conn.rollback();
                throw new IllegalStateException("Creation remboursement impossible");
            }

            Map<Integer, Double> restoredIngredients = new HashMap<>();

            for (RefundLineSelection selection : lines) {
                RefundableOrderLine available = refundableByLineId.get(selection.orderLineId());
                double lineTotal = available.unitPrice() * selection.quantity();
                orderDAO.insertRefundLine(conn, refundId, selection.orderLineId(), selection.quantity(), lineTotal);
                productDAO.adjustStock(conn, selection.productId(), selection.quantity());
                stockMovementDAO.insertMovement(conn, selection.productId(), selection.quantity(), "Remboursement");

                List<ProductIngredientUsage> recipeLines =
                        productIngredientDAO.findRecipeByProduct(conn, selection.productId());
                for (ProductIngredientUsage usage : recipeLines) {
                    double restoredQty = usage.quantityPerProduct() * selection.quantity();
                    if (restoredQty <= 0) {
                        continue;
                    }
                    restoredIngredients.merge(usage.ingredientId(), restoredQty, Double::sum);
                }
            }

            for (Map.Entry<Integer, Double> entry : restoredIngredients.entrySet()) {
                int ingredientId = entry.getKey();
                double restoredQty = entry.getValue();
                Ingredient ingredient = ingredientDAO.findById(conn, ingredientId);
                if (ingredient == null) {
                    continue;
                }
                double unitCost = ingredient.getUnitCost();
                ingredientDAO.adjustStock(conn, ingredientId, restoredQty);
                ingredientMovementDAO.insertMovement(
                        conn,
                        ingredientId,
                        restoredQty,
                        "REFUND",
                        unitCost,
                        -(restoredQty * unitCost),
                        workPeriodId,
                        orderId,
                        userId
                );
            }

            if (toRfid) {
                Integer customerId = orderDAO.findOrderCustomerId(conn, orderId);
                if (customerId == null) {
                    conn.rollback();
                    throw new IllegalStateException("Commande sans client RFID");
                }
                Customer customer = customerDAO.findById(conn, customerId);
                if (customer == null) {
                    conn.rollback();
                    throw new IllegalStateException("Client introuvable");
                }
                double newBalance = customer.getBalance() + totalRefund;
                customerDAO.updateBalance(conn, customerId, newBalance);
                accountTransactionDAO.insertTransaction(conn, customerId, totalRefund,
                        "Remboursement POS", userIdForLog, newBalance, orderId);
            }

            conn.commit();
        }
    }
}
