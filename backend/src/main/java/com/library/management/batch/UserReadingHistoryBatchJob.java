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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import com.library.management.dto.UserReadingHistory;
import com.library.management.entity.User;

import java.time.LocalDateTime;

@Component
public class UserReadingHistoryBatchJob {
    
    @Autowired
    private UserHistoryProcessor userHistoryProcessor;
    
    @Autowired
    private UserHistoryWriter userHistoryWriter;

    @Bean(name = "userReadingHistoryJob")
    public Job userReadingHistoryJob(JobRepository jobRepository, 
                                   Step userHistoryStep) {
        return new JobBuilder("userReadingHistoryJob", jobRepository)
                .start(userHistoryStep)
                .listener(new JobExecutionListener() {
                    @Override
                    public void beforeJob(org.springframework.batch.core.JobExecution jobExecution) {
                        System.out.println("ユーザー読書履歴集計バッチ開始: " + LocalDateTime.now());
                    }
                    
                    @Override
                    public void afterJob(org.springframework.batch.core.JobExecution jobExecution) {
                        System.out.println("ユーザー読書履歴集計バッチ完了: " + jobExecution.getStatus());
                    }
                })
                .build();
    }
    
    @Bean(name = "userHistoryStep")
    public Step userHistoryStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               @Qualifier("userItemReader") ItemReader<User> userReader,
                               ItemProcessor<User, UserReadingHistory> userHistoryProcessor,
                               ItemWriter<UserReadingHistory> userHistoryWriter) {
        return new StepBuilder("userHistoryStep", jobRepository)
                .<User, UserReadingHistory>chunk(50, transactionManager)
                .reader(userReader)
                .processor(userHistoryProcessor)
                .writer(userHistoryWriter)
                .faultTolerant()
                .retryLimit(3)
                .retry(Exception.class)
                .skipLimit(5)
                .skip(Exception.class)
                .build();
    }
}