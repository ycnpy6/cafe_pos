package com.cafepos.service;

import com.cafepos.dao.AccountTransactionDAO;
import com.cafepos.dao.CustomerDAO;
import com.cafepos.dao.OrderDAO;
import com.cafepos.dao.ProductDAO;
import com.cafepos.dao.StockMovementDAO;
import com.cafepos.db.DatabaseManager;
import com.cafepos.model.Customer;
import com.cafepos.model.Order;
import com.cafepos.model.OrderLine;
import com.cafepos.model.PaymentType;
import com.cafepos.model.User;

public class OrderService {
    private final OrderDAO orderDAO = new OrderDAO();
    private final StockMovementDAO stockMovementDAO = new StockMovementDAO();
    private final AccountTransactionDAO accountTransactionDAO = new AccountTransactionDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final ProductDAO productDAO = new ProductDAO();

    public int saveOrder(Order order, PaymentType paymentType) throws Exception {
        User user = SessionManager.getCurrentUser();
        int userId = user == null ? 0 : user.getId();
        Integer workPeriodId = SessionManager.getCurrentWorkPeriodId();
        order.setPaymentType(paymentType);

        if (paymentType == PaymentType.PREPAYE) {
            Customer customer = order.getCustomer();
            if (customer == null) {
                throw new IllegalStateException("Client non charge");
            }
        }

        try (java.sql.Connection conn = DatabaseManager.openConnection()) {
            conn.setAutoCommit(false);
            for (OrderLine line : order.getLines()) {
                int available = productDAO.getStockById(conn, line.getProduct().getId());
                if (available < line.getQuantity()) {
                    conn.rollback();
                    throw new IllegalStateException("Stock insuffisant pour " + line.getProduct().getName());
                }
            }
            int orderId = orderDAO.insertOrder(conn, order, userId, workPeriodId);
            for (OrderLine line : order.getLines()) {
                productDAO.decrementStock(conn, line.getProduct().getId(), line.getQuantity());
                stockMovementDAO.insertMovement(conn, line.getProduct().getId(), -line.getQuantity(), "Vente");
            }

            if (paymentType == PaymentType.PREPAYE) {
                Customer customer = customerDAO.findByCardUid(order.getCustomer().getCardUid());
                if (customer == null) {
                    conn.rollback();
                    throw new IllegalStateException("Client introuvable");
                }
                if (customer.getBalance() < order.getTotal()) {
                    conn.rollback();
                    throw new IllegalStateException("Solde insuffisant");
                }
                double newBalance = customer.getBalance() - order.getTotal();
                customerDAO.updateBalance(conn, customer.getId(), newBalance);
                accountTransactionDAO.insertTransaction(conn, customer.getId(), -order.getTotal(), "Vente POS", userId);
            }
            conn.commit();
            return orderId;
        } catch (Exception ex) {
            throw ex;
        }
    }
}
