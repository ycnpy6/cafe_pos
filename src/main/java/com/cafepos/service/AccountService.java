package com.cafepos.service;

import com.cafepos.dao.AccountTransactionDAO;
import com.cafepos.dao.CustomerDAO;
import com.cafepos.db.DatabaseManager;
import com.cafepos.model.Customer;
import com.cafepos.model.User;
import com.cafepos.util.Money;

public class AccountService {
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final AccountTransactionDAO accountTransactionDAO = new AccountTransactionDAO();

    public Customer topUp(Customer customer, double amount) throws Exception {
        if (customer == null) {
            throw new IllegalArgumentException("Client requis");
        }
        if (!customer.isActive()) {
            throw new IllegalStateException("Client inactif");
        }
        if (amount < 100) {
            throw new IllegalArgumentException("Montant minimum: 100 DZD");
        }
        User user = SessionManager.getCurrentUser();
        int userId = user == null ? 0 : user.getId();
        try (java.sql.Connection conn = DatabaseManager.openConnection()) {
            conn.setAutoCommit(false);
            double newBalance = Money.round2(customer.getBalance() + amount);
            customerDAO.updateBalance(conn, customer.getId(), newBalance);
            accountTransactionDAO.insertTransaction(conn, customer.getId(), amount, "Recharge", userId, newBalance, null);
            conn.commit();
            return new Customer(customer.getId(), customer.getName(), customer.getCardUid(), newBalance, true);
        }
    }
}
