package com.library.management.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class GenreAnalysis {
    private String genre;
    private LocalDate analysisDate;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private GenreBasicStats basicStats;
    private GenreTrendAnalysis trendAnalysis;
    private GenreUserDemographics userDemographics;
    private List<PopularBookInGenre> popularBooks;
    private GenreSeasonality seasonality;

    public GenreAnalysis() {}

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public LocalDate getAnalysisDate() {
        return analysisDate;
    }

    public void setAnalysisDate(LocalDate analysisDate) {
        this.analysisDate = analysisDate;
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

    public GenreBasicStats getBasicStats() {
        return basicStats;
    }

    public void setBasicStats(GenreBasicStats basicStats) {
        this.basicStats = basicStats;
    }

    public GenreTrendAnalysis getTrendAnalysis() {
        return trendAnalysis;
    }

    public void setTrendAnalysis(GenreTrendAnalysis trendAnalysis) {
        this.trendAnalysis = trendAnalysis;
    }

    public GenreUserDemographics getUserDemographics() {
        return userDemographics;
    }

    public void setUserDemographics(GenreUserDemographics userDemographics) {
        this.userDemographics = userDemographics;
    }

    public List<PopularBookInGenre> getPopularBooks() {
        return popularBooks;
    }

    public void setPopularBooks(List<PopularBookInGenre> popularBooks) {
        this.popularBooks = popularBooks;
    }

    public GenreSeasonality getSeasonality() {
        return seasonality;
    }

    public void setSeasonality(GenreSeasonality seasonality) {
        this.seasonality = seasonality;
    }

    public static class GenreBasicStats {
        private Integer totalBooks;           // 総登録数
        private Integer completedBooks;       // 読了数
        private Integer readingBooks;         // 読書中数
        private Integer onHoldBooks;          // 中断中数
        private Double completionRate;        // 読了率
        private Integer uniqueUsers;          // ユニークユーザー数
        private Double averageReadingDays;    // 平均読書日数

        public GenreBasicStats() {}

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

        public Integer getReadingBooks() {
            return readingBooks;
        }

        public void setReadingBooks(Integer readingBooks) {
            this.readingBooks = readingBooks;
        }

        public Integer getOnHoldBooks() {
            return onHoldBooks;
        }

        public void setOnHoldBooks(Integer onHoldBooks) {
            this.onHoldBooks = onHoldBooks;
        }

        public Double getCompletionRate() {
            return completionRate;
        }

        public void setCompletionRate(Double completionRate) {
            this.completionRate = completionRate;
        }

        public Integer getUniqueUsers() {
            return uniqueUsers;
        }

        public void setUniqueUsers(Integer uniqueUsers) {
            this.uniqueUsers = uniqueUsers;
        }

        public Double getAverageReadingDays() {
            return averageReadingDays;
        }

        public void setAverageReadingDays(Double averageReadingDays) {
            this.averageReadingDays = averageReadingDays;
        }
    }

    public static class GenreTrendAnalysis {
        private Double growthRate;            // 成長率（前期比）
        private String trendDirection;        // "INCREASING", "DECREASING", "STABLE"
        private List<Map<String, Object>> monthlyTrend;  // 月別推移
        private Integer peakMonth;            // ピーク月
        private Integer lowMonth;             // 低調月

        public GenreTrendAnalysis() {}

        public Double getGrowthRate() {
            return growthRate;
        }

        public void setGrowthRate(Double growthRate) {
            this.growthRate = growthRate;
        }

        public String getTrendDirection() {
            return trendDirection;
        }

        public void setTrendDirection(String trendDirection) {
            this.trendDirection = trendDirection;
        }

        public List<Map<String, Object>> getMonthlyTrend() {
            return monthlyTrend;
        }

        public void setMonthlyTrend(List<Map<String, Object>> monthlyTrend) {
            this.monthlyTrend = monthlyTrend;
        }

        public Integer getPeakMonth() {
            return peakMonth;
        }

        public void setPeakMonth(Integer peakMonth) {
            this.peakMonth = peakMonth;
        }

        public Integer getLowMonth() {
            return lowMonth;
        }

        public void setLowMonth(Integer lowMonth) {
            this.lowMonth = lowMonth;
        }
    }

    public static class GenreUserDemographics {
        private Map<String, Integer> ageDistribution;     // 年代別分布（仮想）
        private Map<String, Integer> genderDistribution;  // 性別分布（仮想）
        private Integer newUsersCount;        // 新規ユーザー数
        private Integer returningUsersCount;  // リピートユーザー数
        private List<String> topUsers;        // このジャンルの上位読者

        public GenreUserDemographics() {}

        public Map<String, Integer> getAgeDistribution() {
            return ageDistribution;
        }

        public void setAgeDistribution(Map<String, Integer> ageDistribution) {
            this.ageDistribution = ageDistribution;
        }

        public Map<String, Integer> getGenderDistribution() {
            return genderDistribution;
        }

        public void setGenderDistribution(Map<String, Integer> genderDistribution) {
            this.genderDistribution = genderDistribution;
        }

        public Integer getNewUsersCount() {
            return newUsersCount;
        }

        public void setNewUsersCount(Integer newUsersCount) {
            this.newUsersCount = newUsersCount;
        }

        public Integer getReturningUsersCount() {
            return returningUsersCount;
        }

        public void setReturningUsersCount(Integer returningUsersCount) {
            this.returningUsersCount = returningUsersCount;
        }

        public List<String> getTopUsers() {
            return topUsers;
        }

        public void setTopUsers(List<String> topUsers) {
            this.topUsers = topUsers;
        }
    }

    public static class PopularBookInGenre {
        private String title;
        private String author;
        private String publisher;
        private Integer registrationCount;
        private Double completionRate;
        private Double popularityScore;

        public PopularBookInGenre() {}

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getPublisher() {
            return publisher;
        }

        public void setPublisher(String publisher) {
            this.publisher = publisher;
        }

        public Integer getRegistrationCount() {
            return registrationCount;
        }

        public void setRegistrationCount(Integer registrationCount) {
            this.registrationCount = registrationCount;
        }

        public Double getCompletionRate() {
            return completionRate;
        }

        public void setCompletionRate(Double completionRate) {
            this.completionRate = completionRate;
        }

        public Double getPopularityScore() {
            return popularityScore;
        }

        public void setPopularityScore(Double popularityScore) {
            this.popularityScore = popularityScore;
        }
    }

    public static class GenreSeasonality {
        private Map<String, Double> quarterlyDistribution;  // 四半期別分布
        private String peakSeason;            // ピークシーズン
        private Double seasonalityIndex;      // 季節性指数

        public GenreSeasonality() {}

        public Map<String, Double> getQuarterlyDistribution() {
            return quarterlyDistribution;
        }

        public void setQuarterlyDistribution(Map<String, Double> quarterlyDistribution) {
            this.quarterlyDistribution = quarterlyDistribution;
        }

        public String getPeakSeason() {
            return peakSeason;
        }

        public void setPeakSeason(String peakSeason) {
            this.peakSeason = peakSeason;
        }

        public Double getSeasonalityIndex() {
            return seasonalityIndex;
        }

        public void setSeasonalityIndex(Double seasonalityIndex) {
            this.seasonalityIndex = seasonalityIndex;
        }
    }
}