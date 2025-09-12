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

@Component
public class BookRankingScheduler {
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    @Qualifier("bookRankingJob")
    private Job bookRankingJob;
    
    // 毎日午前5時に実行
    @Scheduled(cron = "0 0 5 * * ?")
    public void runBookRankingJob() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("period", "daily")
                    .toJobParameters();
                    
            JobExecution jobExecution = jobLauncher.run(bookRankingJob, jobParameters);
            System.out.println("書籍ランキングジョブ実行ID: " + jobExecution.getId());
            
        } catch (Exception e) {
            System.err.println("書籍ランキングジョブ実行エラー: " + e.getMessage());
        }
    }
    
    // 毎週月曜日は週間ランキングも生成
    @Scheduled(cron = "0 0 6 * * MON")
    public void runWeeklyBookRankingJob() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("period", "weekly")
                    .toJobParameters();
                    
            JobExecution jobExecution = jobLauncher.run(bookRankingJob, jobParameters);
            System.out.println("週間書籍ランキングジョブ実行ID: " + jobExecution.getId());
            
        } catch (Exception e) {
            System.err.println("週間書籍ランキングジョブ実行エラー: " + e.getMessage());
        }
    }
}