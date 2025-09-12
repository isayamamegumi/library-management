package com.library.management.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.management.dto.PeriodicalStats;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class WeeklyStatsTasklet implements Tasklet {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public RepeatStatus execute(StepContribution contribution, 
                               ChunkContext chunkContext) throws Exception {
        
        // 今週の期間設定
        LocalDate now = LocalDate.now();
        LocalDate weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        
        PeriodicalStats weeklyStats = new PeriodicalStats();
        weeklyStats.setPeriodType("WEEKLY");
        weeklyStats.setPeriodStart(weekStart);
        weeklyStats.setPeriodEnd(weekEnd);
        
        // 基本統計データ収集
        calculateBasicStats(weekStart, weekEnd, weeklyStats);
        
        // ジャンル分布計算
        calculateGenreDistribution(weekStart, weekEnd, weeklyStats);
        
        // トレンド比較（前週比）
        calculateTrendComparison(weekStart, weekEnd, weeklyStats, "WEEKLY");
        
        // 成長指標計算
        calculateGrowthMetrics(weekStart, weekEnd, weeklyStats, "WEEKLY");
        
        // 結果保存
        saveStats(weeklyStats, "WEEKLY_STATS");
        
        System.out.println("週次統計生成完了: " + weekStart + " - " + weekEnd);
        return RepeatStatus.FINISHED;
    }
    
    public void calculateBasicStats(LocalDate start, LocalDate end, PeriodicalStats stats) {
        // 期間内総登録書籍数
        Integer totalBooks = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books WHERE created_at BETWEEN ? AND ?",
            Integer.class, start, end.plusDays(1));
        stats.setTotalBooks(totalBooks);
        
        // 期間内読了書籍数
        Integer completedBooks = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books b JOIN read_status rs ON b.id = rs.book_id " +
            "WHERE rs.status = 'COMPLETED' AND rs.updated_at BETWEEN ? AND ?",
            Integer.class, start, end.plusDays(1));
        stats.setCompletedBooks(completedBooks);
        
        // アクティブユーザー数
        Integer activeUsers = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT user_id) FROM books WHERE created_at BETWEEN ? AND ?",
            Integer.class, start, end.plusDays(1));
        stats.setActiveUsers(activeUsers);
        
        // 読了率
        Double completionRate = totalBooks > 0 ? 
            (completedBooks.doubleValue() / totalBooks) * 100 : 0.0;
        stats.setCompletionRate(completionRate);
    }
    
    public void calculateGenreDistribution(LocalDate start, LocalDate end, PeriodicalStats stats) {
        List<Map<String, Object>> genreData = jdbcTemplate.queryForList(
            "SELECT g.name as genre, COUNT(*) as count FROM books b " +
            "JOIN genres g ON b.genre_id = g.id " +
            "WHERE b.created_at BETWEEN ? AND ? " +
            "GROUP BY g.name ORDER BY count DESC",
            start, end.plusDays(1));
        
        Map<String, Integer> distribution = genreData.stream()
            .collect(Collectors.toMap(
                row -> (String) row.get("genre"),
                row -> ((Number) row.get("count")).intValue()
            ));
        stats.setGenreDistribution(distribution);
    }
    
    public void calculateTrendComparison(LocalDate start, LocalDate end, 
                                        PeriodicalStats stats, String periodType) {
        Map<String, Object> trendData = new HashMap<>();
        
        // 前期間の同じ期間と比較
        LocalDate prevStart, prevEnd;
        switch (periodType) {
            case "WEEKLY":
                prevStart = start.minusWeeks(1);
                prevEnd = end.minusWeeks(1);
                break;
            case "MONTHLY":
                prevStart = start.minusMonths(1);
                prevEnd = end.minusMonths(1);
                break;
            case "QUARTERLY":
                prevStart = start.minusMonths(3);
                prevEnd = end.minusMonths(3);
                break;
            case "YEARLY":
                prevStart = start.minusYears(1);
                prevEnd = end.minusYears(1);
                break;
            default:
                prevStart = start.minusWeeks(1);
                prevEnd = end.minusWeeks(1);
        }
        
        // 前期間の登録数
        Integer prevTotalBooks = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books WHERE created_at BETWEEN ? AND ?",
            Integer.class, prevStart, prevEnd.plusDays(1));
        
        // 前期間の読了数
        Integer prevCompletedBooks = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books b JOIN read_status rs ON b.id = rs.book_id " +
            "WHERE rs.status = 'COMPLETED' AND rs.updated_at BETWEEN ? AND ?",
            Integer.class, prevStart, prevEnd.plusDays(1));
        
        // 増減率計算
        Double bookGrowthRate = prevTotalBooks > 0 ? 
            ((stats.getTotalBooks().intValue() - prevTotalBooks.intValue()) / (double) prevTotalBooks.intValue()) * 100 : 0.0;
        
        Double completionGrowthRate = prevCompletedBooks > 0 ? 
            ((stats.getCompletedBooks().intValue() - prevCompletedBooks.intValue()) / (double) prevCompletedBooks.intValue()) * 100 : 0.0;
        
        trendData.put("previousPeriodBooks", prevTotalBooks);
        trendData.put("previousPeriodCompleted", prevCompletedBooks);
        trendData.put("bookGrowthRate", bookGrowthRate);
        trendData.put("completionGrowthRate", completionGrowthRate);
        
        stats.setTrendData(trendData);
    }
    
    public void calculateGrowthMetrics(LocalDate start, LocalDate end, 
                                      PeriodicalStats stats, String periodType) {
        Map<String, Object> growthMetrics = new HashMap<>();
        
        // 日平均登録数
        long daysBetween = ChronoUnit.DAYS.between(start, end) + 1;
        Double dailyAvgBooks = stats.getTotalBooks().doubleValue() / daysBetween;
        growthMetrics.put("dailyAverageBooks", dailyAvgBooks);
        
        // ユーザーあたり平均登録数
        Double booksPerUser = stats.getActiveUsers() > 0 ? 
            stats.getTotalBooks().doubleValue() / stats.getActiveUsers() : 0.0;
        growthMetrics.put("booksPerUser", booksPerUser);
        
        // 最も活発な曜日（週次の場合）
        if ("WEEKLY".equals(periodType)) {
            try {
                List<Map<String, Object>> dailyActivity = jdbcTemplate.queryForList(
                    "SELECT EXTRACT(dow FROM created_at) as day_of_week, COUNT(*) as count " +
                    "FROM books WHERE created_at BETWEEN ? AND ? " +
                    "GROUP BY EXTRACT(dow FROM created_at) " +
                    "ORDER BY count DESC LIMIT 1",
                    start, end.plusDays(1));
                
                if (!dailyActivity.isEmpty()) {
                    Integer mostActiveDow = ((Number) dailyActivity.get(0).get("day_of_week")).intValue();
                    String[] dayNames = {"日曜日", "月曜日", "火曜日", "水曜日", "木曜日", "金曜日", "土曜日"};
                    String dayName = dayNames[mostActiveDow];
                    growthMetrics.put("mostActiveDay", dayName);
                }
            } catch (Exception e) {
                growthMetrics.put("mostActiveDay", "不明");
            }
        }
        
        stats.setGrowthMetrics(growthMetrics);
    }
    
    public void saveStats(PeriodicalStats stats, String reportType) throws Exception {
        String statsJson = objectMapper.writeValueAsString(stats);
        
        jdbcTemplate.update(
            "INSERT INTO batch_statistics (report_type, target_date, data_json) " +
            "VALUES (?, ?, ?::jsonb) " +
            "ON CONFLICT (report_type, target_date) " +
            "DO UPDATE SET data_json = ?::jsonb, updated_at = NOW()",
            reportType, 
            LocalDate.now(), 
            statsJson, 
            statsJson
        );
    }
}