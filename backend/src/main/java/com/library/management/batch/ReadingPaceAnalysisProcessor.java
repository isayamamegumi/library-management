package com.library.management.batch;

import com.library.management.dto.ReadingPaceAnalysis;
import com.library.management.entity.User;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ReadingPaceAnalysisProcessor implements ItemProcessor<User, ReadingPaceAnalysis> {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Override
    public ReadingPaceAnalysis process(User user) throws Exception {
        System.out.println("読書ペース分析処理中: " + user.getUsername());
        
        ReadingPaceAnalysis analysis = new ReadingPaceAnalysis();
        analysis.setUserId(user.getId());
        analysis.setUsername(user.getUsername());
        analysis.setAnalysisDate(LocalDate.now());
        
        // 現在の読書ペース指標計算
        ReadingPaceAnalysis.ReadingPaceMetrics metrics = calculateCurrentMetrics(user.getId());
        analysis.setCurrentMetrics(metrics);
        
        // ペーストレンド分析
        ReadingPaceAnalysis.ReadingPaceTrends trends = calculateTrends(user.getId());
        analysis.setTrends(trends);
        
        // 読書習慣分析
        ReadingPaceAnalysis.ReadingHabits habits = analyzeHabits(user.getId());
        analysis.setHabits(habits);
        
        // 読書目標設定・進捗
        ReadingPaceAnalysis.ReadingGoals goals = calculateGoals(user.getId(), metrics);
        analysis.setGoals(goals);
        
        // 将来予測
        List<ReadingPaceAnalysis.ReadingPrediction> predictions = generatePredictions(user.getId(), metrics, trends);
        analysis.setPredictions(predictions);
        
        return analysis;
    }
    
    private ReadingPaceAnalysis.ReadingPaceMetrics calculateCurrentMetrics(Long userId) {
        ReadingPaceAnalysis.ReadingPaceMetrics metrics = new ReadingPaceAnalysis.ReadingPaceMetrics();
        
        LocalDate today = LocalDate.now();
        LocalDate oneMonthAgo = today.minusMonths(1);
        LocalDate threeMonthsAgo = today.minusMonths(3);
        
        // 過去1ヶ月の読書数
        Integer booksLastMonth = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books WHERE user_id = ? AND created_at >= ?",
            Integer.class, userId, oneMonthAgo);
        
        // 日平均読書数（過去1ヶ月）
        long daysInPeriod = ChronoUnit.DAYS.between(oneMonthAgo, today);
        Double dailyAverage = daysInPeriod > 0 ? booksLastMonth.doubleValue() / daysInPeriod : 0.0;
        metrics.setDailyAverageBooks(dailyAverage);
        
        // 週平均読書数
        Double weeklyAverage = dailyAverage * 7;
        metrics.setWeeklyAverageBooks(weeklyAverage);
        
        // 月平均読書数（過去3ヶ月基準）
        Integer booksLast3Months = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books WHERE user_id = ? AND created_at >= ?",
            Integer.class, userId, threeMonthsAgo);
        Double monthlyAverage = booksLast3Months.doubleValue() / 3;
        metrics.setMonthlyAverageBooks(monthlyAverage);
        
        // 平均読了日数
        try {
            Double avgCompletionDays = jdbcTemplate.queryForObject(
                "SELECT AVG(EXTRACT(epoch FROM (rs.updated_at - b.created_at))/86400) " +
                "FROM books b JOIN read_status rs ON b.id = rs.book_id " +
                "WHERE b.user_id = ? AND rs.status = 'COMPLETED' AND b.created_at >= ?",
                Double.class, userId, threeMonthsAgo);
            metrics.setAverageCompletionDays(avgCompletionDays != null ? avgCompletionDays : 0.0);
        } catch (Exception e) {
            metrics.setAverageCompletionDays(0.0);
        }
        
        // 連続読書日数計算
        Integer currentStreak = calculateCurrentStreak(userId);
        metrics.setCurrentStreak(currentStreak);
        
        Integer longestStreak = calculateLongestStreak(userId);
        metrics.setLongestStreak(longestStreak);
        
        // 読書速度スコア（登録数と読了率の組み合わせ）
        Integer completedBooks = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books b JOIN read_status rs ON b.id = rs.book_id " +
            "WHERE b.user_id = ? AND rs.status = 'COMPLETED' AND b.created_at >= ?",
            Integer.class, userId, oneMonthAgo);
        
        Double completionRate = booksLastMonth > 0 ? 
            (completedBooks.doubleValue() / booksLastMonth) * 100 : 0.0;
        
        Double readingVelocity = (dailyAverage * 10) + (completionRate / 10);
        metrics.setReadingVelocity(readingVelocity);
        
        // ペースレベル判定
        String paceLevel;
        if (dailyAverage >= 1.0) {
            paceLevel = "VERY_FAST";
        } else if (dailyAverage >= 0.5) {
            paceLevel = "FAST";
        } else if (dailyAverage >= 0.2) {
            paceLevel = "MODERATE";
        } else {
            paceLevel = "SLOW";
        }
        metrics.setPaceLevel(paceLevel);
        
        return metrics;
    }
    
    private ReadingPaceAnalysis.ReadingPaceTrends calculateTrends(Long userId) {
        ReadingPaceAnalysis.ReadingPaceTrends trends = new ReadingPaceAnalysis.ReadingPaceTrends();
        
        LocalDate today = LocalDate.now();
        LocalDate oneMonthAgo = today.minusMonths(1);
        LocalDate twoMonthsAgo = today.minusMonths(2);
        
        // 今月と先月の比較
        Integer thisMonth = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books WHERE user_id = ? AND created_at >= ?",
            Integer.class, userId, oneMonthAgo);
        
        Integer lastMonth = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books WHERE user_id = ? AND created_at BETWEEN ? AND ?",
            Integer.class, userId, twoMonthsAgo, oneMonthAgo);
        
        // ペース変化率
        Double paceChangeRate = lastMonth > 0 ? 
            ((thisMonth.intValue() - lastMonth.intValue()) / (double) lastMonth.intValue()) * 100 : 0.0;
        trends.setPaceChangeRate(paceChangeRate);
        
        // トレンド方向
        String trendDirection;
        if (paceChangeRate > 10.0) {
            trendDirection = "IMPROVING";
        } else if (paceChangeRate < -10.0) {
            trendDirection = "DECLINING";
        } else {
            trendDirection = "STABLE";
        }
        trends.setTrendDirection(trendDirection);
        
        // 過去6ヶ月の月別進捗
        try {
            List<Map<String, Object>> monthlyProgress = jdbcTemplate.queryForList(
                "SELECT " +
                "  EXTRACT(year FROM created_at) as year, " +
                "  EXTRACT(month FROM created_at) as month, " +
                "  COUNT(*) as count " +
                "FROM books " +
                "WHERE user_id = ? AND created_at >= ? " +
                "GROUP BY EXTRACT(year FROM created_at), EXTRACT(month FROM created_at) " +
                "ORDER BY year, month",
                userId, today.minusMonths(6));
            trends.setMonthlyProgress(monthlyProgress);
        } catch (Exception e) {
            trends.setMonthlyProgress(new ArrayList<>());
        }
        
        // ジャンル別ペース比較
        try {
            List<Map<String, Object>> genrePaceData = jdbcTemplate.queryForList(
                "SELECT " +
                "  genre, " +
                "  COUNT(*) as book_count, " +
                "  AVG(EXTRACT(epoch FROM (rs.updated_at - b.created_at))/86400) as avg_days " +
                "FROM books b " +
                "LEFT JOIN read_status rs ON b.id = rs.book_id " +
                "WHERE b.user_id = ? AND rs.status = 'COMPLETED' AND b.created_at >= ? " +
                "GROUP BY genre " +
                "HAVING COUNT(*) >= 2",
                userId, today.minusMonths(3));
            
            Map<String, Double> genrePaceComparison = genrePaceData.stream()
                .collect(Collectors.toMap(
                    row -> (String) row.get("genre"),
                    row -> ((Number) row.get("avg_days")).doubleValue()
                ));
            trends.setGenrePaceComparison(genrePaceComparison);
        } catch (Exception e) {
            trends.setGenrePaceComparison(new HashMap<>());
        }
        
        return trends;
    }
    
    private ReadingPaceAnalysis.ReadingHabits analyzeHabits(Long userId) {
        ReadingPaceAnalysis.ReadingHabits habits = new ReadingPaceAnalysis.ReadingHabits();
        
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        
        // 時間帯別分布
        try {
            List<Map<String, Object>> hourlyData = jdbcTemplate.queryForList(
                "SELECT " +
                "  CASE " +
                "    WHEN EXTRACT(hour FROM created_at) BETWEEN 6 AND 11 THEN '朝' " +
                "    WHEN EXTRACT(hour FROM created_at) BETWEEN 12 AND 17 THEN '昼' " +
                "    WHEN EXTRACT(hour FROM created_at) BETWEEN 18 AND 22 THEN '夜' " +
                "    ELSE '深夜・早朝' " +
                "  END as time_period, " +
                "  COUNT(*) as count " +
                "FROM books " +
                "WHERE user_id = ? AND created_at >= ? " +
                "GROUP BY " +
                "  CASE " +
                "    WHEN EXTRACT(hour FROM created_at) BETWEEN 6 AND 11 THEN '朝' " +
                "    WHEN EXTRACT(hour FROM created_at) BETWEEN 12 AND 17 THEN '昼' " +
                "    WHEN EXTRACT(hour FROM created_at) BETWEEN 18 AND 22 THEN '夜' " +
                "    ELSE '深夜・早朝' " +
                "  END",
                userId, threeMonthsAgo);
            
            Map<String, Integer> timeDistribution = hourlyData.stream()
                .collect(Collectors.toMap(
                    row -> (String) row.get("time_period"),
                    row -> ((Number) row.get("count")).intValue()
                ));
            habits.setTimeDistribution(timeDistribution);
            
            // 好みの読書時間
            String preferredTime = timeDistribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("不明");
            habits.setPreferredReadingTime(preferredTime);
        } catch (Exception e) {
            habits.setTimeDistribution(new HashMap<>());
            habits.setPreferredReadingTime("不明");
        }
        
        // 曜日別分布
        try {
            List<Map<String, Object>> weekdayData = jdbcTemplate.queryForList(
                "SELECT " +
                "  CASE EXTRACT(dow FROM created_at) " +
                "    WHEN 0 THEN '日曜日' WHEN 1 THEN '月曜日' WHEN 2 THEN '火曜日' " +
                "    WHEN 3 THEN '水曜日' WHEN 4 THEN '木曜日' WHEN 5 THEN '金曜日' " +
                "    WHEN 6 THEN '土曜日' " +
                "  END as day_name, " +
                "  COUNT(*) as count " +
                "FROM books " +
                "WHERE user_id = ? AND created_at >= ? " +
                "GROUP BY EXTRACT(dow FROM created_at) " +
                "ORDER BY EXTRACT(dow FROM created_at)",
                userId, threeMonthsAgo);
            
            Map<String, Integer> dayDistribution = weekdayData.stream()
                .collect(Collectors.toMap(
                    row -> (String) row.get("day_name"),
                    row -> ((Number) row.get("count")).intValue()
                ));
            habits.setDayOfWeekDistribution(dayDistribution);
            
            // 最も活発な曜日
            String mostActiveDay = dayDistribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("不明");
            habits.setMostActiveDay(mostActiveDay);
        } catch (Exception e) {
            habits.setDayOfWeekDistribution(new HashMap<>());
            habits.setMostActiveDay("不明");
        }
        
        // 一貫性スコア計算（読書日の分散具合）
        List<LocalDate> readingDates = jdbcTemplate.queryForList(
            "SELECT DISTINCT DATE(created_at) as reading_date " +
            "FROM books WHERE user_id = ? AND created_at >= ? " +
            "ORDER BY reading_date",
            LocalDate.class, userId, threeMonthsAgo);
        
        Double consistencyScore = calculateConsistencyScore(readingDates);
        habits.setConsistencyScore(consistencyScore);
        
        return habits;
    }
    
    private ReadingPaceAnalysis.ReadingGoals calculateGoals(Long userId, ReadingPaceAnalysis.ReadingPaceMetrics metrics) {
        ReadingPaceAnalysis.ReadingGoals goals = new ReadingPaceAnalysis.ReadingGoals();
        
        // 現在のペースベースで月間目標を算出
        Integer monthlyGoal = (int) Math.ceil(metrics.getMonthlyAverageBooks() * 1.2); // 20%増し
        goals.setMonthlyGoal(monthlyGoal);
        
        // 今月の進捗
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        Integer currentProgress = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books WHERE user_id = ? AND created_at >= ?",
            Integer.class, userId, monthStart);
        goals.setCurrentMonthProgress(currentProgress);
        
        // 目標達成率
        Double achievementRate = monthlyGoal > 0 ? 
            (currentProgress.doubleValue() / monthlyGoal) * 100 : 0.0;
        goals.setGoalAchievementRate(achievementRate);
        
        // 目標達成まで日数・推奨日次読書数
        LocalDate monthEnd = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        long remainingDays = ChronoUnit.DAYS.between(LocalDate.now(), monthEnd);
        goals.setDaysToGoal((int) remainingDays);
        
        Integer remainingBooks = monthlyGoal - currentProgress;
        Integer recommendedDaily = remainingDays > 0 ? 
            (int) Math.ceil(remainingBooks.doubleValue() / remainingDays) : 0;
        goals.setRecommendedDailyBooks(Math.max(0, recommendedDaily));
        
        // 目標ステータス
        String status;
        if (achievementRate >= 100.0) {
            status = "AHEAD";
        } else if (achievementRate >= 80.0) {
            status = "ON_TRACK";
        } else {
            status = "BEHIND";
        }
        goals.setGoalStatus(status);
        
        return goals;
    }
    
    private List<ReadingPaceAnalysis.ReadingPrediction> generatePredictions(Long userId, 
                                                       ReadingPaceAnalysis.ReadingPaceMetrics metrics, 
                                                       ReadingPaceAnalysis.ReadingPaceTrends trends) {
        List<ReadingPaceAnalysis.ReadingPrediction> predictions = new ArrayList<>();
        
        // 来月予測
        ReadingPaceAnalysis.ReadingPrediction monthlyPrediction = new ReadingPaceAnalysis.ReadingPrediction();
        monthlyPrediction.setPredictionType("MONTHLY");
        monthlyPrediction.setTargetDate(LocalDate.now().plusMonths(1));
        
        // トレンドを考慮した予測
        Double baseMonthlyBooks = metrics.getMonthlyAverageBooks();
        Double trendAdjustment = trends.getPaceChangeRate() / 100.0;
        Integer predictedMonthly = (int) Math.round(baseMonthlyBooks * (1 + trendAdjustment));
        monthlyPrediction.setPredictedBooks(Math.max(0, predictedMonthly));
        
        // 信頼度計算（データの一貫性に基づく）
        Double confidence = Math.min(0.9, 0.5 + (metrics.getCurrentStreak() * 0.05));
        monthlyPrediction.setConfidence(confidence);
        monthlyPrediction.setReasoning("過去3ヶ月の平均ペースとトレンドから算出");
        
        predictions.add(monthlyPrediction);
        
        // 四半期予測
        ReadingPaceAnalysis.ReadingPrediction quarterlyPrediction = new ReadingPaceAnalysis.ReadingPrediction();
        quarterlyPrediction.setPredictionType("QUARTERLY");
        quarterlyPrediction.setTargetDate(LocalDate.now().plusMonths(3));
        quarterlyPrediction.setPredictedBooks(predictedMonthly * 3);
        quarterlyPrediction.setConfidence(confidence * 0.8); // 期間が長いほど信頼度低下
        quarterlyPrediction.setReasoning("月次予測を3倍した値");
        
        predictions.add(quarterlyPrediction);
        
        return predictions;
    }
    
    private Integer calculateCurrentStreak(Long userId) {
        try {
            List<LocalDate> recentDates = jdbcTemplate.queryForList(
                "SELECT DISTINCT DATE(created_at) as reading_date " +
                "FROM books WHERE user_id = ? AND created_at >= ? " +
                "ORDER BY reading_date DESC",
                LocalDate.class, userId, LocalDate.now().minusDays(30));
            
            if (recentDates.isEmpty()) return 0;
            
            int streak = 0;
            LocalDate expectedDate = LocalDate.now();
            
            for (LocalDate date : recentDates) {
                if (date.equals(expectedDate) || date.equals(expectedDate.minusDays(1))) {
                    streak++;
                    expectedDate = date.minusDays(1);
                } else {
                    break;
                }
            }
            
            return streak;
        } catch (Exception e) {
            return 0;
        }
    }
    
    private Integer calculateLongestStreak(Long userId) {
        try {
            List<LocalDate> allDates = jdbcTemplate.queryForList(
                "SELECT DISTINCT DATE(created_at) as reading_date " +
                "FROM books WHERE user_id = ? " +
                "ORDER BY reading_date",
                LocalDate.class, userId);
            
            if (allDates.isEmpty()) return 0;
            
            int maxStreak = 1;
            int currentStreak = 1;
            
            for (int i = 1; i < allDates.size(); i++) {
                if (allDates.get(i).equals(allDates.get(i-1).plusDays(1))) {
                    currentStreak++;
                    maxStreak = Math.max(maxStreak, currentStreak);
                } else {
                    currentStreak = 1;
                }
            }
            
            return maxStreak;
        } catch (Exception e) {
            return 0;
        }
    }
    
    private Double calculateConsistencyScore(List<LocalDate> readingDates) {
        if (readingDates.size() < 2) return 0.0;
        
        // 読書日間の間隔の分散を計算
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < readingDates.size(); i++) {
            long daysBetween = ChronoUnit.DAYS.between(readingDates.get(i-1), readingDates.get(i));
            intervals.add(daysBetween);
        }
        
        double mean = intervals.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double variance = intervals.stream()
            .mapToDouble(interval -> Math.pow(interval - mean, 2))
            .average().orElse(0.0);
        
        // 分散が小さいほど一貫性が高い（0-100スケール）
        double consistencyScore = Math.max(0, 100 - Math.sqrt(variance) * 10);
        return Math.min(100, consistencyScore);
    }
}