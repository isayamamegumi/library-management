package com.library.management.batch.base;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ItemProcessorの抽象基底クラス
 * 共通的な処理ロジックを提供
 *
 * @param <I> 入力アイテムの型
 * @param <O> 出力アイテムの型
 */
public abstract class AbstractItemProcessor<I, O> implements ItemProcessor<I, O> {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected long processedCount = 0;
    protected long errorCount = 0;

    @Override
    public O process(I item) throws Exception {
        try {
            O result = processItem(item);
            processedCount++;

            if (processedCount % 100 == 0) {
                logProgress();
            }

            return result;
        } catch (Exception e) {
            errorCount++;
            handleProcessingError(item, e);
            throw e;
        }
    }

    /**
     * 実際の処理ロジック（サブクラスで実装）
     */
    protected abstract O processItem(I item) throws Exception;

    /**
     * 処理エラーのハンドリング（サブクラスでオーバーライド可能）
     */
    protected void handleProcessingError(I item, Exception e) {
        System.err.printf("処理エラー: item=%s, error=%s%n", item, e.getMessage());
    }

    /**
     * 進捗ログ出力（サブクラスでオーバーライド可能）
     */
    protected void logProgress() {
        System.out.printf("処理進捗: 成功=%d, エラー=%d%n", processedCount, errorCount);
    }

    /**
     * 処理統計の取得
     */
    public ProcessingStats getProcessingStats() {
        return new ProcessingStats(processedCount, errorCount);
    }

    /**
     * 処理統計を保持するレコードクラス
     */
    public record ProcessingStats(long processedCount, long errorCount) {
        public double getSuccessRate() {
            long total = processedCount + errorCount;
            return total > 0 ? (double) processedCount / total * 100 : 0;
        }
    }

    /**
     * 統計をリセット
     */
    public void resetStats() {
        processedCount = 0;
        errorCount = 0;
    }
}