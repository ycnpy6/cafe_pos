package com.cafepos.service;

import com.cafepos.dao.PrintQueueDAO;
import com.cafepos.dao.AccountTransactionDAO;
import com.cafepos.dao.OrderDAO;
import com.cafepos.db.DatabaseManager;
import com.cafepos.hardware.PrinterService;
import com.cafepos.model.Order;
import com.cafepos.model.PrintQueueItem;
import com.cafepos.model.PrintTicketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PrintQueueService {
    private static final Logger LOG = LoggerFactory.getLogger(PrintQueueService.class);
    private static final int BATCH_SIZE = 5;
    private static final PrintQueueService INSTANCE = new PrintQueueService();

    private final PrintQueueDAO printQueueDAO = new PrintQueueDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final AccountTransactionDAO accountTransactionDAO = new AccountTransactionDAO();
    private final PrinterService printerService = new PrinterService();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private PrintQueueService() {
    }

    public static PrintQueueService getInstance() {
        return INSTANCE;
    }

    public void enqueueReceipt(Connection conn, Order order, int orderId, double remainingBalance) throws Exception {
        enqueueTicket(conn, order, orderId, remainingBalance, PrintTicketType.RECEIPT);
    }

    public void enqueueInvoice(Connection conn, Order order, int orderId, double remainingBalance) throws Exception {
        enqueueTicket(conn, order, orderId, remainingBalance, PrintTicketType.INVOICE);
    }

    private void enqueueTicket(Connection conn, Order order, int orderId, double remainingBalance,
                               PrintTicketType ticketType) throws Exception {
        String payload = printerService.buildTicketPayload(order, orderId, remainingBalance, ticketType);
        printQueueDAO.insert(conn, orderId, ticketType.name(), payload);
    }

    public boolean requeueLastReceipt() {
        try {
            var item = printQueueDAO.findLatestItem();
            if (item == null || item.payload() == null || item.payload().isBlank()) {
                return false;
            }
            try (Connection conn = DatabaseManager.openConnection()) {
                String type = item.ticketType() == null ? PrintTicketType.RECEIPT.name() : item.ticketType();
                printQueueDAO.insert(conn, item.orderId(), type, item.payload());
            }
            dispatchAsync();
            return true;
        } catch (Exception ex) {
            LOG.error("Erreur reimpression", ex);
            return false;
        }
    }

    public boolean requeueReceiptForOrder(int orderId) {
        try {
            String payload = printQueueDAO.findLatestPayloadByOrderAndType(orderId, PrintTicketType.RECEIPT.name());
            if (payload == null || payload.isBlank()) {
                return false;
            }
            try (Connection conn = DatabaseManager.openConnection()) {
                printQueueDAO.insert(conn, orderId, PrintTicketType.RECEIPT.name(), payload);
            }
            dispatchAsync();
            return true;
        } catch (Exception ex) {
            LOG.error("Erreur reimpression commande", ex);
            return false;
        }
    }

    public void dispatchAsync() {
        Thread thread = new Thread(this::dispatchPendingSafe, "print-queue-dispatch");
        thread.setDaemon(true);
        thread.start();
    }

    public boolean queueInvoiceForOrder(int orderId) {
        try (Connection conn = DatabaseManager.openConnection()) {
            Order order = orderDAO.findOrderWithLines(conn, orderId);
            if (order == null) {
                return false;
            }
            double remainingBalance = resolveRemainingBalance(conn, orderId, order);
            enqueueInvoice(conn, order, orderId, remainingBalance);
            dispatchAsync();
            return true;
        } catch (Exception ex) {
            LOG.error("Erreur impression facture", ex);
            return false;
        }
    }

    private double resolveRemainingBalance(Connection conn, int orderId, Order order) throws Exception {
        if (order == null || order.getPaymentType() == null) {
            return -1;
        }
        switch (order.getPaymentType()) {
            case PREPAYE, MIXTE -> {
                Double balance = accountTransactionDAO.findBalanceAfterOrder(conn, orderId);
                return balance == null ? -1 : balance;
            }
            default -> {
                return -1;
            }
        }
    }

    public void dispatchPendingSafe() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            dispatchPending();
        } catch (Exception ex) {
            LOG.error("Erreur impression file", ex);
        } finally {
            running.set(false);
        }
    }

    private void dispatchPending() throws Exception {
        List<PrintQueueItem> items = printQueueDAO.findPending(BATCH_SIZE);
        for (PrintQueueItem item : items) {
            if (item.payload() == null || item.payload().isBlank()) {
                printQueueDAO.incrementAttempts(item.id());
                continue;
            }
            try {
                printerService.printPayload(item.payload());
                printQueueDAO.markPrinted(item.id());
            } catch (Exception ex) {
                printQueueDAO.incrementAttempts(item.id());
                LOG.warn("Impression echouee pour ticket {}", item.id(), ex);
            }
        }
    }

    public int countPendingSafe() {
        try {
            return printQueueDAO.countPending();
        } catch (Exception ex) {
            LOG.error("Erreur comptage file", ex);
            return 0;
        }
    }
}
