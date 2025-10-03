package com.library.management.service.report;

import com.library.management.dto.ReportRequest;
import com.library.management.entity.ReportHistory;
import com.library.management.entity.ReportLog;
import com.library.management.repository.ReportHistoryRepository;
import com.library.management.service.report.log.ReportLogService;
import com.library.management.service.report.cache.ReportCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 帳票生成サービスの基底クラス
 * 共通的な帳票処理機能を提供
 */
@Service
public abstract class ReportService {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    protected ReportHistoryRepository reportHistoryRepository;

    @Autowired
    protected ReportLogService reportLogService;

    @Autowired
    protected ReportCacheService reportCacheService;

    @Autowired
    protected ObjectMapper objectMapper;

    @Value("${app.reports.storage-path:./reports}")
    protected String reportsStoragePath;

    @Value("${app.reports.expiry-days:7}")
    protected int expiryDays;

    /**
     * 帳票生成のメインメソッド
     */
    public ReportGenerationResult generateReport(Long userId, ReportRequest request) {
        return generateReport(userId, request, null);
    }

    /**
     * 帳票生成のメインメソッド（HTTP情報付き）
     */
    @Transactional(rollbackFor = Exception.class)
    public ReportGenerationResult generateReport(Long userId, ReportRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        ReportHistory history = null;
        ReportLog reportLog = null;
        Long generationStartTime = System.currentTimeMillis();

        try {
            logger.info("帳票生成開始: userId={}, reportType={}, format={}", userId, request.getReportType(), request.getFormat());

            // 0. キャッシュ確認
            logger.debug("ステップ0: キャッシュ確認");
            ReportCacheService.CacheResult cacheResult = reportCacheService.getCachedReport(userId, request);
            if (cacheResult.isHit()) {
                logger.info("キャッシュヒット: userId={}, reportType={}, filePath={}",
                    userId, request.getReportType(), cacheResult.getFilePath());

                // キャッシュヒット時も履歴レコードは作成（トラッキング用）
                history = createReportHistory(userId, request);
                updateReportHistory(history, cacheResult.getFilePath(), "CACHED");

                return ReportGenerationResult.successWithCache(history.getId(), cacheResult.getFilePath());
            }
            logger.debug("キャッシュミス: {}", cacheResult.getMessage());

            // 1. ログ記録開始
            logger.debug("ステップ1: ログ記録開始");
            reportLog = reportLogService.startReportGeneration(userId, getUsernameById(userId), request, httpRequest);
            logger.debug("ログ記録開始完了: logId={}", reportLog.getId());

            // 2. 履歴レコード作成
            logger.debug("ステップ2: 履歴レコード作成");
            history = createReportHistory(userId, request);
            logger.debug("履歴レコード作成完了: historyId={}", history.getId());

            // 3. データ取得・検証
            logger.debug("ステップ3: リクエスト検証");
            validateRequest(request);
            logger.debug("リクエスト検証完了");

            // 4. 帳票生成処理（実装クラスで定義）
            logger.debug("ステップ4: 帳票生成処理開始");
            String filePath = doGenerateReport(userId, request, history);
            logger.debug("帳票生成処理完了: filePath={}", filePath);

            Long generationEndTime = System.currentTimeMillis();
            Long generationTime = generationEndTime - generationStartTime;

            // レコード数取得
            Integer recordCount = getRecordCount(userId, request);

            // 5. キャッシュ保存（エラーが起きても処理を継続）
            logger.debug("ステップ5: キャッシュ保存");
            try {
                reportCacheService.cacheReport(userId, request, filePath, recordCount, generationTime);
                logger.debug("キャッシュ保存完了");
            } catch (Exception cacheException) {
                logger.warn("キャッシュ保存でエラーが発生しましたが、帳票生成は継続します: {}", cacheException.getMessage());
            }

            // 6. 履歴更新
            logger.debug("ステップ6: 履歴更新");
            updateReportHistory(history, filePath, "COMPLETED");
            logger.debug("履歴更新完了");

            // 7. ログ記録完了
            logger.debug("ステップ7: ログ記録完了");
            File reportFile = new File(filePath);
            reportLogService.completeReportGenerationSuccess(
                reportLog.getId(),
                reportFile.getName(),
                filePath,
                recordCount,
                reportFile.length()
            );
            logger.debug("ログ記録完了");

            logger.info("帳票生成完了: userId={}, reportType={}, filePath={}, generationTime={}ms",
                userId, request.getReportType(), filePath, generationTime);

            return ReportGenerationResult.successWithTime(history.getId(), filePath, generationTime);

        } catch (Exception e) {
            logger.error("帳票生成エラー: userId={}, reportType={}, エラークラス={}, メッセージ={}",
                userId, request != null ? request.getReportType() : "null", e.getClass().getSimpleName(), e.getMessage(), e);

            // ログ記録エラー
            if (reportLog != null) {
                try {
                    reportLogService.completeReportGenerationError(reportLog.getId(), e);
                } catch (Exception logException) {
                    logger.error("エラーログ記録中にエラー", logException);
                }
            }

            // 履歴更新エラー
            if (history != null) {
                try {
                    updateReportHistory(history, null, "FAILED");
                } catch (Exception updateException) {
                    logger.error("履歴更新中にエラー", updateException);
                }
            }

            return ReportGenerationResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 帳票生成の実装（サブクラスで実装）
     */
    protected abstract String doGenerateReport(Long userId, ReportRequest request, ReportHistory history)
        throws Exception;

    /**
     * リクエスト検証（サブクラスでオーバーライド可能）
     */
    protected void validateRequest(ReportRequest request) throws IllegalArgumentException {
        if (request.getReportType() == null || request.getReportType().trim().isEmpty()) {
            throw new IllegalArgumentException("レポートタイプが指定されていません");
        }
        if (request.getFormat() == null || request.getFormat().trim().isEmpty()) {
            throw new IllegalArgumentException("出力フォーマットが指定されていません");
        }
    }

    /**
     * 帳票履歴レコード作成
     */
    @Transactional
    protected ReportHistory createReportHistory(Long userId, ReportRequest request) {
        try {
            logger.info("帳票履歴作成開始: userId={}, reportType={}, format={}", userId, request.getReportType(), request.getFormat());

            String parametersJson;
            try {
                parametersJson = objectMapper.writeValueAsString(request);
                logger.debug("パラメータJSON変換成功: {}", parametersJson);
            } catch (Exception e) {
                logger.error("パラメータJSON変換エラー: {}", e.getMessage(), e);
                // JSON変換に失敗した場合は簡易的な文字列を使用
                parametersJson = String.format("{\"reportType\":\"%s\",\"format\":\"%s\"}",
                    request.getReportType(), request.getFormat());
            }

            ReportHistory history = new ReportHistory(userId, request.getReportType(),
                request.getFormat(), parametersJson);
            history.setExpiresAt(LocalDateTime.now().plusDays(expiryDays));

            logger.debug("ReportHistoryエンティティ作成成功");

            ReportHistory savedHistory = reportHistoryRepository.save(history);
            logger.info("帳票履歴保存成功: id={}", savedHistory.getId());

            return savedHistory;
        } catch (Exception e) {
            logger.error("帳票履歴の作成に失敗しました: userId={}, error={}", userId, e.getMessage(), e);
            throw new RuntimeException("帳票履歴の作成に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * 帳票履歴更新
     */
    @Transactional
    protected void updateReportHistory(ReportHistory history, String filePath, String status) {
        try {
            history.setFilePath(filePath);
            history.setStatus(status);

            if (filePath != null) {
                File file = new File(filePath);
                if (file.exists()) {
                    history.setFileSize(file.length());
                }
            }

            reportHistoryRepository.save(history);
        } catch (Exception e) {
            logger.error("帳票履歴の更新に失敗しました: {}", e.getMessage());
        }
    }

    /**
     * ファイルパス生成
     */
    protected String generateFilePath(String format, String reportType) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(format);

        // ディレクトリ作成
        File directory = new File(reportsStoragePath);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        return reportsStoragePath + File.separator +
               reportType + "_" + timestamp + "_" + uuid + "." + extension;
    }

    /**
     * フォーマットから拡張子を取得
     */
    protected String getFileExtension(String format) {
        switch (format.toUpperCase()) {
            case "PDF":
                return "pdf";
            case "EXCEL":
                return "xlsx";
            default:
                return "bin";
        }
    }

    /**
     * 非同期帳票生成
     */
    public ReportGenerationResult generateReportAsync(Long userId, ReportRequest request) {
        try {
            // 履歴レコード作成（GENERATING状態）
            ReportHistory history = createReportHistory(userId, request);

            // 非同期で帳票生成処理を実行
            processReportAsync(userId, request, history);

            return ReportGenerationResult.success(history.getId(), null);

        } catch (Exception e) {
            logger.error("非同期帳票生成開始エラー", e);
            return ReportGenerationResult.failure("非同期処理の開始に失敗しました: " + e.getMessage());
        }
    }


    /**
     * 非同期処理実行
     */
    @Async
    @Transactional
    protected CompletableFuture<Void> processReportAsync(Long userId, ReportRequest request, ReportHistory history) {
        Long generationStartTime = System.currentTimeMillis();

        try {
            logger.info("非同期帳票生成処理開始: userId={}, reportId={}", userId, history.getId());

            // キャッシュ確認
            ReportCacheService.CacheResult cacheResult = reportCacheService.getCachedReport(userId, request);
            if (cacheResult.isHit()) {
                logger.info("非同期処理でキャッシュヒット: userId={}, reportId={}, filePath={}",
                    userId, history.getId(), cacheResult.getFilePath());
                updateReportHistory(history, cacheResult.getFilePath(), "CACHED");
                return CompletableFuture.completedFuture(null);
            }

            // リクエスト検証
            validateRequest(request);

            // 帳票生成処理
            String filePath = doGenerateReport(userId, request, history);

            Long generationEndTime = System.currentTimeMillis();
            Long generationTime = generationEndTime - generationStartTime;

            // キャッシュ保存
            Integer recordCount = getRecordCount(userId, request);
            reportCacheService.cacheReport(userId, request, filePath, recordCount, generationTime);

            // 履歴更新
            updateReportHistory(history, filePath, "COMPLETED");

            logger.info("非同期帳票生成処理完了: userId={}, reportId={}, filePath={}, generationTime={}ms",
                userId, history.getId(), filePath, generationTime);

        } catch (Exception e) {
            logger.error("非同期帳票生成処理エラー: userId={}, reportId={}",
                userId, history.getId(), e);
            updateReportHistory(history, null, "FAILED");
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * ユーザー名取得（簡易実装）
     */
    protected String getUsernameById(Long userId) {
        // 実際にはUserServiceやUserRepositoryを使用してユーザー名を取得
        return "user_" + userId;
    }

    /**
     * レコード数取得（サブクラスでオーバーライド可能）
     */
    protected Integer getRecordCount(Long userId, ReportRequest request) {
        // デフォルト実装：0を返す
        return 0;
    }

    /**
     * ユーザーの帳票履歴取得
     */
    public List<ReportHistory> getUserReportHistory(Long userId, int limit) {
        return reportHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId,
            org.springframework.data.domain.PageRequest.of(0, limit));
    }

    /**
     * レポートIDとユーザーIDでレポート取得
     */
    public Optional<ReportHistory> getReportById(Long reportId, Long userId) {
        return reportHistoryRepository.findByIdAndUserId(reportId, userId);
    }

    /**
     * 期限切れファイルのクリーンアップ
     */
    public void cleanupExpiredReports() {
        try {
            List<ReportHistory> expiredReports = reportHistoryRepository
                .findByExpiresAtBeforeAndStatus(LocalDateTime.now(), "COMPLETED");

            for (ReportHistory report : expiredReports) {
                try {
                    // ファイル削除
                    if (report.getFilePath() != null) {
                        File file = new File(report.getFilePath());
                        if (file.exists()) {
                            file.delete();
                        }
                    }

                    // 履歴削除
                    reportHistoryRepository.delete(report);

                } catch (Exception e) {
                    logger.warn("期限切れレポートの削除に失敗: {}", report.getFilePath(), e);
                }
            }

            logger.info("期限切れレポートのクリーンアップ完了: {}件", expiredReports.size());

        } catch (Exception e) {
            logger.error("レポートクリーンアップ処理でエラー", e);
        }
    }

    /**
     * 帳票生成結果クラス
     */
    public static class ReportGenerationResult {
        private final boolean success;
        private final String message;
        private final Long reportId;
        private final String filePath;
        private final boolean cacheHit;
        private final Long generationTimeMs;

        private ReportGenerationResult(boolean success, String message, Long reportId, String filePath, boolean cacheHit, Long generationTimeMs) {
            this.success = success;
            this.message = message;
            this.reportId = reportId;
            this.filePath = filePath;
            this.cacheHit = cacheHit;
            this.generationTimeMs = generationTimeMs;
        }

        public static ReportGenerationResult success(Long reportId, String filePath) {
            return new ReportGenerationResult(true, "帳票生成が完了しました", reportId, filePath, false, null);
        }

        public static ReportGenerationResult successWithCache(Long reportId, String filePath) {
            return new ReportGenerationResult(true, "帳票生成が完了しました（キャッシュから取得）", reportId, filePath, true, 0L);
        }

        public static ReportGenerationResult successWithTime(Long reportId, String filePath, Long generationTimeMs) {
            return new ReportGenerationResult(true, "帳票生成が完了しました", reportId, filePath, false, generationTimeMs);
        }

        public static ReportGenerationResult failure(String message) {
            return new ReportGenerationResult(false, message, null, null, false, null);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Long getReportId() { return reportId; }
        public String getFilePath() { return filePath; }
        public boolean isCacheHit() { return cacheHit; }
        public Long getGenerationTimeMs() { return generationTimeMs; }
    }
}