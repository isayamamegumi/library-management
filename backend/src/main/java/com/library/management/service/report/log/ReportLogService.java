package com.library.management.service.report.log;

import com.library.management.dto.ReportRequest;
import com.library.management.entity.ReportLog;
import com.library.management.repository.ReportLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 帳票生成ログ管理サービス
 */
@Service
@Transactional
public class ReportLogService {

    private static final Logger logger = LoggerFactory.getLogger(ReportLogService.class);

    @Autowired
    private ReportLogRepository logRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * レポート生成開始ログ
     */
    public ReportLog startReportGeneration(Long userId, String username, ReportRequest request,
                                          HttpServletRequest httpRequest) {
        try {
            logger.debug("レポート生成開始ログ記録: userId={}, reportType={}", userId, request.getReportType());

            ReportLog reportLog = new ReportLog(userId, username, request.getReportType(), request.getFormat());
            reportLog.setTemplateId(request.getTemplateId());

            // リクエストパラメータをJSON化
            Map<String, Object> params = buildParametersMap(request);
            reportLog.setParameters(objectMapper.writeValueAsString(params));

            // HTTPリクエスト情報記録
            if (httpRequest != null) {
                reportLog.setClientIpAddress(getClientIpAddress(httpRequest));
                reportLog.setUserAgent(httpRequest.getHeader("User-Agent"));
            }

            reportLog.setExecutionContext("MANUAL");

            ReportLog savedLog = logRepository.save(reportLog);
            logger.debug("レポート生成開始ログ記録完了: logId={}", savedLog.getId());

            return savedLog;

        } catch (Exception e) {
            logger.error("レポート生成開始ログ記録エラー: userId={}, reportType={}",
                userId, request.getReportType(), e);
            throw new RuntimeException("ログ記録に失敗しました", e);
        }
    }

    /**
     * スケジュール実行開始ログ
     */
    public ReportLog startScheduledReportGeneration(Long userId, String username,
                                                   ReportRequest request, Long scheduleId) {
        try {
            logger.debug("スケジュール実行開始ログ記録: userId={}, scheduleId={}", userId, scheduleId);

            ReportLog reportLog = new ReportLog(userId, username, request.getReportType(), request.getFormat());
            reportLog.setTemplateId(request.getTemplateId());
            reportLog.setScheduleId(scheduleId);

            // リクエストパラメータをJSON化
            Map<String, Object> params = buildParametersMap(request);
            reportLog.setParameters(objectMapper.writeValueAsString(params));

            reportLog.setExecutionContext("SCHEDULED");

            ReportLog savedLog = logRepository.save(reportLog);
            logger.debug("スケジュール実行開始ログ記録完了: logId={}", savedLog.getId());

            return savedLog;

        } catch (Exception e) {
            logger.error("スケジュール実行開始ログ記録エラー: userId={}, scheduleId={}",
                userId, scheduleId, e);
            throw new RuntimeException("ログ記録に失敗しました", e);
        }
    }

    /**
     * レポート生成成功ログ
     */
    @Async
    public void completeReportGenerationSuccess(Long logId, String fileName, String filePath,
                                              Integer recordCount, Long fileSizeBytes) {
        try {
            Optional<ReportLog> logOpt = logRepository.findById(logId);
            if (logOpt.isEmpty()) {
                logger.warn("ログが見つかりません: logId={}", logId);
                return;
            }

            ReportLog reportLog = logOpt.get();
            reportLog.completeSuccess(fileName, filePath, recordCount, fileSizeBytes);

            logRepository.save(reportLog);

            logger.info("レポート生成成功ログ記録: logId={}, fileName={}, recordCount={}, fileSize={}",
                logId, fileName, recordCount, fileSizeBytes);

        } catch (Exception e) {
            logger.error("レポート生成成功ログ記録エラー: logId={}", logId, e);
        }
    }

    /**
     * レポート生成失敗ログ
     */
    @Async
    public void completeReportGenerationError(Long logId, Exception exception) {
        try {
            Optional<ReportLog> logOpt = logRepository.findById(logId);
            if (logOpt.isEmpty()) {
                logger.warn("ログが見つかりません: logId={}", logId);
                return;
            }

            ReportLog reportLog = logOpt.get();

            String errorMessage = exception.getMessage();
            String stackTrace = getStackTrace(exception);

            reportLog.completeError(errorMessage, stackTrace);

            logRepository.save(reportLog);

            logger.warn("レポート生成失敗ログ記録: logId={}, error={}", logId, errorMessage);

        } catch (Exception e) {
            logger.error("レポート生成失敗ログ記録エラー: logId={}", logId, e);
        }
    }

    /**
     * ユーザーログ一覧取得
     */
    public Page<ReportLog> getUserLogs(Long userId, Pageable pageable) {
        try {
            Page<ReportLog> logs = logRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
            logger.debug("ユーザーログ一覧取得: userId={}, count={}", userId, logs.getTotalElements());
            return logs;
        } catch (Exception e) {
            logger.error("ユーザーログ一覧取得エラー: userId={}", userId, e);
            throw new RuntimeException("ログ一覧の取得に失敗しました", e);
        }
    }

    /**
     * 管理者用全ログ一覧取得
     */
    public Page<ReportLog> getAllLogs(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        try {
            Page<ReportLog> logs;
            if (startDate != null && endDate != null) {
                logs = logRepository.findByDateRange(startDate, endDate, pageable);
            } else {
                logs = logRepository.findAll(pageable);
            }

            logger.debug("全ログ一覧取得: count={}", logs.getTotalElements());
            return logs;
        } catch (Exception e) {
            logger.error("全ログ一覧取得エラー", e);
            throw new RuntimeException("ログ一覧の取得に失敗しました", e);
        }
    }

    /**
     * ログ統計情報取得
     */
    public ReportLogStatistics getLogStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            logger.debug("ログ統計情報取得: startDate={}, endDate={}", startDate, endDate);

            ReportLogStatistics statistics = new ReportLogStatistics();

            // 基本統計
            statistics.setTotalCount(logRepository.countByStatusAndDateRange("SUCCESS", startDate, endDate) +
                                   logRepository.countByStatusAndDateRange("ERROR", startDate, endDate));
            statistics.setSuccessCount(logRepository.countByStatusAndDateRange("SUCCESS", startDate, endDate));
            statistics.setErrorCount(logRepository.countByStatusAndDateRange("ERROR", startDate, endDate));

            // レポートタイプ別統計
            List<Object[]> reportTypeStats = logRepository.getReportTypeStatistics(startDate, endDate);
            Map<String, Long> reportTypeCounts = new HashMap<>();
            for (Object[] stat : reportTypeStats) {
                reportTypeCounts.put((String) stat[0], ((Number) stat[1]).longValue());
            }
            statistics.setReportTypeCounts(reportTypeCounts);

            // ステータス別統計
            List<Object[]> statusStats = logRepository.getStatusStatistics(startDate, endDate);
            Map<String, Long> statusCounts = new HashMap<>();
            for (Object[] stat : statusStats) {
                statusCounts.put((String) stat[0], ((Number) stat[1]).longValue());
            }
            statistics.setStatusCounts(statusCounts);

            // 日次統計
            List<Object[]> dailyStats = logRepository.getDailyStatistics(startDate, endDate);
            Map<String, Long> dailyCounts = new HashMap<>();
            for (Object[] stat : dailyStats) {
                dailyCounts.put(stat[0].toString(), ((Number) stat[1]).longValue());
            }
            statistics.setDailyCounts(dailyCounts);

            // 平均処理時間
            Double avgProcessingTime = logRepository.getAverageProcessingTime("BOOK_LIST", startDate, endDate);
            statistics.setAverageProcessingTimeMs(avgProcessingTime != null ? avgProcessingTime.longValue() : 0L);

            // 最近のエラー
            List<ReportLog> recentErrors = logRepository.findRecentErrors(startDate, endDate);
            statistics.setRecentErrors(recentErrors);

            logger.debug("ログ統計情報取得完了: totalCount={}, successCount={}, errorCount={}",
                statistics.getTotalCount(), statistics.getSuccessCount(), statistics.getErrorCount());

            return statistics;

        } catch (Exception e) {
            logger.error("ログ統計情報取得エラー", e);
            throw new RuntimeException("統計情報の取得に失敗しました", e);
        }
    }

    /**
     * 古いログの自動削除
     */
    @Scheduled(cron = "0 0 2 * * ?") // 毎日午前2時実行
    @Transactional
    public void cleanupOldLogs() {
        try {
            logger.info("古いログ削除開始");

            // 90日より古いログを削除
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);

            List<ReportLog> oldLogs = logRepository.findByStatusAndCreatedAtBefore("SUCCESS", cutoffDate);
            oldLogs.addAll(logRepository.findByStatusAndCreatedAtBefore("ERROR", cutoffDate));

            if (!oldLogs.isEmpty()) {
                logRepository.deleteAll(oldLogs);
                logger.info("古いログ削除完了: 削除件数={}", oldLogs.size());
            } else {
                logger.info("削除対象の古いログなし");
            }

        } catch (Exception e) {
            logger.error("古いログ削除エラー", e);
        }
    }

    /**
     * エラーログ監視
     */
    @Scheduled(fixedRate = 300000) // 5分間隔
    public void monitorErrorLogs() {
        try {
            LocalDateTime startTime = LocalDateTime.now().minusMinutes(5);
            LocalDateTime endTime = LocalDateTime.now();

            List<ReportLog> recentErrors = logRepository.findRecentErrors(startTime, endTime);

            if (!recentErrors.isEmpty()) {
                logger.warn("最近のエラーログ発見: count={}", recentErrors.size());

                // エラー頻度が高い場合はアラート
                if (recentErrors.size() > 5) {
                    logger.error("エラー頻度が高い状況を検出: errorCount={}, period=5分", recentErrors.size());
                    // TODO: 管理者への通知機能を追加
                }
            }

        } catch (Exception e) {
            logger.error("エラーログ監視エラー", e);
        }
    }

    /**
     * パラメータマップ構築
     */
    private Map<String, Object> buildParametersMap(ReportRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put("reportType", request.getReportType());
        params.put("format", request.getFormat());
        params.put("templateId", request.getTemplateId());

        if (request.getFilters() != null) {
            params.put("filters", request.getFilters());
        }

        if (request.getOptions() != null) {
            params.put("options", request.getOptions());
        }

        return params;
    }

    /**
     * クライアントIPアドレス取得
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };

        for (String headerName : headerNames) {
            String value = request.getHeader(headerName);
            if (value != null && !value.isEmpty() && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * スタックトレース取得
     */
    private String getStackTrace(Exception exception) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            exception.printStackTrace(pw);
            return sw.toString();
        } catch (Exception e) {
            return "スタックトレース取得エラー: " + e.getMessage();
        }
    }

    /**
     * ログ統計データクラス
     */
    public static class ReportLogStatistics {
        private long totalCount;
        private long successCount;
        private long errorCount;
        private Map<String, Long> reportTypeCounts = new HashMap<>();
        private Map<String, Long> statusCounts = new HashMap<>();
        private Map<String, Long> dailyCounts = new HashMap<>();
        private long averageProcessingTimeMs;
        private List<ReportLog> recentErrors;

        // Getters and Setters
        public long getTotalCount() { return totalCount; }
        public void setTotalCount(long totalCount) { this.totalCount = totalCount; }

        public long getSuccessCount() { return successCount; }
        public void setSuccessCount(long successCount) { this.successCount = successCount; }

        public long getErrorCount() { return errorCount; }
        public void setErrorCount(long errorCount) { this.errorCount = errorCount; }

        public Map<String, Long> getReportTypeCounts() { return reportTypeCounts; }
        public void setReportTypeCounts(Map<String, Long> reportTypeCounts) { this.reportTypeCounts = reportTypeCounts; }

        public Map<String, Long> getStatusCounts() { return statusCounts; }
        public void setStatusCounts(Map<String, Long> statusCounts) { this.statusCounts = statusCounts; }

        public Map<String, Long> getDailyCounts() { return dailyCounts; }
        public void setDailyCounts(Map<String, Long> dailyCounts) { this.dailyCounts = dailyCounts; }

        public long getAverageProcessingTimeMs() { return averageProcessingTimeMs; }
        public void setAverageProcessingTimeMs(long averageProcessingTimeMs) { this.averageProcessingTimeMs = averageProcessingTimeMs; }

        public List<ReportLog> getRecentErrors() { return recentErrors; }
        public void setRecentErrors(List<ReportLog> recentErrors) { this.recentErrors = recentErrors; }

        public double getSuccessRate() {
            return totalCount > 0 ? (double) successCount / totalCount * 100 : 0.0;
        }

        public double getErrorRate() {
            return totalCount > 0 ? (double) errorCount / totalCount * 100 : 0.0;
        }
    }
}