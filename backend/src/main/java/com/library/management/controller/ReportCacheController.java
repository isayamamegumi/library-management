package com.library.management.controller;

import com.library.management.service.report.cache.ReportCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * レポートキャッシュコントローラー
 * キャッシュ管理API
 */
@RestController
@RequestMapping("/api/report-cache")
public class ReportCacheController {

    private static final Logger logger = LoggerFactory.getLogger(ReportCacheController.class);

    @Autowired
    private ReportCacheService cacheService;

    /**
     * キャッシュ統計取得
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getCacheStatistics(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);

            // 管理者権限チェック
            if (!isAdmin(userId)) {
                response.put("success", false);
                response.put("message", "管理者権限が必要です");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            ReportCacheService.CacheStatistics statistics = cacheService.getCacheStatistics();

            Map<String, Object> statsMap = new HashMap<>();
            statsMap.put("totalEntries", statistics.getTotalEntries());
            statsMap.put("validEntries", statistics.getValidEntries());
            statsMap.put("totalSizeBytes", statistics.getTotalSizeBytes());
            statsMap.put("formattedSize", statistics.getFormattedSize());
            statsMap.put("memoryCacheSize", statistics.getMemoryCacheSize());
            statsMap.put("averageHitCount", statistics.getAverageHitCount());
            statsMap.put("hitRate", statistics.getHitRate());
            statsMap.put("typeStatistics", statistics.getTypeStatistics());

            response.put("success", true);
            response.put("statistics", statsMap);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("キャッシュ統計取得エラー", e);
            response.put("success", false);
            response.put("message", "キャッシュ統計の取得に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * ユーザーキャッシュ無効化
     */
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> invalidateUserCache(
            @PathVariable Long userId,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            Long currentUserId = getUserId(authentication);

            // 権限チェック（管理者または本人のみ）
            if (!isAdmin(currentUserId) && !currentUserId.equals(userId)) {
                response.put("success", false);
                response.put("message", "権限がありません");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            cacheService.invalidateUserCaches(userId);

            response.put("success", true);
            response.put("message", "ユーザーキャッシュを無効化しました");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("ユーザーキャッシュ無効化エラー: userId={}", userId, e);
            response.put("success", false);
            response.put("message", "キャッシュ無効化に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * レポートタイプ別キャッシュ無効化
     */
    @DeleteMapping("/type/{reportType}")
    public ResponseEntity<Map<String, Object>> invalidateCacheByType(
            @PathVariable String reportType,
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

            cacheService.invalidateCachesByReportType(reportType);

            response.put("success", true);
            response.put("message", "レポートタイプ別キャッシュを無効化しました");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("レポートタイプ別キャッシュ無効化エラー: reportType={}", reportType, e);
            response.put("success", false);
            response.put("message", "キャッシュ無効化に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 特定キャッシュ無効化
     */
    @DeleteMapping("/key/{cacheKey}")
    public ResponseEntity<Map<String, Object>> invalidateSpecificCache(
            @PathVariable String cacheKey,
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

            cacheService.invalidateCache(cacheKey);

            response.put("success", true);
            response.put("message", "指定キャッシュを無効化しました");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("指定キャッシュ無効化エラー: cacheKey={}", cacheKey, e);
            response.put("success", false);
            response.put("message", "キャッシュ無効化に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 手動キャッシュクリーンアップ
     */
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> performManualCleanup(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);

            // 管理者権限チェック
            if (!isAdmin(userId)) {
                response.put("success", false);
                response.put("message", "管理者権限が必要です");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            // 手動クリーンアップ実行
            cacheService.performScheduledCleanup();

            response.put("success", true);
            response.put("message", "手動キャッシュクリーンアップを実行しました");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("手動キャッシュクリーンアップエラー", e);
            response.put("success", false);
            response.put("message", "キャッシュクリーンアップに失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * キャッシュ設定情報取得
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getCacheConfig(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);

            // 管理者権限チェック
            if (!isAdmin(userId)) {
                response.put("success", false);
                response.put("message", "管理者権限が必要です");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            Map<String, Object> config = new HashMap<>();
            config.put("cacheEnabled", true); // 実際の設定値を取得
            config.put("defaultTtlMinutes", 60);
            config.put("maxCacheSizeMb", 500);
            config.put("maxEntriesPerUser", 10);
            config.put("cleanupIntervalMinutes", 30);

            response.put("success", true);
            response.put("config", config);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("キャッシュ設定取得エラー", e);
            response.put("success", false);
            response.put("message", "キャッシュ設定の取得に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * ユーザー自身のキャッシュ状況取得
     */
    @GetMapping("/my-cache")
    public ResponseEntity<Map<String, Object>> getMyCache(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);

            // ユーザー固有の統計を取得（実装簡略化）
            Map<String, Object> userCacheInfo = new HashMap<>();
            userCacheInfo.put("message", "ユーザー個別キャッシュ情報は管理者向け機能で確認できます");

            response.put("success", true);
            response.put("cacheInfo", userCacheInfo);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("ユーザーキャッシュ情報取得エラー", e);
            response.put("success", false);
            response.put("message", "キャッシュ情報の取得に失敗しました");
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