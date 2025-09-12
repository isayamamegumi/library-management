package com.library.management.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import com.library.management.dto.UserStats;
import com.library.management.entity.User;

import java.time.LocalDateTime;

@Component
public class MonthlyStatsBatchJob {
    
    @Autowired
    private UserStatsProcessor userStatsProcessor;
    
    @Autowired
    private MonthlyStatsWriter monthlyStatsWriter;
    
    @Autowired
    private OverallStatsTasklet overallStatsTasklet;

    @Bean(name = "monthlyStatsJob")
    public Job monthlyStatsJob(JobRepository jobRepository, 
                              Step userStatsStep, 
                              Step overallStatsStep) {
        return new JobBuilder("monthlyStatsJob", jobRepository)
                .start(userStatsStep)
                .next(overallStatsStep)
                .listener(new JobExecutionListener() {
                    @Override
                    public void beforeJob(org.springframework.batch.core.JobExecution jobExecution) {
                        System.out.println("月次統計バッチ開始: " + LocalDateTime.now());
                    }
                    
                    @Override
                    public void afterJob(org.springframework.batch.core.JobExecution jobExecution) {
                        System.out.println("月次統計バッチ完了: " + jobExecution.getStatus());
                    }
                })
                .build();
    }
    
    @Bean(name = "userStatsStep")
    public Step userStatsStep(JobRepository jobRepository,
                             PlatformTransactionManager transactionManager,
                             @Qualifier("userItemReader") ItemReader<User> userReader,
                             ItemProcessor<User, UserStats> userStatsProcessor,
                             @Qualifier("monthlyStatsWriter") ItemWriter<UserStats> userStatsWriter) {
        return new StepBuilder("userStatsStep", jobRepository)
                .<User, UserStats>chunk(100, transactionManager)
                .reader(userReader)
                .processor(userStatsProcessor)
                .writer(userStatsWriter)
                .faultTolerant()
                .retryLimit(3)
                .retry(Exception.class)
                .skipLimit(10)
                .skip(Exception.class)
                .build();
    }
    
    @Bean(name = "overallStatsStep")
    public Step overallStatsStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager) {
        return new StepBuilder("overallStatsStep", jobRepository)
                .tasklet(overallStatsTasklet, transactionManager)
                .build();
    }
}