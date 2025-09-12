package com.library.management.batch;

import com.library.management.dto.GenreAnalysis;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Primary
public class GenreAnalysisProcessorSimple implements ItemProcessor<String, GenreAnalysis> {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Override
    public GenreAnalysis process(String genreName) throws Exception {
        System.out.println("ジャンル分析処理中: " + genreName);
        
        LocalDate analysisDate = LocalDate.now();
        LocalDate periodStart = analysisDate.minusMonths(3);  // 過去3ヶ月
        LocalDate periodEnd = analysisDate;
        
        GenreAnalysis analysis = new GenreAnalysis();
        analysis.setGenre(genreName);
        analysis.setAnalysisDate(analysisDate);
        analysis.setPeriodStart(periodStart);
        analysis.setPeriodEnd(periodEnd);
        
        // 基本統計のみ実装（簡易版）
        GenreAnalysis.GenreBasicStats basicStats = calculateBasicStats(genreName, periodStart, periodEnd);
        analysis.setBasicStats(basicStats);
        
        // 他の分析は空のオブジェクトで初期化
        analysis.setTrendAnalysis(new GenreAnalysis.GenreTrendAnalysis());
        analysis.setUserDemographics(new GenreAnalysis.GenreUserDemographics());
        analysis.setPopularBooks(new java.util.ArrayList<>());
        analysis.setSeasonality(new GenreAnalysis.GenreSeasonality());
        
        return analysis;
    }
    
    private GenreAnalysis.GenreBasicStats calculateBasicStats(String genreName, LocalDate start, LocalDate end) {
        GenreAnalysis.GenreBasicStats stats = new GenreAnalysis.GenreBasicStats();
        
        try {
            // 総登録数（JOINを使用）
            Integer totalBooks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM books b " +
                "JOIN genres g ON b.genre_id = g.id " +
                "WHERE g.name = ? AND b.created_at BETWEEN ? AND ?",
                Integer.class, genreName, start, end.plusDays(1));
            stats.setTotalBooks(totalBooks != null ? totalBooks : 0);
            
            // 読了数
            Integer completedBooks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM books b " +
                "JOIN genres g ON b.genre_id = g.id " +
                "JOIN read_status rs ON b.read_status_id = rs.id " +
                "WHERE g.name = ? AND rs.status = 'COMPLETED' " +
                "AND b.created_at BETWEEN ? AND ?",
                Integer.class, genreName, start, end.plusDays(1));
            stats.setCompletedBooks(completedBooks != null ? completedBooks : 0);
            
            // 読了率
            Double completionRate = stats.getTotalBooks() > 0 ? 
                (stats.getCompletedBooks().doubleValue() / stats.getTotalBooks()) * 100 : 0.0;
            stats.setCompletionRate(completionRate);
            
            // 他のフィールドはデフォルト値
            stats.setReadingBooks(0);
            stats.setOnHoldBooks(0);
            
        } catch (Exception e) {
            System.err.println("ジャンル統計計算エラー: " + e.getMessage());
            // エラー時はデフォルト値を設定
            stats.setTotalBooks(0);
            stats.setCompletedBooks(0);
            stats.setReadingBooks(0);
            stats.setOnHoldBooks(0);
            stats.setCompletionRate(0.0);
        }
        
        return stats;
    }
}