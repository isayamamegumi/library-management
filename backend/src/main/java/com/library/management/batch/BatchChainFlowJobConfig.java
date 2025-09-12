package com.library.management.batch;

import org.springframework.batch.core.*;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Map;

@Configuration
public class BatchChainFlowJobConfig {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    // バッチチェーン処理ジョブ（条件分岐フロー付き）
    @Bean
    public Job batchChainFlowJob(JobRepository jobRepository,
                                Step dataValidationStep,
                                Step systemHealthCheckStep,
                                JobExecutionDecider dataVolumeDecider,
                                Step lightProcessingStep,
                                Step heavyProcessingStep,
                                Step cleanupStep,
                                Step notificationStep) {
        return new JobBuilder("batchChainFlowJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                // 1. データ検証
                .start(dataValidationStep)
                    .on("FAILED").to(notificationStep) // 検証失敗時は通知して終了
                // 2. システムヘルスチェック
                .from(dataValidationStep).on("COMPLETED").to(systemHealthCheckStep)
                    .on("FAILED").to(notificationStep) // ヘルスチェック失敗時も通知
                // 3. データ量に基づく条件分岐
                .from(systemHealthCheckStep).on("COMPLETED").to(dataVolumeDecider)
                    .on("LIGHT_PROCESSING").to(lightProcessingStep)
                    .on("HEAVY_PROCESSING").to(heavyProcessingStep)
                // 4. 軽い処理後のフロー
                .from(lightProcessingStep).on("COMPLETED").to(cleanupStep)
                // 5. 重い処理後のフロー
                .from(heavyProcessingStep).on("COMPLETED").to(cleanupStep)
                // 6. クリーンアップ後の終了処理
                .from(cleanupStep).on("*").to(notificationStep)
                .end()
                .build();
    }
    
    // 1. データ検証 Step
    @Bean
    public Step dataValidationStep(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager) {
        return new StepBuilder("dataValidationStep", jobRepository)
                .tasklet(dataValidationTasklet(), transactionManager)
                .build();
    }
    
    @Bean
    public Tasklet dataValidationTasklet() {
        return (contribution, chunkContext) -> {
            System.out.println("[データ検証] 開始: " + LocalDateTime.now());
            
            try {
                // データ整合性チェック
                Long invalidBooks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM books WHERE title IS NULL OR title = ''", Long.class);
                
                Long invalidUsers = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE username IS NULL OR username = ''", Long.class);
                
                // 孤立データチェック
                Long orphanedBooks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM books WHERE user_id NOT IN (SELECT id FROM users)", Long.class);
                
                if (invalidBooks > 0 || invalidUsers > 0 || orphanedBooks > 0) {
                    String errorMsg = String.format(
                        "データ整合性エラー: 無効な書籍=%d, 無効なユーザー=%d, 孤立書籍=%d",
                        invalidBooks, invalidUsers, orphanedBooks);
                    
                    chunkContext.getStepContext().getStepExecution()
                        .getExecutionContext().put("validationError", errorMsg);
                    
                    System.err.println(errorMsg);
                    contribution.setExitStatus(ExitStatus.FAILED);
                    return RepeatStatus.FINISHED;
                }
                
                System.out.println("[データ検証] 成功: データに問題なし");
                return RepeatStatus.FINISHED;
                
            } catch (Exception e) {
                System.err.println("[データ検証] エラー: " + e.getMessage());
                contribution.setExitStatus(ExitStatus.FAILED);
                return RepeatStatus.FINISHED;
            }
        };
    }
    
    // 2. システムヘルスチェック Step
    @Bean
    public Step systemHealthCheckStep(JobRepository jobRepository,
                                     PlatformTransactionManager transactionManager) {
        return new StepBuilder("systemHealthCheckStep", jobRepository)
                .tasklet(systemHealthCheckTasklet(), transactionManager)
                .build();
    }
    
    @Bean
    public Tasklet systemHealthCheckTasklet() {
        return (contribution, chunkContext) -> {
            System.out.println("[システムヘルスチェック] 開始: " + LocalDateTime.now());
            
            try {
                // データベース接続チェック
                Integer dbTest = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                if (dbTest == null || dbTest != 1) {
                    throw new RuntimeException("データベース接続エラー");
                }
                
                // メモリ使用量チェック
                Runtime runtime = Runtime.getRuntime();
                long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                long maxMemory = runtime.maxMemory();
                double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
                
                if (memoryUsagePercent > 80) {
                    System.out.println("警告: メモリ使用率が高い: " + String.format("%.1f%%", memoryUsagePercent));
                }
                
                // システム情報をコンテキストに保存
                ExecutionContext executionContext = chunkContext.getStepContext().getStepExecution().getExecutionContext();
                executionContext.put("memoryUsagePercent", memoryUsagePercent);
                executionContext.put("usedMemoryMB", usedMemory / 1024 / 1024);
                
                System.out.println("[システムヘルスチェック] 成功: メモリ使用率 " + String.format("%.1f%%", memoryUsagePercent));
                return RepeatStatus.FINISHED;
                
            } catch (Exception e) {
                System.err.println("[システムヘルスチェック] エラー: " + e.getMessage());
                contribution.setExitStatus(ExitStatus.FAILED);
                return RepeatStatus.FINISHED;
            }
        };
    }
    
    // 3. データ量ベースの条件分岐デシジョン
    @Bean
    public JobExecutionDecider dataVolumeDecider() {
        return (jobExecution, stepExecution) -> {
            try {
                Long totalRecords = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM books WHERE created_at >= CURRENT_DATE - INTERVAL '30 days'", Long.class);
                
                System.out.println("[条件分岐] 処理対象レコード数: " + totalRecords);
                
                // レコード数に基づいて処理方法を決定
                if (totalRecords < 1000) {
                    System.out.println("[条件分岐] 軽い処理を選択");
                    return new FlowExecutionStatus("LIGHT_PROCESSING");
                } else {
                    System.out.println("[条件分岐] 重い処理を選択");
                    return new FlowExecutionStatus("HEAVY_PROCESSING");
                }
                
            } catch (Exception e) {
                System.err.println("[条件分岐] エラー: " + e.getMessage());
                return FlowExecutionStatus.FAILED;
            }
        };
    }
    
    // 4. 軽い処理 Step
    @Bean
    public Step lightProcessingStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager) {
        return new StepBuilder("lightProcessingStep", jobRepository)
                .tasklet(lightProcessingTasklet(), transactionManager)
                .build();
    }
    
    @Bean
    public Tasklet lightProcessingTasklet() {
        return (contribution, chunkContext) -> {
            System.out.println("[軽い処理] 開始: " + LocalDateTime.now());
            
            // シンプルな統計処理
            Map<String, Object> simpleStats = jdbcTemplate.queryForMap("""
                SELECT 
                    COUNT(*) as total_books,
                    COUNT(CASE WHEN rs.name = '読了' THEN 1 END) as completed_books
                FROM books b
                LEFT JOIN read_statuses rs ON b.read_status_id = rs.id
                WHERE b.created_at >= CURRENT_DATE - INTERVAL '30 days'
                """);
            
            System.out.println("[軽い処理] 結果: " + simpleStats);
            
            // 結果をコンテキストに保存
            ExecutionContext executionContext = chunkContext.getStepContext().getStepExecution().getExecutionContext();
            executionContext.put("processingType", "LIGHT");
            executionContext.put("processedRecords", simpleStats.get("total_books"));
            
            Thread.sleep(2000); // シミュレート用の待機
            
            System.out.println("[軽い処理] 完了");
            return RepeatStatus.FINISHED;
        };
    }
    
    // 5. 重い処理 Step
    @Bean
    public Step heavyProcessingStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager) {
        return new StepBuilder("heavyProcessingStep", jobRepository)
                .tasklet(heavyProcessingTasklet(), transactionManager)
                .build();
    }
    
    @Bean
    public Tasklet heavyProcessingTasklet() {
        return (contribution, chunkContext) -> {
            System.out.println("[重い処理] 開始: " + LocalDateTime.now());
            
            // 複集な統計処理
            java.util.List<Map<String, Object>> complexStats = jdbcTemplate.queryForList("""
                SELECT 
                    u.username,
                    g.name as genre_name,
                    COUNT(b.id) as book_count,
                    AVG(CASE WHEN rs.name = '読了' AND b.updated_at > b.created_at 
                        THEN EXTRACT(EPOCH FROM (b.updated_at - b.created_at))/86400 END) as avg_reading_days
                FROM users u
                JOIN books b ON u.id = b.user_id
                LEFT JOIN genres g ON b.genre_id = g.id
                LEFT JOIN read_statuses rs ON b.read_status_id = rs.id
                WHERE b.created_at >= CURRENT_DATE - INTERVAL '30 days'
                GROUP BY u.id, u.username, g.id, g.name
                HAVING COUNT(b.id) >= 2
                ORDER BY book_count DESC
                """);
            
            System.out.println("[重い処理] 複集統計件数: " + complexStats.size());
            
            // 結果をコンテキストに保存
            ExecutionContext executionContext = chunkContext.getStepContext().getStepExecution().getExecutionContext();
            executionContext.put("processingType", "HEAVY");
            executionContext.put("processedRecords", complexStats.size());
            
            Thread.sleep(5000); // シミュレート用の待機
            
            System.out.println("[重い処理] 完了");
            return RepeatStatus.FINISHED;
        };
    }
    
    // 6. クリーンアップ Step
    @Bean
    public Step cleanupStep(JobRepository jobRepository,
                           PlatformTransactionManager transactionManager) {
        return new StepBuilder("cleanupStep", jobRepository)
                .tasklet(cleanupTasklet(), transactionManager)
                .build();
    }
    
    @Bean
    public Tasklet cleanupTasklet() {
        return (contribution, chunkContext) -> {
            System.out.println("[クリーンアップ] 開始: " + LocalDateTime.now());
            
            // 一時テーブルのクリーンアップ
            int tempTableRows = jdbcTemplate.update(
                "DELETE FROM system_logs WHERE created_at < NOW() - INTERVAL '7 days'");
            
            System.out.println("[クリーンアップ] 削除されたログ: " + tempTableRows + "件");
            
            // 結果をコンテキストに保存
            ExecutionContext executionContext = chunkContext.getStepContext().getStepExecution().getExecutionContext();
            executionContext.put("cleanedRows", tempTableRows);
            
            System.out.println("[クリーンアップ] 完了");
            return RepeatStatus.FINISHED;
        };
    }
    
    // 7. 通知 Step
    @Bean
    public Step notificationStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager) {
        return new StepBuilder("notificationStep", jobRepository)
                .tasklet(notificationTasklet(), transactionManager)
                .build();
    }
    
    @Bean
    public Tasklet notificationTasklet() {
        return (contribution, chunkContext) -> {
            System.out.println("[通知] 開始: " + LocalDateTime.now());
            
            JobExecution jobExecution = chunkContext.getStepContext().getStepExecution().getJobExecution();
            
            // ジョブの結果を集計
            StringBuilder summary = new StringBuilder();
            summary.append("バッチチェーン処理結果:\n");
            summary.append("ジョブ名: ").append(jobExecution.getJobInstance().getJobName()).append("\n");
            summary.append("実行ID: ").append(jobExecution.getId()).append("\n");
            summary.append("ステータス: ").append(jobExecution.getStatus()).append("\n");
            
            // 各Stepの結果を集計
            for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
                summary.append("Step: ").append(stepExecution.getStepName())
                       .append(" - ").append(stepExecution.getStatus()).append("\n");
                
                ExecutionContext stepContext = stepExecution.getExecutionContext();
                if (stepContext.containsKey("processingType")) {
                    summary.append("  処理タイプ: ").append(stepContext.get("processingType")).append("\n");
                }
                if (stepContext.containsKey("processedRecords")) {
                    summary.append("  処理件数: ").append(stepContext.get("processedRecords")).append("\n");
                }
            }
            
            System.out.println("[通知] ジョブ完了通知:");
            System.out.println(summary.toString());
            
            // 実際の環境ではここでメール送信やSlack通知などを行う
            
            return RepeatStatus.FINISHED;
        };
    }
}