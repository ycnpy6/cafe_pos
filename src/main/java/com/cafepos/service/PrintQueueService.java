package com.cafepos.service;

import com.cafepos.dao.PrintQueueDAO;
import com.cafepos.db.DatabaseManager;
import com.cafepos.hardware.PrinterService;
import com.cafepos.model.Order;
import com.cafepos.model.PrintQueueItem;
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
    private final PrinterService printerService = new PrinterService();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private PrintQueueService() {
    }

    public static PrintQueueService getInstance() {
        return INSTANCE;
    }

    public void enqueueReceipt(Connection conn, Order order, int orderId, double remainingBalance) throws Exception {
        String payload = printerService.buildReceiptPayload(order, remainingBalance);
        printQueueDAO.insert(conn, orderId, "RECEIPT", payload);
    }

    public boolean requeueLastReceipt() {
        try {
            var item = printQueueDAO.findLatestItem();
            if (item == null || item.payload() == null || item.payload().isBlank()) {
                return false;
            }
            try (Connection conn = DatabaseManager.openConnection()) {
                printQueueDAO.insert(conn, item.orderId(), "RECEIPT", item.payload());
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
            String payload = printQueueDAO.findLatestPayloadByOrder(orderId);
            if (payload == null || payload.isBlank()) {
                return false;
            }
            try (Connection conn = DatabaseManager.openConnection()) {
                printQueueDAO.insert(conn, orderId, "RECEIPT", payload);
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
