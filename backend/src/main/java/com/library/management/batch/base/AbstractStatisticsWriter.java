package com.library.management.batch.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 統計データWriter抽象基底クラス
 * 統計データの保存処理を標準化
 *
 * @param <T> 統計データの型
 */
public abstract class AbstractStatisticsWriter<T> implements ItemWriter<T> {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper;
    private final String reportType;

    public AbstractStatisticsWriter(String reportType) {
        this.reportType = reportType;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void write(Chunk<? extends T> items) throws Exception {
        if (items.isEmpty()) {
            return;
        }

        List<T> itemList = new ArrayList<>();
        items.forEach(itemList::add);

        // 追加のメタデータを含む統計オブジェクトを作成
        Map<String, Object> statisticsData = createStatisticsData(itemList);
        statisticsData.put("generatedAt", LocalDateTime.now());
        statisticsData.put("itemCount", itemList.size());
        statisticsData.put("reportType", reportType);

        // データベースに保存
        saveStatistics(statisticsData);

        // 追加処理（サブクラスで実装可能）
        afterWrite(itemList);

        System.out.printf("[%s] 統計データ保存完了: %d件%n", reportType, itemList.size());
    }

    /**
     * 統計データオブジェクトを作成（サブクラスで実装）
     */
    protected abstract Map<String, Object> createStatisticsData(List<T> items);

    /**
     * 書き込み後の追加処理（サブクラスでオーバーライド可能）
     */
    protected void afterWrite(List<T> items) {
        // デフォルトでは何もしない
    }

    /**
     * 統計データをデータベースに保存
     */
    private void saveStatistics(Map<String, Object> statisticsData) throws Exception {
        String jsonData = objectMapper.writeValueAsString(statisticsData);

        jdbcTemplate.update(
            """
            INSERT INTO batch_statistics (report_type, target_date, data_json, created_at, updated_at)
            VALUES (?, ?, ?::jsonb, NOW(), NOW())
            ON CONFLICT (report_type, target_date)
            DO UPDATE SET
                data_json = EXCLUDED.data_json,
                updated_at = NOW()
            """,
            reportType,
            LocalDate.now(),
            jsonData
        );
    }

    /**
     * 統計サマリーを作成するヘルパーメソッド
     */
    protected Map<String, Object> createSummary(List<T> items, String itemType) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalCount", items.size());
        summary.put("itemType", itemType);
        summary.put("processingDate", LocalDate.now());
        return summary;
    }

    /**
     * レポートタイプを取得
     */
    public String getReportType() {
        return reportType;
    }
}