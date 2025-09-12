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
public class UserHistoryScheduler {
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    @Qualifier("userReadingHistoryJob")
    private Job userReadingHistoryJob;
    
    // 毎週日曜日の午前4時に実行
    @Scheduled(cron = "0 0 4 * * SUN")
    public void runUserHistoryJob() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("executionType", "weekly")
                    .toJobParameters();
                    
            JobExecution jobExecution = jobLauncher.run(userReadingHistoryJob, jobParameters);
            System.out.println("ユーザー読書履歴ジョブ実行ID: " + jobExecution.getId());
            
        } catch (Exception e) {
            System.err.println("ユーザー読書履歴ジョブ実行エラー: " + e.getMessage());
        }
    }
}