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
public class ReadingPaceScheduler {
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    @Qualifier("readingPaceAnalysisJob")
    private Job readingPaceAnalysisJob;
    
    // 毎日午前6時に実行
    @Scheduled(cron = "0 0 6 * * ?")
    public void runReadingPaceAnalysisJob() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("analysisType", "daily")
                    .toJobParameters();
                    
            JobExecution jobExecution = jobLauncher.run(readingPaceAnalysisJob, jobParameters);
            System.out.println("読書ペース分析ジョブ実行ID: " + jobExecution.getId());
            
        } catch (Exception e) {
            System.err.println("読書ペース分析ジョブ実行エラー: " + e.getMessage());
        }
    }
}