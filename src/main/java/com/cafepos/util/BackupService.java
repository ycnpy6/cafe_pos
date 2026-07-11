package com.cafepos.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import com.cafepos.db.DatabaseManager;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BackupService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(BackupService.class);
    private static final String BACKUP_PREFIX = "cafepos_";
    private static final String BACKUP_SUFFIX = ".db";
    private static final String PENDING_RESTORE_FILE = "cafepos_restore_pending.db";
    private static final String BACKUP_TARGET_DIR_KEY = "backup.target.dir";
    private static final int MAX_BACKUPS = 30;

    public Path runBackup() throws IOException {
        return runBackup(getDefaultBackupDir());
    }

    /**
     * Sauvegarde planifiee (00h05) : toujours une copie locale, puis un miroir
     * vers le dossier configure dans les reglages (cle USB, partage reseau,
     * dossier Dropbox/OneDrive...). L'indisponibilite du miroir ne fait pas
     * echouer la sauvegarde locale.
     */
    public Path runScheduledBackup() throws IOException {
        Path local = runBackup(getDefaultBackupDir());
        Path mirrorDir = resolveConfiguredBackupDir();
        if (mirrorDir != null && !mirrorDir.equals(getDefaultBackupDir())) {
            try {
                Files.createDirectories(mirrorDir);
                Path mirror = mirrorDir.resolve(local.getFileName());
                Files.copy(local, mirror, StandardCopyOption.REPLACE_EXISTING);
                cleanupOldBackups(mirrorDir);
                LOG.info("Sauvegarde miroir ecrite: {}", mirror.toAbsolutePath());
            } catch (Exception ex) {
                LOG.warn("Miroir de sauvegarde inaccessible ({}), copie locale conservee: {}",
                        mirrorDir, local.toAbsolutePath(), ex);
            }
        }
        return local;
    }

    private Path resolveConfiguredBackupDir() {
        try {
            String value = new com.cafepos.dao.SettingsDAO().getValue(BACKUP_TARGET_DIR_KEY);
            if (value == null || value.isBlank()) {
                return null;
            }
            return Paths.get(value.trim());
        } catch (Exception ex) {
            LOG.warn("Lecture du dossier de sauvegarde configure impossible", ex);
            return null;
        }
    }

    public Path runBackup(Path backupDir) throws IOException {
        Path dbPath = getDbPath();
        if (!Files.exists(dbPath)) {
            throw new IOException("Base de donnees introuvable: " + dbPath);
        }
        Files.createDirectories(backupDir);
        String name = BACKUP_PREFIX + LocalDate.now() + BACKUP_SUFFIX;
        Path target = backupDir.resolve(name);
        // Une copie fichier d'une base en mode WAL perd le contenu du -wal et
        // peut capturer un etat incoherent. VACUUM INTO produit un snapshot
        // transactionnel complet et compacte, sans arreter l'application.
        Files.deleteIfExists(target); // VACUUM INTO exige que la cible n'existe pas.
        String escapedTarget = target.toAbsolutePath().toString().replace("'", "''");
        try (Connection conn = DatabaseManager.openConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("VACUUM INTO '" + escapedTarget + "'");
        } catch (SQLException | IllegalStateException ex) {
            throw new IOException("Echec sauvegarde (VACUUM INTO): " + ex.getMessage(), ex);
        }
        cleanupOldBackups(backupDir);
        return target;
    }

    public Path stageRestore(Path sourceBackupFile) throws IOException {
        if (sourceBackupFile == null || !Files.exists(sourceBackupFile)) {
            throw new IOException("Fichier de sauvegarde introuvable");
        }
        String filename = sourceBackupFile.getFileName().toString().toLowerCase();
        if (!filename.endsWith(BACKUP_SUFFIX)) {
            throw new IOException("Fichier invalide (extension .db requise)");
        }

        Path pendingFile = getDbPath().getParent().resolve(PENDING_RESTORE_FILE);
        Files.createDirectories(pendingFile.getParent());
        Files.copy(sourceBackupFile, pendingFile, StandardCopyOption.REPLACE_EXISTING);
        return pendingFile;
    }

    public static boolean applyPendingRestoreIfAny() throws IOException {
        Path dbPath = resolveDbPath();
        Path pendingFile = dbPath.getParent().resolve(PENDING_RESTORE_FILE);
        if (!Files.exists(pendingFile)) {
            return false;
        }

        Files.createDirectories(dbPath.getParent());
        Files.copy(pendingFile, dbPath, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(pendingFile);
        Files.deleteIfExists(Paths.get(dbPath.toString() + "-wal"));
        Files.deleteIfExists(Paths.get(dbPath.toString() + "-shm"));
        return true;
    }

    private void cleanupOldBackups(Path backupDir) throws IOException {
        try (Stream<Path> stream = Files.list(backupDir)) {
            List<Path> backups = stream
                    .filter(path -> path.getFileName().toString().startsWith(BACKUP_PREFIX))
                    .sorted(Comparator.comparingLong((Path path) -> path.toFile().lastModified()).reversed())
                    .collect(Collectors.toList());
            for (int i = MAX_BACKUPS; i < backups.size(); i++) {
                Files.deleteIfExists(backups.get(i));
            }
        }
    }

    public Path getDefaultBackupDir() {
        return resolveBackupDir();
    }

    private Path getDbPath() {
        return resolveDbPath();
    }

    private static Path resolveDbPath() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            String userHome = System.getProperty("user.home");
            return Paths.get(userHome, ".CafePOS", "data", "cafepos.db");
        }
        return Paths.get(appData, "CafePOS", "data", "cafepos.db");
    }

    private static Path resolveBackupDir() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            String userHome = System.getProperty("user.home");
            return Paths.get(userHome, ".CafePOS", "backups");
        }
        return Paths.get(appData, "CafePOS", "backups");
    }
}
