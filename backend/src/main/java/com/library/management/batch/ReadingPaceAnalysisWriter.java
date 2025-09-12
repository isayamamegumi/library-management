package com.library.management.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.management.dto.ReadingPaceAnalysis;
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
import java.util.stream.Collectors;

@Component
public class ReadingPaceAnalysisWriter implements ItemWriter<ReadingPaceAnalysis> {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public void write(Chunk<? extends ReadingPaceAnalysis> items) throws Exception {
        for (ReadingPaceAnalysis analysis : items.getItems()) {
            // 個別ユーザーの分析をJSON形式で保存
            String analysisJson = objectMapper.writeValueAsString(analysis);
            
            jdbcTemplate.update(
                "INSERT INTO batch_statistics (report_type, target_date, data_json) " +
                "VALUES (?, ?, ?::jsonb) " +
                "ON CONFLICT (report_type, target_date) " +
                "DO UPDATE SET data_json = ?::jsonb, updated_at = NOW()",
                "READING_PACE_ANALYSIS_" + analysis.getUserId(), 
                LocalDate.now(), 
                analysisJson, 
                analysisJson
            );
        }
        
        // 全ユーザー統計レポート作成
        createPaceSummaryReport(items);
        
        System.out.println("読書ペース分析保存完了: " + items.size() + "ユーザー");
    }
    
    private void createPaceSummaryReport(Chunk<? extends ReadingPaceAnalysis> items) throws Exception {
        List<? extends ReadingPaceAnalysis> analyses = items.getItems();
        Map<String, Object> summary = new HashMap<>();
        
        // 全体統計
        int totalUsers = analyses.size();
        
        Map<String, Long> paceLevelDistribution = analyses.stream()
            .collect(Collectors.groupingBy(
                a -> a.getCurrentMetrics().getPaceLevel(),
                Collectors.counting()
            ));
        
        double avgDailyPace = analyses.stream()
            .mapToDouble(a -> a.getCurrentMetrics().getDailyAverageBooks())
            .average().orElse(0.0);
        
        double avgConsistency = analyses.stream()
            .mapToDouble(a -> a.getHabits().getConsistencyScore())
            .average().orElse(0.0);
        
        // 最も一貫性の高いユーザー
        String mostConsistentUser = analyses.stream()
            .max(Comparator.comparing(a -> a.getHabits().getConsistencyScore()))
            .map(ReadingPaceAnalysis::getUsername)
            .orElse("不明");
        
        // 最も速いペースのユーザー
        String fastestUser = analyses.stream()
            .max(Comparator.comparing(a -> a.getCurrentMetrics().getDailyAverageBooks()))
            .map(ReadingPaceAnalysis::getUsername)
            .orElse("不明");
        
        summary.put("totalUsers", totalUsers);
        summary.put("paceLevelDistribution", paceLevelDistribution);
        summary.put("averageDailyPace", avgDailyPace);
        summary.put("averageConsistencyScore", avgConsistency);
        summary.put("mostConsistentUser", mostConsistentUser);
        summary.put("fastestUser", fastestUser);
        summary.put("generatedAt", LocalDateTime.now());
        
        String summaryJson = objectMapper.writeValueAsString(summary);
        
        jdbcTemplate.update(
            "INSERT INTO batch_statistics (report_type, target_date, data_json) " +
            "VALUES (?, ?, ?::jsonb) " +
            "ON CONFLICT (report_type, target_date) " +
            "DO UPDATE SET data_json = ?::jsonb, updated_at = NOW()",
            "READING_PACE_SUMMARY", 
            LocalDate.now(), 
            summaryJson, 
            summaryJson
        );
    }
}