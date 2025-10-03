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
public class BookRankingBatchJob {

    @Autowired
    private PopularityRankingTasklet popularityRankingTasklet;

    @Autowired
    private CompletionRankingTasklet completionRankingTasklet;

    @Autowired
    private AuthorRankingTasklet authorRankingTasklet;

    @Autowired
    private GenreRankingTasklet genreRankingTasklet;

    @Autowired
    private BatchJobExecutionListener batchJobExecutionListener;

    @Bean(name = "bookRankingJob")
    public Job bookRankingJob(JobRepository jobRepository,
                             Step popularityRankingStep,
                             Step completionRankingStep,
                             Step authorRankingStep,
                             Step genreRankingStep) {
        return new JobBuilder("bookRankingJob", jobRepository)
                .start(popularityRankingStep)
                .next(completionRankingStep)
                .next(authorRankingStep)
                .next(genreRankingStep)
                .listener(batchJobExecutionListener)
                .build();
    }
    
    @Bean(name = "popularityRankingStep")
    public Step popularityRankingStep(JobRepository jobRepository,
                                     PlatformTransactionManager transactionManager) {
        return new StepBuilder("popularityRankingStep", jobRepository)
                .tasklet(popularityRankingTasklet, transactionManager)
                .build();
    }
    
    @Bean(name = "completionRankingStep")
    public Step completionRankingStep(JobRepository jobRepository,
                                     PlatformTransactionManager transactionManager) {
        return new StepBuilder("completionRankingStep", jobRepository)
                .tasklet(completionRankingTasklet, transactionManager)
                .build();
    }
    
    @Bean(name = "authorRankingStep")
    public Step authorRankingStep(JobRepository jobRepository,
                                 PlatformTransactionManager transactionManager) {
        return new StepBuilder("authorRankingStep", jobRepository)
                .tasklet(authorRankingTasklet, transactionManager)
                .build();
    }
    
    @Bean(name = "genreRankingStep")
    public Step genreRankingStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager) {
        return new StepBuilder("genreRankingStep", jobRepository)
                .tasklet(genreRankingTasklet, transactionManager)
                .build();
    }
}