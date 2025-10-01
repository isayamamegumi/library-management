package com.library.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class ReportRequest {
    @NotBlank(message = "レポートタイプは必須です")
    private String reportType;

    @NotBlank(message = "フォーマットは必須です")
    private String format;

    private Long templateId;
    private Long userId;
    private ReportFilters filters;
    private ReportOptions options;

    // Constructors
    public ReportRequest() {}

    public ReportRequest(String reportType, String format) {
        this.reportType = reportType;
        this.format = format;
    }

    // Getters and Setters
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public ReportFilters getFilters() { return filters; }
    public void setFilters(ReportFilters filters) { this.filters = filters; }

    public ReportOptions getOptions() { return options; }
    public void setOptions(ReportOptions options) { this.options = options; }

    // Inner classes for filters and options
    public static class ReportFilters {
        private List<String> readStatus;
        private String publisher;
        private LocalDate startDate;
        private LocalDate endDate;
        private String author;
        private String genre;

        // Getters and Setters
        public List<String> getReadStatus() { return readStatus; }
        public void setReadStatus(List<String> readStatus) { this.readStatus = readStatus; }

        public String getPublisher() { return publisher; }
        public void setPublisher(String publisher) { this.publisher = publisher; }

        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }

        public String getGenre() { return genre; }
        public void setGenre(String genre) { this.genre = genre; }
    }

    public static class ReportOptions {
        private String sortBy = "created_at";
        private String sortOrder = "DESC";
        private boolean includeImages = false;
        private Long templateId;
        private Map<String, Object> customOptions;

        // Getters and Setters
        public String getSortBy() { return sortBy; }
        public void setSortBy(String sortBy) { this.sortBy = sortBy; }

        public String getSortOrder() { return sortOrder; }
        public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }

        public boolean isIncludeImages() { return includeImages; }
        public void setIncludeImages(boolean includeImages) { this.includeImages = includeImages; }

        public Long getTemplateId() { return templateId; }
        public void setTemplateId(Long templateId) { this.templateId = templateId; }

        public Map<String, Object> getCustomOptions() { return customOptions; }
        public void setCustomOptions(Map<String, Object> customOptions) { this.customOptions = customOptions; }
    }
}