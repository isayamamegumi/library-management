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

import java.time.LocalDate;

@Component
public class MonthlyStatsScheduler {
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    @Qualifier("monthlyStatsJob")
    private Job monthlyStatsJob;
    
    // 毎月1日の午前3時に実行
    @Scheduled(cron = "0 0 3 1 * ?")
    public void runMonthlyStatsJob() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("targetMonth", LocalDate.now().toString())
                    .toJobParameters();
                    
            JobExecution jobExecution = jobLauncher.run(monthlyStatsJob, jobParameters);
            System.out.println("月次統計ジョブ実行ID: " + jobExecution.getId());
            
        } catch (Exception e) {
            System.err.println("月次統計ジョブ実行エラー: " + e.getMessage());
        }
    }
}