package com.library.management.batch;

import com.library.management.entity.Book;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BookBatchJobConfig {
    
    @Autowired
    private BookItemReader bookItemReader;
    
    @Autowired
    private BookItemProcessor bookItemProcessor;
    
    @Autowired
    private BookItemWriter bookItemWriter;
    
    @Autowired
    private BatchJobExecutionListener batchJobExecutionListener;
    
    @Bean
    public Job bookProcessingJob(JobRepository jobRepository, Step bookProcessingStep) {
        return new JobBuilder("bookProcessingJob", jobRepository)
                .listener(batchJobExecutionListener)
                .start(bookProcessingStep)
                .build();
    }
    
    @Bean
    public Step bookProcessingStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("bookProcessingStep", jobRepository)
                .<Book, Book>chunk(10, transactionManager)
                .reader(bookItemReader)
                .processor(bookItemProcessor)
                .writer(bookItemWriter)
                .faultTolerant()
                .retryLimit(3)
                .retry(Exception.class)
                .skipLimit(5)
                .skip(Exception.class)
                .build();
    }
}