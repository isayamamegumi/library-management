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
public class CompletionRankingTasklet implements Tasklet {
    
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
        
        // 読了率ランキング（最低5件以上登録された書籍対象）
        List<Map<String, Object>> completionData = jdbcTemplate.queryForList(
            "SELECT " +
            "  b.title, " +
            "  b.publisher, " +
            "  b.genre, " +
            "  COALESCE(a.name, '不明') as author_name, " +
            "  COUNT(b.id) as registration_count, " +
            "  COUNT(CASE WHEN rs.status = 'COMPLETED' THEN 1 END) as completion_count, " +
            "  ROUND(" +
            "    COUNT(CASE WHEN rs.status = 'COMPLETED' THEN 1 END) * 100.0 / " +
            "    COUNT(b.id), 2" +
            "  ) as completion_rate " +
            "FROM books b " +
            "LEFT JOIN read_status rs ON b.id = rs.book_id " +
            "LEFT JOIN book_author ba ON b.id = ba.book_id " +
            "LEFT JOIN author a ON ba.author_id = a.id " +
            "WHERE b.created_at >= ? " +
            "GROUP BY b.title, b.publisher, b.genre, a.name " +
            "HAVING COUNT(b.id) >= 5 " +
            "ORDER BY completion_rate DESC, registration_count DESC " +
            "LIMIT 30",
            startDate);
        
        // ランキングアイテム作成
        List<BookRanking.RankingItem> rankings = new ArrayList<>();
        for (int i = 0; i < completionData.size(); i++) {
            Map<String, Object> data = completionData.get(i);
            
            BookRanking.RankingItem item = new BookRanking.RankingItem();
            item.setRank(i + 1);
            item.setTitle((String) data.get("title"));
            item.setPublisher((String) data.get("publisher"));
            item.setAuthorName((String) data.get("author_name"));
            item.setGenre((String) data.get("genre"));
            item.setRegistrationCount(((Number) data.get("registration_count")).intValue());
            item.setCompletionCount(((Number) data.get("completion_count")).intValue());
            item.setCompletionRate(((Number) data.get("completion_rate")).doubleValue());
            
            rankings.add(item);
        }
        
        // メタデータ作成
        BookRanking.RankingMetadata metadata = new BookRanking.RankingMetadata();
        metadata.setPeriodStart(startDate);
        metadata.setPeriodEnd(endDate);
        metadata.setRankingCriteria("読了率（最低5件以上登録された書籍）");
        
        // BookRanking作成
        BookRanking ranking = new BookRanking();
        ranking.setRankingType("COMPLETION_RATE");
        ranking.setGeneratedAt(LocalDateTime.now());
        ranking.setRankings(rankings);
        ranking.setMetadata(metadata);
        
        // 結果保存
        popularityRankingTasklet.saveRanking(ranking, "COMPLETION_RATE_RANKING");
        
        System.out.println("読了率ランキング生成完了: TOP" + rankings.size());
        return RepeatStatus.FINISHED;
    }
}