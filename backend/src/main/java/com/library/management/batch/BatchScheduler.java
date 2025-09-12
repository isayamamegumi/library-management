package com.library.management.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class BatchScheduler {
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    private Job bookProcessingJob;
    
    @Autowired
    private Job dataCleanupJob;
    
    @Scheduled(cron = "0 0 2 * * ?")
    public void runBookProcessingJob() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("timestamp", LocalDateTime.now().toString())
                    .addLong("chunkSize", 20L)
                    .toJobParameters();
            
            jobLauncher.run(bookProcessingJob, jobParameters);
        } catch (Exception e) {
            System.err.println("バッチジョブの実行に失敗しました: " + e.getMessage());
        }
    }
    
    @Scheduled(cron = "0 0 3 * * SUN")
    public void runDataCleanupJob() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("timestamp", LocalDateTime.now().toString())
                    .addLong("daysToKeep", 90L)
                    .toJobParameters();
            
            jobLauncher.run(dataCleanupJob, jobParameters);
        } catch (Exception e) {
            System.err.println("データクリーンアップジョブの実行に失敗しました: " + e.getMessage());
        }
    }
}