package com.library.management.controller;

import com.library.management.service.report.optimization.MemoryOptimizationService;
import com.library.management.service.report.optimization.QueryOptimizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * レポート最適化コントローラー
 * 管理者向けの最適化情報・制御API
 */
@RestController
@RequestMapping("/api/report-optimization")
public class ReportOptimizationController {

    private static final Logger logger = LoggerFactory.getLogger(ReportOptimizationController.class);

    @Autowired
    private MemoryOptimizationService memoryOptimizationService;

    @Autowired
    private QueryOptimizationService queryOptimizationService;

    /**
     * システム最適化状況取得
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getOptimizationStatus(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);

            // 管理者権限チェック
            if (!isAdmin(userId)) {
                response.put("success", false);
                response.put("message", "管理者権限が必要です");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            // メモリ状況
            MemoryOptimizationService.MemoryStatus memoryStatus = memoryOptimizationService.getCurrentMemoryStatus();
            Map<String, Object> memoryInfo = new HashMap<>();
            memoryInfo.put("heapUsed", memoryStatus.getHeapUsed());
            memoryInfo.put("heapMax", memoryStatus.getHeapMax());
            memoryInfo.put("heapAvailable", memoryStatus.getHeapAvailable());
            memoryInfo.put("heapUsagePercentage", memoryStatus.getHeapUsagePercentage());
            memoryInfo.put("formattedUsage", memoryStatus.getFormattedHeapUsage());
            memoryInfo.put("isHigh", memoryOptimizationService.isMemoryUsageHigh());
            memoryInfo.put("isCritical", memoryOptimizationService.isMemoryUsageCritical());

            // クエリ統計
            Map<String, QueryOptimizationService.QueryStatistics> queryStats = queryOptimizationService.getQueryStatistics();
            Map<String, Object> queryInfo = new HashMap<>();
            queryInfo.put("totalQueries", queryStats.size());
            queryInfo.put("totalExecutions", queryStats.values().stream()
                .mapToInt(QueryOptimizationService.QueryStatistics::getExecutionCount)
                .sum());
            queryInfo.put("totalErrors", queryStats.values().stream()
                .mapToInt(QueryOptimizationService.QueryStatistics::getErrorCount)
                .sum());

            response.put("success", true);
            response.put("memory", memoryInfo);
            response.put("queries", queryInfo);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("最適化状況取得エラー", e);
            response.put("success", false);
            response.put("message", "最適化状況の取得に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * メモリ最適化制御
     */
    @PostMapping("/memory/optimize")
    public ResponseEntity<Map<String, Object>> optimizeMemory(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);

            // 管理者権限チェック
            if (!isAdmin(userId)) {
                response.put("success", false);
                response.put("message", "管理者権限が必要です");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            // 実行前メモリ状況
            MemoryOptimizationService.MemoryStatus beforeStatus = memoryOptimizationService.getCurrentMemoryStatus();

            // 緊急メモリクリーンアップ実行
            memoryOptimizationService.performEmergencyCleanup();

            // 実行後メモリ状況
            MemoryOptimizationService.MemoryStatus afterStatus = memoryOptimizationService.getCurrentMemoryStatus();

            Map<String, Object> optimizationResult = new HashMap<>();
            optimizationResult.put("beforeUsage", beforeStatus.getHeapUsagePercentage());
            optimizationResult.put("afterUsage", afterStatus.getHeapUsagePercentage());
            optimizationResult.put("memoryFreed", beforeStatus.getHeapUsed() - afterStatus.getHeapUsed());
            optimizationResult.put("improvementPercentage",
                beforeStatus.getHeapUsagePercentage() - afterStatus.getHeapUsagePercentage());

            response.put("success", true);
            response.put("message", "メモリ最適化を実行しました");
            response.put("result", optimizationResult);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("メモリ最適化エラー", e);
            response.put("success", false);
            response.put("message", "メモリ最適化に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * メモリ監視制御
     */
    @PostMapping("/memory/monitoring/{action}")
    public ResponseEntity<Map<String, Object>> controlMemoryMonitoring(
            @PathVariable String action,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);

            // 管理者権限チェック
            if (!isAdmin(userId)) {
                response.put("success", false);
                response.put("message", "管理者権限が必要です");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            switch (action.toLowerCase()) {
                case "start":
                    memoryOptimizationService.startMemoryMonitoring();
                    response.put("message", "メモリ監視を開始しました");
                    break;
                case "stop":
                    memoryOptimizationService.stopMemoryMonitoring();
                    response.put("message", "メモリ監視を停止しました");
                    break;
                default:
                    response.put("success", false);
                    response.put("message", "無効なアクション: " + action);
                    return ResponseEntity.badRequest().body(response);
            }

            response.put("success", true);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("メモリ監視制御エラー", e);
            response.put("success", false);
            response.put("message", "メモリ監視制御に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * クエリ統計取得
     */
    @GetMapping("/queries/statistics")
    public ResponseEntity<Map<String, Object>> getQueryStatistics(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);

            // 管理者権限チェック
            if (!isAdmin(userId)) {
                response.put("success", false);
                response.put("message", "管理者権限が必要です");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            Map<String, QueryOptimizationService.QueryStatistics> queryStats = queryOptimizationService.getQueryStatistics();

            // 統計情報変換
            List<Map<String, Object>> statisticsList = queryStats.entrySet().stream()
                .map(entry -> {
                    QueryOptimizationService.QueryStatistics stats = entry.getValue();
                    Map<String, Object> statMap = new HashMap<>();
                    statMap.put("query", entry.getKey());
                    statMap.put("executionCount", stats.getExecutionCount());
                    statMap.put("averageExecutionTime", stats.getAverageExecutionTime());
                    statMap.put("minExecutionTime", stats.getMinExecutionTime());
                    statMap.put("maxExecutionTime", stats.getMaxExecutionTime());
                    statMap.put("averageResultCount", stats.getAverageResultCount());
                    statMap.put("errorCount", stats.getErrorCount());
                    return statMap;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("averageExecutionTime"), (Long) a.get("averageExecutionTime")))
                .toList();

            response.put("success", true);
            response.put("statistics", statisticsList);
            response.put("totalQueries", queryStats.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("クエリ統計取得エラー", e);
            response.put("success", false);
            response.put("message", "クエリ統計の取得に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 遅いクエリ取得
     */
    @GetMapping("/queries/slow")
    public ResponseEntity<Map<String, Object>> getSlowQueries(
            @RequestParam(defaultValue = "1000") int threshold,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);

            // 管理者権限チェック
            if (!isAdmin(userId)) {
                response.put("success", false);
                response.put("message", "管理者権限が必要です");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            List<QueryOptimizationService.QueryStatistics> slowQueries = queryOptimizationService.getSlowQueries(threshold);

            List<Map<String, Object>> slowQueriesList = slowQueries.stream()
                .limit(20) // 上位20件
                .map(stats -> {
                    Map<String, Object> queryMap = new HashMap<>();
                    queryMap.put("query", stats.getQuery());
                    queryMap.put("averageExecutionTime", stats.getAverageExecutionTime());
                    queryMap.put("executionCount", stats.getExecutionCount());
                    queryMap.put("maxExecutionTime", stats.getMaxExecutionTime());
                    queryMap.put("errorCount", stats.getErrorCount());
                    return queryMap;
                })
                .toList();

            response.put("success", true);
            response.put("slowQueries", slowQueriesList);
            response.put("threshold", threshold);
            response.put("count", slowQueriesList.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("遅いクエリ取得エラー", e);
            response.put("success", false);
            response.put("message", "遅いクエリの取得に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * システム推奨事項取得
     */
    @GetMapping("/recommendations")
    public ResponseEntity<Map<String, Object>> getOptimizationRecommendations(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);

            // 管理者権限チェック
            if (!isAdmin(userId)) {
                response.put("success", false);
                response.put("message", "管理者権限が必要です");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            List<Map<String, Object>> recommendations = new ArrayList<>();

            // メモリ関連の推奨事項
            MemoryOptimizationService.MemoryStatus memoryStatus = memoryOptimizationService.getCurrentMemoryStatus();
            if (memoryStatus.getHeapUsagePercentage() > 0.8) {
                Map<String, Object> memoryRec = new HashMap<>();
                memoryRec.put("type", "MEMORY");
                memoryRec.put("priority", "HIGH");
                memoryRec.put("title", "メモリ使用量が高い状態です");
                memoryRec.put("description", "メモリクリーンアップまたはヒープサイズの増加を検討してください");
                memoryRec.put("action", "メモリ最適化実行");
                recommendations.add(memoryRec);
            }

            // クエリ関連の推奨事項
            List<QueryOptimizationService.QueryStatistics> slowQueries = queryOptimizationService.getSlowQueries(2000);
            if (!slowQueries.isEmpty()) {
                Map<String, Object> queryRec = new HashMap<>();
                queryRec.put("type", "QUERY");
                queryRec.put("priority", "MEDIUM");
                queryRec.put("title", "遅いクエリが検出されました");
                queryRec.put("description", String.format("%d個のクエリが2秒以上の実行時間を要しています", slowQueries.size()));
                queryRec.put("action", "インデックス追加またはクエリ最適化");
                recommendations.add(queryRec);
            }

            // 一般的な推奨事項
            if (recommendations.isEmpty()) {
                Map<String, Object> normalRec = new HashMap<>();
                normalRec.put("type", "GENERAL");
                normalRec.put("priority", "LOW");
                normalRec.put("title", "システムは正常に動作しています");
                normalRec.put("description", "現在、特に最適化が必要な問題は検出されていません");
                normalRec.put("action", "定期的な監視を継続してください");
                recommendations.add(normalRec);
            }

            response.put("success", true);
            response.put("recommendations", recommendations);
            response.put("count", recommendations.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("最適化推奨事項取得エラー", e);
            response.put("success", false);
            response.put("message", "推奨事項の取得に失敗しました");
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
     * 管理者権限チェック
     */
    private boolean isAdmin(Long userId) {
        // 実際には ReportAccessControlService を使用
        return userId.equals(1L); // 仮実装
    }
}