package com.library.management.service.report.distribution;

import com.library.management.entity.ReportDistribution;
import com.library.management.repository.ReportDistributionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 帳票配信サービス
 */
@Service
@Transactional
public class ReportDistributionService {

    private static final Logger logger = LoggerFactory.getLogger(ReportDistributionService.class);
    private static final int MAX_DISTRIBUTIONS_PER_USER = 20;

    @Autowired
    private ReportDistributionRepository distributionRepository;

    @Autowired
    private EmailDistributionService emailService;

    @Autowired
    private SlackDistributionService slackService;

    @Autowired
    private FileDistributionService fileService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 配信設定作成
     */
    public ReportDistribution createDistribution(DistributionCreateRequest request, Long userId) {
        try {
            logger.info("配信設定作成開始: name={}, userId={}, distributionType={}",
                request.getName(), userId, request.getDistributionType());

            // バリデーション
            validateDistributionCreation(request, userId);

            // 配信設定作成
            ReportDistribution distribution = new ReportDistribution(
                request.getName(),
                userId,
                request.getDistributionType(),
                objectMapper.writeValueAsString(request.getRecipients())
            );

            distribution.setScheduleId(request.getScheduleId());
            distribution.setDistributionConfig(objectMapper.writeValueAsString(request.getDistributionConfig()));
            distribution.setSubject(request.getSubject());
            distribution.setMessage(request.getMessage());
            distribution.setAttachFile(request.getAttachFile());
            distribution.setCompressFile(request.getCompressFile());
            distribution.setPasswordProtection(request.getPasswordProtection());

            ReportDistribution savedDistribution = distributionRepository.save(distribution);
            logger.info("配信設定作成完了: distributionId={}", savedDistribution.getId());

            return savedDistribution;

        } catch (Exception e) {
            logger.error("配信設定作成エラー: name={}, userId={}", request.getName(), userId, e);
            throw new RuntimeException("配信設定の作成に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * 配信設定更新
     */
    public ReportDistribution updateDistribution(Long distributionId, DistributionUpdateRequest request, Long userId) {
        try {
            Optional<ReportDistribution> distributionOpt = distributionRepository.findById(distributionId);
            if (distributionOpt.isEmpty()) {
                throw new IllegalArgumentException("配信設定が見つかりません: " + distributionId);
            }

            ReportDistribution distribution = distributionOpt.get();

            // 権限チェック
            if (!userId.equals(distribution.getUserId())) {
                throw new IllegalArgumentException("配信設定の更新権限がありません");
            }

            // 更新処理
            if (request.getName() != null) {
                distribution.setName(request.getName());
            }

            if (request.getRecipients() != null) {
                distribution.setRecipients(objectMapper.writeValueAsString(request.getRecipients()));
            }

            if (request.getDistributionConfig() != null) {
                distribution.setDistributionConfig(objectMapper.writeValueAsString(request.getDistributionConfig()));
            }

            if (request.getSubject() != null) {
                distribution.setSubject(request.getSubject());
            }

            if (request.getMessage() != null) {
                distribution.setMessage(request.getMessage());
            }

            if (request.getAttachFile() != null) {
                distribution.setAttachFile(request.getAttachFile());
            }

            if (request.getCompressFile() != null) {
                distribution.setCompressFile(request.getCompressFile());
            }

            if (request.getPasswordProtection() != null) {
                distribution.setPasswordProtection(request.getPasswordProtection());
            }

            if (request.getStatus() != null) {
                distribution.setStatus(request.getStatus());
            }

            ReportDistribution updatedDistribution = distributionRepository.save(distribution);
            logger.info("配信設定更新完了: distributionId={}, userId={}", distributionId, userId);

            return updatedDistribution;

        } catch (Exception e) {
            logger.error("配信設定更新エラー: distributionId={}, userId={}", distributionId, userId, e);
            throw new RuntimeException("配信設定の更新に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * 配信設定削除
     */
    public void deleteDistribution(Long distributionId, Long userId) {
        try {
            Optional<ReportDistribution> distributionOpt = distributionRepository.findById(distributionId);
            if (distributionOpt.isEmpty()) {
                throw new IllegalArgumentException("配信設定が見つかりません: " + distributionId);
            }

            ReportDistribution distribution = distributionOpt.get();

            // 権限チェック
            if (!userId.equals(distribution.getUserId())) {
                throw new IllegalArgumentException("配信設定の削除権限がありません");
            }

            // 論理削除
            distribution.setIsActive(false);
            distributionRepository.save(distribution);

            logger.info("配信設定削除完了: distributionId={}, userId={}", distributionId, userId);

        } catch (Exception e) {
            logger.error("配信設定削除エラー: distributionId={}, userId={}", distributionId, userId, e);
            throw new RuntimeException("配信設定の削除に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * ユーザーの配信設定一覧取得
     */
    public List<ReportDistribution> getUserDistributions(Long userId) {
        try {
            List<ReportDistribution> distributions = distributionRepository.findByUserIdAndIsActiveTrue(userId);
            logger.info("ユーザー配信設定一覧取得完了: userId={}, count={}", userId, distributions.size());
            return distributions;
        } catch (Exception e) {
            logger.error("ユーザー配信設定一覧取得エラー: userId={}", userId, e);
            throw new RuntimeException("配信設定一覧の取得に失敗しました", e);
        }
    }

    /**
     * スケジュール配信実行
     */
    @Async
    public CompletableFuture<Void> executeDistribution(Long scheduleId, File reportFile, String reportFileName) {
        try {
            logger.info("スケジュール配信実行開始: scheduleId={}, fileName={}", scheduleId, reportFileName);

            List<ReportDistribution> distributions = distributionRepository.findByScheduleIdAndIsActiveTrue(scheduleId);

            if (distributions.isEmpty()) {
                logger.info("配信対象なし: scheduleId={}", scheduleId);
                return CompletableFuture.completedFuture(null);
            }

            logger.info("配信実行開始: scheduleId={}, distributionCount={}", scheduleId, distributions.size());

            // 各配信設定に対して配信実行
            for (ReportDistribution distribution : distributions) {
                try {
                    executeSingleDistribution(distribution, reportFile, reportFileName);

                    // 配信記録更新
                    distribution.setLastDistributionTime(LocalDateTime.now());
                    distribution.setDistributionCount(distribution.getDistributionCount() + 1);
                    distributionRepository.save(distribution);

                } catch (Exception e) {
                    logger.error("個別配信エラー: distributionId={}", distribution.getId(), e);
                    // 個別配信エラーでも他の配信は継続
                }
            }

            logger.info("スケジュール配信実行完了: scheduleId={}", scheduleId);
            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            logger.error("スケジュール配信実行エラー: scheduleId={}", scheduleId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 単一配信実行
     */
    private void executeSingleDistribution(ReportDistribution distribution, File reportFile, String reportFileName)
            throws Exception {

        logger.info("単一配信実行開始: distributionId={}, type={}",
            distribution.getId(), distribution.getDistributionType());

        switch (distribution.getDistributionType().toUpperCase()) {
            case "EMAIL":
                emailService.sendReport(distribution, reportFile, reportFileName);
                break;
            case "SLACK":
                slackService.sendReport(distribution, reportFile, reportFileName);
                break;
            case "FILE":
                fileService.saveReport(distribution, reportFile, reportFileName);
                break;
            default:
                throw new IllegalArgumentException("サポートされていない配信タイプ: " + distribution.getDistributionType());
        }

        logger.info("単一配信実行完了: distributionId={}", distribution.getId());
    }

    /**
     * 手動配信実行
     */
    @Async
    public CompletableFuture<Void> executeManualDistribution(Long distributionId, File reportFile, String reportFileName) {
        try {
            logger.info("手動配信実行開始: distributionId={}, fileName={}", distributionId, reportFileName);

            Optional<ReportDistribution> distributionOpt = distributionRepository.findById(distributionId);
            if (distributionOpt.isEmpty()) {
                throw new IllegalArgumentException("配信設定が見つかりません: " + distributionId);
            }

            ReportDistribution distribution = distributionOpt.get();
            executeSingleDistribution(distribution, reportFile, reportFileName);

            // 配信記録更新
            distribution.setLastDistributionTime(LocalDateTime.now());
            distribution.setDistributionCount(distribution.getDistributionCount() + 1);
            distributionRepository.save(distribution);

            logger.info("手動配信実行完了: distributionId={}", distributionId);
            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            logger.error("手動配信実行エラー: distributionId={}", distributionId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 配信設定作成バリデーション
     */
    private void validateDistributionCreation(DistributionCreateRequest request, Long userId) {
        // 同名チェック
        Optional<ReportDistribution> existing = distributionRepository.findByUserIdAndName(userId, request.getName());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("同名の配信設定が既に存在します: " + request.getName());
        }

        // 上限チェック
        long userDistributionCount = distributionRepository.countActiveDistributionsByUser(userId);
        if (userDistributionCount >= MAX_DISTRIBUTIONS_PER_USER) {
            throw new IllegalArgumentException("配信設定登録上限に達しています（最大" + MAX_DISTRIBUTIONS_PER_USER + "件）");
        }

        // 宛先バリデーション
        validateRecipients(request.getRecipients(), request.getDistributionType());

        // 配信設定バリデーション
        validateDistributionConfig(request.getDistributionConfig(), request.getDistributionType());
    }

    /**
     * 宛先バリデーション
     */
    private void validateRecipients(List<String> recipients, String distributionType) {
        if (recipients == null || recipients.isEmpty()) {
            throw new IllegalArgumentException("宛先を指定してください");
        }

        switch (distributionType.toUpperCase()) {
            case "EMAIL":
                for (String recipient : recipients) {
                    if (!isValidEmail(recipient)) {
                        throw new IllegalArgumentException("無効なメールアドレス: " + recipient);
                    }
                }
                break;
            case "SLACK":
                for (String recipient : recipients) {
                    if (!isValidSlackChannel(recipient)) {
                        throw new IllegalArgumentException("無効なSlackチャンネル: " + recipient);
                    }
                }
                break;
            case "FILE":
                for (String recipient : recipients) {
                    if (!isValidFilePath(recipient)) {
                        throw new IllegalArgumentException("無効なファイルパス: " + recipient);
                    }
                }
                break;
        }
    }

    /**
     * 配信設定バリデーション
     */
    private void validateDistributionConfig(Map<String, Object> config, String distributionType) {
        if (config == null) {
            return; // オプショナル
        }

        switch (distributionType.toUpperCase()) {
            case "EMAIL":
                validateEmailConfig(config);
                break;
            case "SLACK":
                validateSlackConfig(config);
                break;
            case "FILE":
                validateFileConfig(config);
                break;
        }
    }

    /**
     * Email設定バリデーション
     */
    private void validateEmailConfig(Map<String, Object> config) {
        // SMTP設定のバリデーション
        if (config.containsKey("smtpHost") && config.get("smtpHost") == null) {
            throw new IllegalArgumentException("SMTPホストが無効です");
        }
    }

    /**
     * Slack設定バリデーション
     */
    private void validateSlackConfig(Map<String, Object> config) {
        // Slack設定のバリデーション
        if (config.containsKey("webhookUrl")) {
            String webhookUrl = (String) config.get("webhookUrl");
            if (webhookUrl != null && !webhookUrl.startsWith("https://hooks.slack.com/")) {
                throw new IllegalArgumentException("無効なSlack Webhook URL");
            }
        }
    }

    /**
     * ファイル設定バリデーション
     */
    private void validateFileConfig(Map<String, Object> config) {
        // ファイル配信設定のバリデーション
        if (config.containsKey("basePath")) {
            String basePath = (String) config.get("basePath");
            if (basePath != null && !new File(basePath).exists()) {
                throw new IllegalArgumentException("指定されたベースパスが存在しません: " + basePath);
            }
        }
    }

    /**
     * メールアドレス形式チェック
     */
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    /**
     * Slackチャンネル形式チェック
     */
    private boolean isValidSlackChannel(String channel) {
        return channel != null && (channel.startsWith("#") || channel.startsWith("@"));
    }

    /**
     * ファイルパス形式チェック
     */
    private boolean isValidFilePath(String path) {
        try {
            new File(path);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 配信作成リクエスト
     */
    public static class DistributionCreateRequest {
        private String name;
        private Long scheduleId;
        private String distributionType;
        private List<String> recipients;
        private Map<String, Object> distributionConfig;
        private String subject;
        private String message;
        private Boolean attachFile = true;
        private Boolean compressFile = false;
        private String passwordProtection;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Long getScheduleId() { return scheduleId; }
        public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }

        public String getDistributionType() { return distributionType; }
        public void setDistributionType(String distributionType) { this.distributionType = distributionType; }

        public List<String> getRecipients() { return recipients; }
        public void setRecipients(List<String> recipients) { this.recipients = recipients; }

        public Map<String, Object> getDistributionConfig() { return distributionConfig; }
        public void setDistributionConfig(Map<String, Object> distributionConfig) { this.distributionConfig = distributionConfig; }

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public Boolean getAttachFile() { return attachFile; }
        public void setAttachFile(Boolean attachFile) { this.attachFile = attachFile; }

        public Boolean getCompressFile() { return compressFile; }
        public void setCompressFile(Boolean compressFile) { this.compressFile = compressFile; }

        public String getPasswordProtection() { return passwordProtection; }
        public void setPasswordProtection(String passwordProtection) { this.passwordProtection = passwordProtection; }
    }

    /**
     * 配信更新リクエスト
     */
    public static class DistributionUpdateRequest {
        private String name;
        private List<String> recipients;
        private Map<String, Object> distributionConfig;
        private String subject;
        private String message;
        private Boolean attachFile;
        private Boolean compressFile;
        private String passwordProtection;
        private String status;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<String> getRecipients() { return recipients; }
        public void setRecipients(List<String> recipients) { this.recipients = recipients; }

        public Map<String, Object> getDistributionConfig() { return distributionConfig; }
        public void setDistributionConfig(Map<String, Object> distributionConfig) { this.distributionConfig = distributionConfig; }

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public Boolean getAttachFile() { return attachFile; }
        public void setAttachFile(Boolean attachFile) { this.attachFile = attachFile; }

        public Boolean getCompressFile() { return compressFile; }
        public void setCompressFile(Boolean compressFile) { this.compressFile = compressFile; }

        public String getPasswordProtection() { return passwordProtection; }
        public void setPasswordProtection(String passwordProtection) { this.passwordProtection = passwordProtection; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}