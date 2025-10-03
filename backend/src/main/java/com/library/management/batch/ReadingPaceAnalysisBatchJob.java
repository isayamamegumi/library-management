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

import com.library.management.dto.ReadingPaceAnalysis;
import com.library.management.entity.User;

import java.time.LocalDateTime;

@Component
public class ReadingPaceAnalysisBatchJob {

    @Autowired
    private ReadingPaceAnalysisProcessor paceAnalysisProcessor;

    @Autowired
    private ReadingPaceAnalysisWriter paceAnalysisWriter;

    @Autowired
    private BatchJobExecutionListener batchJobExecutionListener;

    @Bean(name = "readingPaceAnalysisJob")
    public Job readingPaceAnalysisJob(JobRepository jobRepository,
                                     Step paceAnalysisStep) {
        return new JobBuilder("readingPaceAnalysisJob", jobRepository)
                .start(paceAnalysisStep)
                .listener(batchJobExecutionListener)
                .build();
    }
    
    @Bean(name = "paceAnalysisStep")
    public Step paceAnalysisStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager,
                                @Qualifier("userItemReader") ItemReader<User> userReader,
                                ItemProcessor<User, ReadingPaceAnalysis> paceAnalysisProcessor,
                                ItemWriter<ReadingPaceAnalysis> paceAnalysisWriter) {
        return new StepBuilder("paceAnalysisStep", jobRepository)
                .<User, ReadingPaceAnalysis>chunk(25, transactionManager)
                .reader(userReader)
                .processor(paceAnalysisProcessor)
                .writer(paceAnalysisWriter)
                .faultTolerant()
                .retryLimit(3)
                .retry(Exception.class)
                .skipLimit(5)
                .skip(Exception.class)
                .build();
    }
}