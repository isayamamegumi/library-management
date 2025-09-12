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

@Component
public class YearlyStatsTasklet implements Tasklet {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private WeeklyStatsTasklet weeklyStatsTasklet;
    
    @Override
    public RepeatStatus execute(StepContribution contribution, 
                               ChunkContext chunkContext) throws Exception {
        
        // 今年の期間設定
        LocalDate now = LocalDate.now();
        LocalDate yearStart = LocalDate.of(now.getYear(), 1, 1);
        LocalDate yearEnd = LocalDate.of(now.getYear(), 12, 31);
        
        PeriodicalStats yearlyStats = new PeriodicalStats();
        yearlyStats.setPeriodType("YEARLY");
        yearlyStats.setPeriodStart(yearStart);
        yearlyStats.setPeriodEnd(yearEnd);
        
        // 統計計算の共通ロジックを再利用
        weeklyStatsTasklet.calculateBasicStats(yearStart, yearEnd, yearlyStats);
        weeklyStatsTasklet.calculateGenreDistribution(yearStart, yearEnd, yearlyStats);
        weeklyStatsTasklet.calculateTrendComparison(yearStart, yearEnd, yearlyStats, "YEARLY");
        weeklyStatsTasklet.calculateGrowthMetrics(yearStart, yearEnd, yearlyStats, "YEARLY");
        
        // 結果保存
        weeklyStatsTasklet.saveStats(yearlyStats, "YEARLY_STATS");
        
        System.out.println("年次統計生成完了: " + yearStart + " - " + yearEnd);
        return RepeatStatus.FINISHED;
    }
}