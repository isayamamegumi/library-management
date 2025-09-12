package com.library.management.batch;

import com.library.management.dto.UserReadingHistory;
import com.library.management.entity.User;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class UserHistoryProcessor implements ItemProcessor<User, UserReadingHistory> {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Override
    public UserReadingHistory process(User user) throws Exception {
        UserReadingHistory history = new UserReadingHistory();
        history.setUserId(user.getId());
        history.setUsername(user.getUsername());
        
        // 過去1年間のデータを対象
        LocalDate fromDate = LocalDate.now().minusYears(1);
        LocalDate toDate = LocalDate.now();
        history.setFromDate(fromDate);
        history.setToDate(toDate);
        
        // 基本統計
        calculateBasicStats(user.getId(), fromDate, history);
        
        // 好みジャンル・著者分析
        analyzeFavorites(user.getId(), fromDate, history);
        
        // 読書ペース分析
        analyzeReadingPace(user.getId(), fromDate, history);
        
        // 読書パターン分析
        analyzeReadingPattern(user.getId(), fromDate, history);
        
        return history;
    }
    
    private void calculateBasicStats(Long userId, LocalDate fromDate, UserReadingHistory history) {
        // 登録総数
        Integer totalRegistered = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books WHERE user_id = ? AND created_at >= ?",
            Integer.class, userId, fromDate);
        history.setTotalBooksRegistered(totalRegistered);
        
        // 読了総数
        Integer totalCompleted = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books b JOIN read_status rs ON b.id = rs.book_id " +
            "WHERE b.user_id = ? AND rs.status = 'COMPLETED' AND b.created_at >= ?",
            Integer.class, userId, fromDate);
        history.setTotalBooksCompleted(totalCompleted);
        
        // 読書中
        Integer inProgress = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books b JOIN read_status rs ON b.id = rs.book_id " +
            "WHERE b.user_id = ? AND rs.status = 'READING'",
            Integer.class, userId);
        history.setBooksInProgress(inProgress);
        
        // 中断中
        Integer onHold = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books b JOIN read_status rs ON b.id = rs.book_id " +
            "WHERE b.user_id = ? AND rs.status = 'ON_HOLD'",
            Integer.class, userId);
        history.setBooksOnHold(onHold);
        
        // 読了率
        Double completionRate = totalRegistered > 0 ? 
            (totalCompleted.doubleValue() / totalRegistered) * 100 : 0.0;
        history.setCompletionRate(completionRate);
    }
    
    private void analyzeFavorites(Long userId, LocalDate fromDate, UserReadingHistory history) {
        // 好みジャンルTOP3
        List<String> favoriteGenres = jdbcTemplate.queryForList(
            "SELECT g.name FROM books b " +
            "JOIN genres g ON b.genre_id = g.id " +
            "WHERE b.user_id = ? AND b.created_at >= ? " +
            "GROUP BY g.name ORDER BY COUNT(*) DESC LIMIT 3",
            String.class, userId, fromDate);
        history.setFavoriteGenres(favoriteGenres);
        
        // 好み著者TOP3
        List<String> favoriteAuthors = jdbcTemplate.queryForList(
            "SELECT a.name FROM books b " +
            "JOIN book_author ba ON b.id = ba.book_id " +
            "JOIN author a ON ba.author_id = a.id " +
            "WHERE b.user_id = ? AND b.created_at >= ? " +
            "GROUP BY a.name ORDER BY COUNT(*) DESC LIMIT 3",
            String.class, userId, fromDate);
        history.setFavoriteAuthors(favoriteAuthors);
    }
    
    private void analyzeReadingPace(Long userId, LocalDate fromDate, UserReadingHistory history) {
        // 平均読書日数（読了した本の登録から完了までの平均日数）
        try {
            Double averageDays = jdbcTemplate.queryForObject(
                "SELECT AVG(EXTRACT(epoch FROM (rs.updated_at - b.created_at))/86400) " +
                "FROM books b JOIN read_status rs ON b.id = rs.book_id " +
                "WHERE b.user_id = ? AND rs.status = 'COMPLETED' AND b.created_at >= ?",
                Double.class, userId, fromDate);
            history.setAverageReadingDays(averageDays != null ? averageDays : 0.0);
        } catch (Exception e) {
            history.setAverageReadingDays(0.0);
        }
        
        // 連続読書日数（最長の連続読書期間）
        Integer streak = calculateReadingStreak(userId, fromDate);
        history.setReadingStreak(streak);
    }
    
    private void analyzeReadingPattern(Long userId, LocalDate fromDate, UserReadingHistory history) {
        // 読書開始時間の分析（朝型/夜型の判定）
        try {
            List<Object[]> hourlyActivity = jdbcTemplate.query(
                "SELECT EXTRACT(hour FROM created_at) as hour, COUNT(*) as count " +
                "FROM books WHERE user_id = ? AND created_at >= ? " +
                "GROUP BY EXTRACT(hour FROM created_at) " +
                "ORDER BY count DESC LIMIT 1",
                (rs, rowNum) -> new Object[]{rs.getInt("hour"), rs.getInt("count")},
                userId, fromDate);
            
            String pattern = "不明";
            if (!hourlyActivity.isEmpty()) {
                Integer mostActiveHour = (Integer) hourlyActivity.get(0)[0];
                if (mostActiveHour >= 6 && mostActiveHour < 12) {
                    pattern = "朝型読書";
                } else if (mostActiveHour >= 12 && mostActiveHour < 18) {
                    pattern = "昼型読書";
                } else if (mostActiveHour >= 18 && mostActiveHour < 24) {
                    pattern = "夜型読書";
                } else {
                    pattern = "深夜型読書";
                }
            }
            history.setReadingPattern(pattern);
        } catch (Exception e) {
            history.setReadingPattern("不明");
        }
    }
    
    private Integer calculateReadingStreak(Long userId, LocalDate fromDate) {
        try {
            List<LocalDate> readingDates = jdbcTemplate.queryForList(
                "SELECT DISTINCT DATE(created_at) as reading_date " +
                "FROM books WHERE user_id = ? AND created_at >= ? " +
                "ORDER BY reading_date",
                LocalDate.class, userId, fromDate);
            
            if (readingDates.isEmpty()) return 0;
            
            int maxStreak = 1;
            int currentStreak = 1;
            
            for (int i = 1; i < readingDates.size(); i++) {
                LocalDate prevDate = readingDates.get(i - 1);
                LocalDate currentDate = readingDates.get(i);
                
                if (prevDate.plusDays(1).equals(currentDate)) {
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
}