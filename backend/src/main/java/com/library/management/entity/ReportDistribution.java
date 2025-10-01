package com.library.management.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_distributions")
public class ReportDistribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Long userId;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(nullable = false, length = 50)
    private String distributionType;

    @Column(columnDefinition = "TEXT")
    private String recipients;

    @Column(columnDefinition = "TEXT")
    private String distributionConfig;

    @Column(length = 200)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private Boolean attachFile = true;

    @Column(nullable = false)
    private Boolean compressFile = false;

    @Column(length = 100)
    private String passwordProtection;

    @Column(length = 50)
    private String status;

    @Column
    private LocalDateTime lastDistributionTime;

    @Column
    private Integer distributionCount = 0;

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

    public ReportDistribution() {}

    public ReportDistribution(String name, Long userId, String distributionType, String recipients) {
        this.name = name;
        this.userId = userId;
        this.distributionType = distributionType;
        this.recipients = recipients;
        this.status = "ACTIVE";
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }

    public String getDistributionType() { return distributionType; }
    public void setDistributionType(String distributionType) { this.distributionType = distributionType; }

    public String getRecipients() { return recipients; }
    public void setRecipients(String recipients) { this.recipients = recipients; }

    public String getDistributionConfig() { return distributionConfig; }
    public void setDistributionConfig(String distributionConfig) { this.distributionConfig = distributionConfig; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Boolean getAttachFile() { return attachFile; }
    public void setAttachFile(Boolean attachFile) { this.attachFile = attachFile; }

    public Boolean getCompressFile() { return compressFile; }
    public void setCompressFile(Boolean compressFile) { this.compressFile = compressFile; }

    public String getPasswordProtection() { return passwordProtection; }
    public void setPasswordProtection(String passwordProtection) { this.passwordProtection = passwordProtection; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getLastDistributionTime() { return lastDistributionTime; }
    public void setLastDistributionTime(LocalDateTime lastDistributionTime) { this.lastDistributionTime = lastDistributionTime; }

    public Integer getDistributionCount() { return distributionCount; }
    public void setDistributionCount(Integer distributionCount) { this.distributionCount = distributionCount; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}