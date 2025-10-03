package com.library.management.controller;

import com.library.management.batch.BatchMonitoringService;
import com.library.management.batch.BatchErrorRecoveryService;
import com.library.management.service.BatchParameterService;
import com.library.management.service.BatchScheduleService;
import com.library.management.service.BatchNotificationService;
import com.library.management.service.BatchManagementService;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/api/batch")
@CrossOrigin(origins = "http://localhost:3000")
public class BatchController {

    @Autowired
    private BatchMonitoringService batchMonitoringService;

    @Autowired
    private BatchManagementService batchManagementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JobExplorer jobExplorer;

    @Autowired
    private BatchParameterService batchParameterService;

    @Autowired
    private BatchScheduleService batchScheduleService;

    @Autowired
    private BatchNotificationService batchNotificationService;

    @Autowired
    private BatchErrorRecoveryService batchErrorRecoveryService;
    
    // 既存のメソッド
    @GetMapping("/jobs")
    public ResponseEntity<List<String>> getAllJobNames() {
        return ResponseEntity.ok(batchMonitoringService.getAllJobNames());
    }
    
    @GetMapping("/jobs/{jobName}/instances")
    public ResponseEntity<List<JobInstance>> getJobInstances(
            @PathVariable String jobName,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int count) {
        return ResponseEntity.ok(batchMonitoringService.getJobInstances(jobName, start, count));
    }
    
    @GetMapping("/jobs/{jobName}/running")
    public ResponseEntity<Set<JobExecution>> getRunningExecutions(@PathVariable String jobName) {
        return ResponseEntity.ok(batchMonitoringService.getRunningExecutions(jobName));
    }
    
    @GetMapping("/jobs/{jobName}/status")
    public ResponseEntity<Boolean> isJobRunning(@PathVariable String jobName) {
        return ResponseEntity.ok(batchMonitoringService.isJobRunning(jobName));
    }
    
    @GetMapping("/executions/{executionId}")
    public ResponseEntity<JobExecution> getJobExecution(@PathVariable Long executionId) {
        return ResponseEntity.ok(batchMonitoringService.getJobExecution(executionId));
    }
    
    // 新しいメソッド - バッチジョブ手動実行
    @PostMapping("/jobs/{jobName}/execute")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> executeJob(
            @PathVariable String jobName,
            @RequestBody(required = false) Map<String, String> params) {

        BatchManagementService.JobExecutionResult result = batchManagementService.executeJob(jobName, params);

        Map<String, Object> response = new HashMap<>();
        if (result.success()) {
            response.put("success", true);
            response.put("jobName", result.jobName());
            response.put("jobExecutionId", result.jobExecutionId());
            response.put("status", result.status());
            response.put("startTime", result.startTime());
            response.put("message", result.message());
            return ResponseEntity.ok(response);
        } else {
            response.put("error", result.message());
            if (result.message().contains("存在しないジョブ名")) {
                response.put("availableJobs", batchManagementService.getAvailableJobs().keySet());
                return ResponseEntity.badRequest().body(response);
            } else if (result.message().contains("既に実行中")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        }
    }
    
    // バッチ統計情報取得
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getBatchStatistics() {
        try {
            // ジョブ実行統計
            Map<String, Object> jobStats = jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(*) as total_executions,
                    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as completed_executions,
                    COUNT(CASE WHEN status = 'FAILED' THEN 1 END) as failed_executions,
                    COUNT(CASE WHEN status = 'STARTED' THEN 1 END) as running_executions,
                    ROUND(AVG(CASE WHEN end_time IS NOT NULL AND start_time IS NOT NULL
                        THEN EXTRACT(EPOCH FROM (end_time - start_time)) * 1000 END)) as avg_execution_time_ms,
                    COALESCE(SUM(read_count), 0) as total_read_count,
                    COALESCE(SUM(write_count), 0) as total_write_count
                FROM batch_execution_logs
                WHERE start_time >= CURRENT_DATE - CAST('30 days' AS INTERVAL)
                """);
            
            // ジョブ別統計
            List<Map<String, Object>> jobBreakdown = jdbcTemplate.queryForList("""
                SELECT
                    job_name,
                    COUNT(*) as execution_count,
                    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as success_count,
                    COUNT(CASE WHEN status = 'FAILED' THEN 1 END) as failure_count,
                    ROUND(AVG(CASE WHEN end_time IS NOT NULL AND start_time IS NOT NULL
                        THEN EXTRACT(EPOCH FROM (end_time - start_time)) * 1000 END)) as avg_time_ms,
                    MAX(start_time) as last_execution
                FROM batch_execution_logs
                WHERE start_time >= CURRENT_DATE - CAST('30 days' AS INTERVAL)
                GROUP BY job_name
                ORDER BY execution_count DESC
                """);
            
            // 統計データの可用性
            List<Map<String, Object>> availableReports = jdbcTemplate.queryForList("""
                SELECT
                    report_type,
                    target_date,
                    created_at,
                    updated_at
                FROM batch_statistics
                WHERE target_date >= CURRENT_DATE - CAST('30 days' AS INTERVAL)
                ORDER BY updated_at DESC
                """);
            
            Map<String, Object> response = new HashMap<>();
            response.put("jobStatistics", jobStats);
            response.put("jobBreakdown", jobBreakdown);
            response.put("availableReports", availableReports);

            Map<String, String> availableJobs = batchManagementService.getAvailableJobs();
            System.out.println("[DEBUG] Available jobs: " + availableJobs);
            response.put("availableJobs", availableJobs);
            response.put("generatedAt", LocalDateTime.now());

            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[ERROR] Statistics error: " + e.getClass().getName() + ": " + e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "統計情報取得エラー: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    // 統計レポート取得
    @GetMapping("/reports/{reportType}")
    public ResponseEntity<Map<String, Object>> getStatisticsReport(
            @PathVariable String reportType,
            @RequestParam(required = false) String targetDate) {

        try {
            StringBuilder sql = new StringBuilder(
                "SELECT report_type, target_date, data_json, created_at, updated_at " +
                "FROM batch_statistics WHERE report_type = ?");

            List<Object> params = new ArrayList<>();
            params.add(reportType);

            if (targetDate != null && !targetDate.trim().isEmpty()) {
                sql.append(" AND target_date = ?");
                params.add(targetDate);
            }

            sql.append(" ORDER BY target_date DESC LIMIT 1");

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql.toString(), params.toArray());

            if (results.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> reportData = results.get(0);

            Map<String, Object> response = new HashMap<>();
            response.put("reportType", reportData.get("report_type"));
            response.put("targetDate", reportData.get("target_date"));
            response.put("data", reportData.get("data_json"));
            response.put("createdAt", reportData.get("created_at"));
            response.put("updatedAt", reportData.get("updated_at"));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "レポート取得エラー: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // バッチ実行履歴取得
    @GetMapping("/executions")
    public ResponseEntity<Map<String, Object>> getExecutionHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String status) {

        try {
            StringBuilder sql = new StringBuilder(
                "SELECT job_name, job_execution_id, start_time, end_time, status, " +
                "exit_code, exit_message, read_count, write_count, execution_time_ms, " +
                "error_message, created_at FROM batch_execution_logs WHERE 1=1");

            List<Object> params = new ArrayList<>();

            if (jobName != null && !jobName.trim().isEmpty()) {
                sql.append(" AND job_name = ?");
                params.add(jobName);
            }

            if (status != null && !status.trim().isEmpty()) {
                sql.append(" AND status = ?");
                params.add(status);
            }

            // 総件数取得
            String countSql = sql.toString().replace(
                "SELECT job_name, job_execution_id, start_time, end_time, status, " +
                "exit_code, exit_message, read_count, write_count, execution_time_ms, " +
                "error_message, created_at", "SELECT COUNT(*)");

            Integer totalCount = jdbcTemplate.queryForObject(countSql, params.toArray(), Integer.class);

            // ページング適用
            sql.append(" ORDER BY start_time DESC LIMIT ? OFFSET ?");
            params.add(size);
            params.add(page * size);

            List<Map<String, Object>> executions = jdbcTemplate.queryForList(sql.toString(), params.toArray());

            Map<String, Object> response = new HashMap<>();
            response.put("executions", executions);
            response.put("totalCount", totalCount);
            response.put("currentPage", page);
            response.put("pageSize", size);
            response.put("totalPages", (int) Math.ceil((double) totalCount / size));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "実行履歴取得エラー: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // 実行中バッチジョブ取得
    @GetMapping("/running")
    public ResponseEntity<List<BatchManagementService.RunningJobInfo>> getRunningJobs() {
        try {
            List<BatchManagementService.RunningJobInfo> runningJobs = batchManagementService.getRunningJobs();
            return ResponseEntity.ok(runningJobs);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // バッチジョブ停止
    @PostMapping("/executions/{executionId}/stop")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> stopExecution(@PathVariable Long executionId) {
        Map<String, Object> response = new HashMap<>();

        try {
            JobExecution jobExecution = jobExplorer.getJobExecution(executionId);

            if (jobExecution == null) {
                response.put("error", "指定された実行IDのジョブが見つかりません");
                return ResponseEntity.notFound().build();
            }

            if (jobExecution.getStatus() != BatchStatus.STARTED) {
                response.put("error", "ジョブが実行中ではありません。現在のステータス: " + jobExecution.getStatus());
                return ResponseEntity.badRequest().body(response);
            }

            // ジョブ停止（実際の停止は Spring Batch の機能に依存）
            jobExecution.setStatus(BatchStatus.STOPPING);

            response.put("success", true);
            response.put("message", "ジョブ停止リクエストを送信しました");
            response.put("executionId", executionId);
            response.put("jobName", jobExecution.getJobInstance().getJobName());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("error", "ジョブ停止エラー: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // バッチログ取得
    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> getBatchLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String level) {

        try {
            StringBuilder sql = new StringBuilder(
                "SELECT id, log_level, message, user_id, ip_address, created_at " +
                "FROM system_logs WHERE 1=1");

            List<Object> params = new ArrayList<>();

            if (jobName != null && !jobName.trim().isEmpty()) {
                sql.append(" AND message LIKE ?");
                params.add("%" + jobName + "%");
            }

            if (level != null && !level.trim().isEmpty()) {
                sql.append(" AND log_level = ?");
                params.add(level);
            }

            // 総件数取得
            String countSql = sql.toString().replace(
                "SELECT id, log_level, message, user_id, ip_address, created_at", "SELECT COUNT(*)");

            Integer totalCount = jdbcTemplate.queryForObject(countSql, params.toArray(), Integer.class);

            // ページング適用
            sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
            params.add(size);
            params.add(page * size);

            List<Map<String, Object>> logs = jdbcTemplate.queryForList(sql.toString(), params.toArray());

            Map<String, Object> response = new HashMap<>();
            response.put("logs", logs);
            response.put("totalCount", totalCount);
            response.put("currentPage", page);
            response.put("pageSize", size);
            response.put("totalPages", (int) Math.ceil((double) totalCount / size));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "ログ取得エラー: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // バッチパラメータ管理API
    @GetMapping("/parameters")
    public ResponseEntity<List<BatchParameterService.JobParameter>> getAllParameters() {
        try {
            List<BatchParameterService.JobParameter> parameters = batchParameterService.getAllJobParameters();
            return ResponseEntity.ok(parameters);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/parameters/{jobName}")
    public ResponseEntity<List<BatchParameterService.JobParameter>> getJobParameters(@PathVariable String jobName) {
        try {
            List<BatchParameterService.JobParameter> parameters = batchParameterService.getJobParameters(jobName);
            return ResponseEntity.ok(parameters);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/parameters")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchParameterService.JobParameter> createParameter(
            @RequestBody BatchParameterService.JobParameter parameter) {
        try {
            BatchParameterService.JobParameter saved = batchParameterService.saveJobParameter(parameter);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/parameters/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchParameterService.JobParameter> updateParameter(
            @PathVariable Long id,
            @RequestBody BatchParameterService.JobParameter parameter) {
        try {
            parameter.setId(id);
            BatchParameterService.JobParameter updated = batchParameterService.saveJobParameter(parameter);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/parameters/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteParameter(@PathVariable Long id) {
        try {
            batchParameterService.deleteJobParameter(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/parameters/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> toggleParameterStatus(@PathVariable Long id) {
        try {
            batchParameterService.toggleParameterStatus(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // バッチスケジュール管理API
    @GetMapping("/schedules")
    public ResponseEntity<List<BatchScheduleService.BatchSchedule>> getAllSchedules() {
        try {
            List<BatchScheduleService.BatchSchedule> schedules = batchScheduleService.getAllSchedules();
            return ResponseEntity.ok(schedules);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/schedules/enabled")
    public ResponseEntity<List<BatchScheduleService.BatchSchedule>> getEnabledSchedules() {
        try {
            List<BatchScheduleService.BatchSchedule> schedules = batchScheduleService.getEnabledSchedules();
            return ResponseEntity.ok(schedules);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/schedules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createSchedule(
            @RequestBody BatchScheduleService.BatchSchedule schedule) {
        Map<String, Object> response = new HashMap<>();

        try {
            // クーロン式のバリデーション
            if (!batchScheduleService.isValidCronExpression(schedule.getCronExpression())) {
                response.put("error", "無効なクーロン式です: " + schedule.getCronExpression());
                return ResponseEntity.badRequest().body(response);
            }

            // 既存ジョブ名のチェック
            BatchScheduleService.BatchSchedule existing = batchScheduleService.getScheduleByJobName(schedule.getJobName());
            if (existing != null) {
                response.put("error", "指定されたジョブ名のスケジュールが既に存在します: " + schedule.getJobName());
                return ResponseEntity.badRequest().body(response);
            }

            BatchScheduleService.BatchSchedule saved = batchScheduleService.saveSchedule(schedule);
            response.put("success", true);
            response.put("schedule", saved);
            response.put("nextExecution", batchScheduleService.getNextExecutionDescription(
                schedule.getCronExpression(), schedule.getTimezone()));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "スケジュール作成エラー: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PutMapping("/schedules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateSchedule(
            @PathVariable Long id,
            @RequestBody BatchScheduleService.BatchSchedule schedule) {
        Map<String, Object> response = new HashMap<>();

        try {
            // クーロン式のバリデーション
            if (!batchScheduleService.isValidCronExpression(schedule.getCronExpression())) {
                response.put("error", "無効なクーロン式です: " + schedule.getCronExpression());
                return ResponseEntity.badRequest().body(response);
            }

            schedule.setId(id);
            BatchScheduleService.BatchSchedule updated = batchScheduleService.saveSchedule(schedule);
            response.put("success", true);
            response.put("schedule", updated);
            response.put("nextExecution", batchScheduleService.getNextExecutionDescription(
                schedule.getCronExpression(), schedule.getTimezone()));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "スケジュール更新エラー: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @DeleteMapping("/schedules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long id) {
        try {
            batchScheduleService.deleteSchedule(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/schedules/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> toggleScheduleStatus(@PathVariable Long id) {
        try {
            batchScheduleService.toggleScheduleStatus(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/schedules/validate-cron")
    public ResponseEntity<Map<String, Object>> validateCronExpression(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String cronExpression = request.get("cronExpression");
            String timezone = request.get("timezone");

            if (cronExpression == null || cronExpression.trim().isEmpty()) {
                response.put("valid", false);
                response.put("error", "クーロン式が空です");
                return ResponseEntity.ok(response);
            }

            boolean isValid = batchScheduleService.isValidCronExpression(cronExpression);
            response.put("valid", isValid);

            if (isValid) {
                response.put("nextExecution", batchScheduleService.getNextExecutionDescription(cronExpression, timezone));
            } else {
                response.put("error", "無効なクーロン式です");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("valid", false);
            response.put("error", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    // バッチ通知設定API
    @GetMapping("/notifications")
    public ResponseEntity<List<BatchNotificationService.NotificationConfig>> getAllNotifications() {
        try {
            List<BatchNotificationService.NotificationConfig> notifications = batchNotificationService.getAllNotificationConfigs();
            return ResponseEntity.ok(notifications);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/notifications")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchNotificationService.NotificationConfig> createNotification(
            @RequestBody BatchNotificationService.NotificationConfig notification) {
        try {
            BatchNotificationService.NotificationConfig saved = batchNotificationService.saveNotificationConfig(notification);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/notifications/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchNotificationService.NotificationConfig> updateNotification(
            @PathVariable Long id,
            @RequestBody BatchNotificationService.NotificationConfig notification) {
        try {
            notification.setId(id);
            BatchNotificationService.NotificationConfig updated = batchNotificationService.saveNotificationConfig(notification);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/notifications/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        try {
            batchNotificationService.deleteNotificationConfig(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/notifications/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> toggleNotificationStatus(@PathVariable Long id) {
        try {
            batchNotificationService.toggleNotificationConfig(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // バッチエラー回復API
    @PostMapping("/recovery/restart/{executionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> restartFailedJob(@PathVariable Long executionId) {
        try {
            BatchErrorRecoveryService.RecoveryResult result = batchErrorRecoveryService.restartFailedJob(executionId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());

            if (result.getNewExecutionId() != null) {
                response.put("newExecutionId", result.getNewExecutionId());
            }

            if (result.getError() != null) {
                response.put("error", result.getError().getMessage());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "ジョブ再起動エラー: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/recovery/retry-step/{executionId}/{stepName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> retryFailedStep(
            @PathVariable Long executionId,
            @PathVariable String stepName) {
        try {
            BatchErrorRecoveryService.RecoveryResult result = batchErrorRecoveryService.retryFailedStep(executionId, stepName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "ステップ再実行エラー: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/recovery/analyze-skips/{executionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> analyzeSkippedItems(@PathVariable Long executionId) {
        try {
            BatchErrorRecoveryService.RecoveryResult result = batchErrorRecoveryService.recoverSkippedItems(executionId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "スキップアイテム分析エラー: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/recovery/stop-long-running/{executionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> stopLongRunningJob(@PathVariable Long executionId) {
        try {
            BatchErrorRecoveryService.RecoveryResult result = batchErrorRecoveryService.stopLongRunningJob(executionId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "ジョブ停止エラー: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/recovery/analyze/{executionId}")
    public ResponseEntity<Map<String, Object>> analyzeJobFailure(@PathVariable Long executionId) {
        try {
            Map<String, Object> analysis = batchErrorRecoveryService.analyzeJobFailure(executionId);
            return ResponseEntity.ok(analysis);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "失敗分析エラー: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/recovery/recommendations/{executionId}")
    public ResponseEntity<List<Map<String, Object>>> getRecoveryRecommendations(@PathVariable Long executionId) {
        try {
            List<Map<String, Object>> recommendations = batchErrorRecoveryService.getRecoveryRecommendations(executionId);
            return ResponseEntity.ok(recommendations);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }
}