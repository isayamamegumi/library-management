package com.library.management.batch;

import com.library.management.dto.UserStats;
import com.library.management.entity.User;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class UserStatsProcessor implements ItemProcessor<User, UserStats> {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Override
    public UserStats process(User user) throws Exception {
        UserStats stats = new UserStats();
        stats.setUserId(user.getId());
        stats.setUsername(user.getUsername());
        stats.setTargetMonth(LocalDate.now().withDayOfMonth(1));
        
        // 今月の統計計算
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        
        // 総登録書籍数
        Integer totalBooks = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books WHERE user_id = ? AND created_at >= ?",
            Integer.class, user.getId(), startOfMonth);
        stats.setTotalBooks(totalBooks);
        
        // 読了書籍数
        Integer completedBooks = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books b JOIN read_status rs ON b.id = rs.book_id " +
            "WHERE b.user_id = ? AND rs.status = 'COMPLETED' AND rs.updated_at >= ?",
            Integer.class, user.getId(), startOfMonth);
        stats.setCompletedBooks(completedBooks);
        
        // 読書中書籍数
        Integer readingBooks = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books b JOIN read_status rs ON b.id = rs.book_id " +
            "WHERE b.user_id = ? AND rs.status = 'READING' AND rs.updated_at >= ?",
            Integer.class, user.getId(), startOfMonth);
        stats.setReadingBooks(readingBooks);
        
        // 読了率計算
        Double progressRate = totalBooks > 0 ? 
            (completedBooks.doubleValue() / totalBooks) * 100 : 0.0;
        stats.setProgressRate(progressRate);
        
        // 最頻出ジャンル取得
        try {
            String favoriteGenre = jdbcTemplate.queryForObject(
                "SELECT genre FROM books WHERE user_id = ? AND created_at >= ? " +
                "GROUP BY genre ORDER BY COUNT(*) DESC LIMIT 1",
                String.class, user.getId(), startOfMonth);
            stats.setFavoriteGenre(favoriteGenre);
        } catch (Exception e) {
            stats.setFavoriteGenre("未設定");
        }
        
        return stats;
    }
}