package com.library.management.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;

@Component
public class PeriodicalStatsBatchJob {
    
    @Autowired
    private WeeklyStatsTasklet weeklyStatsTasklet;
    
    @Autowired
    private MonthlyStatsTasklet monthlyStatsTasklet;
    
    @Autowired
    private QuarterlyStatsTasklet quarterlyStatsTasklet;
    
    @Autowired
    private YearlyStatsTasklet yearlyStatsTasklet;

    @Bean(name = "periodicalStatsJob")
    public Job periodicalStatsJob(JobRepository jobRepository, 
                                 Step weeklyStatsStep,
                                 Step monthlyStatsStep,
                                 Step quarterlyStatsStep,
                                 Step yearlyStatsStep) {
        return new JobBuilder("periodicalStatsJob", jobRepository)
                .start(weeklyStatsStep)
                .next(monthlyStatsStep)
                .next(quarterlyStatsStep)
                .next(yearlyStatsStep)
                .listener(new JobExecutionListener() {
                    @Override
                    public void beforeJob(org.springframework.batch.core.JobExecution jobExecution) {
                        System.out.println("期間別統計バッチ開始: " + LocalDateTime.now());
                    }
                    
                    @Override
                    public void afterJob(org.springframework.batch.core.JobExecution jobExecution) {
                        System.out.println("期間別統計バッチ完了: " + jobExecution.getStatus());
                    }
                })
                .build();
    }
    
    @Bean(name = "weeklyStatsStep")
    public Step weeklyStatsStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager) {
        return new StepBuilder("weeklyStatsStep", jobRepository)
                .tasklet(weeklyStatsTasklet, transactionManager)
                .build();
    }
    
    @Bean(name = "monthlyStatsStep")
    public Step monthlyStatsStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager) {
        return new StepBuilder("monthlyStatsStep", jobRepository)
                .tasklet(monthlyStatsTasklet, transactionManager)
                .build();
    }
    
    @Bean(name = "quarterlyStatsStep")
    public Step quarterlyStatsStep(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager) {
        return new StepBuilder("quarterlyStatsStep", jobRepository)
                .tasklet(quarterlyStatsTasklet, transactionManager)
                .build();
    }
    
    @Bean(name = "yearlyStatsStep")
    public Step yearlyStatsStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager) {
        return new StepBuilder("yearlyStatsStep", jobRepository)
                .tasklet(yearlyStatsTasklet, transactionManager)
                .build();
    }
}