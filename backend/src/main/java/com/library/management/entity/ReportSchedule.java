package com.library.management.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "report_schedules")
public class ReportSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Long userId;

    @Column(name = "template_id")
    private Long templateId;

    @Column(nullable = false, length = 50)
    private String reportType;

    @Column(nullable = false, length = 20)
    private String format;

    @Column(columnDefinition = "TEXT")
    private String reportFilters;

    @Column(columnDefinition = "TEXT")
    private String reportOptions;

    @Column(nullable = false, length = 50)
    private String scheduleType;

    @Column(columnDefinition = "TEXT")
    private String scheduleConfig;

    @Column
    private LocalDateTime nextRunTime;

    @Column
    private LocalDateTime lastRunTime;

    @Column(length = 50)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String outputConfig;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public ReportSchedule() {}

    public ReportSchedule(String name, Long userId, String reportType, String format,
                         String scheduleType, String scheduleConfig) {
        this.name = name;
        this.userId = userId;
        this.reportType = reportType;
        this.format = format;
        this.scheduleType = scheduleType;
        this.scheduleConfig = scheduleConfig;
        this.status = "ACTIVE";
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getReportFilters() { return reportFilters; }
    public void setReportFilters(String reportFilters) { this.reportFilters = reportFilters; }

    public String getReportOptions() { return reportOptions; }
    public void setReportOptions(String reportOptions) { this.reportOptions = reportOptions; }

    public String getScheduleType() { return scheduleType; }
    public void setScheduleType(String scheduleType) { this.scheduleType = scheduleType; }

    public String getScheduleConfig() { return scheduleConfig; }
    public void setScheduleConfig(String scheduleConfig) { this.scheduleConfig = scheduleConfig; }

    public LocalDateTime getNextRunTime() { return nextRunTime; }
    public void setNextRunTime(LocalDateTime nextRunTime) { this.nextRunTime = nextRunTime; }

    public LocalDateTime getLastRunTime() { return lastRunTime; }
    public void setLastRunTime(LocalDateTime lastRunTime) { this.lastRunTime = lastRunTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOutputConfig() { return outputConfig; }
    public void setOutputConfig(String outputConfig) { this.outputConfig = outputConfig; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}