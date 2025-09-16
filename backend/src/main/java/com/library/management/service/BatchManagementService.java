package com.library.management.service;

import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * バッチジョブの統合管理サービス
 * 全てのバッチ関連操作を一元化
 */
@Service
public class BatchManagementService {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JobExplorer jobExplorer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 利用可能なジョブ定義
    private static final Map<String, JobDefinition> AVAILABLE_JOBS = Map.of(
        "complexStatsJob", new JobDefinition(
            "complexStatsJob",
            "複合統計ジョブ",
            "ユーザー・ジャンル・読書ペースの複合分析を実行",
            JobCategory.STATISTICS,
            EstimatedDuration.MEDIUM
        ),
        "batchChainFlowJob", new JobDefinition(
            "batchChainFlowJob",
            "バッチチェーンフロージョブ",
            "条件分岐を含む連続バッチ処理を実行",
            JobCategory.WORKFLOW,
            EstimatedDuration.LONG
        ),
        "parallelPartitionedJob", new JobDefinition(
            "parallelPartitionedJob",
            "並列パーティションジョブ",
            "データを分割して並列処理を実行",
            JobCategory.DATA_PROCESSING,
            EstimatedDuration.MEDIUM
        )
    );

    /**
     * ジョブ実行
     */
    public JobExecutionResult executeJob(String jobName, Map<String, String> parameters) {
        try {
            JobDefinition jobDef = AVAILABLE_JOBS.get(jobName);
            if (jobDef == null) {
                return JobExecutionResult.failure("存在しないジョブ名: " + jobName);
            }

            // 実行前チェック
            ValidationResult validation = validateJobExecution(jobName);
            if (!validation.isValid()) {
                return JobExecutionResult.failure("実行前チェック失敗: " + validation.message());
            }

            // ジョブパラメータ作成
            JobParameters jobParameters = createJobParameters(parameters);

            // ジョブ実行
            Job job = applicationContext.getBean(jobName, Job.class);
            JobExecution jobExecution = jobLauncher.run(job, jobParameters);

            return JobExecutionResult.success(jobExecution, jobDef);

        } catch (JobExecutionAlreadyRunningException e) {
            return JobExecutionResult.failure("ジョブが既に実行中です");
        } catch (JobInstanceAlreadyCompleteException e) {
            return JobExecutionResult.failure("ジョブインスタンスが既に完了しています");
        } catch (Exception e) {
            return JobExecutionResult.failure("ジョブ実行エラー: " + e.getMessage());
        }
    }

    /**
     * 実行前バリデーション
     */
    private ValidationResult validateJobExecution(String jobName) {
        try {
            // システムリソースチェック
            double memoryUsage = getMemoryUsage();
            if (memoryUsage > 80) {
                return new ValidationResult(false, "メモリ使用率が高すぎます: " + String.format("%.1f%%", memoryUsage));
            }

            // 同時実行チェック
            Set<JobExecution> runningExecutions = jobExplorer.findRunningJobExecutions(jobName);
            if (!runningExecutions.isEmpty()) {
                return new ValidationResult(false, "同じジョブが既に実行中です");
            }

            // データ整合性チェック
            if (!validateDataIntegrity()) {
                return new ValidationResult(false, "データ整合性に問題があります");
            }

            return new ValidationResult(true, null);

        } catch (Exception e) {
            return new ValidationResult(false, "バリデーションエラー: " + e.getMessage());
        }
    }

    /**
     * ジョブパラメータ作成
     */
    private JobParameters createJobParameters(Map<String, String> userParams) {
        JobParametersBuilder builder = new JobParametersBuilder();

        // 標準パラメータ
        builder.addLong("timestamp", System.currentTimeMillis());
        builder.addString("triggeredBy", "MANUAL_EXECUTION");
        builder.addString("executionTime", LocalDateTime.now().toString());

        // ユーザー定義パラメータ
        if (userParams != null) {
            userParams.forEach(builder::addString);
        }

        return builder.toJobParameters();
    }

    /**
     * 利用可能なジョブ一覧取得
     */
    public Map<String, String> getAvailableJobs() {
        return AVAILABLE_JOBS.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().displayName()
            ));
    }

    /**
     * ジョブ詳細情報取得
     */
    public List<JobInfo> getJobDetails() {
        return AVAILABLE_JOBS.values().stream()
            .map(this::createJobInfo)
            .collect(Collectors.toList());
    }

    /**
     * 実行中ジョブ一覧取得
     */
    public List<RunningJobInfo> getRunningJobs() {
        List<RunningJobInfo> runningJobs = new ArrayList<>();

        for (String jobName : AVAILABLE_JOBS.keySet()) {
            Set<JobExecution> executions = jobExplorer.findRunningJobExecutions(jobName);
            for (JobExecution execution : executions) {
                runningJobs.add(createRunningJobInfo(execution));
            }
        }

        return runningJobs;
    }

    /**
     * ジョブ停止
     */
    public StopResult stopJob(Long executionId) {
        try {
            JobExecution jobExecution = jobExplorer.getJobExecution(executionId);
            if (jobExecution == null) {
                return StopResult.failure("指定された実行IDのジョブが見つかりません");
            }

            if (jobExecution.getStatus() != BatchStatus.STARTED) {
                return StopResult.failure("ジョブが実行中ではありません");
            }

            jobExecution.setStatus(BatchStatus.STOPPING);
            return StopResult.success("ジョブ停止リクエストを送信しました");

        } catch (Exception e) {
            return StopResult.failure("ジョブ停止エラー: " + e.getMessage());
        }
    }

    // ヘルパーメソッド
    private double getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        return (double) usedMemory / maxMemory * 100;
    }

    private boolean validateDataIntegrity() {
        try {
            Long invalidBooks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM books WHERE title IS NULL OR title = ''", Long.class);
            Long invalidUsers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username IS NULL OR username = ''", Long.class);
            return invalidBooks == 0 && invalidUsers == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private JobInfo createJobInfo(JobDefinition def) {
        return new JobInfo(
            def.jobName(),
            def.displayName(),
            def.description(),
            def.category().name(),
            def.estimatedDuration().name(),
            getLastExecutionInfo(def.jobName())
        );
    }

    private RunningJobInfo createRunningJobInfo(JobExecution execution) {
        long runningTime = execution.getStartTime() != null
            ? java.time.Duration.between(execution.getStartTime(), LocalDateTime.now()).toMillis()
            : 0;

        return new RunningJobInfo(
            execution.getJobInstance().getJobName(),
            execution.getId(),
            execution.getStatus().toString(),
            execution.getStartTime(),
            runningTime,
            createStepInfos(execution)
        );
    }

    private List<StepInfo> createStepInfos(JobExecution execution) {
        return execution.getStepExecutions().stream()
            .map(step -> new StepInfo(
                step.getStepName(),
                step.getStatus().toString(),
                (int) step.getReadCount(),
                (int) step.getWriteCount(),
                (int) step.getCommitCount()
            ))
            .collect(Collectors.toList());
    }

    private String getLastExecutionInfo(String jobName) {
        try {
            List<JobInstance> instances = jobExplorer.getJobInstances(jobName, 0, 1);
            if (!instances.isEmpty()) {
                List<JobExecution> executions = jobExplorer.getJobExecutions(instances.get(0));
                if (!executions.isEmpty()) {
                    JobExecution lastExecution = executions.get(0);
                    return String.format("%s (%s)",
                        lastExecution.getEndTime() != null ? lastExecution.getEndTime().toString() : "実行中",
                        lastExecution.getStatus());
                }
            }
            return "未実行";
        } catch (Exception e) {
            return "不明";
        }
    }

    // 内部クラス・レコード定義
    public record JobDefinition(
        String jobName,
        String displayName,
        String description,
        JobCategory category,
        EstimatedDuration estimatedDuration
    ) {}

    public enum JobCategory {
        STATISTICS, WORKFLOW, DATA_PROCESSING, MAINTENANCE
    }

    public enum EstimatedDuration {
        SHORT, MEDIUM, LONG
    }

    public record JobExecutionResult(
        boolean success,
        String message,
        Long jobExecutionId,
        String jobName,
        String status,
        LocalDateTime startTime
    ) {
        public static JobExecutionResult success(JobExecution execution, JobDefinition jobDef) {
            return new JobExecutionResult(
                true,
                "ジョブを正常に開始しました",
                execution.getId(),
                jobDef.jobName(),
                execution.getStatus().toString(),
                execution.getStartTime()
            );
        }

        public static JobExecutionResult failure(String message) {
            return new JobExecutionResult(false, message, null, null, null, null);
        }
    }

    public record ValidationResult(boolean valid, String message) {
        public boolean isValid() {
            return valid;
        }
    }

    public record StopResult(boolean success, String message) {
        public static StopResult success(String message) {
            return new StopResult(true, message);
        }

        public static StopResult failure(String message) {
            return new StopResult(false, message);
        }
    }

    public record JobInfo(
        String jobName,
        String displayName,
        String description,
        String category,
        String estimatedDuration,
        String lastExecution
    ) {}

    public record RunningJobInfo(
        String jobName,
        Long executionId,
        String status,
        LocalDateTime startTime,
        long runningTimeMs,
        List<StepInfo> stepExecutions
    ) {}

    public record StepInfo(
        String stepName,
        String status,
        int readCount,
        int writeCount,
        int commitCount
    ) {}
}