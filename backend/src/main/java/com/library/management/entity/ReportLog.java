package com.library.management.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_logs")
public class ReportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 50)
    private String username;

    @Column(nullable = false, length = 50)
    private String reportType;

    @Column(nullable = false, length = 20)
    private String format;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "distribution_id")
    private Long distributionId;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column
    private LocalDateTime endTime;

    @Column
    private Long processingTimeMs;

    @Column
    private Integer recordCount;

    @Column
    private Long fileSizeBytes;

    @Column(length = 500)
    private String fileName;

    @Column(length = 500)
    private String filePath;

    @Column(columnDefinition = "TEXT")
    private String parameters;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String errorStackTrace;

    @Column(length = 100)
    private String executionContext;

    @Column(length = 45)
    private String clientIpAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(columnDefinition = "TEXT")
    private String additionalInfo;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ReportLog() {}

    public ReportLog(Long userId, String username, String reportType, String format) {
        this.userId = userId;
        this.username = username;
        this.reportType = reportType;
        this.format = format;
        this.status = "STARTED";
        this.startTime = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }

    public Long getDistributionId() { return distributionId; }
    public void setDistributionId(Long distributionId) { this.distributionId = distributionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }

    public Integer getRecordCount() { return recordCount; }
    public void setRecordCount(Integer recordCount) { this.recordCount = recordCount; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getParameters() { return parameters; }
    public void setParameters(String parameters) { this.parameters = parameters; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getErrorStackTrace() { return errorStackTrace; }
    public void setErrorStackTrace(String errorStackTrace) { this.errorStackTrace = errorStackTrace; }

    public String getExecutionContext() { return executionContext; }
    public void setExecutionContext(String executionContext) { this.executionContext = executionContext; }

    public String getClientIpAddress() { return clientIpAddress; }
    public void setClientIpAddress(String clientIpAddress) { this.clientIpAddress = clientIpAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getAdditionalInfo() { return additionalInfo; }
    public void setAdditionalInfo(String additionalInfo) { this.additionalInfo = additionalInfo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * 処理完了時の更新
     */
    public void completeSuccess(String fileName, String filePath, Integer recordCount, Long fileSizeBytes) {
        this.status = "SUCCESS";
        this.endTime = LocalDateTime.now();
        this.processingTimeMs = java.time.Duration.between(startTime, endTime).toMillis();
        this.fileName = fileName;
        this.filePath = filePath;
        this.recordCount = recordCount;
        this.fileSizeBytes = fileSizeBytes;
    }

    /**
     * 処理失敗時の更新
     */
    public void completeError(String errorMessage, String stackTrace) {
        this.status = "ERROR";
        this.endTime = LocalDateTime.now();
        this.processingTimeMs = java.time.Duration.between(startTime, endTime).toMillis();
        this.errorMessage = errorMessage;
        this.errorStackTrace = stackTrace;
    }
}