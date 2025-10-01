package com.library.management.service.report.distribution;

import com.library.management.entity.ReportDistribution;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * ファイル配信サービス
 */
@Service
public class FileDistributionService {

    private static final Logger logger = LoggerFactory.getLogger(FileDistributionService.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.report.default-output-dir:/tmp/reports}")
    private String defaultOutputDir;

    @Value("${app.file.distribution.enabled:true}")
    private boolean fileDistributionEnabled;

    /**
     * レポートファイル保存
     */
    public void saveReport(ReportDistribution distribution, File reportFile, String reportFileName) {
        try {
            if (!fileDistributionEnabled) {
                logger.warn("ファイル配信が無効化されています: distributionId={}", distribution.getId());
                return;
            }

            logger.info("レポートファイル保存開始: distributionId={}, fileName={}",
                distribution.getId(), reportFileName);

            // 配信先パスリスト取得
            List<String> outputPaths = parseRecipients(distribution.getRecipients());

            // 配信設定取得
            Map<String, Object> config = parseDistributionConfig(distribution.getDistributionConfig());

            // 各配信先にファイル保存
            for (String outputPath : outputPaths) {
                saveToSinglePath(distribution, reportFile, reportFileName, outputPath, config);
            }

            logger.info("レポートファイル保存完了: distributionId={}, pathCount={}",
                distribution.getId(), outputPaths.size());

        } catch (Exception e) {
            logger.error("レポートファイル保存エラー: distributionId={}", distribution.getId(), e);
            throw new RuntimeException("ファイル保存に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * 単一パスへのファイル保存
     */
    private void saveToSinglePath(ReportDistribution distribution, File reportFile,
                                String fileName, String outputPath, Map<String, Object> config) throws IOException {

        // 出力ディレクトリ解決
        String resolvedPath = resolveOutputPath(outputPath, config);

        // ディレクトリ作成
        Path outputDir = Paths.get(resolvedPath);
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
            logger.info("出力ディレクトリ作成: {}", outputDir);
        }

        // ファイル名生成
        String finalFileName = generateFileName(fileName, config);

        // ファイルパス構築
        Path targetPath = outputDir.resolve(finalFileName);

        // ファイルコピー
        Files.copy(reportFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        logger.info("ファイル保存完了: path={}", targetPath);

        // メタデータファイル作成（オプション）
        if (shouldCreateMetadata(config)) {
            createMetadataFile(distribution, targetPath, config);
        }
    }

    /**
     * 出力パス解決
     */
    private String resolveOutputPath(String outputPath, Map<String, Object> config) {
        // 絶対パス指定の場合
        if (Paths.get(outputPath).isAbsolute()) {
            return outputPath;
        }

        // ベースパス設定がある場合
        if (config != null && config.containsKey("basePath")) {
            String basePath = (String) config.get("basePath");
            if (basePath != null && !basePath.trim().isEmpty()) {
                return Paths.get(basePath, outputPath).toString();
            }
        }

        // デフォルトベースパス使用
        return Paths.get(defaultOutputDir, outputPath).toString();
    }

    /**
     * ファイル名生成
     */
    private String generateFileName(String originalFileName, Map<String, Object> config) {
        if (config == null) {
            return originalFileName;
        }

        // タイムスタンプ付加設定
        Boolean addTimestamp = (Boolean) config.get("addTimestamp");
        if (Boolean.TRUE.equals(addTimestamp)) {
            return addTimestampToFileName(originalFileName);
        }

        // プレフィックス付加設定
        String prefix = (String) config.get("filePrefix");
        if (prefix != null && !prefix.trim().isEmpty()) {
            return prefix + "_" + originalFileName;
        }

        // サフィックス付加設定
        String suffix = (String) config.get("fileSuffix");
        if (suffix != null && !suffix.trim().isEmpty()) {
            return addSuffixToFileName(originalFileName, suffix);
        }

        return originalFileName;
    }

    /**
     * タイムスタンプ付きファイル名生成
     */
    private String addTimestampToFileName(String fileName) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            String nameWithoutExt = fileName.substring(0, dotIndex);
            String extension = fileName.substring(dotIndex);
            return nameWithoutExt + "_" + timestamp + extension;
        } else {
            return fileName + "_" + timestamp;
        }
    }

    /**
     * サフィックス付きファイル名生成
     */
    private String addSuffixToFileName(String fileName, String suffix) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            String nameWithoutExt = fileName.substring(0, dotIndex);
            String extension = fileName.substring(dotIndex);
            return nameWithoutExt + "_" + suffix + extension;
        } else {
            return fileName + "_" + suffix;
        }
    }

    /**
     * メタデータファイル作成判定
     */
    private boolean shouldCreateMetadata(Map<String, Object> config) {
        if (config == null) {
            return false;
        }
        Boolean createMetadata = (Boolean) config.get("createMetadata");
        return Boolean.TRUE.equals(createMetadata);
    }

    /**
     * メタデータファイル作成
     */
    private void createMetadataFile(ReportDistribution distribution, Path reportPath, Map<String, Object> config)
            throws IOException {

        // メタデータ構築
        Map<String, Object> metadata = Map.of(
            "distributionId", distribution.getId(),
            "distributionName", distribution.getName(),
            "originalFileName", reportPath.getFileName().toString(),
            "createdAt", LocalDateTime.now().toString(),
            "fileSize", Files.size(reportPath),
            "distributionType", distribution.getDistributionType(),
            "userId", distribution.getUserId()
        );

        // メタデータファイルパス
        String metadataFileName = reportPath.getFileName().toString() + ".metadata.json";
        Path metadataPath = reportPath.getParent().resolve(metadataFileName);

        // JSON書き込み
        String metadataJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata);
        Files.write(metadataPath, metadataJson.getBytes());

        logger.info("メタデータファイル作成: {}", metadataPath);
    }

    /**
     * ディスク容量チェック
     */
    private void checkDiskSpace(Path outputPath, long fileSize) throws IOException {
        try {
            long freeSpace = Files.getFileStore(outputPath).getUsableSpace();
            long requiredSpace = fileSize + (1024 * 1024 * 100); // 100MB余裕

            if (freeSpace < requiredSpace) {
                throw new IOException("ディスク容量不足: 利用可能=" + (freeSpace / 1024 / 1024) + "MB, " +
                    "必要=" + (requiredSpace / 1024 / 1024) + "MB");
            }
        } catch (Exception e) {
            logger.warn("ディスク容量チェックエラー: {}", e.getMessage());
        }
    }

    /**
     * ファイル権限設定
     */
    private void setFilePermissions(Path filePath, Map<String, Object> config) {
        try {
            if (config != null && config.containsKey("filePermissions")) {
                String permissions = (String) config.get("filePermissions");
                if (permissions != null && !permissions.trim().isEmpty()) {
                    // Unix系システムでのファイル権限設定
                    if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
                        Runtime.getRuntime().exec("chmod " + permissions + " " + filePath.toString());
                        logger.debug("ファイル権限設定: {} -> {}", filePath, permissions);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("ファイル権限設定エラー: path={}, error={}", filePath, e.getMessage());
        }
    }

    /**
     * 古いファイル削除
     */
    public void cleanupOldFiles(String outputPath, int retentionDays) {
        try {
            logger.info("古いファイル削除開始: path={}, retentionDays={}", outputPath, retentionDays);

            Path outputDir = Paths.get(outputPath);
            if (!Files.exists(outputDir)) {
                return;
            }

            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);

            Files.walk(outputDir)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toInstant()
                            .isBefore(cutoffTime.atZone(java.time.ZoneId.systemDefault()).toInstant());
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        logger.debug("古いファイル削除: {}", path);
                    } catch (IOException e) {
                        logger.warn("ファイル削除エラー: path={}, error={}", path, e.getMessage());
                    }
                });

            logger.info("古いファイル削除完了: path={}", outputPath);

        } catch (Exception e) {
            logger.error("古いファイル削除エラー: path={}", outputPath, e);
        }
    }

    /**
     * 宛先リスト解析
     */
    @SuppressWarnings("unchecked")
    private List<String> parseRecipients(String recipientsJson) throws Exception {
        return objectMapper.readValue(recipientsJson, List.class);
    }

    /**
     * 配信設定解析
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDistributionConfig(String configJson) throws Exception {
        if (configJson == null || configJson.trim().isEmpty()) {
            return Map.of();
        }
        return objectMapper.readValue(configJson, Map.class);
    }
}