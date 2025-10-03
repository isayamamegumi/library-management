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

import com.library.management.dto.GenreAnalysis;

import java.time.LocalDateTime;

@Component
public class GenreAnalysisBatchJob {

    @Autowired
    private GenreReader genreReader;

    @Autowired
    private GenreAnalysisProcessor genreAnalysisProcessor;

    @Autowired
    private GenreAnalysisWriter genreAnalysisWriter;

    @Autowired
    private BatchJobExecutionListener batchJobExecutionListener;

    @Bean(name = "genreAnalysisJob")
    public Job genreAnalysisJob(JobRepository jobRepository,
                               Step genreAnalysisStep) {
        return new JobBuilder("genreAnalysisJob", jobRepository)
                .start(genreAnalysisStep)
                .listener(batchJobExecutionListener)
                .build();
    }
    
    @Bean(name = "genreAnalysisStep")
    public Step genreAnalysisStep(JobRepository jobRepository,
                                 PlatformTransactionManager transactionManager,
                                 @Qualifier("genreItemReader") ItemReader<String> genreReader,
                                 ItemProcessor<String, GenreAnalysis> genreAnalysisProcessor,
                                 ItemWriter<GenreAnalysis> genreAnalysisWriter) {
        return new StepBuilder("genreAnalysisStep", jobRepository)
                .<String, GenreAnalysis>chunk(10, transactionManager)
                .reader(genreReader)
                .processor(genreAnalysisProcessor)
                .writer(genreAnalysisWriter)
                .faultTolerant()
                .retryLimit(3)
                .retry(Exception.class)
                .skipLimit(5)
                .skip(Exception.class)
                .build();
    }
}