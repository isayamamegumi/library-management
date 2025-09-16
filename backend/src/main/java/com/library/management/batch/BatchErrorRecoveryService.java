package com.library.management.batch;

import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.sql.Timestamp;

@Service
public class BatchErrorRecoveryService {

    @Autowired
    private JobExplorer jobExplorer;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public static class RecoveryResult {
        private boolean success;
        private String message;
        private Long newExecutionId;
        private Exception error;

        public RecoveryResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public RecoveryResult(boolean success, String message, Long newExecutionId) {
            this.success = success;
            this.message = message;
            this.newExecutionId = newExecutionId;
        }

        public RecoveryResult(boolean success, String message, Exception error) {
            this.success = success;
            this.message = message;
            this.error = error;
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public Long getNewExecutionId() { return newExecutionId; }
        public void setNewExecutionId(Long newExecutionId) { this.newExecutionId = newExecutionId; }

        public Exception getError() { return error; }
        public void setError(Exception error) { this.error = error; }
    }

    /**
     * 失敗したジョブを再起動する
     */
    public RecoveryResult restartFailedJob(Long jobExecutionId) {
        try {
            JobExecution failedExecution = jobExplorer.getJobExecution(jobExecutionId);

            if (failedExecution == null) {
                return new RecoveryResult(false, "指定された実行IDのジョブが見つかりません: " + jobExecutionId);
            }

            if (failedExecution.getStatus() != BatchStatus.FAILED) {
                return new RecoveryResult(false, "ジョブが失敗状態ではありません。現在のステータス: " + failedExecution.getStatus());
            }

            // ジョブインスタンスから新しいパラメータを作成
            JobInstance jobInstance = failedExecution.getJobInstance();
            JobParameters originalParameters = failedExecution.getJobParameters();

            // 再起動用パラメータを作成（新しいタイムスタンプを追加）
            JobParametersBuilder parametersBuilder = new JobParametersBuilder(originalParameters);
            parametersBuilder.addLong("restartTimestamp", System.currentTimeMillis());
            parametersBuilder.addString("restartReason", "ERROR_RECOVERY");
            parametersBuilder.addLong("originalExecutionId", jobExecutionId);

            // ジョブを取得して再実行
            Job job = applicationContext.getBean(jobInstance.getJobName(), Job.class);
            JobExecution newExecution = jobLauncher.run(job, parametersBuilder.toJobParameters());

            // 再起動ログを保存
            logRecoveryAction(jobExecutionId, newExecution.getId(), "RESTART", "ジョブ再起動実行");

            return new RecoveryResult(true, "ジョブを正常に再起動しました", newExecution.getId());

        } catch (JobExecutionAlreadyRunningException e) {
            return new RecoveryResult(false, "ジョブが既に実行中です", e);
        } catch (JobInstanceAlreadyCompleteException e) {
            return new RecoveryResult(false, "ジョブインスタンスが既に完了しています", e);
        } catch (JobRestartException e) {
            return new RecoveryResult(false, "ジョブの再起動に失敗しました: " + e.getMessage(), e);
        } catch (Exception e) {
            return new RecoveryResult(false, "予期しないエラーが発生しました: " + e.getMessage(), e);
        }
    }

    /**
     * 失敗したステップのみを再実行する
     */
    public RecoveryResult retryFailedStep(Long jobExecutionId, String stepName) {
        try {
            JobExecution jobExecution = jobExplorer.getJobExecution(jobExecutionId);

            if (jobExecution == null) {
                return new RecoveryResult(false, "指定された実行IDのジョブが見つかりません: " + jobExecutionId);
            }

            // 失敗したステップを探す
            StepExecution failedStep = null;
            for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
                if (stepExecution.getStepName().equals(stepName) &&
                    stepExecution.getStatus() == BatchStatus.FAILED) {
                    failedStep = stepExecution;
                    break;
                }
            }

            if (failedStep == null) {
                return new RecoveryResult(false, "指定されたステップが失敗状態で見つかりません: " + stepName);
            }

            // ステップの再実行（実際の実装は Spring Batch の機能に依存）
            // ここでは簡略化してジョブ全体を再実行
            return restartFailedJob(jobExecutionId);

        } catch (Exception e) {
            return new RecoveryResult(false, "ステップ再実行エラー: " + e.getMessage(), e);
        }
    }

    /**
     * スキップされたアイテムをリカバリする
     */
    public RecoveryResult recoverSkippedItems(Long jobExecutionId) {
        try {
            JobExecution jobExecution = jobExplorer.getJobExecution(jobExecutionId);

            if (jobExecution == null) {
                return new RecoveryResult(false, "指定された実行IDのジョブが見つかりません: " + jobExecutionId);
            }

            // スキップされたアイテムの情報を収集
            long totalSkipCount = 0;
            StringBuilder skipInfo = new StringBuilder();

            for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
                long skipCount = stepExecution.getSkipCount();
                if (skipCount > 0) {
                    totalSkipCount += skipCount;
                    skipInfo.append(String.format("Step: %s, Skip Count: %d; ",
                        stepExecution.getStepName(), skipCount));
                }
            }

            if (totalSkipCount == 0) {
                return new RecoveryResult(false, "スキップされたアイテムはありません");
            }

            // スキップされたアイテムの詳細をログに記録
            logRecoveryAction(jobExecutionId, null, "SKIP_ANALYSIS",
                String.format("スキップアイテム分析完了。総スキップ数: %d, 詳細: %s", totalSkipCount, skipInfo.toString()));

            return new RecoveryResult(true,
                String.format("スキップされたアイテムを分析しました。総数: %d件", totalSkipCount));

        } catch (Exception e) {
            return new RecoveryResult(false, "スキップアイテム分析エラー: " + e.getMessage(), e);
        }
    }

    /**
     * 長時間実行中のジョブを安全に停止する
     */
    public RecoveryResult stopLongRunningJob(Long jobExecutionId) {
        try {
            JobExecution jobExecution = jobExplorer.getJobExecution(jobExecutionId);

            if (jobExecution == null) {
                return new RecoveryResult(false, "指定された実行IDのジョブが見つかりません: " + jobExecutionId);
            }

            if (jobExecution.getStatus() != BatchStatus.STARTED) {
                return new RecoveryResult(false,
                    "ジョブが実行中ではありません。現在のステータス: " + jobExecution.getStatus());
            }

            // 実行時間チェック
            if (jobExecution.getStartTime() != null) {
                long runningTime = System.currentTimeMillis() -
                    jobExecution.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                long runningMinutes = runningTime / (60 * 1000);

                if (runningMinutes < 30) { // 30分未満の場合は警告
                    return new RecoveryResult(false,
                        String.format("ジョブの実行時間が短いため停止を推奨しません。実行時間: %d分", runningMinutes));
                }
            }

            // ジョブを停止状態に設定
            jobExecution.setStatus(BatchStatus.STOPPING);

            // 停止ログを保存
            logRecoveryAction(jobExecutionId, null, "FORCE_STOP",
                "長時間実行ジョブの強制停止");

            return new RecoveryResult(true, "ジョブの停止リクエストを送信しました");

        } catch (Exception e) {
            return new RecoveryResult(false, "ジョブ停止エラー: " + e.getMessage(), e);
        }
    }

    /**
     * 失敗したジョブの詳細分析
     */
    public Map<String, Object> analyzeJobFailure(Long jobExecutionId) {
        JobExecution jobExecution = jobExplorer.getJobExecution(jobExecutionId);

        if (jobExecution == null) {
            return Map.of("error", "指定された実行IDのジョブが見つかりません: " + jobExecutionId);
        }

        // 失敗分析データを構築
        return Map.of(
            "jobName", jobExecution.getJobInstance().getJobName(),
            "executionId", jobExecutionId,
            "status", jobExecution.getStatus().toString(),
            "startTime", jobExecution.getStartTime(),
            "endTime", jobExecution.getEndTime(),
            "exitCode", jobExecution.getExitStatus().getExitCode(),
            "exitDescription", jobExecution.getExitStatus().getExitDescription(),
            "failedSteps", analyzeFailedSteps(jobExecution),
            "errorSummary", summarizeErrors(jobExecution),
            "recoveryRecommendations", generateRecoveryRecommendations(jobExecution)
        );
    }

    /**
     * 自動復旧の提案
     */
    public List<Map<String, Object>> getRecoveryRecommendations(Long jobExecutionId) {
        JobExecution jobExecution = jobExplorer.getJobExecution(jobExecutionId);

        if (jobExecution == null || jobExecution.getStatus() != BatchStatus.FAILED) {
            return List.of();
        }

        return generateRecoveryRecommendations(jobExecution);
    }

    private List<Map<String, Object>> analyzeFailedSteps(JobExecution jobExecution) {
        return jobExecution.getStepExecutions().stream()
            .filter(step -> step.getStatus() == BatchStatus.FAILED)
            .map(step -> Map.of(
                "stepName", step.getStepName(),
                "status", step.getStatus().toString(),
                "readCount", step.getReadCount(),
                "writeCount", step.getWriteCount(),
                "skipCount", step.getSkipCount(),
                "rollbackCount", step.getRollbackCount(),
                "exceptions", step.getFailureExceptions().stream()
                    .map(Throwable::getMessage)
                    .toList()
            ))
            .toList();
    }

    private String summarizeErrors(JobExecution jobExecution) {
        StringBuilder summary = new StringBuilder();

        // ジョブレベルのエラー
        if (jobExecution.getExitStatus().getExitDescription() != null) {
            summary.append("ジョブエラー: ").append(jobExecution.getExitStatus().getExitDescription()).append("; ");
        }

        // ステップレベルのエラー
        for (StepExecution step : jobExecution.getStepExecutions()) {
            if (step.getStatus() == BatchStatus.FAILED) {
                summary.append(String.format("Step[%s]: ", step.getStepName()));
                for (Throwable exception : step.getFailureExceptions()) {
                    summary.append(exception.getMessage()).append("; ");
                }
            }
        }

        return summary.toString();
    }

    private List<Map<String, Object>> generateRecoveryRecommendations(JobExecution jobExecution) {
        List<Map<String, Object>> recommendations = new java.util.ArrayList<>();

        // 基本的な再起動推奨
        recommendations.add(Map.of(
            "action", "RESTART",
            "description", "ジョブを再起動する",
            "confidence", "HIGH",
            "reason", "最も一般的な復旧方法"
        ));

        // スキップ数に基づく推奨
        boolean hasSkips = jobExecution.getStepExecutions().stream()
            .anyMatch(step -> step.getSkipCount() > 0);

        if (hasSkips) {
            recommendations.add(Map.of(
                "action", "ANALYZE_SKIPS",
                "description", "スキップされたアイテムを分析する",
                "confidence", "MEDIUM",
                "reason", "スキップされたデータの確認が必要"
            ));
        }

        // データベースエラーの検出
        String errorSummary = summarizeErrors(jobExecution);
        if (errorSummary.toLowerCase().contains("database") ||
            errorSummary.toLowerCase().contains("connection")) {
            recommendations.add(Map.of(
                "action", "CHECK_DATABASE",
                "description", "データベース接続とデータを確認する",
                "confidence", "HIGH",
                "reason", "データベース関連のエラーが検出されました"
            ));
        }

        return recommendations;
    }

    private void logRecoveryAction(Long originalExecutionId, Long newExecutionId, String actionType, String description) {
        try {
            String sql = """
                INSERT INTO batch_execution_logs
                (job_name, job_execution_id, start_time, status, exit_message, error_message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

            jdbcTemplate.update(sql,
                "RECOVERY_ACTION",
                newExecutionId != null ? newExecutionId : originalExecutionId,
                Timestamp.valueOf(LocalDateTime.now()),
                actionType,
                description,
                String.format("Recovery action for execution ID: %d", originalExecutionId),
                Timestamp.valueOf(LocalDateTime.now())
            );

        } catch (Exception e) {
            System.err.println("復旧アクションログ保存エラー: " + e.getMessage());
        }
    }
}