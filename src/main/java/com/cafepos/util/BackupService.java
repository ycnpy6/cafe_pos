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

public class BackupService {
    public void runBackup() throws IOException {
        Path dbPath = getDbPath();
        if (!Files.exists(dbPath)) {
            return;
        }
        Path backupDir = getBackupDir();
        Files.createDirectories(backupDir);
        String name = "cafepos_" + LocalDate.now() + ".db";
        Path target = backupDir.resolve(name);
        Files.copy(dbPath, target, StandardCopyOption.REPLACE_EXISTING);
        cleanupOldBackups(backupDir);
    }

    private void cleanupOldBackups(Path backupDir) throws IOException {
        List<Path> backups = Files.list(backupDir)
            .filter(path -> path.getFileName().toString().startsWith("cafepos_"))
            .sorted(Comparator.comparingLong((Path path) -> path.toFile().lastModified()).reversed())
            .collect(Collectors.toList());
        for (int i = 30; i < backups.size(); i++) {
            Files.deleteIfExists(backups.get(i));
        }
    }

    private Path getDbPath() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            String userHome = System.getProperty("user.home");
            return Paths.get(userHome, ".CafePOS", "data", "cafepos.db");
        }
        return Paths.get(appData, "CafePOS", "data", "cafepos.db");
    }

    private Path getBackupDir() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            String userHome = System.getProperty("user.home");
            return Paths.get(userHome, ".CafePOS", "backups");
        }
        return Paths.get(appData, "CafePOS", "backups");
    }
}
