package com.library.management.batch;

import com.library.management.entity.Book;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

@Configuration
public class DataCleanupJobConfig {
    
    @Autowired
    private DataSource dataSource;
    
    @Bean
    public Job dataCleanupJob(JobRepository jobRepository, Step dataCleanupStep) {
        return new JobBuilder("dataCleanupJob", jobRepository)
                .start(dataCleanupStep)
                .build();
    }
    
    @Bean
    public Step dataCleanupStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("dataCleanupStep", jobRepository)
                .tasklet(dataCleanupTasklet(null), transactionManager)
                .build();
    }
    
    @Bean
    @JobScope
    public Tasklet dataCleanupTasklet(@Value("#{jobParameters['daysToKeep'] ?: 30}") Integer daysToKeep) {
        return (contribution, chunkContext) -> {
            try (Connection connection = dataSource.getConnection()) {
                String sql = "DELETE FROM books WHERE created_at < NOW() - INTERVAL ? DAY AND read_status_id = (SELECT id FROM read_statuses WHERE status = 'COMPLETED')";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setInt(1, daysToKeep);
                    int deletedRows = statement.executeUpdate();
                    System.out.println("データクリーンアップ完了: " + deletedRows + " 件のレコードを削除しました");
                }
            }
            return RepeatStatus.FINISHED;
        };
    }
}