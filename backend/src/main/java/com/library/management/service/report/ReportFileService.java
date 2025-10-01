package com.library.management.service.report;

import com.library.management.entity.ReportHistory;
import com.library.management.repository.ReportHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 帳票ファイル管理サービス
 * ファイルの保存、取得、削除、クリーンアップを担当
 */
@Service
public class ReportFileService {

    private static final Logger logger = LoggerFactory.getLogger(ReportFileService.class);

    @Autowired
    private ReportHistoryRepository reportHistoryRepository;

    @Value("${app.reports.storage-path:./reports}")
    private String reportsStoragePath;

    @Value("${app.reports.expiry-days:7}")
    private int expiryDays;

    @Value("${app.reports.max-file-size:104857600}") // 100MB
    private long maxFileSize;

    /**
     * 帳票ファイル取得
     */
    public Resource getReportFile(Long userId, Long reportId) throws IOException {
        // レポート履歴確認
        Optional<ReportHistory> reportOpt = reportHistoryRepository.findByIdAndUserId(reportId, userId);
        if (reportOpt.isEmpty()) {
            throw new IllegalArgumentException("指定されたレポートが見つかりません");
        }

        ReportHistory report = reportOpt.get();
        if (!"COMPLETED".equals(report.getStatus())) {
            throw new IllegalStateException("レポートが生成中または失敗しています");
        }

        // ファイル存在確認
        String filePath = report.getFilePath();
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalStateException("ファイルパスが設定されていません");
        }

        Path file = Paths.get(filePath);
        if (!Files.exists(file)) {
            logger.warn("ファイルが存在しません: {}", filePath);
            throw new IOException("ファイルが見つかりません");
        }

        // 期限確認
        if (report.getExpiresAt() != null && report.getExpiresAt().isBefore(LocalDateTime.now())) {
            logger.warn("期限切れファイルへのアクセス: {}", filePath);
            throw new IllegalStateException("ファイルの有効期限が切れています");
        }

        return new UrlResource(file.toUri());
    }

    /**
     * ファイルサイズ確認
     */
    public boolean isFileSizeValid(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return false;
            }
            return file.length() <= maxFileSize;
        } catch (Exception e) {
            logger.warn("ファイルサイズ確認エラー: {}", filePath, e);
            return false;
        }
    }

    /**
     * ファイル情報取得
     */
    public FileInfo getFileInfo(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return null;
            }

            FileInfo info = new FileInfo();
            info.setFileName(file.getName());
            info.setFileSize(file.length());
            info.setLastModified(LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(file.lastModified()),
                java.time.ZoneId.systemDefault()));

            // MIME タイプ推定
            String extension = getFileExtension(file.getName());
            info.setContentType(getMimeType(extension));

            return info;
        } catch (Exception e) {
            logger.warn("ファイル情報取得エラー: {}", filePath, e);
            return null;
        }
    }

    /**
     * 拡張子取得
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase() : "";
    }

    /**
     * MIMEタイプ取得
     */
    private String getMimeType(String extension) {
        switch (extension) {
            case "pdf":
                return "application/pdf";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls":
                return "application/vnd.ms-excel";
            default:
                return "application/octet-stream";
        }
    }

    /**
     * レポートディレクトリ初期化
     */
    public void initializeReportDirectory() {
        try {
            Path reportDir = Paths.get(reportsStoragePath);
            if (!Files.exists(reportDir)) {
                Files.createDirectories(reportDir);
                logger.info("レポートディレクトリを作成しました: {}", reportsStoragePath);
            }
        } catch (IOException e) {
            logger.error("レポートディレクトリの作成に失敗しました: {}", reportsStoragePath, e);
            throw new RuntimeException("レポートディレクトリの初期化に失敗しました", e);
        }
    }

    /**
     * 期限切れファイルの自動クリーンアップ（毎日午前2時実行）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredFiles() {
        logger.info("期限切れファイルのクリーンアップを開始します");

        int deletedCount = 0;
        int errorCount = 0;

        try {
            // 期限切れレポート取得
            List<ReportHistory> expiredReports = reportHistoryRepository
                .findByExpiresAtBeforeAndStatus(LocalDateTime.now(), "COMPLETED");

            for (ReportHistory report : expiredReports) {
                try {
                    // ファイル削除
                    if (report.getFilePath() != null) {
                        File file = new File(report.getFilePath());
                        if (file.exists() && file.delete()) {
                            logger.debug("ファイル削除成功: {}", report.getFilePath());
                        }
                    }

                    // 履歴削除
                    reportHistoryRepository.delete(report);
                    deletedCount++;

                } catch (Exception e) {
                    logger.warn("期限切れレポートの削除に失敗: reportId={}, filePath={}",
                        report.getId(), report.getFilePath(), e);
                    errorCount++;
                }
            }

            logger.info("期限切れファイルのクリーンアップ完了: 削除数={}, エラー数={}", deletedCount, errorCount);

        } catch (Exception e) {
            logger.error("クリーンアップ処理でエラーが発生しました", e);
        }
    }

    /**
     * 孤立ファイルのクリーンアップ（毎週日曜日午前3時実行）
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    public void cleanupOrphanedFiles() {
        logger.info("孤立ファイルのクリーンアップを開始します");

        try {
            Path reportDir = Paths.get(reportsStoragePath);
            if (!Files.exists(reportDir)) {
                return;
            }

            // DBに登録されているファイルパス一覧取得
            List<ReportHistory> allReports = reportHistoryRepository.findAll();
            java.util.Set<String> registeredFiles = allReports.stream()
                .map(ReportHistory::getFilePath)
                .filter(path -> path != null && !path.trim().isEmpty())
                .collect(java.util.stream.Collectors.toSet());

            int deletedCount = 0;

            // ディレクトリ内のファイルをチェック
            Files.walk(reportDir)
                .filter(Files::isRegularFile)
                .forEach(filePath -> {
                    String pathStr = filePath.toString();
                    if (!registeredFiles.contains(pathStr)) {
                        try {
                            Files.delete(filePath);
                            logger.debug("孤立ファイル削除: {}", pathStr);
                        } catch (IOException e) {
                            logger.warn("孤立ファイル削除失敗: {}", pathStr, e);
                        }
                    }
                });

            logger.info("孤立ファイルのクリーンアップ完了: 削除数={}", deletedCount);

        } catch (Exception e) {
            logger.error("孤立ファイルクリーンアップ処理でエラーが発生しました", e);
        }
    }

    /**
     * ディスク使用量チェック（毎時実行）
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void checkDiskUsage() {
        try {
            Path reportDir = Paths.get(reportsStoragePath);
            if (!Files.exists(reportDir)) {
                return;
            }

            long totalSize = Files.walk(reportDir)
                .filter(Files::isRegularFile)
                .mapToLong(filePath -> {
                    try {
                        return Files.size(filePath);
                    } catch (IOException e) {
                        return 0L;
                    }
                })
                .sum();

            long maxTotalSize = 1024L * 1024L * 1024L * 5L; // 5GB制限
            if (totalSize > maxTotalSize) {
                logger.warn("レポートディレクトリの使用量が制限を超えています: {}MB / {}MB",
                    totalSize / 1024 / 1024, maxTotalSize / 1024 / 1024);
            }

        } catch (Exception e) {
            logger.error("ディスク使用量チェックでエラーが発生しました", e);
        }
    }

    /**
     * ファイル情報クラス
     */
    public static class FileInfo {
        private String fileName;
        private long fileSize;
        private LocalDateTime lastModified;
        private String contentType;

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }

        public LocalDateTime getLastModified() { return lastModified; }
        public void setLastModified(LocalDateTime lastModified) { this.lastModified = lastModified; }

        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
    }
}