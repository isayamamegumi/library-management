package com.library.management.controller;

import com.library.management.service.report.cache.ReportCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ReportCacheControllerのテストクラス
 */
@ExtendWith(MockitoExtension.class)
class ReportCacheControllerTest {

    @Mock
    private ReportCacheService cacheService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ReportCacheController reportCacheController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(reportCacheController).build();
        objectMapper = new ObjectMapper();

        // 認証モックの設定
        when(authentication.getName()).thenReturn("1"); // userId = 1 (管理者)
    }

    @Test
    void testGetCacheStatistics_Success() throws Exception {
        // モック設定
        ReportCacheService.CacheStatistics mockStats = new ReportCacheService.CacheStatistics();
        mockStats.setTotalEntries(100L);
        mockStats.setValidEntries(80L);
        mockStats.setTotalSizeBytes(1024L * 1024 * 50); // 50MB
        mockStats.setMemoryCacheSize(10);
        mockStats.setAverageHitCount(2.5);

        Map<String, Long> typeStats = new HashMap<>();
        typeStats.put("BOOK_LIST", 50L);
        typeStats.put("READING_STATS", 30L);
        mockStats.setTypeStatistics(typeStats);

        when(cacheService.getCacheStatistics()).thenReturn(mockStats);

        // テスト実行
        mockMvc.perform(get("/api/report-cache/statistics")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.statistics.totalEntries").value(100))
                .andExpect(jsonPath("$.statistics.validEntries").value(80))
                .andExpect(jsonPath("$.statistics.memoryCacheSize").value(10))
                .andExpect(jsonPath("$.statistics.averageHitCount").value(2.5));

        verify(cacheService).getCacheStatistics();
    }

    @Test
    void testGetCacheStatistics_Forbidden() throws Exception {
        // 非管理者ユーザー
        when(authentication.getName()).thenReturn("2"); // userId = 2 (一般ユーザー)

        // テスト実行
        mockMvc.perform(get("/api/report-cache/statistics")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("管理者権限が必要です"));

        verify(cacheService, never()).getCacheStatistics();
    }

    @Test
    void testInvalidateUserCache_Success() throws Exception {
        Long targetUserId = 2L;

        // 管理者として実行
        when(authentication.getName()).thenReturn("1"); // 管理者

        // テスト実行
        mockMvc.perform(delete("/api/report-cache/user/{userId}", targetUserId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ユーザーキャッシュを無効化しました"));

        verify(cacheService).invalidateUserCaches(targetUserId);
    }

    @Test
    void testInvalidateUserCache_SelfAccess() throws Exception {
        Long targetUserId = 2L;

        // 本人として実行
        when(authentication.getName()).thenReturn("2");

        // テスト実行
        mockMvc.perform(delete("/api/report-cache/user/{userId}", targetUserId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ユーザーキャッシュを無効化しました"));

        verify(cacheService).invalidateUserCaches(targetUserId);
    }

    @Test
    void testInvalidateUserCache_Forbidden() throws Exception {
        Long targetUserId = 3L;

        // 他のユーザーとして実行
        when(authentication.getName()).thenReturn("2"); // userId = 2が他のユーザー3を操作しようとする

        // テスト実行
        mockMvc.perform(delete("/api/report-cache/user/{userId}", targetUserId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("権限がありません"));

        verify(cacheService, never()).invalidateUserCaches(any());
    }

    @Test
    void testInvalidateCacheByType_Success() throws Exception {
        String reportType = "BOOK_LIST";

        // 管理者として実行
        when(authentication.getName()).thenReturn("1");

        // テスト実行
        mockMvc.perform(delete("/api/report-cache/type/{reportType}", reportType)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("レポートタイプ別キャッシュを無効化しました"));

        verify(cacheService).invalidateCachesByReportType(reportType);
    }

    @Test
    void testInvalidateCacheByType_Forbidden() throws Exception {
        String reportType = "BOOK_LIST";

        // 一般ユーザーとして実行
        when(authentication.getName()).thenReturn("2");

        // テスト実行
        mockMvc.perform(delete("/api/report-cache/type/{reportType}", reportType)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("管理者権限が必要です"));

        verify(cacheService, never()).invalidateCachesByReportType(any());
    }

    @Test
    void testInvalidateSpecificCache_Success() throws Exception {
        String cacheKey = "test_cache_key";

        // 管理者として実行
        when(authentication.getName()).thenReturn("1");

        // テスト実行
        mockMvc.perform(delete("/api/report-cache/key/{cacheKey}", cacheKey)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("指定キャッシュを無効化しました"));

        verify(cacheService).invalidateCache(cacheKey);
    }

    @Test
    void testPerformManualCleanup_Success() throws Exception {
        // 管理者として実行
        when(authentication.getName()).thenReturn("1");

        // テスト実行
        mockMvc.perform(post("/api/report-cache/cleanup")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("手動キャッシュクリーンアップを実行しました"));

        verify(cacheService).performScheduledCleanup();
    }

    @Test
    void testPerformManualCleanup_Forbidden() throws Exception {
        // 一般ユーザーとして実行
        when(authentication.getName()).thenReturn("2");

        // テスト実行
        mockMvc.perform(post("/api/report-cache/cleanup")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("管理者権限が必要です"));

        verify(cacheService, never()).performScheduledCleanup();
    }

    @Test
    void testGetCacheConfig_Success() throws Exception {
        // 管理者として実行
        when(authentication.getName()).thenReturn("1");

        // テスト実行
        mockMvc.perform(get("/api/report-cache/config")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.config.cacheEnabled").value(true))
                .andExpect(jsonPath("$.config.defaultTtlMinutes").value(60))
                .andExpect(jsonPath("$.config.maxCacheSizeMb").value(500))
                .andExpect(jsonPath("$.config.maxEntriesPerUser").value(10))
                .andExpect(jsonPath("$.config.cleanupIntervalMinutes").value(30));
    }

    @Test
    void testGetMyCache_Success() throws Exception {
        // 一般ユーザーとして実行
        when(authentication.getName()).thenReturn("2");

        // テスト実行
        mockMvc.perform(get("/api/report-cache/my-cache")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.cacheInfo").exists());
    }

    @Test
    void testGetCacheStatistics_Error() throws Exception {
        // 管理者として実行
        when(authentication.getName()).thenReturn("1");

        // サービスでエラーが発生する設定
        when(cacheService.getCacheStatistics()).thenThrow(new RuntimeException("Test error"));

        // テスト実行
        mockMvc.perform(get("/api/report-cache/statistics")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("キャッシュ統計の取得に失敗しました"));
    }
}