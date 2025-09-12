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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MonthlyStatsTasklet implements Tasklet {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private WeeklyStatsTasklet weeklyStatsTasklet;
    
    @Override
    public RepeatStatus execute(StepContribution contribution, 
                               ChunkContext chunkContext) throws Exception {
        
        // 今月の期間設定
        LocalDate now = LocalDate.now();
        LocalDate monthStart = now.withDayOfMonth(1);
        LocalDate monthEnd = now.withDayOfMonth(now.lengthOfMonth());
        
        PeriodicalStats monthlyStats = new PeriodicalStats();
        monthlyStats.setPeriodType("MONTHLY");
        monthlyStats.setPeriodStart(monthStart);
        monthlyStats.setPeriodEnd(monthEnd);
        
        // 統計計算の共通ロジックを再利用
        weeklyStatsTasklet.calculateBasicStats(monthStart, monthEnd, monthlyStats);
        weeklyStatsTasklet.calculateGenreDistribution(monthStart, monthEnd, monthlyStats);
        weeklyStatsTasklet.calculateTrendComparison(monthStart, monthEnd, monthlyStats, "MONTHLY");
        weeklyStatsTasklet.calculateGrowthMetrics(monthStart, monthEnd, monthlyStats, "MONTHLY");
        
        // 月次特有の分析
        calculateMonthlySpecificMetrics(monthStart, monthEnd, monthlyStats);
        
        // 結果保存
        weeklyStatsTasklet.saveStats(monthlyStats, "MONTHLY_STATS");
        
        System.out.println("月次統計生成完了: " + monthStart + " - " + monthEnd);
        return RepeatStatus.FINISHED;
    }
    
    private void calculateMonthlySpecificMetrics(LocalDate start, LocalDate end, 
                                               PeriodicalStats stats) {
        Map<String, Object> monthlyMetrics = new HashMap<>();
        
        // 週別の登録数推移
        try {
            List<Map<String, Object>> weeklyTrend = jdbcTemplate.queryForList(
                "SELECT " +
                "  EXTRACT(week FROM created_at) as week_num, " +
                "  COUNT(*) as count " +
                "FROM books " +
                "WHERE created_at BETWEEN ? AND ? " +
                "GROUP BY EXTRACT(week FROM created_at) " +
                "ORDER BY week_num",
                start, end.plusDays(1));
            
            monthlyMetrics.put("weeklyTrend", weeklyTrend);
        } catch (Exception e) {
            monthlyMetrics.put("weeklyTrend", null);
        }
        
        // 月内の最高登録日
        try {
            Map<String, Object> peakDay = jdbcTemplate.queryForMap(
                "SELECT DATE(created_at) as peak_date, COUNT(*) as peak_count " +
                "FROM books " +
                "WHERE created_at BETWEEN ? AND ? " +
                "GROUP BY DATE(created_at) " +
                "ORDER BY peak_count DESC LIMIT 1",
                start, end.plusDays(1));
            
            monthlyMetrics.put("peakDay", peakDay);
        } catch (Exception e) {
            monthlyMetrics.put("peakDay", null);
        }
        
        // 既存のgrowthMetricsに追加
        Map<String, Object> existingMetrics = stats.getGrowthMetrics();
        if (existingMetrics == null) {
            existingMetrics = new HashMap<>();
        }
        existingMetrics.putAll(monthlyMetrics);
        stats.setGrowthMetrics(existingMetrics);
    }
}