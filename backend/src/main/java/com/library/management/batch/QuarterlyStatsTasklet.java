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
import java.time.Month;

@Component
public class QuarterlyStatsTasklet implements Tasklet {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private WeeklyStatsTasklet weeklyStatsTasklet;
    
    @Override
    public RepeatStatus execute(StepContribution contribution, 
                               ChunkContext chunkContext) throws Exception {
        
        // 今四半期の期間設定
        LocalDate now = LocalDate.now();
        LocalDate quarterStart = getQuarterStart(now);
        LocalDate quarterEnd = getQuarterEnd(now);
        
        PeriodicalStats quarterlyStats = new PeriodicalStats();
        quarterlyStats.setPeriodType("QUARTERLY");
        quarterlyStats.setPeriodStart(quarterStart);
        quarterlyStats.setPeriodEnd(quarterEnd);
        
        // 統計計算の共通ロジックを再利用
        weeklyStatsTasklet.calculateBasicStats(quarterStart, quarterEnd, quarterlyStats);
        weeklyStatsTasklet.calculateGenreDistribution(quarterStart, quarterEnd, quarterlyStats);
        weeklyStatsTasklet.calculateTrendComparison(quarterStart, quarterEnd, quarterlyStats, "QUARTERLY");
        weeklyStatsTasklet.calculateGrowthMetrics(quarterStart, quarterEnd, quarterlyStats, "QUARTERLY");
        
        // 結果保存
        weeklyStatsTasklet.saveStats(quarterlyStats, "QUARTERLY_STATS");
        
        System.out.println("四半期統計生成完了: " + quarterStart + " - " + quarterEnd);
        return RepeatStatus.FINISHED;
    }
    
    private LocalDate getQuarterStart(LocalDate date) {
        Month month = date.getMonth();
        if (month.getValue() <= 3) {
            return LocalDate.of(date.getYear(), Month.JANUARY, 1);
        } else if (month.getValue() <= 6) {
            return LocalDate.of(date.getYear(), Month.APRIL, 1);
        } else if (month.getValue() <= 9) {
            return LocalDate.of(date.getYear(), Month.JULY, 1);
        } else {
            return LocalDate.of(date.getYear(), Month.OCTOBER, 1);
        }
    }
    
    private LocalDate getQuarterEnd(LocalDate date) {
        Month month = date.getMonth();
        if (month.getValue() <= 3) {
            return LocalDate.of(date.getYear(), Month.MARCH, 31);
        } else if (month.getValue() <= 6) {
            return LocalDate.of(date.getYear(), Month.JUNE, 30);
        } else if (month.getValue() <= 9) {
            return LocalDate.of(date.getYear(), Month.SEPTEMBER, 30);
        } else {
            return LocalDate.of(date.getYear(), Month.DECEMBER, 31);
        }
    }
}