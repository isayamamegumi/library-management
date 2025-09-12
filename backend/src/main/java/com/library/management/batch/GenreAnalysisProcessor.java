package com.library.management.batch;

import com.library.management.dto.GenreAnalysis;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GenreAnalysisProcessor implements ItemProcessor<String, GenreAnalysis> {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Override
    public GenreAnalysis process(String genre) throws Exception {
        System.out.println("ジャンル分析処理中: " + genre);
        
        LocalDate analysisDate = LocalDate.now();
        LocalDate periodStart = analysisDate.minusMonths(3);  // 過去3ヶ月
        LocalDate periodEnd = analysisDate;
        
        GenreAnalysis analysis = new GenreAnalysis();
        analysis.setGenre(genre);
        analysis.setAnalysisDate(analysisDate);
        analysis.setPeriodStart(periodStart);
        analysis.setPeriodEnd(periodEnd);
        
        // 基本統計計算
        GenreAnalysis.GenreBasicStats basicStats = calculateBasicStats(genre, periodStart, periodEnd);
        analysis.setBasicStats(basicStats);
        
        // トレンド分析
        GenreAnalysis.GenreTrendAnalysis trendAnalysis = calculateTrendAnalysis(genre, periodStart, periodEnd);
        analysis.setTrendAnalysis(trendAnalysis);
        
        // ユーザー分析
        GenreAnalysis.GenreUserDemographics userDemographics = calculateUserDemographics(genre, periodStart, periodEnd);
        analysis.setUserDemographics(userDemographics);
        
        // 人気書籍
        List<GenreAnalysis.PopularBookInGenre> popularBooks = calculatePopularBooks(genre, periodStart, periodEnd);
        analysis.setPopularBooks(popularBooks);
        
        // 季節性分析
        GenreAnalysis.GenreSeasonality seasonality = calculateSeasonality(genre);
        analysis.setSeasonality(seasonality);
        
        return analysis;
    }
    
    private GenreAnalysis.GenreBasicStats calculateBasicStats(String genre, LocalDate start, LocalDate end) {
        GenreAnalysis.GenreBasicStats stats = new GenreAnalysis.GenreBasicStats();
        
        // 総登録数
        Integer totalBooks = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books WHERE genre = ? AND created_at BETWEEN ? AND ?",
            Integer.class, genre, start, end.plusDays(1));
        stats.setTotalBooks(totalBooks);
        
        // 読了数
        Integer completedBooks = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books b JOIN read_status rs ON b.id = rs.book_id " +
            "WHERE b.genre = ? AND rs.status = 'COMPLETED' " +
            "AND b.created_at BETWEEN ? AND ?",
            Integer.class, genre, start, end.plusDays(1));
        stats.setCompletedBooks(completedBooks);
        
        // 読書中数
        Integer readingBooks = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books b JOIN read_status rs ON b.id = rs.book_id " +
            "WHERE b.genre = ? AND rs.status = 'READING' " +
            "AND b.created_at BETWEEN ? AND ?",
            Integer.class, genre, start, end.plusDays(1));
        stats.setReadingBooks(readingBooks);
        
        // 中断中数
        Integer onHoldBooks = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books b JOIN read_status rs ON b.id = rs.book_id " +
            "WHERE b.genre = ? AND rs.status = 'ON_HOLD' " +
            "AND b.created_at BETWEEN ? AND ?",
            Integer.class, genre, start, end.plusDays(1));
        stats.setOnHoldBooks(onHoldBooks);
        
        // 読了率
        Double completionRate = totalBooks > 0 ? 
            (completedBooks.doubleValue() / totalBooks) * 100 : 0.0;
        stats.setCompletionRate(completionRate);
        
        // ユニークユーザー数
        Integer uniqueUsers = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT user_id) FROM books " +
            "WHERE genre = ? AND created_at BETWEEN ? AND ?",
            Integer.class, genre, start, end.plusDays(1));
        stats.setUniqueUsers(uniqueUsers);
        
        // 平均読書日数
        try {
            Double avgReadingDays = jdbcTemplate.queryForObject(
                "SELECT AVG(EXTRACT(epoch FROM (rs.updated_at - b.created_at))/86400) " +
                "FROM books b JOIN read_status rs ON b.id = rs.book_id " +
                "WHERE b.genre = ? AND rs.status = 'COMPLETED' " +
                "AND b.created_at BETWEEN ? AND ?",
                Double.class, genre, start, end.plusDays(1));
            stats.setAverageReadingDays(avgReadingDays != null ? avgReadingDays : 0.0);
        } catch (Exception e) {
            stats.setAverageReadingDays(0.0);
        }
        
        return stats;
    }
    
    private GenreAnalysis.GenreTrendAnalysis calculateTrendAnalysis(String genre, LocalDate start, LocalDate end) {
        GenreAnalysis.GenreTrendAnalysis trend = new GenreAnalysis.GenreTrendAnalysis();
        
        // 前期比較（3ヶ月前の同期間）
        LocalDate prevPeriodStart = start.minusMonths(3);
        LocalDate prevPeriodEnd = end.minusMonths(3);
        
        Integer currentPeriodBooks = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books WHERE genre = ? AND created_at BETWEEN ? AND ?",
            Integer.class, genre, start, end.plusDays(1));
        
        Integer prevPeriodBooks = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books WHERE genre = ? AND created_at BETWEEN ? AND ?",
            Integer.class, genre, prevPeriodStart, prevPeriodEnd.plusDays(1));
        
        // 成長率計算
        Double growthRate = prevPeriodBooks > 0 ? 
            ((currentPeriodBooks.intValue() - prevPeriodBooks.intValue()) / (double) prevPeriodBooks.intValue()) * 100 : 0.0;
        trend.setGrowthRate(growthRate);
        
        // トレンド方向
        String trendDirection;
        if (growthRate > 5.0) {
            trendDirection = "INCREASING";
        } else if (growthRate < -5.0) {
            trendDirection = "DECREASING";
        } else {
            trendDirection = "STABLE";
        }
        trend.setTrendDirection(trendDirection);
        
        // 月別推移
        try {
            List<Map<String, Object>> monthlyTrend = jdbcTemplate.queryForList(
                "SELECT " +
                "  EXTRACT(month FROM created_at) as month, " +
                "  EXTRACT(year FROM created_at) as year, " +
                "  COUNT(*) as count " +
                "FROM books " +
                "WHERE genre = ? AND created_at BETWEEN ? AND ? " +
                "GROUP BY EXTRACT(year FROM created_at), EXTRACT(month FROM created_at) " +
                "ORDER BY year, month",
                genre, start, end.plusDays(1));
            trend.setMonthlyTrend(monthlyTrend);
            
            // ピーク月とロー月
            if (!monthlyTrend.isEmpty()) {
                Map<String, Object> peakMonth = monthlyTrend.stream()
                    .max(Comparator.comparing(m -> ((Number) m.get("count")).intValue()))
                    .orElse(null);
                
                Map<String, Object> lowMonth = monthlyTrend.stream()
                    .min(Comparator.comparing(m -> ((Number) m.get("count")).intValue()))
                    .orElse(null);
                
                if (peakMonth != null) {
                    trend.setPeakMonth(((Number) peakMonth.get("month")).intValue());
                }
                if (lowMonth != null) {
                    trend.setLowMonth(((Number) lowMonth.get("month")).intValue());
                }
            }
        } catch (Exception e) {
            trend.setMonthlyTrend(new ArrayList<>());
        }
        
        return trend;
    }
    
    private GenreAnalysis.GenreUserDemographics calculateUserDemographics(String genre, LocalDate start, LocalDate end) {
        GenreAnalysis.GenreUserDemographics demographics = new GenreAnalysis.GenreUserDemographics();
        
        // 新規ユーザー数（このジャンルを初めて読むユーザー）
        Integer newUsers = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT user_id) FROM books b1 " +
            "WHERE b1.genre = ? AND b1.created_at BETWEEN ? AND ? " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM books b2 " +
            "  WHERE b2.user_id = b1.user_id AND b2.genre = ? " +
            "  AND b2.created_at < ?" +
            ")",
            Integer.class, genre, start, end.plusDays(1), genre, start);
        demographics.setNewUsersCount(newUsers);
        
        // リピートユーザー数
        Integer returningUsers = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT user_id) FROM books b1 " +
            "WHERE b1.genre = ? AND b1.created_at BETWEEN ? AND ? " +
            "AND EXISTS (" +
            "  SELECT 1 FROM books b2 " +
            "  WHERE b2.user_id = b1.user_id AND b2.genre = ? " +
            "  AND b2.created_at < ?" +
            ")",
            Integer.class, genre, start, end.plusDays(1), genre, start);
        demographics.setReturningUsersCount(returningUsers);
        
        // このジャンルの上位読者
        List<String> topUsers = jdbcTemplate.queryForList(
            "SELECT u.username FROM users u " +
            "JOIN books b ON u.id = b.user_id " +
            "WHERE b.genre = ? AND b.created_at BETWEEN ? AND ? " +
            "GROUP BY u.id, u.username " +
            "ORDER BY COUNT(*) DESC LIMIT 5",
            String.class, genre, start, end.plusDays(1));
        demographics.setTopUsers(topUsers);
        
        return demographics;
    }
    
    private List<GenreAnalysis.PopularBookInGenre> calculatePopularBooks(String genre, LocalDate start, LocalDate end) {
        List<Map<String, Object>> popularBooksData = jdbcTemplate.queryForList(
            "SELECT " +
            "  b.title, " +
            "  COALESCE(a.name, '不明') as author, " +
            "  b.publisher, " +
            "  COUNT(b.id) as registration_count, " +
            "  COUNT(CASE WHEN rs.status = 'COMPLETED' THEN 1 END) as completion_count, " +
            "  ROUND(" +
            "    COUNT(CASE WHEN rs.status = 'COMPLETED' THEN 1 END) * 100.0 / " +
            "    NULLIF(COUNT(b.id), 0), 2" +
            "  ) as completion_rate " +
            "FROM books b " +
            "LEFT JOIN read_status rs ON b.id = rs.book_id " +
            "LEFT JOIN book_author ba ON b.id = ba.book_id " +
            "LEFT JOIN author a ON ba.author_id = a.id " +
            "WHERE b.genre = ? AND b.created_at BETWEEN ? AND ? " +
            "GROUP BY b.title, a.name, b.publisher " +
            "ORDER BY registration_count DESC, completion_rate DESC " +
            "LIMIT 10",
            genre, start, end.plusDays(1));
        
        List<GenreAnalysis.PopularBookInGenre> popularBooks = new ArrayList<>();
        for (Map<String, Object> data : popularBooksData) {
            GenreAnalysis.PopularBookInGenre book = new GenreAnalysis.PopularBookInGenre();
            book.setTitle((String) data.get("title"));
            book.setAuthor((String) data.get("author"));
            book.setPublisher((String) data.get("publisher"));
            book.setRegistrationCount(((Number) data.get("registration_count")).intValue());
            book.setCompletionRate(((Number) data.get("completion_rate")).doubleValue());
            
            // 人気スコア計算（登録数 + 読了率の重み付け）
            Double popularityScore = book.getRegistrationCount() * 1.0 + 
                                   (book.getCompletionRate() / 100.0) * 10.0;
            book.setPopularityScore(popularityScore);
            
            popularBooks.add(book);
        }
        
        return popularBooks;
    }
    
    private GenreAnalysis.GenreSeasonality calculateSeasonality(String genre) {
        GenreAnalysis.GenreSeasonality seasonality = new GenreAnalysis.GenreSeasonality();
        
        // 過去1年の四半期別分布
        LocalDate oneYearAgo = LocalDate.now().minusYears(1);
        
        try {
            List<Map<String, Object>> quarterlyData = jdbcTemplate.queryForList(
                "SELECT " +
                "  CASE " +
                "    WHEN EXTRACT(month FROM created_at) IN (1,2,3) THEN 'Q1' " +
                "    WHEN EXTRACT(month FROM created_at) IN (4,5,6) THEN 'Q2' " +
                "    WHEN EXTRACT(month FROM created_at) IN (7,8,9) THEN 'Q3' " +
                "    ELSE 'Q4' " +
                "  END as quarter, " +
                "  COUNT(*) as count " +
                "FROM books " +
                "WHERE genre = ? AND created_at >= ? " +
                "GROUP BY " +
                "  CASE " +
                "    WHEN EXTRACT(month FROM created_at) IN (1,2,3) THEN 'Q1' " +
                "    WHEN EXTRACT(month FROM created_at) IN (4,5,6) THEN 'Q2' " +
                "    WHEN EXTRACT(month FROM created_at) IN (7,8,9) THEN 'Q3' " +
                "    ELSE 'Q4' " +
                "  END " +
                "ORDER BY quarter",
                genre, oneYearAgo);
            
            Map<String, Double> quarterlyDistribution = new HashMap<>();
            Integer totalCount = 0;
            
            for (Map<String, Object> data : quarterlyData) {
                Integer count = ((Number) data.get("count")).intValue();
                totalCount += count;
                quarterlyDistribution.put((String) data.get("quarter"), count.doubleValue());
            }
            
            // パーセンテージに変換
            if (totalCount > 0) {
                final double finalTotalCount = totalCount.doubleValue();
                quarterlyDistribution.replaceAll((k, v) -> (v / finalTotalCount) * 100);
            }
            
            seasonality.setQuarterlyDistribution(quarterlyDistribution);
            
            // ピークシーズン特定
            String peakSeason = quarterlyDistribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("不明");
            seasonality.setPeakSeason(peakSeason);
            
            // 季節性指数計算（標準偏差ベース）
            double mean = quarterlyDistribution.values().stream()
                .mapToDouble(Double::doubleValue)
                .average().orElse(0.0);
            
            double variance = quarterlyDistribution.values().stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0.0);
            
            double seasonalityIndex = Math.sqrt(variance);
            seasonality.setSeasonalityIndex(seasonalityIndex);
        } catch (Exception e) {
            seasonality.setQuarterlyDistribution(new HashMap<>());
            seasonality.setPeakSeason("不明");
            seasonality.setSeasonalityIndex(0.0);
        }
        
        return seasonality;
    }
}