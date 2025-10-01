package com.library.management.service.report.optimization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * メモリ最適化サービス
 * 帳票生成時のメモリ使用量を監視・最適化
 */
@Service
public class MemoryOptimizationService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryOptimizationService.class);

    @Value("${app.report.memory.warning-threshold:0.8}")
    private double memoryWarningThreshold;

    @Value("${app.report.memory.critical-threshold:0.9}")
    private double memoryCriticalThreshold;

    @Value("${app.report.memory.monitoring-interval:30}")
    private int monitoringIntervalSeconds;

    @Value("${app.report.memory.auto-gc:true}")
    private boolean autoGarbageCollection;

    private final MemoryMXBean memoryBean;
    private final ScheduledExecutorService scheduler;
    private volatile boolean monitoring = false;

    public MemoryOptimizationService() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * メモリ監視開始
     */
    public void startMemoryMonitoring() {
        if (!monitoring) {
            monitoring = true;
            scheduler.scheduleAtFixedRate(this::monitorMemoryUsage,
                0, monitoringIntervalSeconds, TimeUnit.SECONDS);
            logger.info("メモリ監視を開始しました: 監視間隔={}秒", monitoringIntervalSeconds);
        }
    }

    /**
     * メモリ監視停止
     */
    public void stopMemoryMonitoring() {
        if (monitoring) {
            monitoring = false;
            scheduler.shutdown();
            logger.info("メモリ監視を停止しました");
        }
    }

    /**
     * 現在のメモリ使用状況取得
     */
    public MemoryStatus getCurrentMemoryStatus() {
        MemoryUsage heapMemory = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemory = memoryBean.getNonHeapMemoryUsage();

        return new MemoryStatus(heapMemory, nonHeapMemory);
    }

    /**
     * メモリ使用量チェック
     */
    public boolean isMemoryUsageHigh() {
        MemoryStatus status = getCurrentMemoryStatus();
        return status.getHeapUsagePercentage() > memoryWarningThreshold;
    }

    /**
     * メモリ使用量が危険レベルかチェック
     */
    public boolean isMemoryUsageCritical() {
        MemoryStatus status = getCurrentMemoryStatus();
        return status.getHeapUsagePercentage() > memoryCriticalThreshold;
    }

    /**
     * 推奨処理サイズ計算
     */
    public int calculateRecommendedBatchSize(int currentBatchSize, int estimatedRecordSize) {
        MemoryStatus status = getCurrentMemoryStatus();
        long availableMemory = status.getHeapAvailable();

        // 安全マージンを考慮（利用可能メモリの50%まで）
        long safeMemory = availableMemory / 2;

        // 推奨バッチサイズ計算
        int recommendedSize = (int) (safeMemory / estimatedRecordSize);

        // 最小・最大制限
        recommendedSize = Math.max(100, Math.min(recommendedSize, 10000));

        if (recommendedSize < currentBatchSize) {
            logger.warn("メモリ不足のため、バッチサイズを削減します: {} -> {}",
                currentBatchSize, recommendedSize);
        }

        return recommendedSize;
    }

    /**
     * 緊急メモリクリーンアップ
     */
    public void performEmergencyCleanup() {
        logger.warn("緊急メモリクリーンアップ実行");

        // 明示的ガベージコレクション実行
        if (autoGarbageCollection) {
            System.gc();
            System.runFinalization();

            // 少し待機してGCの効果を確認
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            MemoryStatus afterCleanup = getCurrentMemoryStatus();
            logger.info("緊急クリーンアップ後のメモリ使用率: {:.1f}%",
                afterCleanup.getHeapUsagePercentage() * 100);
        }
    }

    /**
     * メモリ使用量監視
     */
    private void monitorMemoryUsage() {
        try {
            MemoryStatus status = getCurrentMemoryStatus();

            if (status.getHeapUsagePercentage() > memoryCriticalThreshold) {
                logger.error("メモリ使用量が危険レベルです: {:.1f}% (閾値: {:.1f}%)",
                    status.getHeapUsagePercentage() * 100,
                    memoryCriticalThreshold * 100);

                if (autoGarbageCollection) {
                    performEmergencyCleanup();
                }

            } else if (status.getHeapUsagePercentage() > memoryWarningThreshold) {
                logger.warn("メモリ使用量が警告レベルです: {:.1f}% (閾値: {:.1f}%)",
                    status.getHeapUsagePercentage() * 100,
                    memoryWarningThreshold * 100);
            } else {
                logger.debug("メモリ使用量: {:.1f}%", status.getHeapUsagePercentage() * 100);
            }

        } catch (Exception e) {
            logger.error("メモリ監視エラー", e);
        }
    }

    /**
     * 処理前メモリチェック
     */
    public ProcessingRecommendation getProcessingRecommendation(int estimatedRecords, int recordSize) {
        MemoryStatus status = getCurrentMemoryStatus();
        long estimatedMemoryNeeded = (long) estimatedRecords * recordSize;

        if (estimatedMemoryNeeded > status.getHeapAvailable()) {
            // メモリ不足の場合の推奨事項
            int maxRecords = (int) (status.getHeapAvailable() / recordSize / 2); // 安全マージン
            return new ProcessingRecommendation(
                ProcessingRecommendation.Strategy.BATCH_PROCESSING,
                maxRecords,
                "メモリ不足のためバッチ処理を推奨します"
            );
        } else if (estimatedMemoryNeeded > status.getHeapAvailable() / 2) {
            // メモリ使用量が多い場合
            return new ProcessingRecommendation(
                ProcessingRecommendation.Strategy.MEMORY_OPTIMIZED,
                estimatedRecords,
                "メモリ効率化処理を推奨します"
            );
        } else {
            // 通常処理可能
            return new ProcessingRecommendation(
                ProcessingRecommendation.Strategy.NORMAL,
                estimatedRecords,
                "通常処理で問題ありません"
            );
        }
    }

    /**
     * メモリ状況クラス
     */
    public static class MemoryStatus {
        private final MemoryUsage heapMemory;
        private final MemoryUsage nonHeapMemory;

        public MemoryStatus(MemoryUsage heapMemory, MemoryUsage nonHeapMemory) {
            this.heapMemory = heapMemory;
            this.nonHeapMemory = nonHeapMemory;
        }

        public long getHeapUsed() {
            return heapMemory.getUsed();
        }

        public long getHeapMax() {
            return heapMemory.getMax();
        }

        public long getHeapAvailable() {
            return heapMemory.getMax() - heapMemory.getUsed();
        }

        public double getHeapUsagePercentage() {
            return (double) heapMemory.getUsed() / heapMemory.getMax();
        }

        public long getNonHeapUsed() {
            return nonHeapMemory.getUsed();
        }

        public String getFormattedHeapUsage() {
            return String.format("%.1f%% (%s / %s)",
                getHeapUsagePercentage() * 100,
                formatBytes(getHeapUsed()),
                formatBytes(getHeapMax()));
        }

        private String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 処理推奨事項クラス
     */
    public static class ProcessingRecommendation {
        public enum Strategy {
            NORMAL("通常処理"),
            MEMORY_OPTIMIZED("メモリ最適化"),
            BATCH_PROCESSING("バッチ処理"),
            STREAMING("ストリーミング");

            private final String description;

            Strategy(String description) {
                this.description = description;
            }

            public String getDescription() {
                return description;
            }
        }

        private final Strategy strategy;
        private final int recommendedRecords;
        private final String message;

        public ProcessingRecommendation(Strategy strategy, int recommendedRecords, String message) {
            this.strategy = strategy;
            this.recommendedRecords = recommendedRecords;
            this.message = message;
        }

        public Strategy getStrategy() { return strategy; }
        public int getRecommendedRecords() { return recommendedRecords; }
        public String getMessage() { return message; }
    }
}