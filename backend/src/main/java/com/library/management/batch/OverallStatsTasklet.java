package com.library.management.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OverallStatsTasklet implements Tasklet {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public RepeatStatus execute(StepContribution contribution, 
                               ChunkContext chunkContext) throws Exception {
        
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        
        // 全体統計データ収集
        Map<String, Object> overallStats = new HashMap<>();
        
        // 今月の総登録書籍数
        Integer totalBooksThisMonth = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books WHERE created_at >= ?",
            Integer.class, startOfMonth);
        overallStats.put("totalBooksThisMonth", totalBooksThisMonth);
        
        // 今月の総読了書籍数
        Integer totalCompletedThisMonth = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books b JOIN read_status rs ON b.id = rs.book_id " +
            "WHERE rs.status = 'COMPLETED' AND rs.updated_at >= ?",
            Integer.class, startOfMonth);
        overallStats.put("totalCompletedThisMonth", totalCompletedThisMonth);
        
        // アクティブユーザー数（今月書籍を登録したユーザー）
        Integer activeUsers = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT user_id) FROM books WHERE created_at >= ?",
            Integer.class, startOfMonth);
        overallStats.put("activeUsers", activeUsers);
        
        // 人気ジャンルTOP5
        List<Map<String, Object>> popularGenres = jdbcTemplate.queryForList(
            "SELECT g.name as genre, COUNT(*) as count FROM books b " +
            "JOIN genres g ON b.genre_id = g.id " +
            "WHERE b.created_at >= ? " +
            "GROUP BY g.name ORDER BY count DESC LIMIT 5",
            startOfMonth);
        overallStats.put("popularGenres", popularGenres);
        
        // 平均読了率
        try {
            Double averageCompletionRate = jdbcTemplate.queryForObject(
                "SELECT AVG(completion_rate) FROM (" +
                "  SELECT " +
                "    CASE WHEN COUNT(*) > 0 THEN " +
                "      COUNT(CASE WHEN rs.status = 'COMPLETED' THEN 1 END) * 100.0 / COUNT(*) " +
                "    ELSE 0 END as completion_rate" +
                "  FROM books b " +
                "  LEFT JOIN read_status rs ON b.id = rs.book_id " +
                "  WHERE b.created_at >= ? " +
                "  GROUP BY b.user_id" +
                ") user_rates",
                Double.class, startOfMonth);
            overallStats.put("averageCompletionRate", averageCompletionRate != null ? averageCompletionRate : 0.0);
        } catch (Exception e) {
            overallStats.put("averageCompletionRate", 0.0);
        }
        
        overallStats.put("generatedAt", LocalDateTime.now());
        overallStats.put("targetMonth", startOfMonth);
        
        // JSON形式で保存
        String statsJson = objectMapper.writeValueAsString(overallStats);
        
        jdbcTemplate.update(
            "INSERT INTO batch_statistics (report_type, target_date, data_json) " +
            "VALUES (?, ?, ?::jsonb) " +
            "ON CONFLICT (report_type, target_date) " +
            "DO UPDATE SET data_json = ?::jsonb, updated_at = NOW()",
            "MONTHLY_OVERALL_STATS", 
            LocalDate.now(), 
            statsJson, 
            statsJson
        );
        
        System.out.println("月次全体統計生成完了");
        return RepeatStatus.FINISHED;
    }
}