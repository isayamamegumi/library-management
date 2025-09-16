package com.library.management.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * バッチ実行履歴のリポジトリ
 * データベースアクセスを最適化
 */
@Repository
public class BatchExecutionRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * バッチ実行履歴を取得（ページング、フィルタリング対応）
     */
    public List<Map<String, Object>> findExecutions(
            int page, int size, String jobName, String status) {

        StringBuilder sql = new StringBuilder("""
            SELECT job_name, job_execution_id, start_time, end_time, status,
                   exit_code, exit_message, read_count, write_count, execution_time_ms,
                   error_message, created_at
            FROM batch_execution_logs
            WHERE 1=1
            """);

        var params = new java.util.ArrayList<Object>();

        if (jobName != null && !jobName.trim().isEmpty()) {
            sql.append(" AND job_name = ?");
            params.add(jobName);
        }

        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND status = ?");
            params.add(status);
        }

        sql.append(" ORDER BY start_time DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /**
     * 実行履歴の総数を取得
     */
    public int countExecutions(String jobName, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM batch_execution_logs WHERE 1=1");
        var params = new java.util.ArrayList<Object>();

        if (jobName != null && !jobName.trim().isEmpty()) {
            sql.append(" AND job_name = ?");
            params.add(jobName);
        }

        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND status = ?");
            params.add(status);
        }

        return jdbcTemplate.queryForObject(sql.toString(), params.toArray(), Integer.class);
    }

    /**
     * ジョブ統計情報を取得（最適化済みクエリ）
     */
    public Map<String, Object> getJobStatistics(LocalDate since) {
        return jdbcTemplate.queryForMap("""
            SELECT
                COUNT(*) as total_executions,
                COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as completed_executions,
                COUNT(CASE WHEN status = 'FAILED' THEN 1 END) as failed_executions,
                COUNT(CASE WHEN status = 'STARTED' THEN 1 END) as running_executions,
                ROUND(AVG(CASE WHEN execution_time_ms IS NOT NULL THEN execution_time_ms END)) as avg_execution_time_ms,
                COALESCE(SUM(read_count), 0) as total_read_count,
                COALESCE(SUM(write_count), 0) as total_write_count
            FROM batch_execution_logs
            WHERE start_time >= ?
            """, since);
    }

    /**
     * ジョブ別統計情報を取得
     */
    public List<Map<String, Object>> getJobBreakdown(LocalDate since) {
        return jdbcTemplate.queryForList("""
            SELECT
                job_name,
                COUNT(*) as execution_count,
                COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as success_count,
                COUNT(CASE WHEN status = 'FAILED' THEN 1 END) as failure_count,
                ROUND(AVG(CASE WHEN execution_time_ms IS NOT NULL THEN execution_time_ms END)) as avg_time_ms,
                MAX(start_time) as last_execution
            FROM batch_execution_logs
            WHERE start_time >= ?
            GROUP BY job_name
            ORDER BY execution_count DESC
            """, since);
    }

    /**
     * バッチ実行ログを挿入または更新
     */
    public void upsertExecutionLog(
            String jobName, Long jobExecutionId, String status,
            LocalDateTime startTime, LocalDateTime endTime,
            String errorMessage, Integer readCount, Integer writeCount) {

        jdbcTemplate.update("""
            INSERT INTO batch_execution_logs
            (job_name, job_execution_id, status, start_time, end_time, error_message,
             read_count, write_count, execution_time_ms, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?,
                CASE WHEN ? IS NOT NULL AND ? IS NOT NULL
                     THEN EXTRACT(EPOCH FROM (? - ?)) * 1000
                     ELSE NULL END,
                NOW())
            ON CONFLICT (job_execution_id)
            DO UPDATE SET
                status = EXCLUDED.status,
                end_time = EXCLUDED.end_time,
                error_message = EXCLUDED.error_message,
                read_count = EXCLUDED.read_count,
                write_count = EXCLUDED.write_count,
                execution_time_ms = EXCLUDED.execution_time_ms
            """, jobName, jobExecutionId, status, startTime, endTime, errorMessage,
            readCount, writeCount, endTime, startTime, endTime, startTime);
    }

    /**
     * 失敗したジョブ実行を取得
     */
    public List<Map<String, Object>> findFailedExecutions(int limit) {
        return jdbcTemplate.queryForList("""
            SELECT job_name, job_execution_id, start_time, end_time,
                   error_message, execution_time_ms
            FROM batch_execution_logs
            WHERE status = 'FAILED'
            ORDER BY start_time DESC
            LIMIT ?
            """, limit);
    }

    /**
     * 長時間実行中のジョブを取得
     */
    public List<Map<String, Object>> findLongRunningJobs(int thresholdMinutes) {
        return jdbcTemplate.queryForList("""
            SELECT job_name, job_execution_id, start_time,
                   EXTRACT(EPOCH FROM (NOW() - start_time))/60 as running_minutes
            FROM batch_execution_logs
            WHERE status = 'STARTED'
              AND start_time < NOW() - INTERVAL ? MINUTE
            ORDER BY start_time ASC
            """, thresholdMinutes);
    }

    /**
     * 特定期間の実行回数を取得
     */
    public Map<String, Integer> getExecutionCountByPeriod(String jobName, LocalDate since) {
        List<Map<String, Object>> results = jdbcTemplate.queryForList("""
            SELECT
                DATE_TRUNC('day', start_time) as execution_date,
                COUNT(*) as count
            FROM batch_execution_logs
            WHERE job_name = ? AND start_time >= ?
            GROUP BY DATE_TRUNC('day', start_time)
            ORDER BY execution_date DESC
            """, jobName, since);

        return results.stream()
            .collect(java.util.stream.Collectors.toMap(
                row -> row.get("execution_date").toString(),
                row -> ((Number) row.get("count")).intValue()
            ));
    }

    /**
     * 実行時間の統計を取得
     */
    public Map<String, Object> getExecutionTimeStats(String jobName, LocalDate since) {
        return jdbcTemplate.queryForMap("""
            SELECT
                MIN(execution_time_ms) as min_time,
                MAX(execution_time_ms) as max_time,
                AVG(execution_time_ms) as avg_time,
                PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY execution_time_ms) as median_time,
                PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY execution_time_ms) as p95_time
            FROM batch_execution_logs
            WHERE job_name = ? AND start_time >= ? AND execution_time_ms IS NOT NULL
            """, jobName, since);
    }

    /**
     * エラーメッセージの分析を取得
     */
    public List<Map<String, Object>> getErrorAnalysis(LocalDate since, int limit) {
        return jdbcTemplate.queryForList("""
            SELECT
                SUBSTRING(error_message, 1, 100) as error_pattern,
                COUNT(*) as occurrence_count,
                MAX(start_time) as last_occurrence
            FROM batch_execution_logs
            WHERE status = 'FAILED' AND start_time >= ? AND error_message IS NOT NULL
            GROUP BY SUBSTRING(error_message, 1, 100)
            ORDER BY occurrence_count DESC
            LIMIT ?
            """, since, limit);
    }

    /**
     * 古い実行ログをクリーンアップ
     */
    public int cleanupOldExecutionLogs(LocalDate before) {
        return jdbcTemplate.update("""
            DELETE FROM batch_execution_logs
            WHERE start_time < ?
            """, before);
    }

    /**
     * 特定ジョブの最新実行情報を取得
     */
    public Optional<Map<String, Object>> findLatestExecution(String jobName) {
        List<Map<String, Object>> results = jdbcTemplate.queryForList("""
            SELECT job_name, job_execution_id, start_time, end_time, status,
                   execution_time_ms, error_message
            FROM batch_execution_logs
            WHERE job_name = ?
            ORDER BY start_time DESC
            LIMIT 1
            """, jobName);

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}