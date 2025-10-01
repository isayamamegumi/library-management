package com.library.management.controller;

import com.library.management.entity.ReportLog;
import com.library.management.service.report.log.ReportLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * レポートログコントローラー
 */
@RestController
@RequestMapping("/api/report-logs")
public class ReportLogController {

    private static final Logger logger = LoggerFactory.getLogger(ReportLogController.class);

    @Autowired
    private ReportLogService logService;

    /**
     * ユーザーのログ一覧取得
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getUserLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);
            Pageable pageable = PageRequest.of(page, size);

            Page<ReportLog> logs = logService.getUserLogs(userId, pageable);

            response.put("success", true);
            response.put("logs", logs.getContent().stream().map(this::convertToDto).toList());
            response.put("totalElements", logs.getTotalElements());
            response.put("totalPages", logs.getTotalPages());
            response.put("currentPage", page);
            response.put("size", size);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("ユーザーログ一覧取得エラー", e);
            response.put("success", false);
            response.put("message", "ログ一覧の取得に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 管理者用全ログ一覧取得
     */
    @GetMapping("/admin")
    public ResponseEntity<Map<String, Object>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            // 管理者権限チェック（簡易実装）
            Long userId = getUserId(authentication);
            if (!isAdmin(userId)) {
                response.put("success", false);
                response.put("message", "管理者権限が必要です");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<ReportLog> logs = logService.getAllLogs(startDate, endDate, pageable);

            response.put("success", true);
            response.put("logs", logs.getContent().stream().map(this::convertToDto).toList());
            response.put("totalElements", logs.getTotalElements());
            response.put("totalPages", logs.getTotalPages());
            response.put("currentPage", page);
            response.put("size", size);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("管理者ログ一覧取得エラー", e);
            response.put("success", false);
            response.put("message", "ログ一覧の取得に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * ログ詳細取得
     */
    @GetMapping("/{logId}")
    public ResponseEntity<Map<String, Object>> getLogDetail(
            @PathVariable Long logId,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);

            // ログ取得とアクセス権限チェックは省略（実装簡略化）
            // 実際には ReportLogService にメソッドを追加して権限チェックを行う

            response.put("success", true);
            response.put("message", "ログ詳細機能は実装中です");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("ログ詳細取得エラー: logId={}", logId, e);
            response.put("success", false);
            response.put("message", "ログ詳細の取得に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * ログ統計取得
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getLogStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);

            // 管理者以外は自分のログ統計のみ
            if (!isAdmin(userId)) {
                response.put("success", false);
                response.put("message", "統計情報の閲覧権限がありません");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            // デフォルト期間設定（過去30日）
            if (startDate == null) {
                startDate = LocalDateTime.now().minusDays(30);
            }
            if (endDate == null) {
                endDate = LocalDateTime.now();
            }

            ReportLogService.ReportLogStatistics statistics = logService.getLogStatistics(startDate, endDate);

            response.put("success", true);
            response.put("statistics", convertStatisticsToDto(statistics));
            response.put("period", Map.of(
                "startDate", startDate,
                "endDate", endDate
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("ログ統計取得エラー", e);
            response.put("success", false);
            response.put("message", "統計情報の取得に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * ユーザーID取得
     */
    private Long getUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }

    /**
     * 管理者権限チェック（簡易実装）
     */
    private boolean isAdmin(Long userId) {
        // 実際には ReportAccessControlService を使用
        return userId.equals(1L); // 仮実装
    }

    /**
     * ログDTO変換
     */
    private Map<String, Object> convertToDto(ReportLog log) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", log.getId());
        dto.put("userId", log.getUserId());
        dto.put("username", log.getUsername());
        dto.put("reportType", log.getReportType());
        dto.put("format", log.getFormat());
        dto.put("templateId", log.getTemplateId());
        dto.put("scheduleId", log.getScheduleId());
        dto.put("distributionId", log.getDistributionId());
        dto.put("status", log.getStatus());
        dto.put("startTime", log.getStartTime());
        dto.put("endTime", log.getEndTime());
        dto.put("processingTimeMs", log.getProcessingTimeMs());
        dto.put("recordCount", log.getRecordCount());
        dto.put("fileSizeBytes", log.getFileSizeBytes());
        dto.put("fileName", log.getFileName());
        dto.put("executionContext", log.getExecutionContext());
        dto.put("createdAt", log.getCreatedAt());

        // エラー情報（管理者のみ）
        if (log.getStatus() != null && log.getStatus().equals("ERROR")) {
            dto.put("errorMessage", log.getErrorMessage());
            // スタックトレースは管理者のみ表示
        }

        // パラメータは一部のみ表示
        dto.put("hasParameters", log.getParameters() != null && !log.getParameters().trim().isEmpty());

        return dto;
    }

    /**
     * 統計DTO変換
     */
    private Map<String, Object> convertStatisticsToDto(ReportLogService.ReportLogStatistics statistics) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("totalCount", statistics.getTotalCount());
        dto.put("successCount", statistics.getSuccessCount());
        dto.put("errorCount", statistics.getErrorCount());
        dto.put("successRate", statistics.getSuccessRate());
        dto.put("errorRate", statistics.getErrorRate());
        dto.put("averageProcessingTimeMs", statistics.getAverageProcessingTimeMs());
        dto.put("reportTypeCounts", statistics.getReportTypeCounts());
        dto.put("statusCounts", statistics.getStatusCounts());
        dto.put("dailyCounts", statistics.getDailyCounts());

        // 最近のエラー（要約情報のみ）
        if (statistics.getRecentErrors() != null) {
            dto.put("recentErrorCount", statistics.getRecentErrors().size());
            dto.put("recentErrors", statistics.getRecentErrors().stream()
                .limit(5) // 最新5件のみ
                .map(this::convertErrorSummaryToDto)
                .toList());
        }

        return dto;
    }

    /**
     * エラー要約DTO変換
     */
    private Map<String, Object> convertErrorSummaryToDto(ReportLog errorLog) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", errorLog.getId());
        dto.put("reportType", errorLog.getReportType());
        dto.put("username", errorLog.getUsername());
        dto.put("errorMessage", errorLog.getErrorMessage());
        dto.put("createdAt", errorLog.getCreatedAt());
        return dto;
    }
}