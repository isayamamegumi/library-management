package com.library.management.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.management.dto.GenreAnalysis;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GenreAnalysisWriter implements ItemWriter<GenreAnalysis> {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public void write(Chunk<? extends GenreAnalysis> items) throws Exception {
        for (GenreAnalysis analysis : items.getItems()) {
            // 個別ジャンル分析をJSON形式で保存
            String analysisJson = objectMapper.writeValueAsString(analysis);
            
            jdbcTemplate.update(
                "INSERT INTO batch_statistics (report_type, target_date, data_json) " +
                "VALUES (?, ?, ?::jsonb) " +
                "ON CONFLICT (report_type, target_date) " +
                "DO UPDATE SET data_json = ?::jsonb, updated_at = NOW()",
                "GENRE_ANALYSIS_" + analysis.getGenre().replaceAll("\\s+", "_"), 
                LocalDate.now(), 
                analysisJson, 
                analysisJson
            );
        }
        
        // 全ジャンル統合レポート作成
        createGenreSummaryReport(items);
        
        System.out.println("ジャンル分析保存完了: " + items.size() + "ジャンル");
    }
    
    private void createGenreSummaryReport(Chunk<? extends GenreAnalysis> items) throws Exception {
        List<? extends GenreAnalysis> analyses = items.getItems();
        Map<String, Object> summary = new HashMap<>();
        
        // 全ジャンル統計
        int totalGenres = analyses.size();
        
        int totalBooks = analyses.stream()
            .mapToInt(a -> a.getBasicStats().getTotalBooks())
            .sum();
        
        double avgCompletionRate = analyses.stream()
            .mapToDouble(a -> a.getBasicStats().getCompletionRate())
            .average().orElse(0.0);
        
        // 最も成長しているジャンル
        String fastestGrowingGenre = analyses.stream()
            .max(Comparator.comparing(a -> a.getTrendAnalysis().getGrowthRate()))
            .map(GenreAnalysis::getGenre)
            .orElse("不明");
        
        // 最も人気のジャンル（登録数基準）
        String mostPopularGenre = analyses.stream()
            .max(Comparator.comparing(a -> a.getBasicStats().getTotalBooks()))
            .map(GenreAnalysis::getGenre)
            .orElse("不明");
        
        // 最も読了率の高いジャンル
        String highestCompletionGenre = analyses.stream()
            .max(Comparator.comparing(a -> a.getBasicStats().getCompletionRate()))
            .map(GenreAnalysis::getGenre)
            .orElse("不明");
        
        summary.put("totalGenres", totalGenres);
        summary.put("totalBooks", totalBooks);
        summary.put("averageCompletionRate", avgCompletionRate);
        summary.put("fastestGrowingGenre", fastestGrowingGenre);
        summary.put("mostPopularGenre", mostPopularGenre);
        summary.put("highestCompletionGenre", highestCompletionGenre);
        summary.put("generatedAt", LocalDateTime.now());
        
        String summaryJson = objectMapper.writeValueAsString(summary);
        
        jdbcTemplate.update(
            "INSERT INTO batch_statistics (report_type, target_date, data_json) " +
            "VALUES (?, ?, ?::jsonb) " +
            "ON CONFLICT (report_type, target_date) " +
            "DO UPDATE SET data_json = ?::jsonb, updated_at = NOW()",
            "GENRE_ANALYSIS_SUMMARY", 
            LocalDate.now(), 
            summaryJson, 
            summaryJson
        );
    }
}