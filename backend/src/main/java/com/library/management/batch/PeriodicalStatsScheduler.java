package com.library.management.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Component
public class PeriodicalStatsScheduler {
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    @Qualifier("periodicalStatsJob")
    private Job periodicalStatsJob;
    
    // 毎日午前1時に実行（週次・月次・四半期・年次を自動判定）
    @Scheduled(cron = "0 0 1 * * ?")
    public void runPeriodicalStatsJob() {
        try {
            LocalDate today = LocalDate.now();
            String executionType = determineExecutionType(today);
            
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("executionType", executionType)
                    .addString("targetDate", today.toString())
                    .toJobParameters();
                    
            JobExecution jobExecution = jobLauncher.run(periodicalStatsJob, jobParameters);
            System.out.println("期間別統計ジョブ実行ID: " + jobExecution.getId() + 
                             " (" + executionType + ")");
            
        } catch (Exception e) {
            System.err.println("期間別統計ジョブ実行エラー: " + e.getMessage());
        }
    }
    
    private String determineExecutionType(LocalDate date) {
        // 月曜日は週次統計
        if (date.getDayOfWeek() == DayOfWeek.MONDAY) {
            return "WEEKLY";
        }
        
        // 月初は月次統計
        if (date.getDayOfMonth() == 1) {
            return "MONTHLY";
        }
        
        // 四半期初は四半期統計
        if ((date.getMonthValue() == 1 || date.getMonthValue() == 4 || 
             date.getMonthValue() == 7 || date.getMonthValue() == 10) && 
             date.getDayOfMonth() == 1) {
            return "QUARTERLY";
        }
        
        // 年初は年次統計
        if (date.getMonthValue() == 1 && date.getDayOfMonth() == 1) {
            return "YEARLY";
        }
        
        return "DAILY";
    }
}