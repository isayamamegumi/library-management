package com.library.management.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ReadingPaceAnalysis {
    private Long userId;
    private String username;
    private LocalDate analysisDate;
    private ReadingPaceMetrics currentMetrics;
    private ReadingPaceTrends trends;
    private ReadingHabits habits;
    private ReadingGoals goals;
    private List<ReadingPrediction> predictions;

    public ReadingPaceAnalysis() {}

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public LocalDate getAnalysisDate() {
        return analysisDate;
    }

    public void setAnalysisDate(LocalDate analysisDate) {
        this.analysisDate = analysisDate;
    }

    public ReadingPaceMetrics getCurrentMetrics() {
        return currentMetrics;
    }

    public void setCurrentMetrics(ReadingPaceMetrics currentMetrics) {
        this.currentMetrics = currentMetrics;
    }

    public ReadingPaceTrends getTrends() {
        return trends;
    }

    public void setTrends(ReadingPaceTrends trends) {
        this.trends = trends;
    }

    public ReadingHabits getHabits() {
        return habits;
    }

    public void setHabits(ReadingHabits habits) {
        this.habits = habits;
    }

    public ReadingGoals getGoals() {
        return goals;
    }

    public void setGoals(ReadingGoals goals) {
        this.goals = goals;
    }

    public List<ReadingPrediction> getPredictions() {
        return predictions;
    }

    public void setPredictions(List<ReadingPrediction> predictions) {
        this.predictions = predictions;
    }

    public static class ReadingPaceMetrics {
        private Double dailyAverageBooks;        // 日平均読書数
        private Double weeklyAverageBooks;       // 週平均読書数
        private Double monthlyAverageBooks;      // 月平均読書数
        private Double averageCompletionDays;    // 平均読了日数
        private Integer currentStreak;           // 現在の連続読書日数
        private Integer longestStreak;           // 最長連続読書日数
        private Double readingVelocity;          // 読書速度スコア
        private String paceLevel;                // "SLOW", "MODERATE", "FAST", "VERY_FAST"

        public ReadingPaceMetrics() {}

        public Double getDailyAverageBooks() {
            return dailyAverageBooks;
        }

        public void setDailyAverageBooks(Double dailyAverageBooks) {
            this.dailyAverageBooks = dailyAverageBooks;
        }

        public Double getWeeklyAverageBooks() {
            return weeklyAverageBooks;
        }

        public void setWeeklyAverageBooks(Double weeklyAverageBooks) {
            this.weeklyAverageBooks = weeklyAverageBooks;
        }

        public Double getMonthlyAverageBooks() {
            return monthlyAverageBooks;
        }

        public void setMonthlyAverageBooks(Double monthlyAverageBooks) {
            this.monthlyAverageBooks = monthlyAverageBooks;
        }

        public Double getAverageCompletionDays() {
            return averageCompletionDays;
        }

        public void setAverageCompletionDays(Double averageCompletionDays) {
            this.averageCompletionDays = averageCompletionDays;
        }

        public Integer getCurrentStreak() {
            return currentStreak;
        }

        public void setCurrentStreak(Integer currentStreak) {
            this.currentStreak = currentStreak;
        }

        public Integer getLongestStreak() {
            return longestStreak;
        }

        public void setLongestStreak(Integer longestStreak) {
            this.longestStreak = longestStreak;
        }

        public Double getReadingVelocity() {
            return readingVelocity;
        }

        public void setReadingVelocity(Double readingVelocity) {
            this.readingVelocity = readingVelocity;
        }

        public String getPaceLevel() {
            return paceLevel;
        }

        public void setPaceLevel(String paceLevel) {
            this.paceLevel = paceLevel;
        }
    }

    public static class ReadingPaceTrends {
        private Double paceChangeRate;           // ペース変化率（前月比）
        private String trendDirection;           // "IMPROVING", "DECLINING", "STABLE"
        private List<Map<String, Object>> monthlyProgress;  // 月別進捗
        private Map<String, Double> genrePaceComparison;    // ジャンル別ペース比較
        private LocalDate peakPeriodStart;       // ピーク期間開始
        private LocalDate peakPeriodEnd;         // ピーク期間終了

        public ReadingPaceTrends() {}

        public Double getPaceChangeRate() {
            return paceChangeRate;
        }

        public void setPaceChangeRate(Double paceChangeRate) {
            this.paceChangeRate = paceChangeRate;
        }

        public String getTrendDirection() {
            return trendDirection;
        }

        public void setTrendDirection(String trendDirection) {
            this.trendDirection = trendDirection;
        }

        public List<Map<String, Object>> getMonthlyProgress() {
            return monthlyProgress;
        }

        public void setMonthlyProgress(List<Map<String, Object>> monthlyProgress) {
            this.monthlyProgress = monthlyProgress;
        }

        public Map<String, Double> getGenrePaceComparison() {
            return genrePaceComparison;
        }

        public void setGenrePaceComparison(Map<String, Double> genrePaceComparison) {
            this.genrePaceComparison = genrePaceComparison;
        }

        public LocalDate getPeakPeriodStart() {
            return peakPeriodStart;
        }

        public void setPeakPeriodStart(LocalDate peakPeriodStart) {
            this.peakPeriodStart = peakPeriodStart;
        }

        public LocalDate getPeakPeriodEnd() {
            return peakPeriodEnd;
        }

        public void setPeakPeriodEnd(LocalDate peakPeriodEnd) {
            this.peakPeriodEnd = peakPeriodEnd;
        }
    }

    public static class ReadingHabits {
        private Map<String, Integer> timeDistribution;      // 時間帯別読書分布
        private Map<String, Integer> dayOfWeekDistribution; // 曜日別読書分布
        private String preferredReadingTime;     // 好みの読書時間
        private String mostActiveDay;            // 最も活発な曜日
        private Integer averageSessionLength;    // 平均読書セッション長（分）
        private Double consistencyScore;         // 一貫性スコア（0-100）

        public ReadingHabits() {}

        public Map<String, Integer> getTimeDistribution() {
            return timeDistribution;
        }

        public void setTimeDistribution(Map<String, Integer> timeDistribution) {
            this.timeDistribution = timeDistribution;
        }

        public Map<String, Integer> getDayOfWeekDistribution() {
            return dayOfWeekDistribution;
        }

        public void setDayOfWeekDistribution(Map<String, Integer> dayOfWeekDistribution) {
            this.dayOfWeekDistribution = dayOfWeekDistribution;
        }

        public String getPreferredReadingTime() {
            return preferredReadingTime;
        }

        public void setPreferredReadingTime(String preferredReadingTime) {
            this.preferredReadingTime = preferredReadingTime;
        }

        public String getMostActiveDay() {
            return mostActiveDay;
        }

        public void setMostActiveDay(String mostActiveDay) {
            this.mostActiveDay = mostActiveDay;
        }

        public Integer getAverageSessionLength() {
            return averageSessionLength;
        }

        public void setAverageSessionLength(Integer averageSessionLength) {
            this.averageSessionLength = averageSessionLength;
        }

        public Double getConsistencyScore() {
            return consistencyScore;
        }

        public void setConsistencyScore(Double consistencyScore) {
            this.consistencyScore = consistencyScore;
        }
    }

    public static class ReadingGoals {
        private Integer monthlyGoal;             // 月間目標冊数
        private Integer currentMonthProgress;    // 今月の進捗
        private Double goalAchievementRate;      // 目標達成率
        private Integer daysToGoal;              // 目標達成まで日数
        private Integer recommendedDailyBooks;   // 推奨日次読書数
        private String goalStatus;               // "ON_TRACK", "BEHIND", "AHEAD"

        public ReadingGoals() {}

        public Integer getMonthlyGoal() {
            return monthlyGoal;
        }

        public void setMonthlyGoal(Integer monthlyGoal) {
            this.monthlyGoal = monthlyGoal;
        }

        public Integer getCurrentMonthProgress() {
            return currentMonthProgress;
        }

        public void setCurrentMonthProgress(Integer currentMonthProgress) {
            this.currentMonthProgress = currentMonthProgress;
        }

        public Double getGoalAchievementRate() {
            return goalAchievementRate;
        }

        public void setGoalAchievementRate(Double goalAchievementRate) {
            this.goalAchievementRate = goalAchievementRate;
        }

        public Integer getDaysToGoal() {
            return daysToGoal;
        }

        public void setDaysToGoal(Integer daysToGoal) {
            this.daysToGoal = daysToGoal;
        }

        public Integer getRecommendedDailyBooks() {
            return recommendedDailyBooks;
        }

        public void setRecommendedDailyBooks(Integer recommendedDailyBooks) {
            this.recommendedDailyBooks = recommendedDailyBooks;
        }

        public String getGoalStatus() {
            return goalStatus;
        }

        public void setGoalStatus(String goalStatus) {
            this.goalStatus = goalStatus;
        }
    }

    public static class ReadingPrediction {
        private String predictionType;           // "MONTHLY", "QUARTERLY", "YEARLY"
        private LocalDate targetDate;
        private Integer predictedBooks;
        private Double confidence;               // 予測信頼度（0-1）
        private String reasoning;                // 予測根拠

        public ReadingPrediction() {}

        public String getPredictionType() {
            return predictionType;
        }

        public void setPredictionType(String predictionType) {
            this.predictionType = predictionType;
        }

        public LocalDate getTargetDate() {
            return targetDate;
        }

        public void setTargetDate(LocalDate targetDate) {
            this.targetDate = targetDate;
        }

        public Integer getPredictedBooks() {
            return predictedBooks;
        }

        public void setPredictedBooks(Integer predictedBooks) {
            this.predictedBooks = predictedBooks;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }

        public String getReasoning() {
            return reasoning;
        }

        public void setReasoning(String reasoning) {
            this.reasoning = reasoning;
        }
    }
}