package com.cafepos.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BackupService {
    private static final String BACKUP_PREFIX = "cafepos_";
    private static final String BACKUP_SUFFIX = ".db";
    private static final String PENDING_RESTORE_FILE = "cafepos_restore_pending.db";
    private static final int MAX_BACKUPS = 30;

    public Path runBackup() throws IOException {
        return runBackup(getDefaultBackupDir());
    }

    public Path runBackup(Path backupDir) throws IOException {
        Path dbPath = getDbPath();
        if (!Files.exists(dbPath)) {
            throw new IOException("Base de donnees introuvable: " + dbPath);
        }
        Files.createDirectories(backupDir);
        String name = BACKUP_PREFIX + LocalDate.now() + BACKUP_SUFFIX;
        Path target = backupDir.resolve(name);
        Files.copy(dbPath, target, StandardCopyOption.REPLACE_EXISTING);
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
