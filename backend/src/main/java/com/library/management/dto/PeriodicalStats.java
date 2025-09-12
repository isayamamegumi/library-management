package com.library.management.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class PeriodicalStats {
    private String periodType;        // "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY"
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer totalBooks;
    private Integer completedBooks;
    private Integer activeUsers;
    private Double completionRate;
    private Map<String, Integer> genreDistribution;    // ジャンル別分布
    private Map<String, Object> trendData;            // トレンド比較データ
    private Map<String, Object> growthMetrics;        // 成長指標

    public PeriodicalStats() {}

    public String getPeriodType() {
        return periodType;
    }

    public void setPeriodType(String periodType) {
        this.periodType = periodType;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public Integer getTotalBooks() {
        return totalBooks;
    }

    public void setTotalBooks(Integer totalBooks) {
        this.totalBooks = totalBooks;
    }

    public Integer getCompletedBooks() {
        return completedBooks;
    }

    public void setCompletedBooks(Integer completedBooks) {
        this.completedBooks = completedBooks;
    }

    public Integer getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(Integer activeUsers) {
        this.activeUsers = activeUsers;
    }

    public Double getCompletionRate() {
        return completionRate;
    }

    public void setCompletionRate(Double completionRate) {
        this.completionRate = completionRate;
    }

    public Map<String, Integer> getGenreDistribution() {
        return genreDistribution;
    }

    public void setGenreDistribution(Map<String, Integer> genreDistribution) {
        this.genreDistribution = genreDistribution;
    }

    public Map<String, Object> getTrendData() {
        return trendData;
    }

    public void setTrendData(Map<String, Object> trendData) {
        this.trendData = trendData;
    }

    public Map<String, Object> getGrowthMetrics() {
        return growthMetrics;
    }

    public void setGrowthMetrics(Map<String, Object> growthMetrics) {
        this.growthMetrics = growthMetrics;
    }
}