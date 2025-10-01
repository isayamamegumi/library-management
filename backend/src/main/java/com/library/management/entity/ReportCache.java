package com.library.management.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_cache")
public class ReportCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String cacheKey;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String reportType;

    @Column(nullable = false, length = 20)
    private String format;

    @Column(columnDefinition = "TEXT")
    private String parameters;

    @Column(length = 500)
    private String filePath;

    @Column
    private Long fileSizeBytes;

    @Column
    private Integer recordCount;

    @Column
    private Long generationTimeMs;

    @Column
    private Integer hitCount = 0;

    @Column
    private LocalDateTime lastAccessTime;

    @Column
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Boolean isValid = true;

    @Column(length = 50)
    private String cacheStatus;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public ReportCache() {}

    public ReportCache(String cacheKey, Long userId, String reportType, String format, String parameters) {
        this.cacheKey = cacheKey;
        this.userId = userId;
        this.reportType = reportType;
        this.format = format;
        this.parameters = parameters;
        this.cacheStatus = "GENERATING";
        this.lastAccessTime = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCacheKey() { return cacheKey; }
    public void setCacheKey(String cacheKey) { this.cacheKey = cacheKey; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getParameters() { return parameters; }
    public void setParameters(String parameters) { this.parameters = parameters; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public Integer getRecordCount() { return recordCount; }
    public void setRecordCount(Integer recordCount) { this.recordCount = recordCount; }

    public Long getGenerationTimeMs() { return generationTimeMs; }
    public void setGenerationTimeMs(Long generationTimeMs) { this.generationTimeMs = generationTimeMs; }

    public Integer getHitCount() { return hitCount; }
    public void setHitCount(Integer hitCount) { this.hitCount = hitCount; }

    public LocalDateTime getLastAccessTime() { return lastAccessTime; }
    public void setLastAccessTime(LocalDateTime lastAccessTime) { this.lastAccessTime = lastAccessTime; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public Boolean getIsValid() { return isValid; }
    public void setIsValid(Boolean isValid) { this.isValid = isValid; }

    public String getCacheStatus() { return cacheStatus; }
    public void setCacheStatus(String cacheStatus) { this.cacheStatus = cacheStatus; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /**
     * キャッシュヒット記録
     */
    public void recordHit() {
        this.hitCount++;
        this.lastAccessTime = LocalDateTime.now();
    }

    /**
     * キャッシュ完了記録
     */
    public void markCompleted(String filePath, Long fileSizeBytes, Integer recordCount, Long generationTimeMs) {
        this.filePath = filePath;
        this.fileSizeBytes = fileSizeBytes;
        this.recordCount = recordCount;
        this.generationTimeMs = generationTimeMs;
        this.cacheStatus = "COMPLETED";
        this.lastAccessTime = LocalDateTime.now();
    }

    /**
     * キャッシュ無効化
     */
    public void invalidate() {
        this.isValid = false;
        this.cacheStatus = "INVALID";
    }

    /**
     * 有効期限チェック
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 利用可能かチェック
     */
    public boolean isAvailable() {
        return isValid && !isExpired() && "COMPLETED".equals(cacheStatus);
    }
}