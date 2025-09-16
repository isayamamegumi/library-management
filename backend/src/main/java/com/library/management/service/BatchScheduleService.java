package com.library.management.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class BatchScheduleService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public static class BatchSchedule {
        private Long id;
        private String jobName;
        private String cronExpression;
        private String description;
        private Boolean isEnabled;
        private String timezone;
        private LocalDateTime lastExecution;
        private LocalDateTime nextExecution;
        private Integer executionCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getJobName() { return jobName; }
        public void setJobName(String jobName) { this.jobName = jobName; }

        public String getCronExpression() { return cronExpression; }
        public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Boolean getIsEnabled() { return isEnabled; }
        public void setIsEnabled(Boolean isEnabled) { this.isEnabled = isEnabled; }

        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }

        public LocalDateTime getLastExecution() { return lastExecution; }
        public void setLastExecution(LocalDateTime lastExecution) { this.lastExecution = lastExecution; }

        public LocalDateTime getNextExecution() { return nextExecution; }
        public void setNextExecution(LocalDateTime nextExecution) { this.nextExecution = nextExecution; }

        public Integer getExecutionCount() { return executionCount; }
        public void setExecutionCount(Integer executionCount) { this.executionCount = executionCount; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    private static class BatchScheduleRowMapper implements RowMapper<BatchSchedule> {
        @Override
        public BatchSchedule mapRow(ResultSet rs, int rowNum) throws SQLException {
            BatchSchedule schedule = new BatchSchedule();
            schedule.setId(rs.getLong("id"));
            schedule.setJobName(rs.getString("job_name"));
            schedule.setCronExpression(rs.getString("cron_expression"));
            schedule.setDescription(rs.getString("description"));
            schedule.setIsEnabled(rs.getBoolean("is_enabled"));
            schedule.setTimezone(rs.getString("timezone"));

            if (rs.getTimestamp("last_execution") != null) {
                schedule.setLastExecution(rs.getTimestamp("last_execution").toLocalDateTime());
            }
            if (rs.getTimestamp("next_execution") != null) {
                schedule.setNextExecution(rs.getTimestamp("next_execution").toLocalDateTime());
            }

            schedule.setExecutionCount(rs.getInt("execution_count"));
            schedule.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            schedule.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());

            return schedule;
        }
    }

    public List<BatchSchedule> getAllSchedules() {
        String sql = "SELECT * FROM batch_schedules ORDER BY job_name";
        return jdbcTemplate.query(sql, new BatchScheduleRowMapper());
    }

    public List<BatchSchedule> getEnabledSchedules() {
        String sql = "SELECT * FROM batch_schedules WHERE is_enabled = true ORDER BY next_execution";
        return jdbcTemplate.query(sql, new BatchScheduleRowMapper());
    }

    public BatchSchedule getScheduleById(Long id) {
        String sql = "SELECT * FROM batch_schedules WHERE id = ?";
        List<BatchSchedule> results = jdbcTemplate.query(sql, new BatchScheduleRowMapper(), id);
        return results.isEmpty() ? null : results.get(0);
    }

    public BatchSchedule getScheduleByJobName(String jobName) {
        String sql = "SELECT * FROM batch_schedules WHERE job_name = ?";
        List<BatchSchedule> results = jdbcTemplate.query(sql, new BatchScheduleRowMapper(), jobName);
        return results.isEmpty() ? null : results.get(0);
    }

    public BatchSchedule saveSchedule(BatchSchedule schedule) {
        // 次回実行時刻を計算
        LocalDateTime nextExecution = calculateNextExecution(schedule.getCronExpression(), schedule.getTimezone());
        schedule.setNextExecution(nextExecution);

        if (schedule.getId() == null) {
            // 新規作成
            String sql = """
                INSERT INTO batch_schedules
                (job_name, cron_expression, description, is_enabled, timezone, next_execution, execution_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """;

            jdbcTemplate.update(sql,
                schedule.getJobName(),
                schedule.getCronExpression(),
                schedule.getDescription(),
                schedule.getIsEnabled() != null ? schedule.getIsEnabled() : true,
                schedule.getTimezone() != null ? schedule.getTimezone() : "Asia/Tokyo",
                schedule.getNextExecution(),
                schedule.getExecutionCount() != null ? schedule.getExecutionCount() : 0
            );

            return getScheduleByJobName(schedule.getJobName());
        } else {
            // 更新
            String sql = """
                UPDATE batch_schedules SET
                cron_expression = ?, description = ?, is_enabled = ?, timezone = ?,
                next_execution = ?, updated_at = NOW()
                WHERE id = ?
                """;

            jdbcTemplate.update(sql,
                schedule.getCronExpression(),
                schedule.getDescription(),
                schedule.getIsEnabled(),
                schedule.getTimezone(),
                schedule.getNextExecution(),
                schedule.getId()
            );

            return getScheduleById(schedule.getId());
        }
    }

    public void deleteSchedule(Long id) {
        String sql = "DELETE FROM batch_schedules WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public void toggleScheduleStatus(Long id) {
        String sql = "UPDATE batch_schedules SET is_enabled = NOT is_enabled, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, id);

        // 有効化された場合は次回実行時刻を再計算
        BatchSchedule schedule = getScheduleById(id);
        if (schedule != null && schedule.getIsEnabled()) {
            updateNextExecution(id, schedule.getCronExpression(), schedule.getTimezone());
        }
    }

    public void updateExecutionHistory(String jobName) {
        String sql = """
            UPDATE batch_schedules SET
            last_execution = NOW(),
            execution_count = execution_count + 1,
            next_execution = ?,
            updated_at = NOW()
            WHERE job_name = ?
            """;

        BatchSchedule schedule = getScheduleByJobName(jobName);
        if (schedule != null) {
            LocalDateTime nextExecution = calculateNextExecution(schedule.getCronExpression(), schedule.getTimezone());
            jdbcTemplate.update(sql, nextExecution, jobName);
        }
    }

    public List<BatchSchedule> getSchedulesToExecute() {
        String sql = """
            SELECT * FROM batch_schedules
            WHERE is_enabled = true
            AND next_execution <= NOW()
            ORDER BY next_execution
            """;
        return jdbcTemplate.query(sql, new BatchScheduleRowMapper());
    }

    private LocalDateTime calculateNextExecution(String cronExpression, String timezone) {
        try {
            CronExpression cron = CronExpression.parse(cronExpression);
            ZoneId zoneId = ZoneId.of(timezone != null ? timezone : "Asia/Tokyo");
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            ZonedDateTime next = cron.next(now);
            return next != null ? next.toLocalDateTime() : null;
        } catch (Exception e) {
            // クーロン式が無効な場合はnullを返す
            return null;
        }
    }

    private void updateNextExecution(Long id, String cronExpression, String timezone) {
        LocalDateTime nextExecution = calculateNextExecution(cronExpression, timezone);
        if (nextExecution != null) {
            String sql = "UPDATE batch_schedules SET next_execution = ?, updated_at = NOW() WHERE id = ?";
            jdbcTemplate.update(sql, nextExecution, id);
        }
    }

    public boolean isValidCronExpression(String cronExpression) {
        try {
            CronExpression.parse(cronExpression);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getNextExecutionDescription(String cronExpression, String timezone) {
        try {
            LocalDateTime next = calculateNextExecution(cronExpression, timezone);
            return next != null ? next.toString() : "無効なクーロン式";
        } catch (Exception e) {
            return "無効なクーロン式";
        }
    }

    public int countSchedulesByStatus(boolean enabled) {
        String sql = "SELECT COUNT(*) FROM batch_schedules WHERE is_enabled = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, enabled);
    }

    public List<String> getScheduledJobNames() {
        String sql = "SELECT DISTINCT job_name FROM batch_schedules WHERE is_enabled = true ORDER BY job_name";
        return jdbcTemplate.queryForList(sql, String.class);
    }
}