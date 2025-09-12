package com.library.management.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.management.dto.UserStats;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class MonthlyStatsWriter implements ItemWriter<UserStats> {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public void write(Chunk<? extends UserStats> items) throws Exception {
        List<UserStats> statsList = new ArrayList<>(items.getItems());
        
        // JSON形式で保存
        String statsJson = objectMapper.writeValueAsString(statsList);
        
        // batch_statisticsテーブルに保存
        jdbcTemplate.update(
            "INSERT INTO batch_statistics (report_type, target_date, data_json) " +
            "VALUES (?, ?, ?::jsonb) " +
            "ON CONFLICT (report_type, target_date) " +
            "DO UPDATE SET data_json = ?::jsonb, updated_at = NOW()",
            "MONTHLY_USER_STATS", 
            LocalDate.now(), 
            statsJson, 
            statsJson
        );
        
        System.out.println("月次ユーザー統計保存完了: " + statsList.size() + "件");
    }
}