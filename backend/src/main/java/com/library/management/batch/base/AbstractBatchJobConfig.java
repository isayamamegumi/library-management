package com.library.management.batch.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

/**
 * バッチジョブ設定の抽象基底クラス
 * 共通的な設定とユーティリティメソッドを提供
 */
public abstract class AbstractBatchJobConfig {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected final ObjectMapper objectMapper;

    public AbstractBatchJobConfig() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 共通のJobExecutionListener
     */
    protected JobExecutionListener createJobExecutionListener(String jobDisplayName) {
        return new JobExecutionListener() {
            @Override
            public void beforeJob(JobExecution jobExecution) {
                System.out.println(String.format("[%s] 開始: %s", jobDisplayName, LocalDateTime.now()));
                logJobEvent("START", jobExecution, null);
            }

            @Override
            public void afterJob(JobExecution jobExecution) {
                String status = jobExecution.getStatus().toString();
                System.out.println(String.format("[%s] 完了: %s at %s", jobDisplayName, status, LocalDateTime.now()));

                if (jobExecution.getStatus().isUnsuccessful()) {
                    String errorMessage = jobExecution.getAllFailureExceptions().stream()
                        .map(Throwable::getMessage)
                        .reduce("", (a, b) -> a + "; " + b);
                    logJobEvent("ERROR", jobExecution, errorMessage);
                } else {
                    logJobEvent("COMPLETE", jobExecution, null);
                }
            }
        };
    }

    /**
     * 共通のRunIdIncrementer
     */
    protected RunIdIncrementer createRunIdIncrementer() {
        return new RunIdIncrementer();
    }

    /**
     * ジョブイベントのログ記録
     */
    protected void logJobEvent(String eventType, JobExecution jobExecution, String errorMessage) {
        try {
            String sql = """
                INSERT INTO batch_execution_logs
                (job_name, job_execution_id, status, start_time, end_time, error_message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (job_execution_id)
                DO UPDATE SET
                    status = EXCLUDED.status,
                    end_time = EXCLUDED.end_time,
                    error_message = EXCLUDED.error_message,
                    execution_time_ms = CASE
                        WHEN EXCLUDED.end_time IS NOT NULL AND batch_execution_logs.start_time IS NOT NULL
                        THEN EXTRACT(EPOCH FROM (EXCLUDED.end_time - batch_execution_logs.start_time)) * 1000
                        ELSE NULL
                    END
                """;

            jdbcTemplate.update(sql,
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(),
                jobExecution.getStatus().toString(),
                jobExecution.getStartTime(),
                jobExecution.getEndTime(),
                errorMessage
            );
        } catch (Exception e) {
            System.err.println("ログ記録エラー: " + e.getMessage());
        }
    }

    /**
     * 統計データをJSONとして保存
     */
    protected void saveStatisticsAsJson(String reportType, Object data) {
        try {
            String jsonData = objectMapper.writeValueAsString(data);
            jdbcTemplate.update(
                """
                INSERT INTO batch_statistics (report_type, target_date, data_json)
                VALUES (?, CURRENT_DATE, ?::jsonb)
                ON CONFLICT (report_type, target_date)
                DO UPDATE SET data_json = EXCLUDED.data_json, updated_at = NOW()
                """,
                reportType, jsonData
            );
        } catch (Exception e) {
            throw new RuntimeException("統計データ保存エラー: " + e.getMessage(), e);
        }
    }

    /**
     * メモリ使用量チェック
     */
    protected double checkMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        return (double) usedMemory / maxMemory * 100;
    }

    /**
     * データ整合性チェック
     */
    protected boolean validateDataIntegrity() {
        try {
            Long invalidBooks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM books WHERE title IS NULL OR title = ''", Long.class);
            Long invalidUsers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username IS NULL OR username = ''", Long.class);
            Long orphanedBooks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM books WHERE user_id NOT IN (SELECT id FROM users)", Long.class);

            return invalidBooks == 0 && invalidUsers == 0 && orphanedBooks == 0;
        } catch (Exception e) {
            System.err.println("データ整合性チェックエラー: " + e.getMessage());
            return false;
        }
    }
}