package com.library.management.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

@Service
public class BatchNotificationService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public static class NotificationConfig {
        private Long id;
        private String jobName;
        private String notificationType;
        private String triggerEvent;
        private String recipientAddress;
        private Boolean isEnabled;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getJobName() { return jobName; }
        public void setJobName(String jobName) { this.jobName = jobName; }

        public String getNotificationType() { return notificationType; }
        public void setNotificationType(String notificationType) { this.notificationType = notificationType; }

        public String getTriggerEvent() { return triggerEvent; }
        public void setTriggerEvent(String triggerEvent) { this.triggerEvent = triggerEvent; }

        public String getRecipientAddress() { return recipientAddress; }
        public void setRecipientAddress(String recipientAddress) { this.recipientAddress = recipientAddress; }

        public Boolean getIsEnabled() { return isEnabled; }
        public void setIsEnabled(Boolean isEnabled) { this.isEnabled = isEnabled; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    private static class NotificationConfigRowMapper implements RowMapper<NotificationConfig> {
        @Override
        public NotificationConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
            NotificationConfig config = new NotificationConfig();
            config.setId(rs.getLong("id"));
            config.setJobName(rs.getString("job_name"));
            config.setNotificationType(rs.getString("notification_type"));
            config.setTriggerEvent(rs.getString("trigger_event"));
            config.setRecipientAddress(rs.getString("recipient_address"));
            config.setIsEnabled(rs.getBoolean("is_enabled"));
            config.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            config.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            return config;
        }
    }

    public void handleJobExecutionEvent(JobExecution jobExecution) {
        try {
            // 実行ログを保存
            saveExecutionLog(jobExecution);

            String jobName = jobExecution.getJobInstance().getJobName();
            BatchStatus status = jobExecution.getStatus();

            // 通知イベントの判定と送信
            if (status == BatchStatus.FAILED) {
                sendNotifications(jobName, "FAILURE", buildFailureMessage(jobExecution));
            } else if (status == BatchStatus.COMPLETED) {
                sendNotifications(jobName, "SUCCESS", buildSuccessMessage(jobExecution));
            }

            // 長時間実行チェック
            if (isLongRunningJob(jobExecution)) {
                sendNotifications(jobName, "LONG_RUNNING", buildLongRunningMessage(jobExecution));
            }

        } catch (Exception e) {
            System.err.println("バッチ通知処理エラー: " + e.getMessage());
        }
    }

    private void saveExecutionLog(JobExecution jobExecution) {
        try {
            String sql = """
                INSERT INTO batch_execution_logs
                (job_name, job_execution_id, start_time, end_time, status, exit_code, exit_message,
                 read_count, write_count, execution_time_ms, error_message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """;

            // Step実行統計の集計
            int totalReadCount = 0;
            int totalWriteCount = 0;
            StringBuilder errorMessages = new StringBuilder();

            for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
                totalReadCount += stepExecution.getReadCount();
                totalWriteCount += stepExecution.getWriteCount();

                // エラーメッセージの収集
                if (!stepExecution.getFailureExceptions().isEmpty()) {
                    for (Throwable exception : stepExecution.getFailureExceptions()) {
                        if (errorMessages.length() > 0) errorMessages.append("; ");
                        errorMessages.append(exception.getMessage());
                    }
                }
            }

            // 実行時間計算（LocalDateTimeをエポック時間に変換）
            Long executionTimeMs = null;
            if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
                long startTimeMs = jobExecution.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                long endTimeMs = jobExecution.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                executionTimeMs = endTimeMs - startTimeMs;
            }

            jdbcTemplate.update(sql,
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(),
                jobExecution.getStartTime() != null ? Timestamp.valueOf(jobExecution.getStartTime()) : null,
                jobExecution.getEndTime() != null ? Timestamp.valueOf(jobExecution.getEndTime()) : null,
                jobExecution.getStatus().toString(),
                jobExecution.getExitStatus().getExitCode(),
                jobExecution.getExitStatus().getExitDescription(),
                totalReadCount,
                totalWriteCount,
                executionTimeMs,
                errorMessages.length() > 0 ? errorMessages.toString() : null
            );

        } catch (Exception e) {
            System.err.println("バッチ実行ログ保存エラー: " + e.getMessage());
        }
    }

    private void sendNotifications(String jobName, String triggerEvent, String message) {
        try {
            List<NotificationConfig> configs = getNotificationConfigs(jobName, triggerEvent);

            for (NotificationConfig config : configs) {
                if (config.getIsEnabled()) {
                    switch (config.getNotificationType()) {
                        case "EMAIL":
                            sendEmailNotification(config.getRecipientAddress(), jobName, triggerEvent, message);
                            break;
                        case "SLACK":
                            // Slack通知の実装（今回は省略）
                            System.out.println("Slack通知: " + message);
                            break;
                        case "WEBHOOK":
                            // Webhook通知の実装（今回は省略）
                            System.out.println("Webhook通知: " + message);
                            break;
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("通知送信エラー: " + e.getMessage());
        }
    }

    private List<NotificationConfig> getNotificationConfigs(String jobName, String triggerEvent) {
        String sql = """
            SELECT * FROM batch_notifications
            WHERE is_enabled = true
            AND trigger_event = ?
            AND (job_name = ? OR job_name IS NULL)
            ORDER BY job_name NULLS LAST
            """;
        return jdbcTemplate.query(sql, new NotificationConfigRowMapper(), triggerEvent, jobName);
    }

    private void sendEmailNotification(String recipientAddress, String jobName, String triggerEvent, String message) {
        if (mailSender == null) {
            System.out.println("メール送信設定が無効です。通知内容: " + message);
            return;
        }

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(recipientAddress);
            mailMessage.setSubject(buildEmailSubject(jobName, triggerEvent));
            mailMessage.setText(message);

            mailSender.send(mailMessage);
            System.out.println("メール通知送信完了: " + recipientAddress);

        } catch (Exception e) {
            System.err.println("メール送信エラー: " + e.getMessage());
        }
    }

    private String buildEmailSubject(String jobName, String triggerEvent) {
        switch (triggerEvent) {
            case "SUCCESS":
                return "[バッチ通知] ジョブ正常完了: " + jobName;
            case "FAILURE":
                return "[バッチ通知] ジョブ失敗: " + jobName;
            case "LONG_RUNNING":
                return "[バッチ通知] 長時間実行警告: " + jobName;
            default:
                return "[バッチ通知] " + jobName;
        }
    }

    private String buildSuccessMessage(JobExecution jobExecution) {
        StringBuilder message = new StringBuilder();
        message.append("バッチジョブが正常に完了しました。\n\n");
        message.append("ジョブ名: ").append(jobExecution.getJobInstance().getJobName()).append("\n");
        message.append("実行ID: ").append(jobExecution.getId()).append("\n");
        message.append("開始時刻: ").append(formatDateTime(jobExecution.getStartTime())).append("\n");
        message.append("終了時刻: ").append(formatDateTime(jobExecution.getEndTime())).append("\n");

        if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
            long startTimeMs = jobExecution.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endTimeMs = jobExecution.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long executionTime = endTimeMs - startTimeMs;
            message.append("実行時間: ").append(executionTime / 1000).append("秒\n");
        }

        // Step別の統計情報
        int totalReadCount = 0;
        int totalWriteCount = 0;
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            totalReadCount += stepExecution.getReadCount();
            totalWriteCount += stepExecution.getWriteCount();
        }

        message.append("読み込み件数: ").append(totalReadCount).append("件\n");
        message.append("書き込み件数: ").append(totalWriteCount).append("件\n");

        return message.toString();
    }

    private String buildFailureMessage(JobExecution jobExecution) {
        StringBuilder message = new StringBuilder();
        message.append("バッチジョブでエラーが発生しました。\n\n");
        message.append("ジョブ名: ").append(jobExecution.getJobInstance().getJobName()).append("\n");
        message.append("実行ID: ").append(jobExecution.getId()).append("\n");
        message.append("開始時刻: ").append(formatDateTime(jobExecution.getStartTime())).append("\n");
        message.append("終了時刻: ").append(formatDateTime(jobExecution.getEndTime())).append("\n");
        message.append("エラーメッセージ: ").append(jobExecution.getExitStatus().getExitDescription()).append("\n\n");

        // Step別のエラー詳細
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            if (stepExecution.getStatus() == BatchStatus.FAILED) {
                message.append("失敗Step: ").append(stepExecution.getStepName()).append("\n");
                message.append("読み込み件数: ").append(stepExecution.getReadCount()).append("\n");
                message.append("書き込み件数: ").append(stepExecution.getWriteCount()).append("\n");
                message.append("スキップ件数: ").append(stepExecution.getSkipCount()).append("\n");

                // 例外情報
                for (Throwable exception : stepExecution.getFailureExceptions()) {
                    message.append("例外: ").append(exception.getMessage()).append("\n");
                }
                message.append("\n");
            }
        }

        message.append("管理画面で詳細を確認してください。");
        return message.toString();
    }

    private String buildLongRunningMessage(JobExecution jobExecution) {
        StringBuilder message = new StringBuilder();
        message.append("バッチジョブが長時間実行されています。\n\n");
        message.append("ジョブ名: ").append(jobExecution.getJobInstance().getJobName()).append("\n");
        message.append("実行ID: ").append(jobExecution.getId()).append("\n");
        message.append("開始時刻: ").append(formatDateTime(jobExecution.getStartTime())).append("\n");

        if (jobExecution.getStartTime() != null) {
            long startTimeMs = jobExecution.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long runningTime = System.currentTimeMillis() - startTimeMs;
            message.append("実行時間: ").append(runningTime / (60 * 1000)).append("分\n");
        }

        message.append("\n処理状況を確認してください。");
        return message.toString();
    }

    private boolean isLongRunningJob(JobExecution jobExecution) {
        if (jobExecution.getStartTime() == null || jobExecution.getStatus() != BatchStatus.STARTED) {
            return false;
        }

        long startTimeMs = jobExecution.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long runningTime = System.currentTimeMillis() - startTimeMs;
        long maxRunningTime = 30 * 60 * 1000; // 30分

        return runningTime > maxRunningTime;
    }

    private String formatDateTime(Date date) {
        if (date == null) return "N/A";
        return date.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "N/A";
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // 通知設定管理メソッド
    public List<NotificationConfig> getAllNotificationConfigs() {
        String sql = "SELECT * FROM batch_notifications ORDER BY job_name, trigger_event";
        return jdbcTemplate.query(sql, new NotificationConfigRowMapper());
    }

    public NotificationConfig saveNotificationConfig(NotificationConfig config) {
        if (config.getId() == null) {
            // 新規作成
            String sql = """
                INSERT INTO batch_notifications
                (job_name, notification_type, trigger_event, recipient_address, is_enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, NOW(), NOW())
                """;

            jdbcTemplate.update(sql,
                config.getJobName(),
                config.getNotificationType(),
                config.getTriggerEvent(),
                config.getRecipientAddress(),
                config.getIsEnabled() != null ? config.getIsEnabled() : true
            );

            // 新規作成後、IDを取得して返す
            String selectSql = "SELECT * FROM batch_notifications WHERE job_name = ? AND notification_type = ? AND trigger_event = ? AND recipient_address = ? ORDER BY id DESC LIMIT 1";
            List<NotificationConfig> results = jdbcTemplate.query(selectSql, new NotificationConfigRowMapper(),
                config.getJobName(), config.getNotificationType(), config.getTriggerEvent(), config.getRecipientAddress());
            return results.isEmpty() ? null : results.get(0);
        } else {
            // 更新
            String sql = """
                UPDATE batch_notifications SET
                notification_type = ?, trigger_event = ?, recipient_address = ?, is_enabled = ?, updated_at = NOW()
                WHERE id = ?
                """;

            jdbcTemplate.update(sql,
                config.getNotificationType(),
                config.getTriggerEvent(),
                config.getRecipientAddress(),
                config.getIsEnabled(),
                config.getId()
            );

            return getNotificationConfigById(config.getId());
        }
    }

    public NotificationConfig getNotificationConfigById(Long id) {
        String sql = "SELECT * FROM batch_notifications WHERE id = ?";
        List<NotificationConfig> results = jdbcTemplate.query(sql, new NotificationConfigRowMapper(), id);
        return results.isEmpty() ? null : results.get(0);
    }

    public void deleteNotificationConfig(Long id) {
        String sql = "DELETE FROM batch_notifications WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public void toggleNotificationConfig(Long id) {
        String sql = "UPDATE batch_notifications SET is_enabled = NOT is_enabled, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }
}