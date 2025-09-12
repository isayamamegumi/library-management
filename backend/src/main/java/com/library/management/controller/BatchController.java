package com.library.management.controller;

import com.library.management.batch.BatchMonitoringService;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/batch")
@CrossOrigin(origins = "http://localhost:3000")
public class BatchController {
    
    @Autowired
    private BatchMonitoringService batchMonitoringService;
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    // 利用可能なジョブ一覧
    private static final Map<String, String> AVAILABLE_JOBS = Map.of(
        "complexStatsJob", "複合統計ジョブ",
        "batchChainFlowJob", "バッチチェーンフロージョブ", 
        "parallelPartitionedJob", "並列パーティションジョブ"
    );
    
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
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!AVAILABLE_JOBS.containsKey(jobName)) {
                response.put("error", "存在しないジョブ名: " + jobName);
                response.put("availableJobs", AVAILABLE_JOBS.keySet());
                return ResponseEntity.badRequest().body(response);
            }
            
            // ジョブパラメータ作成
            JobParametersBuilder parametersBuilder = new JobParametersBuilder();
            parametersBuilder.addLong("timestamp", System.currentTimeMillis());
            parametersBuilder.addString("triggeredBy", "MANUAL_EXECUTION");
            
            if (params != null && !params.isEmpty()) {
                params.forEach(parametersBuilder::addString);
            }
            
            // ジョブ取得と実行
            Job job = applicationContext.getBean(jobName, Job.class);
            JobExecution jobExecution = jobLauncher.run(job, parametersBuilder.toJobParameters());
            
            response.put("success", true);
            response.put("jobName", jobName);
            response.put("jobDescription", AVAILABLE_JOBS.get(jobName));
            response.put("jobExecutionId", jobExecution.getId());
            response.put("status", jobExecution.getStatus().toString());
            response.put("startTime", jobExecution.getStartTime());
            response.put("message", "ジョブを正常に開始しました");
            
            return ResponseEntity.ok(response);
            
        } catch (JobExecutionAlreadyRunningException e) {
            response.put("error", "ジョブが既に実行中です");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } catch (JobInstanceAlreadyCompleteException e) {
            response.put("error", "ジョブインスタンスが既に完了しています");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } catch (Exception e) {
            response.put("error", "ジョブ実行エラー: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
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
                    ROUND(AVG(CASE WHEN execution_time_ms IS NOT NULL THEN execution_time_ms END)) as avg_execution_time_ms,
                    COALESCE(SUM(read_count), 0) as total_read_count,
                    COALESCE(SUM(write_count), 0) as total_write_count
                FROM batch_execution_logs
                WHERE start_time >= CURRENT_DATE - INTERVAL '30 days'
                """);
            
            // ジョブ別統計
            List<Map<String, Object>> jobBreakdown = jdbcTemplate.queryForList("""
                SELECT 
                    job_name,
                    COUNT(*) as execution_count,
                    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as success_count,
                    COUNT(CASE WHEN status = 'FAILED' THEN 1 END) as failure_count,
                    ROUND(AVG(CASE WHEN execution_time_ms IS NOT NULL THEN execution_time_ms END)) as avg_time_ms,
                    MAX(start_time) as last_execution
                FROM batch_execution_logs
                WHERE start_time >= CURRENT_DATE - INTERVAL '30 days'
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
                WHERE target_date >= CURRENT_DATE - INTERVAL '30 days'
                ORDER BY updated_at DESC
                """);
            
            Map<String, Object> response = new HashMap<>();
            response.put("jobStatistics", jobStats);
            response.put("jobBreakdown", jobBreakdown);
            response.put("availableReports", availableReports);
            response.put("availableJobs", AVAILABLE_JOBS);
            response.put("generatedAt", LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
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
}