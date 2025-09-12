package com.library.management.batch;

import com.library.management.entity.Book;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class ParallelBatchJobConfig {
    
    @Autowired
    private BookItemReader bookItemReader;
    
    @Autowired
    private BookItemProcessor bookItemProcessor;
    
    @Autowired
    private BookItemWriter bookItemWriter;
    
    @Bean
    public Job parallelBookProcessingJob(JobRepository jobRepository, Step managerStep) {
        return new JobBuilder("parallelBookProcessingJob", jobRepository)
                .start(managerStep)
                .build();
    }
    
    @Bean
    public Step managerStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("managerStep", jobRepository)
                .partitioner("workerStep", bookPartitioner())
                .step(workerStep(jobRepository, transactionManager))
                .gridSize(4)
                .taskExecutor(batchTaskExecutor())
                .build();
    }
    
    @Bean
    public Step workerStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("workerStep", jobRepository)
                .<Book, Book>chunk(5, transactionManager)
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
    
    @Bean
    public Partitioner bookPartitioner() {
        return new Partitioner() {
            @Override
            public Map<String, ExecutionContext> partition(int gridSize) {
                Map<String, ExecutionContext> partitions = new HashMap<>();
                
                for (int i = 0; i < gridSize; i++) {
                    ExecutionContext context = new ExecutionContext();
                    context.putInt("partitionNumber", i);
                    context.putInt("gridSize", gridSize);
                    partitions.put("partition" + i, context);
                }
                
                return partitions;
            }
        };
    }
    
    @Bean
    public TaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("batch-");
        executor.initialize();
        return executor;
    }
}