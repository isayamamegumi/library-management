package com.library.management.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Collection;

@Service
public class BatchJobExecutionListener implements JobExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(BatchJobExecutionListener.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        logger.info("バッチジョブ開始: {} - 実行ID: {}",
                   jobExecution.getJobInstance().getJobName(),
                   jobExecution.getId());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        Long executionId = jobExecution.getId();
        BatchStatus status = jobExecution.getStatus();

        String message = String.format("ジョブ名: %s, 実行ID: %d, ステータス: %s",
                                      jobName, executionId, status);

        if (status == BatchStatus.COMPLETED) {
            logger.info("バッチジョブ正常終了: {}", message);
        } else if (status == BatchStatus.FAILED) {
            logger.error("バッチジョブ異常終了: {}", message);
        } else {
            logger.warn("バッチジョブ異常状態で終了: {}", message);
        }

        // batch_execution_logsテーブルに実行ログを保存
        saveExecutionLog(jobExecution);
    }

    private void saveExecutionLog(JobExecution jobExecution) {
        try {
            String jobName = jobExecution.getJobInstance().getJobName();
            Long jobExecutionId = jobExecution.getId();
            Timestamp startTime = jobExecution.getStartTime() != null ?
                Timestamp.valueOf(jobExecution.getStartTime()) : null;
            Timestamp endTime = jobExecution.getEndTime() != null ?
                Timestamp.valueOf(jobExecution.getEndTime()) : null;
            String status = jobExecution.getStatus().toString();
            String exitCode = jobExecution.getExitStatus().getExitCode();
            String exitMessage = jobExecution.getExitStatus().getExitDescription();

            // ステップ実行統計を集計
            Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
            long totalReadCount = stepExecutions.stream()
                .mapToLong(StepExecution::getReadCount)
                .sum();
            long totalWriteCount = stepExecutions.stream()
                .mapToLong(StepExecution::getWriteCount)
                .sum();

            // 実行時間計算
            Long executionTimeMs = null;
            if (startTime != null && endTime != null) {
                executionTimeMs = Duration.between(
                    startTime.toInstant(),
                    endTime.toInstant()
                ).toMillis();
            }

            // エラーメッセージ取得
            String errorMessage = null;
            if (jobExecution.getStatus() == BatchStatus.FAILED) {
                Collection<Throwable> failures = jobExecution.getAllFailureExceptions();
                if (!failures.isEmpty()) {
                    Throwable firstFailure = failures.iterator().next();
                    errorMessage = firstFailure.getMessage();
                    if (errorMessage != null && errorMessage.length() > 1000) {
                        errorMessage = errorMessage.substring(0, 1000);
                    }
                }
            }

            // batch_execution_logsテーブルに保存
            String sql = """
                INSERT INTO batch_execution_logs
                (job_name, job_execution_id, start_time, end_time, status,
                 exit_code, exit_message, read_count, write_count,
                 execution_time_ms, error_message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """;

            jdbcTemplate.update(sql,
                jobName, jobExecutionId, startTime, endTime, status,
                exitCode, exitMessage, totalReadCount, totalWriteCount,
                executionTimeMs, errorMessage);

            logger.info("バッチ実行ログ保存完了: ジョブ名={}, 実行ID={}", jobName, jobExecutionId);

        } catch (Exception e) {
            logger.error("バッチ実行ログ保存エラー: {}", e.getMessage(), e);
        }
    }

    public void sendCustomNotification(String title, String content) {
        logger.info("=== カスタム通知 ===");
        logger.info("タイトル: {}", title);
        logger.info("内容: {}", content);
    }
}