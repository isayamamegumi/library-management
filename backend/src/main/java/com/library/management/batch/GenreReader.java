package com.library.management.batch;

import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.List;

@Component("genreItemReader")
public class GenreReader implements ItemReader<String> {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    private List<String> genres;
    private int currentIndex = 0;
    
    @PostConstruct
    public void initialize() {
        // 過去3ヶ月で登録があったジャンルを取得
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        
        genres = jdbcTemplate.queryForList(
            "SELECT DISTINCT g.name FROM genres g " +
            "JOIN books b ON g.id = b.genre_id " +
            "WHERE b.created_at >= ? " +
            "ORDER BY g.name",
            String.class, threeMonthsAgo);
        
        System.out.println("分析対象ジャンル数: " + genres.size());
    }
    
    @Override
    public String read() throws Exception {
        if (currentIndex < genres.size()) {
            return genres.get(currentIndex++);
        }
        return null;
    }
}