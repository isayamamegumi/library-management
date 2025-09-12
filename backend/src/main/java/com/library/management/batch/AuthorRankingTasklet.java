package com.library.management.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.management.dto.BookRanking;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AuthorRankingTasklet implements Tasklet {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private PopularityRankingTasklet popularityRankingTasklet;
    
    @Override
    public RepeatStatus execute(StepContribution contribution, 
                               ChunkContext chunkContext) throws Exception {
        
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);
        
        // 著者別人気ランキング
        List<Map<String, Object>> authorData = jdbcTemplate.queryForList(
            "SELECT " +
            "  COALESCE(a.name, '不明') as author_name, " +
            "  COUNT(b.id) as total_books, " +
            "  COUNT(DISTINCT b.title) as unique_titles, " +
            "  COUNT(CASE WHEN rs.status = 'COMPLETED' THEN 1 END) as total_completions, " +
            "  ROUND(" +
            "    COUNT(CASE WHEN rs.status = 'COMPLETED' THEN 1 END) * 100.0 / " +
            "    NULLIF(COUNT(b.id), 0), 2" +
            "  ) as avg_completion_rate, " +
            "  STRING_AGG(DISTINCT b.genre, ', ') as genres " +
            "FROM books b " +
            "LEFT JOIN read_status rs ON b.id = rs.book_id " +
            "LEFT JOIN book_author ba ON b.id = ba.book_id " +
            "LEFT JOIN author a ON ba.author_id = a.id " +
            "WHERE b.created_at >= ? " +
            "GROUP BY a.name " +
            "ORDER BY total_books DESC, total_completions DESC " +
            "LIMIT 20",
            startDate);
        
        // ランキングアイテム作成
        List<BookRanking.RankingItem> rankings = new ArrayList<>();
        for (int i = 0; i < authorData.size(); i++) {
            Map<String, Object> data = authorData.get(i);
            
            BookRanking.RankingItem item = new BookRanking.RankingItem();
            item.setRank(i + 1);
            item.setAuthorName((String) data.get("author_name"));
            item.setRegistrationCount(((Number) data.get("total_books")).intValue());
            item.setCompletionCount(((Number) data.get("total_completions")).intValue());
            item.setCompletionRate(((Number) data.get("avg_completion_rate")).doubleValue());
            item.setGenre((String) data.get("genres"));  // 著者が書いているジャンル一覧
            
            // 著者専用の追加情報
            item.setTitle(((Number) data.get("unique_titles")).intValue() + "作品");
            
            rankings.add(item);
        }
        
        // メタデータ作成
        BookRanking.RankingMetadata metadata = new BookRanking.RankingMetadata();
        metadata.setPeriodStart(startDate);
        metadata.setPeriodEnd(endDate);
        metadata.setRankingCriteria("著者別総登録数");
        
        // BookRanking作成
        BookRanking ranking = new BookRanking();
        ranking.setRankingType("AUTHOR");
        ranking.setGeneratedAt(LocalDateTime.now());
        ranking.setRankings(rankings);
        ranking.setMetadata(metadata);
        
        // 結果保存
        popularityRankingTasklet.saveRanking(ranking, "AUTHOR_RANKING");
        
        System.out.println("著者ランキング生成完了: TOP" + rankings.size());
        return RepeatStatus.FINISHED;
    }
}