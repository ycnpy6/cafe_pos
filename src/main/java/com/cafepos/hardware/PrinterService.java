package com.cafepos.hardware;

import com.cafepos.dao.SettingsDAO;
import com.cafepos.model.Order;
import com.cafepos.model.OrderLine;
import com.cafepos.model.PaymentType;
import com.cafepos.util.FormatUtils;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class PrinterService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String PRINTER_KEY = "printer.name";
    private static final int LINE_WIDTH = 42;

    private final SettingsDAO settingsDAO = new SettingsDAO();

    public void printReceipt(Order order, double remainingBalance) throws Exception {
        String payload = buildReceiptPayload(order, remainingBalance);
        printPayload(payload);
    }

    public void printTestTicket() throws Exception {
        byte[] data = buildTestReceipt();
        String payload = Base64.getEncoder().encodeToString(data);
        printPayload(payload);
    }

    public String buildReceiptPayload(Order order, double remainingBalance) {
        byte[] data = buildReceipt(order, remainingBalance);
        return Base64.getEncoder().encodeToString(data);
    }

    public void printPayload(String payload) throws Exception {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Payload vide");
        }
        try {
            PrintService service = findPrinter();
            if (service == null) {
                throw new IllegalStateException("Imprimante non disponible");
            }
            byte[] data = Base64.getDecoder().decode(payload);
            DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
            Doc doc = new SimpleDoc(data, flavor, null);
            DocPrintJob job = service.createPrintJob();
            job.print(doc, null);
        } catch (Exception ex) {
            logError(ex);
            throw ex;
        }
    }

    private PrintService findPrinter() {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        String saved = null;
        try {
            saved = settingsDAO.getValue(PRINTER_KEY);
        } catch (Exception ignored) {
            // Pas bloquant.
        }
        if (saved != null) {
            for (PrintService service : services) {
                if (service.getName().equalsIgnoreCase(saved)) {
                    return service;
                }
            }
        }
        for (PrintService service : services) {
            String name = service.getName().toLowerCase();
            if (name.contains("rongta") || name.contains("generic")) {
                return service;
            }
        }
        return null;
    }

    public java.util.List<String> getPrinterNames() {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        java.util.List<String> names = new java.util.ArrayList<>();
        for (PrintService service : services) {
            names.add(service.getName());
        }
        return names;
    }

    private byte[] buildReceipt(Order order, double remainingBalance) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        appendCommand(out, 0x1B, 0x40); // ESC @
        setAlign(out, 1);
        setBold(out, true);
        appendLine(out, "American Institute Cafe");
        setBold(out, false);
        appendLine(out, "Alger");
        setAlign(out, 0);
        appendLine(out, repeat('-', LINE_WIDTH));
        for (OrderLine line : order.getLines()) {
            String left = line.getProduct().getName() + " x" + line.getQuantity();
            String right = FormatUtils.formatMoney(line.getLineTotal());
            appendLine(out, leftRight(left, right));
            if (line.getTags() != null && !line.getTags().isEmpty()) {
                for (com.cafepos.model.Tag tag : line.getTags()) {
                    String sign = tag.getPriceModifier() >= 0 ? "+" : "";
                    appendLine(out, "  + " + tag.getName() + " " + sign
                            + FormatUtils.formatMoney(Math.abs(tag.getPriceModifier())));
                }
            }
        }
        appendLine(out, repeat('-', LINE_WIDTH));
        setBold(out, true);
        appendLine(out, leftRight("TOTAL", FormatUtils.formatMoney(order.getTotal())));
        setBold(out, false);
        PaymentType type = order.getPaymentType();
        appendLine(out, leftRight("Paiement", type == null ? "ESPECES" : type.name()));
        if (type == PaymentType.PREPAYE) {
            appendLine(out, leftRight("Solde restant", FormatUtils.formatMoney(remainingBalance)));
        }
        appendLine(out, repeat('-', LINE_WIDTH));
        setAlign(out, 1);
        appendLine(out, "Merci de votre visite !");
        appendLine(out, LocalDateTime.now().format(DATE_FORMAT));
        setAlign(out, 0);
        appendLine(out, "");
        appendLine(out, "");
        appendLine(out, "");
        appendLine(out, "");
        // Coupe papier GS V 0 (si supportee par l'imprimante).
        appendCommand(out, 0x1D, 0x56, 0x00);
        return out.toByteArray();
    }

    private byte[] buildTestReceipt() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        appendCommand(out, 0x1B, 0x40);
        setAlign(out, 1);
        setBold(out, true);
        appendLine(out, "Test Impression");
        setBold(out, false);
        appendLine(out, "CafePOS");
        appendLine(out, repeat('-', LINE_WIDTH));
        appendLine(out, leftRight("Article", FormatUtils.formatMoney(0)));
        appendLine(out, repeat('-', LINE_WIDTH));
        appendLine(out, LocalDateTime.now().format(DATE_FORMAT));
        appendLine(out, "");
        appendLine(out, "");
        appendCommand(out, 0x1D, 0x56, 0x00);
        return out.toByteArray();
    }

    private void appendLine(ByteArrayOutputStream out, String text) {
        byte[] bytes = (text + "\n").getBytes(StandardCharsets.UTF_8);
        out.writeBytes(bytes);
    }

    private void appendCommand(ByteArrayOutputStream out, int... bytes) {
        for (int value : bytes) {
            out.write(value);
        }
    }

    private void setAlign(ByteArrayOutputStream out, int mode) {
        appendCommand(out, 0x1B, 0x61, mode); // ESC a n
    }

    private void setBold(ByteArrayOutputStream out, boolean enabled) {
        appendCommand(out, 0x1B, 0x45, enabled ? 1 : 0); // ESC E n
    }

    private String repeat(char ch, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(ch);
        }
        return sb.toString();
    }

    private String leftRight(String left, String right) {
        String safeLeft = left == null ? "" : left;
        String safeRight = right == null ? "" : right;
        if (safeLeft.length() + safeRight.length() >= LINE_WIDTH) {
            return safeLeft + " " + safeRight;
        }
        int spaces = LINE_WIDTH - safeLeft.length() - safeRight.length();
        StringBuilder sb = new StringBuilder(LINE_WIDTH);
        sb.append(safeLeft);
        for (int i = 0; i < spaces; i++) {
            sb.append(' ');
        }
        sb.append(safeRight);
        return sb.toString();
    }

    private void logError(Exception ex) {
        try {
            Path logPath = getLogPath();
            Files.createDirectories(logPath.getParent());
            String line = LocalDateTime.now().format(DATE_FORMAT) + " - " + ex.getMessage() + "\n";
            Files.writeString(logPath, line, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // Pas d'echec si le journal ne peut pas etre ecrit.
        }
    }

    private Path getLogPath() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            String userHome = System.getProperty("user.home");
            return Paths.get(userHome, ".CafePOS", "logs", "print_errors.log");
        }
        return Paths.get(appData, "CafePOS", "logs", "print_errors.log");
    }
}
