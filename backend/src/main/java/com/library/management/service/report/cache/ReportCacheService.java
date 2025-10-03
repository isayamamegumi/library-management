package com.library.management.service.report.cache;

import com.library.management.dto.ReportRequest;
import com.library.management.entity.ReportCache;
import com.library.management.repository.ReportCacheRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 帳票キャッシュサービス
 * 帳票生成結果のキャッシュ管理を行う
 */
@Service
@Transactional
public class ReportCacheService {

    private static final Logger logger = LoggerFactory.getLogger(ReportCacheService.class);

    @Autowired
    private ReportCacheRepository cacheRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.report.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.report.cache.default-ttl-minutes:60}")
    private int defaultTtlMinutes;

    @Value("${app.report.cache.max-size-mb:500}")
    private int maxCacheSizeMb;

    @Value("${app.report.cache.max-entries-per-user:10}")
    private int maxEntriesPerUser;

    @Value("${app.report.cache.cleanup-interval-minutes:30}")
    private int cleanupIntervalMinutes;

    @Value("${app.report.cache.system-report-ttl-minutes:60}")
    private int systemReportTtlMinutes;

    // インメモリキャッシュ（高速アクセス用）
    private final Map<String, CacheEntry> memoryCache = new ConcurrentHashMap<>();

    /**
     * キャッシュから帳票取得
     */
    public CacheResult getCachedReport(Long userId, ReportRequest request) {
        if (!cacheEnabled) {
            return CacheResult.miss("キャッシュが無効です");
        }

        try {
            String cacheKey = generateCacheKey(userId, request);
            logger.debug("キャッシュ検索開始: key={}", cacheKey);

            // インメモリキャッシュをまず確認
            CacheEntry memoryEntry = memoryCache.get(cacheKey);
            if (memoryEntry != null && memoryEntry.isValid()) {
                logger.debug("インメモリキャッシュヒット: key={}", cacheKey);
                memoryEntry.recordHit();
                return CacheResult.hit(memoryEntry.getFilePath(), memoryEntry);
            }

            // データベースキャッシュ確認
            String parametersJson = objectMapper.writeValueAsString(request);
            Optional<ReportCache> cacheOpt = cacheRepository.findValidCache(
                userId, request.getReportType(), request.getFormat(),
                parametersJson, LocalDateTime.now()
            );

            if (cacheOpt.isPresent()) {
                ReportCache cache = cacheOpt.get();

                // ファイル存在確認
                if (cache.getFilePath() != null && new File(cache.getFilePath()).exists()) {
                    // キャッシュヒット記録
                    cache.recordHit();
                    cacheRepository.save(cache);

                    // インメモリキャッシュに追加
                    memoryCache.put(cacheKey, new CacheEntry(cache));

                    logger.info("キャッシュヒット: key={}, hitCount={}", cacheKey, cache.getHitCount());
                    return CacheResult.hit(cache.getFilePath(), cache);
                } else {
                    // ファイルが存在しない場合はキャッシュ無効化
                    cache.invalidate();
                    cacheRepository.save(cache);
                    memoryCache.remove(cacheKey);
                    logger.warn("キャッシュファイルが存在しません: key={}, path={}", cacheKey, cache.getFilePath());
                }
            }

            logger.debug("キャッシュミス: key={}", cacheKey);
            return CacheResult.miss("該当するキャッシュが見つかりません");

        } catch (Exception e) {
            logger.error("キャッシュ取得エラー: userId={}, reportType={}", userId, request.getReportType(), e);
            return CacheResult.miss("キャッシュ取得エラー: " + e.getMessage());
        }
    }

    /**
     * 帳票をキャッシュに保存
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ReportCache cacheReport(Long userId, ReportRequest request, String filePath,
                                  Integer recordCount, Long generationTimeMs) {
        if (!cacheEnabled) {
            return null;
        }

        try {
            String cacheKey = generateCacheKey(userId, request);
            logger.debug("キャッシュ保存開始: key={}, filePath={}", cacheKey, filePath);

            // キャッシュ容量チェック
            if (!checkCacheCapacity(userId)) {
                logger.warn("キャッシュ容量制限のため保存をスキップ: userId={}", userId);
                return null;
            }

            String parametersJson = objectMapper.writeValueAsString(request);

            // 既存キャッシュエントリ確認（cacheKeyベースで検索）
            Optional<ReportCache> existingOpt = cacheRepository.findByCacheKeyAndIsValidTrue(cacheKey);

            ReportCache cache;
            if (existingOpt.isPresent()) {
                // 既存エントリ更新
                cache = existingOpt.get();
                logger.debug("既存キャッシュエントリ更新: id={}, cacheKey={}", cache.getId(), cacheKey);
            } else {
                // 新規エントリ作成
                cache = new ReportCache(cacheKey, userId, request.getReportType(),
                    request.getFormat(), parametersJson);
                logger.debug("新規キャッシュエントリ作成: cacheKey={}", cacheKey);
            }

            // ファイル情報設定
            File file = new File(filePath);
            cache.markCompleted(filePath, file.length(), recordCount, generationTimeMs);

            // 有効期限設定
            cache.setExpiresAt(LocalDateTime.now().plusMinutes(getTtlMinutes(request)));

            // メタデータ設定
            Map<String, Object> metadata = createMetadata(request, recordCount, generationTimeMs);
            cache.setMetadata(objectMapper.writeValueAsString(metadata));

            ReportCache savedCache = cacheRepository.save(cache);

            // インメモリキャッシュに追加
            memoryCache.put(cacheKey, new CacheEntry(savedCache));

            logger.info("キャッシュ保存完了: key={}, id={}, fileSize={}",
                cacheKey, savedCache.getId(), file.length());

            return savedCache;

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // ユニーク制約違反の場合は既存エントリを取得して更新を試みる
            logger.warn("キャッシュキー重複検出、既存エントリを更新します: userId={}", userId);
            try {
                String cacheKey = generateCacheKey(userId, request);
                Optional<ReportCache> existingOpt = cacheRepository.findByCacheKeyAndIsValidTrue(cacheKey);
                if (existingOpt.isPresent()) {
                    ReportCache cache = existingOpt.get();
                    File file = new File(filePath);
                    cache.markCompleted(filePath, file.length(), recordCount, generationTimeMs);
                    cache.setExpiresAt(LocalDateTime.now().plusMinutes(getTtlMinutes(request)));
                    return cacheRepository.save(cache);
                }
            } catch (Exception ex) {
                logger.error("キャッシュ更新リトライ失敗", ex);
            }
            return null;
        } catch (Exception e) {
            logger.error("キャッシュ保存エラー: userId={}, filePath={}", userId, filePath, e);
            return null;
        }
    }

    /**
     * キャッシュキー生成
     */
    private String generateCacheKey(Long userId, ReportRequest request) {
        try {
            // キャッシュキーの構成要素
            StringBuilder keyBuilder = new StringBuilder();

            // SYSTEM権限の場合は特別な処理（全ユーザーデータ対象）
            if ("SYSTEM".equalsIgnoreCase(request.getReportType())) {
                keyBuilder.append("scope:ALL_USERS");
            } else {
                keyBuilder.append("user:").append(userId);
            }

            keyBuilder.append("|type:").append(request.getReportType());
            keyBuilder.append("|format:").append(request.getFormat());
            keyBuilder.append("|template:").append(request.getTemplateId());

            // フィルター情報
            if (request.getFilters() != null) {
                String filtersJson = objectMapper.writeValueAsString(request.getFilters());
                keyBuilder.append("|filters:").append(filtersJson);
            }

            // オプション情報（キャッシュに影響するもののみ）
            if (request.getOptions() != null) {
                Map<String, Object> cacheRelevantOptions = extractCacheRelevantOptions(request.getOptions());
                if (!cacheRelevantOptions.isEmpty()) {
                    String optionsJson = objectMapper.writeValueAsString(cacheRelevantOptions);
                    keyBuilder.append("|options:").append(optionsJson);
                }
            }

            // ハッシュ化
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(keyBuilder.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, 32);

        } catch (Exception e) {
            logger.error("キャッシュキー生成エラー", e);
            return UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        }
    }

    /**
     * キャッシュに関連するオプション抽出
     */
    private Map<String, Object> extractCacheRelevantOptions(ReportRequest.ReportOptions options) {
        Map<String, Object> relevant = new HashMap<>();

        if (options.getSortBy() != null) {
            relevant.put("sortBy", options.getSortBy());
        }
        if (options.getSortOrder() != null) {
            relevant.put("sortOrder", options.getSortOrder());
        }

        // カスタムオプションの一部のみ
        if (options.getCustomOptions() != null) {
            Map<String, Object> customOptions = options.getCustomOptions();
            if (customOptions.containsKey("includeStatistics")) {
                relevant.put("includeStatistics", customOptions.get("includeStatistics"));
            }
            if (customOptions.containsKey("groupBy")) {
                relevant.put("groupBy", customOptions.get("groupBy"));
            }
        }

        return relevant;
    }

    /**
     * TTL取得
     */
    private int getTtlMinutes(ReportRequest request) {
        // レポートタイプに応じたTTL設定
        switch (request.getReportType().toUpperCase()) {
            case "READING_STATS":
                return 120; // 2時間
            case "BOOK_LIST":
                return 60;  // 1時間
            case "SYSTEM":
                return systemReportTtlMinutes;  // 設定値使用（60分）
            default:
                return defaultTtlMinutes;
        }
    }

    /**
     * メタデータ作成
     */
    private Map<String, Object> createMetadata(ReportRequest request, Integer recordCount, Long generationTimeMs) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("recordCount", recordCount);
        metadata.put("generationTimeMs", generationTimeMs);
        metadata.put("reportType", request.getReportType());
        metadata.put("format", request.getFormat());
        metadata.put("templateId", request.getTemplateId());
        metadata.put("cachedAt", LocalDateTime.now().toString());
        return metadata;
    }

    /**
     * キャッシュ容量チェック
     */
    private boolean checkCacheCapacity(Long userId) {
        try {
            // ユーザー別エントリ数制限
            long userCacheCount = cacheRepository.countValidCachesByUser(userId);
            if (userCacheCount >= maxEntriesPerUser) {
                // 古いキャッシュを削除
                cleanupOldCachesForUser(userId);
            }

            // 全体容量制限
            Long totalSize = cacheRepository.getTotalCacheSize();
            if (totalSize != null && totalSize > maxCacheSizeMb * 1024 * 1024) {
                // 容量制限超過時のクリーンアップ
                cleanupBySize();
            }

            return true;

        } catch (Exception e) {
            logger.error("キャッシュ容量チェックエラー", e);
            return false;
        }
    }

    /**
     * ユーザー用古いキャッシュクリーンアップ
     */
    private void cleanupOldCachesForUser(Long userId) {
        try {
            List<ReportCache> userCaches = cacheRepository.findByUserIdAndIsValidTrueOrderByCreatedAtDesc(userId);

            if (userCaches.size() > maxEntriesPerUser) {
                List<ReportCache> toDelete = userCaches.subList(maxEntriesPerUser, userCaches.size());

                for (ReportCache cache : toDelete) {
                    invalidateCache(cache);
                }

                logger.info("ユーザー古いキャッシュクリーンアップ: userId={}, deleted={}", userId, toDelete.size());
            }

        } catch (Exception e) {
            logger.error("ユーザー古いキャッシュクリーンアップエラー: userId={}", userId, e);
        }
    }

    /**
     * 容量制限によるクリーンアップ
     */
    private void cleanupBySize() {
        try {
            // アクセス頻度の低いキャッシュから削除
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(2);
            List<ReportCache> unusedCaches = cacheRepository.findUnusedCaches(cutoffTime);

            for (ReportCache cache : unusedCaches) {
                invalidateCache(cache);
            }

            logger.info("容量制限クリーンアップ: deleted={}", unusedCaches.size());

        } catch (Exception e) {
            logger.error("容量制限クリーンアップエラー", e);
        }
    }

    /**
     * キャッシュ無効化
     */
    public void invalidateCache(String cacheKey) {
        try {
            Optional<ReportCache> cacheOpt = cacheRepository.findByCacheKeyAndIsValidTrue(cacheKey);
            if (cacheOpt.isPresent()) {
                invalidateCache(cacheOpt.get());
                logger.info("キャッシュ無効化: key={}", cacheKey);
            }
        } catch (Exception e) {
            logger.error("キャッシュ無効化エラー: key={}", cacheKey, e);
        }
    }

    /**
     * キャッシュエントリ無効化
     */
    private void invalidateCache(ReportCache cache) {
        try {
            // ファイル削除
            if (cache.getFilePath() != null) {
                File file = new File(cache.getFilePath());
                if (file.exists()) {
                    file.delete();
                }
            }

            // データベース更新
            cache.invalidate();
            cacheRepository.save(cache);

            // インメモリキャッシュから削除
            memoryCache.remove(cache.getCacheKey());

        } catch (Exception e) {
            logger.error("キャッシュエントリ無効化エラー: id={}", cache.getId(), e);
        }
    }

    /**
     * ユーザーキャッシュ無効化
     */
    public void invalidateUserCaches(Long userId) {
        try {
            List<ReportCache> userCaches = cacheRepository.findByUserIdAndIsValidTrueOrderByCreatedAtDesc(userId);

            for (ReportCache cache : userCaches) {
                invalidateCache(cache);
            }

            logger.info("ユーザーキャッシュ無効化: userId={}, count={}", userId, userCaches.size());

        } catch (Exception e) {
            logger.error("ユーザーキャッシュ無効化エラー: userId={}", userId, e);
        }
    }

    /**
     * レポートタイプ別キャッシュ無効化
     */
    public void invalidateCachesByReportType(String reportType) {
        try {
            List<ReportCache> typeCaches = cacheRepository.findUserCachesByType(null, reportType);

            for (ReportCache cache : typeCaches) {
                invalidateCache(cache);
            }

            logger.info("レポートタイプ別キャッシュ無効化: reportType={}, count={}", reportType, typeCaches.size());

        } catch (Exception e) {
            logger.error("レポートタイプ別キャッシュ無効化エラー: reportType={}", reportType, e);
        }
    }

    /**
     * 定期的なキャッシュクリーンアップ
     */
    @Scheduled(fixedRateString = "${app.report.cache.cleanup-interval-minutes:30}000")
    public void performScheduledCleanup() {
        try {
            logger.debug("定期キャッシュクリーンアップ開始");

            // 期限切れキャッシュ削除
            List<ReportCache> expiredCaches = cacheRepository.findExpiredCaches(LocalDateTime.now());
            for (ReportCache cache : expiredCaches) {
                invalidateCache(cache);
            }

            // 長期間未使用キャッシュ削除
            LocalDateTime unusedCutoff = LocalDateTime.now().minusHours(24);
            List<ReportCache> unusedCaches = cacheRepository.findUnusedCaches(unusedCutoff);
            for (ReportCache cache : unusedCaches) {
                invalidateCache(cache);
            }

            // インメモリキャッシュクリーンアップ
            cleanupMemoryCache();

            logger.info("定期キャッシュクリーンアップ完了: expired={}, unused={}",
                expiredCaches.size(), unusedCaches.size());

        } catch (Exception e) {
            logger.error("定期キャッシュクリーンアップエラー", e);
        }
    }

    /**
     * インメモリキャッシュクリーンアップ
     */
    private void cleanupMemoryCache() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);

        memoryCache.entrySet().removeIf(entry -> {
            CacheEntry cacheEntry = entry.getValue();
            return !cacheEntry.isValid() || cacheEntry.getLastAccessTime().isBefore(cutoff);
        });
    }

    /**
     * キャッシュ統計取得
     */
    public CacheStatistics getCacheStatistics() {
        try {
            CacheStatistics stats = new CacheStatistics();

            // 基本統計
            stats.setTotalEntries(cacheRepository.count());
            stats.setValidEntries(cacheRepository.findByStatus("COMPLETED").size());
            stats.setTotalSizeBytes(cacheRepository.getTotalCacheSize());
            stats.setMemoryCacheSize(memoryCache.size());

            // ヒット率統計
            Double avgHitCount = cacheRepository.getAverageHitCount();
            stats.setAverageHitCount(avgHitCount != null ? avgHitCount : 0.0);

            // タイプ別統計
            List<Object[]> typeStats = cacheRepository.getCacheStatsByReportType();
            Map<String, Long> typeCountMap = new HashMap<>();
            for (Object[] stat : typeStats) {
                typeCountMap.put((String) stat[0], ((Number) stat[1]).longValue());
            }
            stats.setTypeStatistics(typeCountMap);

            return stats;

        } catch (Exception e) {
            logger.error("キャッシュ統計取得エラー", e);
            return new CacheStatistics();
        }
    }

    /**
     * キャッシュ結果クラス
     */
    public static class CacheResult {
        private final boolean hit;
        private final String filePath;
        private final String message;
        private final Object cacheInfo;

        private CacheResult(boolean hit, String filePath, String message, Object cacheInfo) {
            this.hit = hit;
            this.filePath = filePath;
            this.message = message;
            this.cacheInfo = cacheInfo;
        }

        public static CacheResult hit(String filePath, Object cacheInfo) {
            return new CacheResult(true, filePath, "キャッシュヒット", cacheInfo);
        }

        public static CacheResult miss(String message) {
            return new CacheResult(false, null, message, null);
        }

        // Getters
        public boolean isHit() { return hit; }
        public String getFilePath() { return filePath; }
        public String getMessage() { return message; }
        public Object getCacheInfo() { return cacheInfo; }
    }

    /**
     * インメモリキャッシュエントリクラス
     */
    private static class CacheEntry {
        private final String filePath;
        private final LocalDateTime expiresAt;
        private LocalDateTime lastAccessTime;
        private int hitCount;

        public CacheEntry(ReportCache cache) {
            this.filePath = cache.getFilePath();
            this.expiresAt = cache.getExpiresAt();
            this.lastAccessTime = cache.getLastAccessTime();
            this.hitCount = cache.getHitCount();
        }

        public boolean isValid() {
            return expiresAt == null || LocalDateTime.now().isBefore(expiresAt);
        }

        public void recordHit() {
            this.hitCount++;
            this.lastAccessTime = LocalDateTime.now();
        }

        // Getters
        public String getFilePath() { return filePath; }
        public LocalDateTime getLastAccessTime() { return lastAccessTime; }
    }

    /**
     * キャッシュ統計クラス
     */
    public static class CacheStatistics {
        private long totalEntries;
        private long validEntries;
        private Long totalSizeBytes;
        private int memoryCacheSize;
        private double averageHitCount;
        private Map<String, Long> typeStatistics = new HashMap<>();

        // Getters and Setters
        public long getTotalEntries() { return totalEntries; }
        public void setTotalEntries(long totalEntries) { this.totalEntries = totalEntries; }

        public long getValidEntries() { return validEntries; }
        public void setValidEntries(long validEntries) { this.validEntries = validEntries; }

        public Long getTotalSizeBytes() { return totalSizeBytes; }
        public void setTotalSizeBytes(Long totalSizeBytes) { this.totalSizeBytes = totalSizeBytes; }

        public int getMemoryCacheSize() { return memoryCacheSize; }
        public void setMemoryCacheSize(int memoryCacheSize) { this.memoryCacheSize = memoryCacheSize; }

        public double getAverageHitCount() { return averageHitCount; }
        public void setAverageHitCount(double averageHitCount) { this.averageHitCount = averageHitCount; }

        public Map<String, Long> getTypeStatistics() { return typeStatistics; }
        public void setTypeStatistics(Map<String, Long> typeStatistics) { this.typeStatistics = typeStatistics; }

        public double getHitRate() {
            return totalEntries > 0 ? (averageHitCount / totalEntries) : 0.0;
        }

        public String getFormattedSize() {
            if (totalSizeBytes == null || totalSizeBytes == 0) return "0 B";
            if (totalSizeBytes < 1024) return totalSizeBytes + " B";
            if (totalSizeBytes < 1024 * 1024) return String.format("%.1f KB", totalSizeBytes / 1024.0);
            if (totalSizeBytes < 1024 * 1024 * 1024) return String.format("%.1f MB", totalSizeBytes / (1024.0 * 1024));
            return String.format("%.1f GB", totalSizeBytes / (1024.0 * 1024 * 1024));
        }
    }
}