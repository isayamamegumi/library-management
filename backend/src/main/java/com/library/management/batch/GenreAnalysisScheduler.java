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
public class GenreAnalysisScheduler {
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    @Qualifier("genreAnalysisJob")
    private Job genreAnalysisJob;
    
    // 毎週土曜日の午前2時に実行
    @Scheduled(cron = "0 0 2 * * SAT")
    public void runGenreAnalysisJob() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("analysisType", "weekly")
                    .toJobParameters();
                    
            JobExecution jobExecution = jobLauncher.run(genreAnalysisJob, jobParameters);
            System.out.println("ジャンル分析ジョブ実行ID: " + jobExecution.getId());
            
        } catch (Exception e) {
            System.err.println("ジャンル分析ジョブ実行エラー: " + e.getMessage());
        }
    }
}