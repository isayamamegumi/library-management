package com.library.management.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.management.dto.UserReadingHistory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class UserHistoryWriter implements ItemWriter<UserReadingHistory> {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public void write(Chunk<? extends UserReadingHistory> items) throws Exception {
        for (UserReadingHistory history : items.getItems()) {
            // 個別ユーザーの履歴をJSON形式で保存
            String historyJson = objectMapper.writeValueAsString(history);
            
            jdbcTemplate.update(
                "INSERT INTO batch_statistics (report_type, target_date, data_json) " +
                "VALUES (?, ?, ?::jsonb) " +
                "ON CONFLICT (report_type, target_date) " +
                "DO UPDATE SET data_json = ?::jsonb, updated_at = NOW()",
                "USER_READING_HISTORY_" + history.getUserId(), 
                LocalDate.now(), 
                historyJson, 
                historyJson
            );
        }
        
        // 全ユーザーの集計データも作成
        createSummaryReport(items);
        
        System.out.println("ユーザー読書履歴保存完了: " + items.size() + "件");
    }
    
    private void createSummaryReport(Chunk<? extends UserReadingHistory> items) throws Exception {
        List<? extends UserReadingHistory> histories = items.getItems();
        Map<String, Object> summary = new HashMap<>();
        
        // 全体サマリー計算
        int totalUsers = histories.size();
        double avgCompletionRate = histories.stream()
            .mapToDouble(UserReadingHistory::getCompletionRate)
            .average().orElse(0.0);
        
        double avgReadingDays = histories.stream()
            .mapToDouble(UserReadingHistory::getAverageReadingDays)
            .average().orElse(0.0);
        
        // 最も人気のジャンル
        Map<String, Long> genreCount = histories.stream()
            .filter(h -> h.getFavoriteGenres() != null)
            .flatMap(h -> h.getFavoriteGenres().stream())
            .collect(Collectors.groupingBy(g -> g, Collectors.counting()));
        
        List<String> topGenres = genreCount.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        summary.put("totalUsers", totalUsers);
        summary.put("averageCompletionRate", avgCompletionRate);
        summary.put("averageReadingDays", avgReadingDays);
        summary.put("topGenres", topGenres);
        summary.put("generatedAt", LocalDateTime.now());
        
        String summaryJson = objectMapper.writeValueAsString(summary);
        
        jdbcTemplate.update(
            "INSERT INTO batch_statistics (report_type, target_date, data_json) " +
            "VALUES (?, ?, ?::jsonb) " +
            "ON CONFLICT (report_type, target_date) " +
            "DO UPDATE SET data_json = ?::jsonb, updated_at = NOW()",
            "USER_HISTORY_SUMMARY", 
            LocalDate.now(), 
            summaryJson, 
            summaryJson
        );
    }
}