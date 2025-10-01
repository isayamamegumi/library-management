package com.library.management.service.report.cache;

import com.library.management.dto.ReportRequest;
import com.library.management.entity.ReportCache;
import com.library.management.repository.ReportCacheRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReportCacheServiceのテストクラス
 */
@ExtendWith(MockitoExtension.class)
class ReportCacheServiceTest {

    @Mock
    private ReportCacheRepository cacheRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ReportCacheService reportCacheService;

    private ReportRequest testRequest;
    private ReportCache testCache;
    private Long testUserId;
    private String testFilePath;

    @BeforeEach
    void setUp() throws IOException {
        testUserId = 1L;
        testFilePath = "/tmp/test_report.pdf";

        // テスト用リクエスト作成
        testRequest = new ReportRequest();
        testRequest.setReportType("BOOK_LIST");
        testRequest.setFormat("PDF");
        testRequest.setTemplateId(1L);

        // ReportRequest.ReportOptionsオブジェクト作成
        ReportRequest.ReportOptions options = new ReportRequest.ReportOptions();
        options.setSortBy("title");
        options.setSortOrder("ASC");
        Map<String, Object> customOptions = new HashMap<>();
        customOptions.put("includeStatistics", true);
        options.setCustomOptions(customOptions);
        testRequest.setOptions(options);

        // テスト用キャッシュエンティティ作成
        testCache = new ReportCache("test_cache_key", testUserId, "BOOK_LIST", "PDF", "{}");
        testCache.setId(1L);
        testCache.setFilePath(testFilePath);
        testCache.setExpiresAt(LocalDateTime.now().plusHours(1));
        testCache.setCacheStatus("COMPLETED");

        // ReflectionTestUtilsを使用してフィールド値を設定
        ReflectionTestUtils.setField(reportCacheService, "cacheEnabled", true);
        ReflectionTestUtils.setField(reportCacheService, "defaultTtlMinutes", 60);
        ReflectionTestUtils.setField(reportCacheService, "maxEntriesPerUser", 10);
        ReflectionTestUtils.setField(reportCacheService, "maxCacheSizeMb", 500);

        // ObjectMapperのモック設定
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    @Test
    void testGetCachedReportHit() throws IOException {
        // モック設定
        when(cacheRepository.findValidCache(eq(testUserId), eq("BOOK_LIST"), eq("PDF"), anyString(), any(LocalDateTime.class)))
            .thenReturn(Optional.of(testCache));

        // テスト対象のファイルを実際に作成（一時的）
        File tempFile = new File(testFilePath);
        tempFile.getParentFile().mkdirs();
        tempFile.createNewFile();

        try {
            // テスト実行
            ReportCacheService.CacheResult result = reportCacheService.getCachedReport(testUserId, testRequest);

            // 検証
            assertTrue(result.isHit());
            assertEquals(testFilePath, result.getFilePath());
            verify(cacheRepository).save(testCache);
            assertEquals(1, testCache.getHitCount());
        } finally {
            // クリーンアップ
            tempFile.delete();
        }
    }

    @Test
    void testGetCachedReportMiss() {
        // モック設定（キャッシュが見つからない）
        when(cacheRepository.findValidCache(eq(testUserId), eq("BOOK_LIST"), eq("PDF"), anyString(), any(LocalDateTime.class)))
            .thenReturn(Optional.empty());

        // テスト実行
        ReportCacheService.CacheResult result = reportCacheService.getCachedReport(testUserId, testRequest);

        // 検証
        assertFalse(result.isHit());
        assertNull(result.getFilePath());
        assertEquals("該当するキャッシュが見つかりません", result.getMessage());
    }

    @Test
    void testGetCachedReportFileNotExists() {
        // モック設定（キャッシュは存在するがファイルが存在しない）
        when(cacheRepository.findValidCache(eq(testUserId), eq("BOOK_LIST"), eq("PDF"), anyString(), any(LocalDateTime.class)))
            .thenReturn(Optional.of(testCache));

        // テスト実行（存在しないファイルパス）
        ReportCacheService.CacheResult result = reportCacheService.getCachedReport(testUserId, testRequest);

        // 検証
        assertFalse(result.isHit());
        verify(cacheRepository).save(testCache);
        assertFalse(testCache.getIsValid());
    }

    @Test
    void testCacheReport() throws IOException {
        // モック設定
        when(cacheRepository.findValidCache(eq(testUserId), eq("BOOK_LIST"), eq("PDF"), anyString(), any(LocalDateTime.class)))
            .thenReturn(Optional.empty());
        when(cacheRepository.countValidCachesByUser(testUserId)).thenReturn(5L);
        when(cacheRepository.getTotalCacheSize()).thenReturn(100L * 1024 * 1024); // 100MB
        when(cacheRepository.save(any(ReportCache.class))).thenReturn(testCache);

        // テスト対象のファイルを実際に作成
        File tempFile = new File(testFilePath);
        tempFile.getParentFile().mkdirs();
        tempFile.createNewFile();

        try {
            // テスト実行
            ReportCache result = reportCacheService.cacheReport(testUserId, testRequest, testFilePath, 100, 5000L);

            // 検証
            assertNotNull(result);
            verify(cacheRepository).save(any(ReportCache.class));
        } finally {
            // クリーンアップ
            tempFile.delete();
        }
    }

    @Test
    void testCacheReportDisabled() {
        // キャッシュ無効設定
        ReflectionTestUtils.setField(reportCacheService, "cacheEnabled", false);

        // テスト実行
        ReportCache result = reportCacheService.cacheReport(testUserId, testRequest, testFilePath, 100, 5000L);

        // 検証
        assertNull(result);
        verify(cacheRepository, never()).save(any());
    }

    @Test
    void testInvalidateCache() {
        // モック設定
        when(cacheRepository.findByCacheKeyAndIsValidTrue("test_key")).thenReturn(Optional.of(testCache));

        // テスト実行
        reportCacheService.invalidateCache("test_key");

        // 検証
        verify(cacheRepository).save(testCache);
        assertFalse(testCache.getIsValid());
        assertEquals("INVALID", testCache.getCacheStatus());
    }

    @Test
    void testInvalidateUserCaches() {
        // モック設定
        List<ReportCache> userCaches = new ArrayList<>();
        userCaches.add(testCache);
        when(cacheRepository.findByUserIdAndIsValidTrueOrderByCreatedAtDesc(testUserId))
            .thenReturn(userCaches);

        // テスト実行
        reportCacheService.invalidateUserCaches(testUserId);

        // 検証
        verify(cacheRepository).save(testCache);
        assertFalse(testCache.getIsValid());
    }

    @Test
    void testInvalidateCachesByReportType() {
        // モック設定
        List<ReportCache> typeCaches = new ArrayList<>();
        typeCaches.add(testCache);
        when(cacheRepository.findUserCachesByType(null, "BOOK_LIST"))
            .thenReturn(typeCaches);

        // テスト実行
        reportCacheService.invalidateCachesByReportType("BOOK_LIST");

        // 検証
        verify(cacheRepository).save(testCache);
        assertFalse(testCache.getIsValid());
    }

    @Test
    void testPerformScheduledCleanup() {
        // モック設定
        List<ReportCache> expiredCaches = new ArrayList<>();
        expiredCaches.add(testCache);
        when(cacheRepository.findExpiredCaches(any(LocalDateTime.class)))
            .thenReturn(expiredCaches);
        when(cacheRepository.findUnusedCaches(any(LocalDateTime.class)))
            .thenReturn(new ArrayList<>());

        // テスト実行
        reportCacheService.performScheduledCleanup();

        // 検証
        verify(cacheRepository).findExpiredCaches(any(LocalDateTime.class));
        verify(cacheRepository).findUnusedCaches(any(LocalDateTime.class));
        verify(cacheRepository).save(testCache);
    }

    @Test
    void testGetCacheStatistics() {
        // モック設定
        when(cacheRepository.count()).thenReturn(10L);
        when(cacheRepository.findByStatus("COMPLETED")).thenReturn(List.of(testCache));
        when(cacheRepository.getTotalCacheSize()).thenReturn(1024L * 1024); // 1MB
        when(cacheRepository.getAverageHitCount()).thenReturn(2.5);

        List<Object[]> typeStats = new ArrayList<>();
        typeStats.add(new Object[]{"BOOK_LIST", 5L});
        typeStats.add(new Object[]{"READING_STATS", 3L});
        when(cacheRepository.getCacheStatsByReportType()).thenReturn(typeStats);

        // テスト実行
        ReportCacheService.CacheStatistics stats = reportCacheService.getCacheStatistics();

        // 検証
        assertNotNull(stats);
        assertEquals(10L, stats.getTotalEntries());
        assertEquals(1L, stats.getValidEntries());
        assertEquals(1024L * 1024, stats.getTotalSizeBytes());
        assertEquals(2.5, stats.getAverageHitCount());
        assertEquals("1.0 MB", stats.getFormattedSize());

        Map<String, Long> typeStatistics = stats.getTypeStatistics();
        assertEquals(2, typeStatistics.size());
        assertEquals(5L, typeStatistics.get("BOOK_LIST"));
        assertEquals(3L, typeStatistics.get("READING_STATS"));
    }

    @Test
    void testCacheCapacityCheck() throws IOException {
        // ユーザーの制限を超過する設定
        when(cacheRepository.countValidCachesByUser(testUserId)).thenReturn(15L);
        List<ReportCache> userCaches = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            ReportCache cache = new ReportCache("key_" + i, testUserId, "BOOK_LIST", "PDF", "{}");
            cache.setId((long) i);
            userCaches.add(cache);
        }
        when(cacheRepository.findByUserIdAndIsValidTrueOrderByCreatedAtDesc(testUserId))
            .thenReturn(userCaches);

        // 全体容量は制限内
        when(cacheRepository.getTotalCacheSize()).thenReturn(100L * 1024 * 1024); // 100MB

        // テスト対象のファイルを実際に作成
        File tempFile = new File(testFilePath);
        tempFile.getParentFile().mkdirs();
        tempFile.createNewFile();

        try {
            // テスト実行
            ReportCache result = reportCacheService.cacheReport(testUserId, testRequest, testFilePath, 100, 5000L);

            // 検証：古いキャッシュのクリーンアップが実行される
            verify(cacheRepository, atLeastOnce()).save(any(ReportCache.class));
        } finally {
            // クリーンアップ
            tempFile.delete();
        }
    }
}